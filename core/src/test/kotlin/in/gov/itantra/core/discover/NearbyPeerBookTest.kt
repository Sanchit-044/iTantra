package `in`.gov.itantra.core.discover

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NearbyPeerBookTest {

    @Test
    fun `same mac merges wifi and bluetooth into one peer`() {
        val wifi = NearbyPeerBook.sighting("Unit-1", NearbyRadio.WIFI, "AA:BB:CC:DD:EE:FF", null, 1L)
        val ble = NearbyPeerBook.sighting("Unit-1", NearbyRadio.BLUETOOTH, "aa:bb:cc:dd:ee:ff", -50, 2L)
        val merged = NearbyPeerBook.upsert(listOf(wifi), ble).single()
        assertTrue(merged.hasWifi)
        assertTrue(merged.hasBluetooth)
        assertEquals("Wi-Fi + BT", merged.radiosLabel)
        assertEquals(RssiBand.NEAR, merged.band)
    }

    @Test
    fun `same distinctive name merges when macs differ`() {
        val wifi = NearbyPeerBook.sighting("FieldPhone", NearbyRadio.WIFI, "11:11:11:11:11:11", null, 1L)
        val ble = NearbyPeerBook.sighting("FieldPhone", NearbyRadio.BLUETOOTH, "22:22:22:22:22:22", -70, 2L)
        val merged = NearbyPeerBook.upsert(listOf(wifi), ble).single()
        assertTrue(merged.hasWifi && merged.hasBluetooth)
    }

    @Test
    fun `generic names do not collapse two devices`() {
        val a = NearbyPeerBook.sighting("Android", NearbyRadio.WIFI, "11:11:11:11:11:11", null, 1L)
        val b = NearbyPeerBook.sighting("Android", NearbyRadio.BLUETOOTH, "22:22:22:22:22:22", -40, 2L)
        assertEquals(2, NearbyPeerBook.upsert(listOf(a), b).size)
    }

    @Test
    fun `stale peers are pruned`() {
        val fresh = NearbyPeerBook.sighting("A", NearbyRadio.BLUETOOTH, "aa:aa:aa:aa:aa:aa", -40, nowMs = 10_000L)
        val stale = NearbyPeerBook.sighting("B", NearbyRadio.BLUETOOTH, "bb:bb:bb:bb:bb:bb", -40, nowMs = 0L)
        val kept = NearbyPeerBook.prune(listOf(fresh, stale), nowMs = 10_000L)
        assertEquals(listOf(fresh), kept)
    }

    @Test
    fun `stable angle is deterministic`() {
        val p = NearbyPeerBook.sighting("X", NearbyRadio.WIFI, "cc:cc:cc:cc:cc:cc", null, 0L)
        assertEquals(p.stableAngleDegrees(), p.copy(lastSeenMs = 99L).stableAngleDegrees())
        assertTrue(p.stableAngleDegrees() in 0f..359f)
    }

    @Test
    fun `fading starts before drop`() {
        val p = NearbyPeerBook.sighting("X", NearbyRadio.BLUETOOTH, "dd:dd:dd:dd:dd:dd", -80, 0L)
        assertFalse(NearbyPeerBook.fading(p, nowMs = 1_000L))
        assertTrue(NearbyPeerBook.fading(p, nowMs = NearbyPeerBook.FADE_MS))
    }
}
