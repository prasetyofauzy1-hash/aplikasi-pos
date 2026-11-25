package id.kasir.pos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import kotlin.math.roundToInt

internal fun loadReceiptLogo(context: Context, logoName: String): Bitmap? {
    val custom = logoName.takeIf { it.isNotBlank() }?.let {
        BitmapFactory.decodeFile(File(context.filesDir, it).path)
    }
    return custom ?: BitmapFactory.decodeResource(context.resources, R.drawable.jusgo_receipt_logo)
}

internal fun Bitmap.fittedSize(maxWidth: Int, maxHeight: Int): Pair<Int, Int> {
    val scale = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)
    return (width * scale).roundToInt().coerceAtLeast(1) to (height * scale).roundToInt().coerceAtLeast(1)
}

internal fun receiptPrinterLogo(context: Context, logoName: String, paper: ThermalPaper): ByteArray? {
    val original = loadReceiptLogo(context, logoName) ?: return null
    val (width, height) = original.fittedSize(paper.logoWidth, paper.logoHeight)
    val bitmap = Bitmap.createScaledBitmap(original, width, height, true)
    try {
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        return thermalRaster(width, height, pixels)
    } finally {
        if (bitmap !== original) bitmap.recycle()
        original.recycle()
    }
}
