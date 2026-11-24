import { createHash } from "node:crypto";
import { readFile } from "node:fs/promises";
import { Miniflare } from "miniflare";
import { afterAll, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import { handle, type Env } from "./worker";

const orderId = "jusgo-01ae0fb1-2dc0-4c3b-a5b6-5ee3a07cc8ac";
const origin = "https://worker.example";
const qrUrl = "https://api.sandbox.midtrans.com/v2/qris/abc/qr-code";
const chargeResult = {
  order_id: orderId, gross_amount: "15000.00", payment_type: "qris", transaction_status: "pending",
  actions: [{ name: "generate-qr-code", url: qrUrl }],
};
const midtrans = vi.fn<(request: Request) => Promise<Response>>();
let miniflare: Miniflare;
let env: Env;

beforeAll(async () => {
  miniflare = new Miniflare({ modules: true, script: "export default {};", d1Databases: ["DB"] });
  const db = await miniflare.getD1Database("DB");
  await db.prepare(await readFile(new URL("./migrations/0001_orders.sql", import.meta.url), "utf8")).run();
  env = {
    DB: db as unknown as D1Database, APP_TOKEN: "test-app", MIDTRANS_SERVER_KEY: "test-server",
    MIDTRANS_ENV: "sandbox", QRIS_EXPIRY_MINUTES: "15",
  };
});
afterAll(async () => { await miniflare?.dispose(); });
beforeEach(async () => {
  await env.DB.prepare("DELETE FROM orders").run();
  midtrans.mockReset().mockResolvedValue(Response.json(chargeResult));
});

function request(path: string, body?: unknown, token = env.APP_TOKEN) {
  return handle(new Request(origin + path, {
    method: body ? "POST" : "GET",
    headers: { Authorization: `Bearer ${token}` },
    body: body ? JSON.stringify(body) : undefined,
  }), env, midtrans);
}
const create = () => request("/orders", { order_id: orderId, gross_amount: 15000 });
const status = () => request(`/orders/${orderId}`);
function notify(overrides: object = {}) {
  const data = { order_id: orderId, status_code: "200", gross_amount: "15000.00", transaction_status: "settlement" };
  const hash = createHash("sha512").update(data.order_id + data.status_code + data.gross_amount + env.MIDTRANS_SERVER_KEY);
  return request("/midtrans/notify", { ...data, signature_key: hash.digest("hex"), ...overrides }, "");
}

describe("Midtrans QRIS", () => {
  it("authenticates creation and polling, charges the exact amount, and reuses the same QR", async () => {
    expect((await request("/orders", { order_id: orderId, gross_amount: 15000 }, "wrong")).status).toBe(401);
    expect((await request(`/orders/${orderId}`, undefined, "wrong")).status).toBe(401);
    midtrans.mockImplementation(async req => {
      expect(req.url).toBe("https://api.sandbox.midtrans.com/v2/charge");
      expect(req.headers.get("X-Override-Notification")).toBe(origin + "/midtrans/notify");
      expect(req.headers.get("Authorization")).toBe("Basic " + btoa("test-server:"));
      expect(await req.json()).toMatchObject({
        payment_type: "qris", transaction_details: { order_id: orderId, gross_amount: 15000 },
        custom_expiry: { expiry_duration: 15, unit: "minute" },
      });
      return Response.json(chargeResult);
    });
    const first = await (await create()).json();
    expect(first).toMatchObject({ qr_url: qrUrl, status: "pending", gross_amount: 15000 });
    expect(await (await create()).json()).toEqual(first);
    expect((await request("/orders", { order_id: orderId, gross_amount: 15001 })).status).toBe(409);
    expect(midtrans).toHaveBeenCalledTimes(1);
  });

  it("rejects a bad signature and does not update the order", async () => {
    await create();
    expect((await notify({ signature_key: "invalid" })).status).toBe(401);
    expect(midtrans).toHaveBeenCalledTimes(1);
    expect(await env.DB.prepare("SELECT status FROM orders").first("status")).toBe("pending");
  });

  it("uses authoritative status, and duplicate or delayed webhooks cannot change a final order", async () => {
    await create();
    midtrans.mockImplementation(async () => Response.json(chargeResult));
    expect((await notify()).status).toBe(200);
    expect(await env.DB.prepare("SELECT status FROM orders").first("status")).toBe("pending");
    midtrans.mockImplementation(async () => Response.json({ ...chargeResult, transaction_status: "settlement" }));
    expect((await notify()).status).toBe(200);
    midtrans.mockClear().mockImplementation(async () => Response.json({ ...chargeResult, transaction_status: "expire" }));
    expect((await notify()).status).toBe(200);
    expect(await (await status()).json()).toMatchObject({ status: "paid" });
    expect(midtrans).not.toHaveBeenCalled();
  });

  it.each([["expire", "expired"], ["cancel", "canceled"], ["settlement", "paid"]])(
    "reconciles %s via polling without a webhook", async (providerStatus, appStatus) => {
      await create();
      midtrans.mockImplementation(async () => Response.json({ ...chargeResult, transaction_status: providerStatus }));
      expect(await (await status()).json()).toMatchObject({ status: appStatus });
    },
  );

  it("does not accept a payment with a different amount", async () => {
    await create();
    midtrans.mockImplementation(async () => Response.json({
      ...chargeResult, gross_amount: "15001.00", transaction_status: "settlement",
    }));
    expect((await status()).status).toBe(503);
    expect(await env.DB.prepare("SELECT status FROM orders").first("status")).toBe("pending");
  });
});
