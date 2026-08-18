package com.rameshta.magnetrail.core.economy

data class EconomySimulationResult(
    val scenario: String,
    val minimumBalance: Int,
    val medianBalance: Int,
    val finalBalance: Int,
    val unaffordableHintRequests: Int,
)

object EconomySimulation {
    fun representativeCampaign(levelCount: Int = 100): List<EconomySimulationResult> = listOf(
        simulate("clean-no-hints", levelCount, hintEvery = null, stars = 3),
        simulate("periodic-assistance", levelCount, hintEvery = 4, stars = 2),
        simulate("hint-every-level", levelCount, hintEvery = 1, stars = 2),
    )

    private fun simulate(
        name: String,
        levelCount: Int,
        hintEvery: Int?,
        stars: Int,
    ): EconomySimulationResult {
        require(levelCount > 0)
        var balance = EconomyConfig.STARTING_BALANCE
        var unaffordable = 0
        val observed = mutableListOf(balance)
        repeat(levelCount) { index ->
            val wantsHint = hintEvery != null && (index + 1) % hintEvery == 0
            if (wantsHint) {
                if (balance >= EconomyConfig.HINT_COST) balance -= EconomyConfig.HINT_COST else unaffordable += 1
                observed += balance
            }
            val reward = RewardPolicy.campaignCompletion(
                previousBalance = balance,
                wasFirstClearRewarded = false,
                previousBestStars = 0,
                earnedStars = stars,
            )
            balance = reward.resultingBalance
            observed += balance
            // A replay with the same result must never change balance.
            val replay = RewardPolicy.campaignCompletion(balance, true, stars, stars)
            check(replay.resultingBalance == balance)
        }
        val sorted = observed.sorted()
        return EconomySimulationResult(
            scenario = name,
            minimumBalance = sorted.first(),
            medianBalance = sorted[sorted.size / 2],
            finalBalance = balance,
            unaffordableHintRequests = unaffordable,
        )
    }
}
