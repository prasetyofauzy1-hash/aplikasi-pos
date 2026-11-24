# Kasir Rapi

Aplikasi kasir Android berbahasa Indonesia untuk satu toko dan satu perangkat, tanpa memerlukan koneksi internet.

- Buat PIN pemilik enam angka saat pertama membuka aplikasi, lalu atur profil toko. PIN juga diminta pada peluncuran baru; gunakan **Toko → Kunci aplikasi** untuk mengunci secara manual.
- Atur nama, alamat opsional, dan logo melalui **Toko**. Logo yang dipilih dapat diperiksa sebelum disimpan. Identitas toko disalin ke setiap struk saat transaksi disimpan.
- Jika email pemilik sudah dihubungkan, **Lupa PIN?** memungkinkan Anda masuk dengan akun itu dan membuat PIN baru tanpa menghapus data toko. Menghubungkan email dan memulihkan PIN memerlukan internet; penggunaan PIN dan kasir tetap bisa offline.
- Gunakan **Produk** untuk menambah, mengubah, atau menghapus barang. Data contoh tersedia pada pemasangan pertama.
- Pilih barang di **Kasir**, periksa keranjang, lalu catat pembayaran **Tunai** atau **QRIS**. QRIS hanya dicatat setelah Anda memeriksa pembayaran di luar aplikasi. Stok berkurang ketika transaksi berhasil disimpan.
- Buka **Riwayat** dan pilih tanggal untuk melihat total penjualan, rincian Tunai dan QRIS, serta struk hari itu. Pilih **Produk terjual** untuk mencocokkan jumlah dan omzet per produk dengan catatan manual. **Bagikan struk** membuka menu berbagi Android; pilih WhatsApp jika terpasang. Struk yang dibagikan berupa teks.

## Pengembangan

Kotlin, Jetpack Compose, dan Room. Diperlukan JDK 17, Android SDK platform 35, dan build-tools 35.0.0. Jalankan:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Email pemilik (opsional)

1. Buat proyek di [Firebase Console](https://console.firebase.google.com/) dan mulai dengan paket gratis Spark.
2. Di **Authentication → Sign-in method**, aktifkan **Email/Password**.
3. Salin **Web API Key** dari **Project settings → General**, tambah `FIREBASE_API_KEY=...` ke `local.properties` di akar repo, lalu build ulang. Gunakan proyek Firebase yang sama untuk pembaruan aplikasi.

Tanpa pengaturan ini, opsi menghubungkan email tidak muncul dan aplikasi tetap bisa digunakan sepenuhnya offline. Pemulihan PIN tanpa akun yang sudah dihubungkan tidak tersedia. Firebase menangani autentikasi saja; profil toko, stok, dan transaksi tetap lokal. Data hilang jika aplikasi dihapus atau datanya dibersihkan. Tidak ada sinkronisasi cloud, dukungan banyak kasir, atau koneksi printer.
