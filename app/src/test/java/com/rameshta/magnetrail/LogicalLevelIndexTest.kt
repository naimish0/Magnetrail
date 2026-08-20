package com.rameshta.magnetrail

import com.rameshta.magnetrail.core.generation.v5.TopologyFamilyV5
import com.rameshta.magnetrail.levels.logicalGeneratedMetadataIndex
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LogicalLevelIndexTest {
    @Test
    fun `ten thousand level index resolves only the requested fifty metadata records`() {
        val profileCalls = AtomicInteger()
        val index = logicalGeneratedMetadataIndex(
            totalLevels = 10_000,
            profileForLevel = { profileCalls.incrementAndGet(); "v5-expert" },
            topologyForLevel = { TopologyFamilyV5.BRANCHING_DEPENDENCY },
            seedForLevel = { it.toLong() * 17L },
        )

        val page = index.range(pageIndex = 199)

        assertEquals(50, page.size)
        assertEquals(9_951, page.first().number)
        assertEquals(10_000, page.last().number)
        assertEquals(50, profileCalls.get())
        assertNotNull(page.last().logicalIdentity)
    }
}
