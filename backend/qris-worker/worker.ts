export interface Env {
  DB: D1Database;
  APP_TOKEN: string;
  MIDTRANS_SERVER_KEY: string;
  MIDTRANS_ENV: string;
  QRIS_EXPIRY_MINUTES: string;
  PUBLIC_WORKER_URL?: string;
}

type Status = "pending" | "paid" | "expired" | "canceled";
interface Order {
  order_id: string;
  gross_amount: number;
  status: Status;
  qr_url: string | null;
  expires_at: number;
}
interface MidtransResult {
  order_id?: string;
  gross_amount?: string;
  status_code?: string;
  transaction_status?: string;
  fraud_status?: string;
  payment_type?: string;
  transaction_id?: string;
  actions?: { name: string; url: string }[];
  signature_key?: string;
}
type Fetcher = (request: Request) => Promise<Response>;
const orderIdPattern = /^jusgo-[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/;
const json = (body: unknown, status = 200) =>
  Response.json(body, { status, headers: { "Cache-Control": "no-store" } });
const error = (status: number, code: string) => json({ error: code }, status);

async function signature(serverKey: string, { order_id, status_code, gross_amount }: MidtransResult) {
  const bytes = new TextEncoder().encode(`${order_id}${status_code}${gross_amount}${serverKey}`);
  return Array.from(new Uint8Array(await crypto.subtle.digest("SHA-512", bytes)),
    byte => byte.toString(16).padStart(2, "0")).join("");
}

function equal(left: string, right: string) {
  if (left.length !== right.length) return false;
  let difference = 0;
  for (let i = 0; i < left.length; i++) difference |= left.charCodeAt(i) ^ right.charCodeAt(i);
  return difference === 0;
}

function parseStatus(result: MidtransResult): Status {
  switch (result.transaction_status) {
    case "settlement": return "paid";
    case "capture": return result.fraud_status === "accept" ? "paid" : "pending";
    case "expire": return "expired";
    case "cancel":
    case "deny":
    case "failure": return "canceled";
    default: return "pending";
  }
}

function sameAmount(value: unknown, gross: number) {
  return typeof value === "string" && /^\d+(?:\.0{1,2})?$/.test(value) && Number(value) === gross;
}

export async function handle(request: Request, env: Env, fetcher: Fetcher = fetch) {
  const url = new URL(request.url);
  if (!env.APP_TOKEN || !env.MIDTRANS_SERVER_KEY) return error(503, "not_configured");
  const notification = url.pathname === "/midtrans/notify" && request.method === "POST";
  if (!notification && !equal(request.headers.get("Authorization") ?? "", `Bearer ${env.APP_TOKEN}`)) {
    return error(401, "unauthorized");
  }
  const midtrans = {
    sandbox: "https://api.sandbox.midtrans.com",
    production: "https://api.midtrans.com",
  }[env.MIDTRANS_ENV as "sandbox" | "production"];
  if (!midtrans) return error(503, "not_configured");
  const duration = Number(env.QRIS_EXPIRY_MINUTES);
  if (!Number.isInteger(duration) || duration < 1 || duration > 60) return error(503, "not_configured");

  const orderById = (id: string) =>
    env.DB.prepare("SELECT * FROM orders WHERE order_id = ?").bind(id).first<Order>();
  const callMidtrans = async (path: string, init: RequestInit = {}) => {
    const headers = new Headers(init.headers);
    headers.set("Authorization", `Basic ${btoa(env.MIDTRANS_SERVER_KEY + ":")}`);
    headers.set("Accept", "application/json");
    return fetcher(new Request(midtrans + path, { ...init, headers, redirect: "error", signal: AbortSignal.timeout(10000) }));
  };
  const storeResult = async (order: Order, result: MidtransResult) => {
    if (result.order_id !== order.order_id || result.payment_type !== "qris" ||
        !sameAmount(result.gross_amount, order.gross_amount)) throw new Error("Unexpected Midtrans order");
    const status = parseStatus(result);
    const action = result.actions?.find(it => it.name === "generate-qr-code")?.url;
    const candidate = action || (/^[0-9a-f-]{36}$/i.test(result.transaction_id ?? "")
      ? `${midtrans}/v2/qris/${result.transaction_id}/qr-code` : "");
    const qrUrl = candidate.startsWith(midtrans + "/v2/qris/") ? candidate : null;
    await env.DB.prepare(
      "UPDATE orders SET status = ?, qr_url = COALESCE(?, qr_url) WHERE order_id = ? AND status = 'pending'",
    ).bind(status, qrUrl, order.order_id).run();
    return orderById(order.order_id);
  };
  const refresh = async (order: Order, allowMissing: boolean) => {
    const response = await callMidtrans(`/v2/${order.order_id}/status`);
    const result: MidtransResult = await response.json();
    if (response.status === 404 || result.status_code === "404") {
      if (!allowMissing) throw new Error("Midtrans order not found");
      if (!order.qr_url && Date.now() >= order.expires_at) {
        await env.DB.prepare(
          "UPDATE orders SET status = 'expired' WHERE order_id = ? AND status = 'pending' AND qr_url IS NULL",
        ).bind(order.order_id).run();
      }
      return orderById(order.order_id);
    }
    if (!response.ok) throw new Error("Midtrans status unavailable");
    return storeResult(order, result);
  };

  try {
    if (notification) {
      const data = await request.json().catch(() => null) as MidtransResult | null;
      if (!data || typeof data.order_id !== "string" || !orderIdPattern.test(data.order_id) ||
          typeof data.gross_amount !== "string" || typeof data.status_code !== "string" ||
          typeof data.signature_key !== "string" ||
          !equal(await signature(env.MIDTRANS_SERVER_KEY, data), data.signature_key)) {
        return error(401, "invalid_signature");
      }
      const order = await orderById(data.order_id);
      if (!order || !sameAmount(data.gross_amount, order.gross_amount)) return error(404, "order_not_found");
      if (order.status === "pending") await refresh(order, false);
      return json({ ok: true });
    }

    if (request.method === "POST" && url.pathname === "/orders") {
      const data = await request.json().catch(() => null) as Partial<Order> | null;
      const id = data?.order_id;
      const gross = data?.gross_amount;
      if (typeof id !== "string" || !orderIdPattern.test(id) || typeof gross !== "number" ||
          !Number.isSafeInteger(gross) || gross <= 0) return error(400, "invalid_order");
      const start = Math.floor(Date.now() / 1000) * 1000;
      await env.DB.prepare("INSERT OR IGNORE INTO orders (order_id, gross_amount, expires_at) VALUES (?, ?, ?)")
        .bind(id, gross, start + duration * 60000).run();
      const order = await orderById(id);
      if (!order || order.gross_amount !== gross) return error(409, "amount_mismatch");
      if (order.status !== "pending" || order.qr_url) return json(order);
      if (Date.now() >= order.expires_at) return json(await refresh(order, true));

      const webhook = new URL("/midtrans/notify", env.PUBLIC_WORKER_URL || url.origin);
      if (webhook.protocol !== "https:") return error(503, "https_webhook_required");
      const jakartaStart = new Date(order.expires_at - duration * 60000 + 7 * 3600000)
        .toISOString().slice(0, 19).replace("T", " ");
      const response = await callMidtrans("/v2/charge", {
        method: "POST",
        headers: { "Content-Type": "application/json", "X-Override-Notification": webhook.href },
        body: JSON.stringify({
          payment_type: "qris",
          transaction_details: { order_id: id, gross_amount: gross },
          custom_expiry: { order_time: jakartaStart + " +0700", expiry_duration: duration, unit: "minute" },
        }),
      });
      const result: MidtransResult = await response.json();
      if (response.status === 406 || result.status_code === "406") return json(await refresh(order, false));
      if (!response.ok) throw new Error("Midtrans charge unavailable");
      return json(await storeResult(order, result));
    }

    const id = url.pathname.startsWith("/orders/") ? url.pathname.slice(8) : "";
    if (request.method === "GET" && orderIdPattern.test(id)) {
      const order = await orderById(id);
      if (!order) return error(404, "order_not_found");
      return json(order.status === "pending" ? await refresh(order, true) : order);
    }
    return error(404, "not_found");
  } catch {
    return error(503, "payment_service_unavailable");
  }
}

export default { fetch: (request: Request, env: Env) => handle(request, env) };
