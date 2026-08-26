package app.alearthapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisastersRepositoryTest {

    @Test
    fun haversineDistanceMatchesExpected() {
        // Istanbul (41.0082, 28.9784) <-> Ankara (39.9334, 32.8597) ~ 350 km
        val dist = DisastersRepository.haversineKm(41.0082, 28.9784, 39.9334, 32.8597)
        assertTrue(dist in 340.0..360.0)
    }

    @Test
    fun turkeyBoundingBoxVerification() {
        // Istanbul is inside
        assertTrue(DisastersRepository.isInsideTurkey(41.0, 28.9))
        // Tokyo is outside
        assertFalse(DisastersRepository.isInsideTurkey(35.6, 139.6))
    }

    @Test
    fun parseUsgsGeoJsonHandlesValidFeatures() {
        val json = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "id": "us7000test",
                  "properties": {
                    "mag": 5.8,
                    "place": "12 km W of Malatya, Turkey",
                    "time": 1724000000000,
                    "url": "https://earthquake.usgs.gov"
                  },
                  "geometry": {
                    "type": "Point",
                    "coordinates": [38.2, 38.3, 10.0]
                  }
                }
              ]
            }
        """.trimIndent()

        val list = DisastersRepository.parseUsgsGeoJson(json)
        assertEquals(1, list.size)
        val q = list[0]
        assertEquals("us7000test", q.id)
        assertEquals(DisasterType.QUAKE, q.type)
        assertEquals(5.8, q.magnitude ?: 0.0, 0.01)
        assertEquals("12 km W of Malatya, Turkey", q.place)
        assertEquals(38.3, q.lat, 0.01)
        assertEquals(38.2, q.lon, 0.01)
        assertEquals(10.0, q.depthKm ?: 0.0, 0.01)
    }

    @Test
    fun parseGdacsGeoJsonHandlesMultiHazard() {
        val json = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "properties": {
                    "eventtype": "FL",
                    "alertlevel": "Red",
                    "name": "Floods in Black Sea",
                    "country": "Turkey",
                    "fromdate": "2026-08-20T10:00:00"
                  },
                  "geometry": {
                    "coordinates": [36.0, 41.5]
                  }
                }
              ]
            }
        """.trimIndent()

        val list = DisastersRepository.parseGdacsGeoJson(json)
        assertEquals(1, list.size)
        val d = list[0]
        assertEquals(DisasterType.FLOOD, d.type)
        assertEquals(AlertLevel.RED, d.alertLevel)
        assertEquals("Floods in Black Sea", d.place)
        assertEquals(41.5, d.lat, 0.01)
        assertEquals(36.0, d.lon, 0.01)
    }

    @Test
    fun deduplicationMergesOverlappingQuakes() {
        val q1 = DisasterEvent(
            id = "usgs_1",
            type = DisasterType.QUAKE,
            magnitude = 6.2,
            place = "Kahramanmaras",
            lat = 37.58,
            lon = 36.93,
            timeMs = 1724000000000L,
            source = "USGS"
        )
        val q2 = DisasterEvent(
            id = "emsc_1",
            type = DisasterType.QUAKE,
            magnitude = 6.1,
            place = "Eastern Turkey",
            lat = 37.60,
            lon = 36.95,
            timeMs = 1724000005000L, // 5 seconds later, 3 km away
            source = "EMSC"
        )

        val merged = DisastersRepository.deduplicate(listOf(q1, q2))
        assertEquals(1, merged.size)
        assertEquals("USGS", merged[0].source)
    }

    @Test
    fun filteringByRegionAndMinMagnitude() {
        val t1 = DisasterEvent(
            id = "q_tr_small",
            type = DisasterType.QUAKE,
            magnitude = 3.2,
            place = "Izmir",
            lat = 38.4,
            lon = 27.1,
            timeMs = 1000L
        )
        val t2 = DisasterEvent(
            id = "q_tr_big",
            type = DisasterType.QUAKE,
            magnitude = 6.5,
            place = "Adana",
            lat = 37.0,
            lon = 35.3,
            timeMs = 1000L
        )
        val world = DisasterEvent(
            id = "q_chile",
            type = DisasterType.QUAKE,
            magnitude = 7.0,
            place = "Chile",
            lat = -30.0,
            lon = -70.0,
            timeMs = 1000L
        )

        val allEvents = listOf(t1, t2, world)

        val trM4Filter = DisastersFilter(
            region = RegionFilter.TURKEY,
            minMag = 4.0,
            timeWindowHours = 100
        )

        val filtered = DisastersRepository.filter(allEvents, trM4Filter, nowMs = 1000L)
        assertEquals(1, filtered.size)
        assertEquals("q_tr_big", filtered[0].id)
    }

    @Test
    fun sortingByNearbyAndSeverity() {
        val e1 = DisasterEvent(id = "1", type = DisasterType.QUAKE, magnitude = 4.0, lat = 41.0, lon = 29.0) // ~0 km from user
        val e2 = DisasterEvent(id = "2", type = DisasterType.QUAKE, magnitude = 7.0, lat = 37.0, lon = 35.0) // ~700 km away, high mag

        val sortedByNear = DisastersRepository.sort(listOf(e2, e1), SortOption.NEARBY, userLat = 41.0, userLon = 29.0)
        assertEquals("1", sortedByNear[0].id)

        val sortedBySev = DisastersRepository.sort(listOf(e1, e2), SortOption.SEVERITY)
        assertEquals("2", sortedBySev[0].id)
    }
}
