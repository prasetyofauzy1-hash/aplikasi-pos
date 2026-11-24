package id.kasir.pos

import org.junit.Assert.assertEquals
import org.junit.Test

class BluetoothPrinterTest {
    @Test fun drainTimeScalesWithReceiptSizeWithinBounds() {
        assertEquals(500L, printerDrainMillis(100))
        assertEquals(1_500L, printerDrainMillis(1_500))
        assertEquals(4_000L, printerDrainMillis(Int.MAX_VALUE))
    }
}
