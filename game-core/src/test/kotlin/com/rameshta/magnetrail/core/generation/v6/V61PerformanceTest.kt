package com.rameshta.magnetrail.core.generation.v6

import com.rameshta.magnetrail.core.model.Arrow
import com.rameshta.magnetrail.core.model.Direction
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.model.Position
import com.rameshta.magnetrail.core.solver.SolverResult
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V61PerformanceTest {
    @Test
    fun `campaign graph-first constructors expose exact synthesis evidence`() {
        val generator = GeneratorV61()
        fun request(band: AutomatedDifficultyBandV61, campaign: Boolean = true) = V61GenerationRequest(
            levelId = "synthesis-${band.name.lowercase()}",
            playerFacingNumber = 1_500,
            ordinal = 1_488,
            band = band,
            causalFamilyIndex = 5,
            varyCausalFamilyByAttempt = campaign,
            budgets = V61GenerationBudgets(maximumAttempts = 1),
        )

        listOf(
            AutomatedDifficultyBandV61.EASY,
            AutomatedDifficultyBandV61.MEDIUM,
            AutomatedDifficultyBandV61.HARD,
            AutomatedDifficultyBandV61.SUPER_HARD,
            AutomatedDifficultyBandV61.EXPERT,
        ).forEach { band ->
            val first = generator.synthesisGraphFingerprintForAttempt(request(band), 0)
            val repeated = generator.synthesisGraphFingerprintForAttempt(request(band), 0)
            assertTrue(first.startsWith("sha256:"))
            assertEquals(first, repeated)
        }
        assertEquals(
            "unavailable:synthesis-graph",
            generator.synthesisGraphFingerprintForAttempt(
                request(AutomatedDifficultyBandV61.EASY, campaign = false),
                0,
            ),
        )
    }

    @Test
    fun `hard capacity embeddings are additive after the frozen checkpoint epoch`() {
        val base = level(Direction.EAST).copy(
            width = 6,
            height = 6,
            arrows = listOf(
                Arrow("a", Position(1, 2), Direction.EAST),
                Arrow("b", Position(5, 6), Direction.NORTH),
            ),
        )

        assertEquals(base, applyHardCampaignCapacityEmbeddingV61(base, 0))
        assertEquals(
            base,
            applyHardCampaignCapacityEmbeddingV61(
                base,
                V61_HARD_CAPACITY_EMBEDDING_START_ATTEMPT - 1,
            ),
        )
        assertEquals(
            0,
            hardCampaignCapacityAttemptV61(V61_HARD_CAPACITY_ROLLOUT_LEVEL - 1, 0),
        )
        assertEquals(
            V61_HARD_CAPACITY_EMBEDDING_START_ATTEMPT,
            hardCampaignCapacityAttemptV61(V61_HARD_CAPACITY_ROLLOUT_LEVEL, 0),
        )
        val embedded = (0 until 7).map { offset ->
            applyHardCampaignCapacityEmbeddingV61(
                base,
                V61_HARD_CAPACITY_EMBEDDING_START_ATTEMPT + offset,
            )
        }

        assertEquals(7, embedded.map { board -> board.arrows.map(Arrow::position) }.toSet().size)
        assertTrue(embedded.all { board ->
            board.arrows.map(Arrow::printedDirection) == base.arrows.map(Arrow::printedDirection) &&
                board.arrows.all { it.position.row in 1..6 && it.position.column in 1..6 }
        })
    }

    @Test
    fun `analysis cache hits identical content and invalidates changed content`() {
        val cache = V61AnalysisCache()
        val computes = AtomicInteger()
        val east = level(Direction.EAST)
        val west = level(Direction.WEST)
        fun compute() = SolverResult(false, null, 0, false, null, emptyList(), 1).also {
            computes.incrementAndGet()
        }

        val first = cache.solver(east, 100, ::compute)
        val repeated = cache.solver(east.copy(id = "same-content-new-id"), 100, ::compute)
        val changed = cache.solver(west, 100, ::compute)

        assertEquals(first, repeated)
        assertEquals(first, changed)
        assertEquals(2, computes.get())
        assertEquals(1, cache.stats().hits)
        assertEquals(2, cache.stats().misses)
    }

    @Test
    fun `analysis cache batch clear releases evidence and forces exact recomputation`() {
        val cache = V61AnalysisCache()
        val computes = AtomicInteger()
        val board = level(Direction.EAST)
        fun compute() = SolverResult(false, null, 0, false, null, emptyList(), 1).also {
            computes.incrementAndGet()
        }

        cache.solver(board, 100, ::compute)
        cache.solver(board, 100, ::compute)
        cache.clear()
        cache.solver(board, 100, ::compute)

        assertEquals(2, computes.get())
        assertEquals(1, cache.stats().hits)
        assertEquals(2, cache.stats().misses)
    }

    @Test
    fun `indexed semantic acceleration exactly verifies every possible threshold neighbour`() {
        val archived = fingerprint("exact-a", "policy-a", descriptor = List(18) { 4.0 })
        val index = V61FingerprintIndex(listOf(archived))
        val samePolicy = fingerprint("exact-b", "policy-a", descriptor = List(18) { 4.0 })
        val differentPolicy = fingerprint("exact-c", "policy-b", descriptor = List(18) { 4.0 })

        assertEquals("REJECT_SOLUTION_POLICY_DUPLICATE", index.duplicateReason(samePolicy))
        assertEquals(null, index.duplicateReason(differentPolicy))
        assertTrue((index.attachExactNearest(samePolicy).nearestSemanticSimilarity ?: 0.0) > 0.92)
        assertNotEquals(archived.exactLayout, differentPolicy.exactLayout)
    }

    @Test
    fun `indexed layout gate rejects exact content before semantic analysis`() {
        val board = level(Direction.EAST)
        val archived = fingerprint("placeholder", "policy-a", descriptor = List(18) { 1.0 }).copy(
            exactLayout = com.rameshta.magnetrail.core.content.ContentFingerprint.exact(board),
            d4Layout = com.rameshta.magnetrail.core.content.ContentFingerprint.symmetryNormalized(board),
        )
        val index = V61FingerprintIndex(listOf(archived))

        assertEquals("REJECT_EXACT_DUPLICATE", index.layoutDuplicateReason(board.copy(id = "renamed")))
    }

    @Test
    fun `indexed uniqueness gate rejects visual and relevance clones independently`() {
        val archived = fingerprint("a", "policy-a", descriptor = List(18) { 1.0 })
        val index = V61FingerprintIndex(listOf(archived))

        assertEquals(
            "REJECT_ARROW_LAYOUT_DUPLICATE",
            index.duplicateReason(fingerprint("b", "policy-b", List(18) { 2.0 }).copy(
                arrowLayout = archived.arrowLayout,
            )),
        )
        assertEquals(
            "REJECT_INTERACTIVE_LAYOUT_DUPLICATE",
            index.duplicateReason(fingerprint("c", "policy-c", List(18) { 3.0 }).copy(
                interactiveLayout = archived.interactiveLayout,
            )),
        )
        assertEquals(
            "REJECT_PERCEPTUAL_LAYOUT_DUPLICATE",
            index.duplicateReason(fingerprint("d", "policy-d", List(18) { 4.0 }).copy(
                perceptualLayout = archived.perceptualLayout,
            )),
        )
        assertEquals(
            "REJECT_RELEVANCE_DUPLICATE",
            index.duplicateReason(fingerprint("e", "policy-e", List(18) { 5.0 }).copy(
                relevancePrunedD4Layout = archived.relevancePrunedD4Layout,
            )),
        )
    }

    private fun level(direction: Direction) = LevelDefinition(
        id = "cache-level",
        number = 1,
        title = "Cache level",
        width = 2,
        height = 1,
        arrows = listOf(Arrow("a", Position(1, 1), direction)),
        magnets = emptyList(),
        walls = emptyList(),
        designedSolutions = emptyList(),
    )

    private fun fingerprint(exact: String, policy: String, descriptor: List<Double>) = V6FingerprintBundle(
        exactLayout = exact,
        d4Layout = "d4-$exact",
        arrowLayout = "arrow-$exact",
        interactiveLayout = "interactive-$exact",
        perceptualLayout = "perceptual-$exact",
        relevancePrunedD4Layout = "relevance-$exact",
        causalHypergraph = "causal-$exact",
        quotientDecisionDag = "dag-$exact",
        solutionPolicy = policy,
        meaningfulDecisionTrace = "trace-$exact",
        mechanicRhythm = "same-rhythm",
        behaviouralDescriptor = descriptor,
    )
}
