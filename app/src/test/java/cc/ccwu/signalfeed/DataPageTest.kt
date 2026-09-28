package cc.ccwu.signalfeed

import org.junit.Assert.*
import org.junit.Test

class DataPageTest {
    @Test fun historyKeepsZerosAndBreaksMissingRounds() {
        fun row(round: Int, points: Double) = cc.ccwu.signalfeed.data.F1HistoryRound(round, "Race", listOf(cc.ccwu.signalfeed.data.F1HistoryDriver("a", "Driver", points)))
        val segments = pointSegments(listOf(row(1, 0.0), row(2, 25.0), row(4, 40.0)), "a")
        assertEquals(listOf(listOf(1 to 0.0, 2 to 25.0), listOf(4 to 40.0)), segments)
        assertTrue(pointSegments(listOf(row(1, 0.0)), "missing").isEmpty())
    }

    @Test fun repeatedRanksAndProviderIdsDoNotCollideAcrossLists() {
        val keys = listOf("race", "driver", "team", "result", "model").flatMap { section ->
            listOf("1", "1", "2").mapIndexed { index, id -> dataRowKey(section, id, index) }
        }
        assertEquals(keys.size, keys.toSet().size)
    }
    @Test fun missingZeroAndInvalidChartValuesRemainSafe() {
        assertEquals(0f, chartFraction(null, 100.0))
        assertEquals(0f, chartFraction(0.0, 0.0))
        assertEquals(0f, chartFraction(Double.NaN, 100.0))
        assertEquals(0f, chartFraction(-10.0, 100.0))
        assertEquals(1f, chartFraction(150.0, 100.0))
        assertEquals(0.5f, chartFraction(50.0, 100.0))
    }
}
