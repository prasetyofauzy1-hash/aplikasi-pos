package id.kasir.pos

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
internal data class CashierShift(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val openedAt: Long,
    val openingCash: Long,
    val closedAt: Long? = null,
    val closingCash: Long? = null,
    val closingCashSales: Long? = null
)

internal data class ShiftSummary(@Embedded val shift: CashierShift, val cashSales: Long) {
    val isOpen get() = shift.closedAt == null
    val expectedCash get() = Math.addExact(shift.openingCash, cashSales)
    fun difference(countedCash: Long) = countedCash - expectedCash
}

private const val MAX_SHIFT_CASH = 999_999_999_999_999_999L

internal fun parseShiftCash(input: String) =
    input.takeIf { ',' !in it }?.let { parseRupiah(it, maxDigits = 18) }

internal fun requireShiftCash(amount: Long) {
    if (amount !in 0..MAX_SHIFT_CASH) throw SaleInputException("Masukkan nominal kas yang valid.")
}
