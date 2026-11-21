package com.vortix.app.data.battery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryStatsWakelockParserTest {

    @Test
    fun identifiesOnlyWakelockAndAggregateRowsForShizukuTransport() {
        assertTrue(BatteryStatsWakelockParser.isWakelockCheckinRow("9,1000,l,wl,Tag,0,f,0"))
        assertTrue(BatteryStatsWakelockParser.isWakelockCheckinRow("9,1000,l,awl,12000,4000"))
        assertTrue(
            !BatteryStatsWakelockParser.isWakelockCheckinRow(
                "9,1000,l,pr,com.example,foreground,1200"
            )
        )
    }

    @Test
    fun parsesLegacyV18PartialWakelockRows() {
        val records = BatteryStatsWakelockParser.parse(
            "9,1000,l,wl,*alarm*,0,f,0,23856,p,151,0,w,0"
        )

        assertEquals(1, records.size)
        assertEquals(1000, records.single().uid)
        assertEquals("*alarm*", records.single().name)
        assertEquals(23_856L, records.single().partialTimeMillis)
        assertEquals(151L, records.single().partialCount)
        assertNull(records.single().actualPartialDurationMillis)
    }

    @Test
    fun parsesV19PartialWakelockRowsWithoutInventingActualDuration() {
        val records = BatteryStatsWakelockParser.parse(
            "9,1000,l,wl,ConnectivityService,0,f,0,-1,-1,15411273,p,263,5,10,0,w,0,-1,-1"
        )

        assertEquals(1, records.size)
        assertEquals(15_411_273L, records.single().partialTimeMillis)
        assertEquals(263L, records.single().partialCount)
        assertNull(records.single().actualPartialDurationMillis)
    }

    @Test
    fun parsesV21ActualPartialDurationWithoutBackgroundFields() {
        val records = BatteryStatsWakelockParser.parse(
            "9,1000,l,wl,GmsNetworkManager,0,f,0,-1,-1,20,5000,p,3,1,2,3000,0,w,0,-1,-1,-1"
        )

        assertEquals(1, records.size)
        assertEquals(5_000L, records.single().partialTimeMillis)
        assertEquals(3L, records.single().partialCount)
        assertEquals(3_000L, records.single().actualPartialDurationMillis)
        assertNull(records.single().backgroundPartialDurationMillis)
    }

    @Test
    fun parsesV22BackgroundPartialDurationFields() {
        val records = BatteryStatsWakelockParser.parse(
            "9,1000,l,wl,GmsNetworkManager,0,f,0,-1,-1,20,5000,p,3,1,2,3000,1000,bp,2,1,2,800,0,w,0,-1,-1,-1"
        )

        assertEquals(1, records.size)
        assertEquals(5_000L, records.single().partialTimeMillis)
        assertEquals(3L, records.single().partialCount)
        assertEquals(3_000L, records.single().actualPartialDurationMillis)
        assertEquals(800L, records.single().backgroundPartialDurationMillis)
    }

    @Test
    fun ignoresNonSinceChargedRowsAndMalformedLines() {
        val records = BatteryStatsWakelockParser.parse(
            listOf(
                "9,1000,u,wl,UnpluggedLock,0,f,0,400,p,2,0,w,0",
                "9,1000,c,wl,CurrentLock,0,f,0,500,p,3,0,w,0",
                "not,a,batterystats,row",
                "9,1000,l,wl,TooShort,0,f"
            ).joinToString("\n")
        )

        assertTrue(records.isEmpty())
    }

    @Test
    fun parsesQuotedTagsWithCommasAndAggregatesDuplicateRows() {
        val records = BatteryStatsWakelockParser.parse(
            listOf(
                "9,1000,l,wl,\"Tag, with comma\",0,f,0,100,p,2,0,w,0",
                "9,1000,l,wl,\"Tag, with comma\",0,f,0,200,p,3,0,w,0"
            ).joinToString("\n")
        )

        assertEquals(1, records.size)
        assertEquals("Tag, with comma", records.single().name)
        assertEquals(300L, records.single().partialTimeMillis)
        assertEquals(5L, records.single().partialCount)
    }

    @Test
    fun usesUidAggregateOnlyWhenDetailedWakelocksAreMissing() {
        val records = BatteryStatsWakelockParser.parse(
            listOf(
                "9,1000,l,awl,12000,4000",
                "9,1001,l,wl,NamedLock,0,f,0,9000,p,5,0,w,0",
                "9,1001,l,awl,9000,2000"
            ).joinToString("\n")
        )

        assertEquals(2, records.size)
        val fallback = records.single { it.uid == 1000 }
        assertEquals("Partial wakelock toplamı", fallback.name)
        assertEquals(12_000L, fallback.partialTimeMillis)
        assertNull(fallback.partialCount)
        assertEquals(4_000L, fallback.backgroundPartialDurationMillis)
        assertEquals("NamedLock", records.single { it.uid == 1001 }.name)
    }
}
