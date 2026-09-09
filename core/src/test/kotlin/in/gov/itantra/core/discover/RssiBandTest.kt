package `in`.gov.itantra.core.discover

import kotlin.test.Test
import kotlin.test.assertEquals

class RssiBandTest {

    @Test
    fun `strong signal is near`() {
        assertEquals(RssiBand.NEAR, RssiBand.fromRssi(-40))
        assertEquals(RssiBand.NEAR, RssiBand.fromRssi(RssiBand.NEAR_MIN_DBM))
    }

    @Test
    fun `medium signal is mid`() {
        assertEquals(RssiBand.MID, RssiBand.fromRssi(-60))
        assertEquals(RssiBand.MID, RssiBand.fromRssi(RssiBand.MID_MIN_DBM))
    }

    @Test
    fun `weak signal is far`() {
        assertEquals(RssiBand.FAR, RssiBand.fromRssi(-90))
        assertEquals(RssiBand.FAR, RssiBand.fromRssi(RssiBand.MID_MIN_DBM - 1))
    }

    @Test
    fun `missing rssi is mid not a fake distance`() {
        assertEquals(RssiBand.MID, RssiBand.fromRssi(null))
    }
}
