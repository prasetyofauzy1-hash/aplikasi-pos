package id.kasir.pos

import org.junit.Assert.assertEquals
import org.junit.Test

class QrisCheckoutTest {
    private val pending = PendingQris("order-1", 15_000)
    private val paid = QrisOrder(pending.orderId, pending.total, QrisStatus.PAID, 0, null)

    @Test fun onlyMatchingPaidOrderCanSave() {
        assertEquals(QrisResolution.SAVE, pending.resolution(paid))
        assertEquals(QrisResolution.WAIT, pending.resolution(paid.copy(status = QrisStatus.PENDING)))
        assertEquals(QrisResolution.WAIT, pending.resolution(paid.copy(id = "other")))
        assertEquals(QrisResolution.WAIT, pending.resolution(paid.copy(total = 15_001)))
    }

    @Test fun paidReplayAfterConsumingOrderCannotSave() {
        assertEquals(QrisResolution.WAIT, null.resolution(paid))
    }

    @Test fun expiryAndCancellationReleaseWithoutSaving() {
        assertEquals(QrisResolution.RELEASE, pending.resolution(paid.copy(status = QrisStatus.EXPIRED)))
        assertEquals(QrisResolution.RELEASE, pending.resolution(paid.copy(status = QrisStatus.CANCELED)))
    }
}
