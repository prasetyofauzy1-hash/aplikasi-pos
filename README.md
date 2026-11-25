# HappyPOS

Aplikasi kasir Android berbahasa Indonesia untuk satu toko dan satu perangkat, tanpa memerlukan koneksi internet.

- Buat PIN pemilik enam angka saat pertama membuka aplikasi, lalu atur profil toko. PIN juga diminta pada peluncuran baru; gunakan **Toko → Kunci aplikasi** untuk mengunci secara manual.
- Atur nama, alamat opsional, dan logo melalui **Toko**. Logo yang dipilih dapat diperiksa sebelum disimpan. Identitas toko disalin ke setiap struk saat transaksi disimpan.
- Jika email pemilik sudah dihubungkan, **Lupa PIN?** memungkinkan Anda masuk dengan akun itu dan membuat PIN baru tanpa menghapus data toko. Menghubungkan email dan memulihkan PIN memerlukan internet; penggunaan PIN dan kasir tetap bisa offline.
- Masukkan jumlah bahan di **Produk → Stok bahan**. Gunakan gram, mililiter, atau buah sesuai bahan. Menu dan resep contoh tersedia pada pemasangan pertama, tetapi stok awal bahan adalah nol.
- Untuk stok opname, buka **Produk → Stok bahan → Stok opname**, hitung bahan secara fisik, lalu isi hasilnya (atau tekan **Sama** jika cocok). Selisih terhadap stok sistem langsung terlihat; setelah disimpan, stok sistem diganti dengan hasil hitungan. Bahan yang dikosongkan tidak diubah. Hasil setiap opname tersimpan di **Riwayat opname**.
- Gunakan **Produk** untuk menambah, mengubah, atau menghapus menu, harga, kategori, dan resep per porsi. Misalnya, satu Berry Go memakai 200 g berry dan 200 g pisang.
- Sebelum menerima pembayaran, pilih **Kasir → Buka shift** dan catat kas awal. Di **Tutup shift**, masukkan jumlah uang yang dihitung di laci untuk melihat selisihnya. Kas yang seharusnya adalah kas awal ditambah penjualan tunai setelah kembalian, tanpa QRIS. Ringkasan shift terakhir menyimpan angka saat shift ditutup.
- Pilih menu di **Kasir**, periksa keranjang, lalu catat pembayaran **Tunai** atau **QRIS**. QRIS hanya dicatat setelah Anda memeriksa pembayaran di luar aplikasi. Menu hanya bisa dijual jika bahannya cukup, termasuk ketika beberapa menu memakai bahan yang sama. Stok bahan berkurang setelah transaksi berhasil disimpan.
- Di tab **Toko**, gunakan **Pilih gambar QRIS** untuk menyimpan gambar QRIS statis dari galeri. Gambar dapat diganti atau dihapus kapan saja. Saat memilih **QRIS** di pembayaran, tampilkan kode di layar untuk dipindai pelanggan. Pelanggan memasukkan nominal yang tertera; tekan **Konfirmasi & simpan QRIS** hanya setelah dana masuk.
- Buka **Riwayat** dan pilih tanggal untuk melihat total penjualan, rincian Tunai dan QRIS, serta struk hari itu. Pilih **Produk terjual** untuk mencocokkan jumlah dan omzet per produk dengan catatan manual. **Bagikan struk** membuka menu berbagi Android; pilih WhatsApp jika terpasang. Struk yang dibagikan berupa teks.
- Buka **Laba** dengan PIN pemilik. Isi **Biaya bahan** dengan harga beli dan jumlah sesuai satuan stok (misalnya Rp25.000 per 1.000 g). **Per menu** menampilkan HPP dan estimasi laba per porsi; produk tanpa resep dapat diisi HPP-nya langsung. **Penjualan** menampilkan laba kotor harian per menu berdasarkan HPP saat transaksi. Penjualan tanpa HPP, termasuk transaksi lama, tidak masuk hitungan laba.

### Cetak struk Bluetooth

1. Nyalakan printer thermal Bluetooth Classic yang mendukung ESC/POS, lalu pasangkan di pengaturan Bluetooth Android.
2. Buka **Toko → Printer → Pilih printer**. Jika diminta, izinkan akses **Perangkat di sekitar**. Pilih printer yang sudah dipasangkan dan ukuran kertas **58 mm** atau **80 mm**; pilihan disimpan otomatis.
3. Tekan **Tes cetak** untuk memeriksa hasilnya.
4. Setelah menyimpan pembayaran, tekan **Cetak struk**. Untuk mencetak ulang, buka transaksi di **Riwayat**, lalu tekan **Cetak struk**.

## Pengembangan

Kotlin, Jetpack Compose, dan Room. Diperlukan JDK 17, Android SDK platform 35, dan build-tools 35.0.0. Jalankan:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

APK debug bisa dipasang di Android 7.0 (API 24) atau lebih baru. Pembaruan mempertahankan riwayat dan produk lama; produk tanpa resep tetap menggunakan stok langsung sampai Anda menambahkan resep.

### Email pemilik (opsional)

1. Buat proyek di [Firebase Console](https://console.firebase.google.com/) dan mulai dengan paket gratis Spark.
2. Di **Authentication → Sign-in method**, aktifkan **Email/Password**.
3. Salin **Web API Key** dari **Project settings → General**, tambah `FIREBASE_API_KEY=...` ke `local.properties` di akar repo, lalu build ulang. Gunakan proyek Firebase yang sama untuk pembaruan aplikasi.

Untuk **Masuk dengan Google**, aktifkan **Google** di **Authentication → Sign-in method**. Di **Project settings → General**, daftarkan aplikasi Android `id.kasir.pos` dan tambahkan SHA-1 sertifikat yang menandatangani APK. Build memakai ID klien OAuth web proyek Jus.go; untuk proyek lain, atur `GOOGLE_WEB_CLIENT_ID` di `local.properties` atau environment build.

Saat pertama membuka aplikasi, masuk dengan Google, gunakan email dan kata sandi, atau pilih **Lanjutkan sebagai tamu** untuk menyimpan data toko hanya di perangkat ini. Setelah itu, buat PIN pemilik. Perangkat yang sudah memiliki PIN langsung meminta PIN tersebut. Tanpa `FIREBASE_API_KEY`, aplikasi langsung meminta pengaturan PIN dan tetap dapat digunakan sepenuhnya offline. Pemulihan PIN dan backup cloud memerlukan akun yang sudah dihubungkan.

### Backup cloud (opsional)

- Buka **Toko → Backup cloud** setelah menghubungkan email pemilik. **Cadangkan ke cloud** menyimpan satu salinan terbaru per akun, menggantikan salinan sebelumnya. Backup tidak otomatis; data baru hanya masuk cloud jika Anda mencadangkan lagi.
- Di perangkat pengganti, buat PIN baru dan pilih **Masuk dengan Google** atau **Gunakan email dan kata sandi → Sudah punya akun? Masuk** untuk menautkan akun pemilik yang sama. Pilih **Pulihkan dari cloud**. Pemulihan mengganti data toko setempat, bukan menggabungkannya. Jualan tetap bisa dilakukan offline.
- Salinan mencakup produk, resep, stok, riwayat opname, transaksi, shift kasir, aturan biaya tambahan, profil dan logo toko, gambar QRIS, dan profil kasir. PIN, tautan login, kasir yang sedang aktif, setelan printer, dan keranjang yang belum dibayar tidak ikut dicadangkan. Menghapus aplikasi atau melepas tautan email tidak menghapus salinan cloud.

**Aktivasi Firebase.** Di proyek yang sama dengan Authentication, buka **Realtime Database → Create Database** dan pilih **Locked mode**. Publikasikan isi [aturan database](firebase.database.rules.json) di tab **Rules** sebelum mencoba backup. Jangan aktifkan akses publik. Untuk Jus.go, build bawaan memakai database `aplikasi-pos-3d3a7-default-rtdb` di Singapura (`asia-southeast1`). Jika menggunakan database lain, tambahkan `FIREBASE_BACKUP_URL=https://...` sesuai URL di Firebase Console ke `local.properties` atau environment build. Aplikasi membatasi setiap arsip terkompresi hingga 6 MiB agar tetap di bawah batas penulisan; periksa [kuota paket gratis Spark](https://firebase.google.com/pricing).
