package com.rameshta.magnetrail.levels

const val LEVEL_RANGE_SIZE = 50

data class LevelRangeWindow(
    val pageIndex: Int,
    val startIndex: Int,
    val endIndexExclusive: Int,
    val totalLevels: Int,
    val pageSize: Int = LEVEL_RANGE_SIZE,
) {
    val startLevelNumber: Int get() = if (totalLevels == 0) 0 else startIndex + 1
    val endLevelNumber: Int get() = endIndexExclusive
    val pageCount: Int get() = LevelRangeNavigator.pageCount(totalLevels, pageSize)
    val hasPrevious: Boolean get() = pageIndex > 0
    val hasNext: Boolean get() = pageIndex + 1 < pageCount
}

/** Pure range math: it can represent 10,000+ logical levels without loading level boards. */
object LevelRangeNavigator {
    fun pageCount(totalLevels: Int, pageSize: Int = LEVEL_RANGE_SIZE): Int {
        require(totalLevels >= 0 && pageSize > 0)
        return if (totalLevels == 0) 0 else (totalLevels + pageSize - 1) / pageSize
    }

    fun pageForLevel(levelNumber: Int, totalLevels: Int, pageSize: Int = LEVEL_RANGE_SIZE): Int {
        require(pageSize > 0)
        if (totalLevels <= 0) return 0
        val safeNumber = levelNumber.coerceIn(1, totalLevels)
        return (safeNumber - 1) / pageSize
    }

    fun window(pageIndex: Int, totalLevels: Int, pageSize: Int = LEVEL_RANGE_SIZE): LevelRangeWindow {
        require(totalLevels >= 0 && pageSize > 0)
        val count = pageCount(totalLevels, pageSize)
        val safePage = if (count == 0) 0 else pageIndex.coerceIn(0, count - 1)
        val start = (safePage * pageSize).coerceAtMost(totalLevels)
        return LevelRangeWindow(
            pageIndex = safePage,
            startIndex = start,
            endIndexExclusive = (start + pageSize).coerceAtMost(totalLevels),
            totalLevels = totalLevels,
            pageSize = pageSize,
        )
    }
}
