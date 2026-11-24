# Kasir Rapi

Aplikasi kasir Android berbahasa Indonesia untuk satu toko dan satu perangkat, tanpa memerlukan koneksi internet.

- Buat PIN pemilik enam angka saat pertama membuka aplikasi. PIN juga diminta pada peluncuran baru; gunakan **Toko → Kunci aplikasi** untuk mengunci secara manual.
- Atur nama, alamat, dan logo melalui **Toko**. Identitas toko disalin ke setiap struk saat transaksi disimpan.
- Gunakan **Produk** untuk menambah, mengubah, atau menghapus barang. Data contoh tersedia pada pemasangan pertama.
- Pilih barang di **Kasir**, periksa keranjang, lalu catat pembayaran **Tunai** atau **QRIS**. QRIS hanya dicatat setelah Anda memeriksa pembayaran di luar aplikasi. Stok berkurang ketika transaksi berhasil disimpan.
- Buka **Riwayat** dan pilih tanggal untuk melihat total penjualan, rincian Tunai dan QRIS, serta struk hari itu. Pilih **Produk terjual** untuk mencocokkan jumlah dan omzet per produk dengan catatan manual. **Bagikan struk** membuka menu berbagi Android; pilih WhatsApp jika terpasang. Struk yang dibagikan berupa teks.

## Pengembangan

Kotlin, Jetpack Compose, dan Room. Diperlukan JDK 17, Android SDK platform 35, dan build-tools 35.0.0. Jalankan:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

APK debug bisa dipasang di Android 7.0 (API 24) atau lebih baru. Data disimpan lokal dan akan hilang jika aplikasi dihapus atau datanya dibersihkan. Tidak ada pemulihan PIN, sinkronisasi cloud, dukungan banyak kasir, atau koneksi printer.
