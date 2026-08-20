package com.rameshta.magnetrail.game

internal enum class TutorialAnimationKind {
    TAP,
    BLOCK,
    MAGNET,
    POLARITY,
    ORDER,
    SCAN,
    VISIBILITY,
    REVEAL,
    CHOICE,
    MASTERY,
}

internal const val TUTORIAL_LEVEL_COUNT = 10

internal data class TutorialLesson(
    val number: Int,
    val title: String,
    val message: String,
    val prompt: String,
    val animation: TutorialAnimationKind,
    val focusArrowId: String?,
)

internal fun GameUiState.activeTutorialLesson(): TutorialLesson? {
    if (isComplete) return null
    val ordinal = when (gameMode) {
        GameMode.CAMPAIGN -> (currentLevel.number - 1).takeIf { it in 0 until TUTORIAL_LEVEL_COUNT }
        GameMode.INFINITE -> progress.infinite.selectionOrdinal.takeIf {
            infiniteDifficulty == com.rameshta.magnetrail.core.infinite.InfiniteDifficulty.PROGRESSIVE &&
                it in 0 until TUTORIAL_LEVEL_COUNT
        }
        GameMode.DAILY -> null
    } ?: return null
    val lesson = when (ordinal) {
        0 -> TutorialLesson(
            1,
            "Follow the arrow",
            "The arrowhead shows its printed direction. A magnet can bend that route.",
            "Watch the hand, then tap the glowing arrow.",
            TutorialAnimationKind.TAP,
            null,
        )
        1 -> TutorialLesson(
            2,
            "Spot blockers",
            "Walls and other arrows stop a route. A failed launch changes nothing.",
            "Trace the glowing direction, then try the arrow.",
            TutorialAnimationKind.BLOCK,
            null,
        )
        2 -> TutorialLesson(
            3,
            "Read the field",
            "PULL brings an aligned arrow closer. PUSH sends it away.",
            "Watch the magnet rings, then tap the glowing arrow.",
            TutorialAnimationKind.MAGNET,
            null,
        )
        3 -> TutorialLesson(
            4,
            "Check the flip",
            "A successful magnetic move flips PULL to PUSH, or PUSH to PULL.",
            "After the move, pause and check the magnet again.",
            TutorialAnimationKind.POLARITY,
            null,
        )
        4 -> TutorialLesson(
            5,
            "Choose the order",
            "Your first move changes what can work next.",
            "Follow the hand, and notice what each move changes.",
            TutorialAnimationKind.ORDER,
            null,
        )
        5 -> TutorialLesson(
            6,
            "Scan the board",
            "Before tapping, trace the arrowhead and check every aligned object.",
            "Scan first, then follow the hand through the sequence.",
            TutorialAnimationKind.SCAN,
            null,
        )
        6 -> TutorialLesson(
            7,
            "Check visibility",
            "Only a visible aligned magnet can control an arrow. Objects can block its field.",
            "Follow the hand and watch which field is visible.",
            TutorialAnimationKind.VISIBILITY,
            null,
        )
        7 -> TutorialLesson(
            8,
            "Watch what opens",
            "Removing one arrow can expose a route or a different controller.",
            "After every tap, look again before following the hand.",
            TutorialAnimationKind.REVEAL,
            null,
        )
        8 -> TutorialLesson(
            9,
            "Compare choices",
            "Two arrows may look safe, but they can create different future boards.",
            "Use the hand to study one reliable sequence.",
            TutorialAnimationKind.CHOICE,
            null,
        )
        else -> TutorialLesson(
            10,
            "Put it together",
            "Read direction, blockers, field, polarity, and order before every move.",
            "Complete the guided sequence, then you are ready to play alone.",
            TutorialAnimationKind.MASTERY,
            null,
        )
    }
    val removedArrowIds = initialState.arrows.mapTo(linkedSetOf()) { it.id }
        .apply { removeAll(boardState.arrows.mapTo(hashSetOf()) { it.id }) }
    val focus = if (inFlightResult == null) {
        currentLevel.designedSolutions.asSequence()
            .filter { solution ->
                solution.size > removedArrowIds.size &&
                    solution.take(removedArrowIds.size).toSet() == removedArrowIds
            }
            .map { solution -> solution[removedArrowIds.size] }
            .firstOrNull { arrowId -> boardState.arrow(arrowId) != null }
    } else null
    val prompt = when {
        focus != null && removedArrowIds.isNotEmpty() -> "Nice. Follow the hand to the next arrow."
        focus == null && inFlightResult == null ->
            "The guided path changed. Restart to show the step-by-step sequence again."
        else -> lesson.prompt
    }
    return lesson.copy(focusArrowId = focus, prompt = prompt)
}
