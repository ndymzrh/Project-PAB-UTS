package id.ac.stmi.mobileproject

import org.junit.Assert.assertEquals
import org.junit.Test

class BookingModelsTest {

    @Test
    fun estimatedPointsUsesTenPercentOfFinalTotal() {
        assertEquals(10_000L, BookingRepository.estimatedPoints(100_000L))
        assertEquals(45_000L, BookingRepository.estimatedPoints(450_000L))
        assertEquals(0L, BookingRepository.estimatedPoints(0L))
    }

    @Test
    fun estimatedPointsNeverBecomesNegative() {
        assertEquals(0L, BookingRepository.estimatedPoints(-1L))
    }
}
