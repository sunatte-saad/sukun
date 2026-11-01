package sukun.minimalist.app.launcher.com.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class GlassHourTimeTest {

    @Test
    fun midnightUsesTwelveOnTheTopPairIn12HourMode() {
        assertEquals("1200".toCharArray().concatToString(), digits(0, 0, use24Hour = false))
        assertEquals("12:00", GlassHourTime.spokenLabel(0, 0, use24Hour = false))
    }

    @Test
    fun afternoonKeepsTwoDigitsInEachPair() {
        assertEquals("0307".toCharArray().concatToString(), digits(15, 7, use24Hour = false))
        assertEquals("1507".toCharArray().concatToString(), digits(15, 7, use24Hour = true))
        assertEquals("3:07", GlassHourTime.spokenLabel(15, 7, use24Hour = false))
        assertEquals("15:07", GlassHourTime.spokenLabel(15, 7, use24Hour = true))
    }

    @Test
    fun noonStaysTwelveAndOutOfRangeValuesStayInsideAClock() {
        assertEquals("1230".toCharArray().concatToString(), digits(12, 30, use24Hour = false))
        assertEquals("2359".toCharArray().concatToString(), digits(23, 59, use24Hour = true))
        assertEquals("2300".toCharArray().concatToString(), digits(99, -4, use24Hour = true))
    }

    private fun digits(hourOfDay: Int, minute: Int, use24Hour: Boolean): String {
        return GlassHourTime.digits(hourOfDay, minute, use24Hour).concatToString()
    }
}
