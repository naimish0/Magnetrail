package com.rameshta.magnetrail

import com.rameshta.magnetrail.levels.LEVEL_RANGE_SIZE
import com.rameshta.magnetrail.levels.LevelRangeNavigator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelRangeNavigationTest {
    @Test
    fun `ten thousand logical levels require only one fifty-level window`() {
        assertEquals(200, LevelRangeNavigator.pageCount(10_000))
        val page = LevelRangeNavigator.pageForLevel(9_876, 10_000)
        val window = LevelRangeNavigator.window(page, 10_000)

        assertEquals(9_851, window.startLevelNumber)
        assertEquals(9_900, window.endLevelNumber)
        assertEquals(LEVEL_RANGE_SIZE, window.endIndexExclusive - window.startIndex)
        assertTrue(window.hasPrevious)
        assertTrue(window.hasNext)
    }

    @Test
    fun `last partial range is bounded`() {
        val window = LevelRangeNavigator.window(99, 205)
        assertEquals(201, window.startLevelNumber)
        assertEquals(205, window.endLevelNumber)
        assertTrue(window.hasPrevious)
        assertFalse(window.hasNext)
    }
}
