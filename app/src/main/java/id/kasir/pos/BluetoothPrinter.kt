package id.kasir.pos

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class PairedPrinter(val address: String, val name: String)

internal class PrinterException(message: String) : Exception(message)

internal const val printerPermissionMessage = "Izinkan akses perangkat di sekitar untuk menggunakan printer Bluetooth."
private val printerService = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
private const val printTimeoutMillis = 12_000L

internal fun hasPrinterPermission(context: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

@SuppressLint("MissingPermission")
private fun pairedBluetoothDevices(context: Context): Set<BluetoothDevice> {
    if (!hasPrinterPermission(context)) throw PrinterException(printerPermissionMessage)
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        ?: throw PrinterException("Perangkat ini tidak mendukung Bluetooth.")
    if (!adapter.isEnabled) throw PrinterException("Aktifkan Bluetooth di pengaturan Android.")
    return adapter.bondedDevices.filterTo(mutableSetOf()) { it.type != BluetoothDevice.DEVICE_TYPE_LE }
}

@SuppressLint("MissingPermission")
internal fun pairedPrinters(context: Context): List<PairedPrinter> = try {
    pairedBluetoothDevices(context).map { PairedPrinter(it.address, it.name ?: "Perangkat Bluetooth") }
        .sortedWith(compareBy<PairedPrinter> { it.name.lowercase(indonesia) }.thenBy { it.address })
} catch (_: SecurityException) {
    throw PrinterException(printerPermissionMessage)
}

@SuppressLint("MissingPermission")
internal suspend fun sendToPrinter(context: Context, printer: PairedPrinter, document: () -> ByteArray) {
    try {
        val sent = withContext(Dispatchers.IO) {
            withTimeoutOrNull(printTimeoutMillis) {
                val device = pairedBluetoothDevices(context).firstOrNull { it.address == printer.address }
                    ?: throw PrinterException("Printer tidak lagi dipasangkan. Pasangkan di Android, lalu pilih lagi di Toko.")
                val bytes = document()
                val socket = device.createRfcommSocketToServiceRecord(printerService)
                suspendCancellableCoroutine<Unit> { continuation ->
                    continuation.invokeOnCancellation { runCatching { socket.close() } }
                    try {
                        socket.use {
                            it.connect()
                            it.outputStream.apply {
                                write(bytes)
                                flush()
                            }
                        }
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                        return@suspendCancellableCoroutine
                    }
                    continuation.resume(Unit)
                }
            }
        }
        if (sent == null) throw PrinterException("Printer tidak merespons. Periksa apakah printer menyala dan berada di dekat ponsel.")
    } catch (_: SecurityException) {
        throw PrinterException(printerPermissionMessage)
    } catch (_: IOException) {
        throw PrinterException("Data cetak tidak dapat dikirim. Periksa printer dan coba lagi.")
    }
}
