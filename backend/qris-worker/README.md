# QRIS Otomatis Jus.go

Cloudflare Workers + D1 menyimpan status pembayaran. Struk dan stok tetap di perangkat. Worker mengecek webhook bertanda tangan dan menggunakan **Get Transaction Status** Midtrans sebagai sumber status pembayaran, juga saat webhook terlambat atau tidak sampai.

## Sandbox

1. Daftar di [Midtrans](https://dashboard.midtrans.com/register), pilih lingkungan **Sandbox**, lalu buka **Settings → Access Keys**. Ambil **Server Key** sandbox; **Client Key** tidak digunakan.
2. Buat akun gratis [Cloudflare](https://dash.cloudflare.com/sign-up) dan aktifkan Workers & Pages. Untuk Wrangler, masuk dengan `npx wrangler login` atau gunakan token dengan izin **Workers Scripts: Edit** dan **D1: Edit** pada akun terkait (sediakan ID akun melalui `CLOUDFLARE_ACCOUNT_ID`). Simpan token dan Server Key secara rahasia.
3. Dari direktori ini (Node.js 20 atau lebih baru):

   ```sh
   npm ci
   npx wrangler d1 create jusgo-qris
   ```

   Salin ID **database** yang ditampilkan ke `database_id` di `wrangler.toml`, lalu jalankan:

   ```sh
   npx wrangler d1 migrations apply jusgo-qris --remote
   npx wrangler secret put MIDTRANS_SERVER_KEY
   npx wrangler secret put APP_TOKEN
   npx wrangler deploy
   ```

   Buat token acak khusus instalasi (misalnya `openssl rand -hex 32`) untuk `APP_TOKEN`. URL Worker HTTPS yang ditampilkan dipakai untuk webhook pada setiap permintaan charge; tidak perlu mengubah URL notifikasi default Midtrans. Jika proxy tidak meneruskan host publik, isi `PUBLIC_WORKER_URL` dengan URL HTTPS Worker.
4. Build Android dengan `QRIS_WORKER_URL` (URL HTTPS dari langkah 3) dan `QRIS_APP_TOKEN` (nilai yang sama dengan `APP_TOKEN`) sebagai variabel lingkungan atau dalam `local.properties` di akar repo. Jangan memasukkan `MIDTRANS_SERVER_KEY` ke proyek Android. Token aplikasi bisa diekstrak dari APK; batasi distribusi APK dan rotasi token bila bocor.
5. Pada emulator, pilih **QRIS Otomatis**, buat kode untuk suatu keranjang, lalu uji pembayaran dengan [Simulator QRIS Midtrans](https://simulator.sandbox.midtrans.com/qris/index). Verifikasi struk dan perubahan stok sesudah pembayaran, termasuk setelah menutup aplikasi saat offline dan membuka lagi. Untuk pengujian kedaluwarsa, atur `QRIS_EXPIRY_MINUTES` ke `1` pada deployment sandbox terpisah.

Gunakan Worker, database, dan kunci terpisah untuk produksi. Ubah `MIDTRANS_ENV` menjadi `production` hanya pada Worker yang memakai akun merchant Midtrans produksi dengan QRIS aktif.

## Cek lokal

```sh
npm run typecheck
npm test
npx wrangler d1 migrations apply jusgo-qris --local
npm run dev
```

Tes Vitest menggunakan D1 lokal dan respons Midtrans simulasi. Untuk menghubungkan Worker lokal ke sandbox, sediakan `MIDTRANS_SERVER_KEY`, `APP_TOKEN`, dan URL HTTPS publik untuk webhook (`PUBLIC_WORKER_URL`) dalam `.dev.vars` yang diabaikan Git.
