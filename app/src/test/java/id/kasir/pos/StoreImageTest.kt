package id.kasir.pos

import org.junit.Assert.assertEquals
import org.junit.Test

class StoreImageTest {
    @Test fun qrisKeepsOriginalResolutionWithinLimit() {
        assertEquals(1, storeImageSampleSize(1080, 1600, 1600))
        assertEquals(1, storeImageSampleSize(1200, 1200, 1600))
    }

    @Test fun largeImagesAreSampledByTheirLongestSide() {
        assertEquals(4, storeImageSampleSize(4400, 800, 1600))
        assertEquals(4, storeImageSampleSize(800, 4400, 1600))
        assertEquals(4, storeImageSampleSize(1600, 1600, 512))
    }
}
