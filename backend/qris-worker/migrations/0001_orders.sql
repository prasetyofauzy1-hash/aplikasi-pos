CREATE TABLE orders (
    order_id TEXT PRIMARY KEY NOT NULL,
    gross_amount INTEGER NOT NULL CHECK (gross_amount > 0),
    status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'paid', 'expired', 'canceled')),
    qr_url TEXT,
    expires_at INTEGER NOT NULL
);
