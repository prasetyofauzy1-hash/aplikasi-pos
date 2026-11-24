# Kasir Rapi

Aplikasi kasir Android berbahasa Indonesia untuk satu toko dan satu perangkat, tanpa memerlukan koneksi internet.

- Buat PIN pemilik enam angka saat pertama membuka aplikasi. PIN juga diminta pada peluncuran baru; gunakan **Toko → Kunci aplikasi** untuk mengunci secara manual.
- Atur nama, alamat, dan logo melalui **Toko**. Identitas toko disalin ke setiap struk saat transaksi disimpan.
- Masukkan jumlah bahan di **Produk → Stok bahan**. Gunakan gram, mililiter, atau buah sesuai bahan. Menu dan resep contoh tersedia pada pemasangan pertama, tetapi stok awal bahan adalah nol.
- Gunakan **Produk** untuk menambah, mengubah, atau menghapus menu, harga, kategori, dan resep per porsi. Misalnya, satu Berry Go memakai 200 g berry dan 200 g pisang.
- Pilih menu di **Kasir**, periksa keranjang, lalu catat pembayaran **Tunai** atau **QRIS**. QRIS hanya dicatat setelah Anda memeriksa pembayaran di luar aplikasi. Menu hanya bisa dijual jika bahannya cukup, termasuk ketika beberapa menu memakai bahan yang sama. Stok bahan berkurang setelah transaksi berhasil disimpan.
- Buka **Riwayat** untuk melihat ringkasan hari ini dan struk lama. **Bagikan struk** membuka menu berbagi Android; pilih WhatsApp jika terpasang. Struk yang dibagikan berupa teks.

## Pengembangan

Kotlin, Jetpack Compose, dan Room. Diperlukan JDK 17, Android SDK platform 35, dan build-tools 35.0.0. Jalankan:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

APK debug bisa dipasang di Android 7.0 (API 24) atau lebih baru. Data disimpan lokal dan akan hilang jika aplikasi dihapus atau datanya dibersihkan. Tidak ada pemulihan PIN, sinkronisasi cloud, dukungan banyak kasir, atau koneksi printer.

Pembaruan dari versi pertama mempertahankan riwayat dan produk lama. Produk tanpa resep tetap menggunakan stok langsung sampai Anda menambahkan resep.
