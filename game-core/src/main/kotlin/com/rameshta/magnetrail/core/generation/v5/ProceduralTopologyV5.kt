package com.rameshta.magnetrail.core.generation.v5

import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.model.Position
import java.security.MessageDigest
import kotlinx.serialization.Serializable

/** Versioned topology identity; a family describes causal structure, never a difficulty score. */
@Serializable
enum class TopologyFamilyV5 {
    ORDERED_POLARITY_V1,
    CAUSAL_POLARITY_TAIL_V2,
    ORDERED_POLARITY_STAIRCASE_V3,
    ORDERED_LONG_RANGE_WEAVE_V4,
    ORDERING_CHAIN,
    POLARITY_CHAIN,
    EXPOSURE_CHAIN,
    MAGNET_CONTROLLER_CHAIN,
    WALL_OCCLUSION,
    ARROW_MAGNET_INTERLOCK,
    BRANCHING_DEPENDENCY,
    MULTI_STAGE_DEPENDENCY,
}

@Serializable
enum class EmptyCellPurposeTypeV5 {
    MAGNETIC_CORRIDOR,
    DELAYED_CONTROLLER_EXPOSURE,
    MOVEMENT_CORRIDOR,
    CONTROLLER_SEPARATION,
}

data class PurposefulEmptyCellV5(
    val position: Position,
    val purpose: EmptyCellPurposeTypeV5,
    val causalEdge: String,
) {
    init {
        require(causalEdge.isNotBlank()) { "Every empty cell must identify its causal purpose" }
    }
}

/** Runtime-stable identity. It stores reconstruction inputs, not a generated board. */
@Serializable
data class LogicalLevelIdentityV5(
    val levelNumber: Int,
    val profileId: String,
    val generatorVersion: Int = GENERATOR_VERSION_V5,
    val seed: Long,
    val topologyFamily: TopologyFamilyV5,
    val variant: Int = 0,
) {
    init {
        require(levelNumber > 0 && profileId.isNotBlank() && generatorVersion > 0 && variant >= 0)
    }

    val stableId: String
        get() = "generated-v$generatorVersion-${profileId}-${levelNumber.toString().padStart(6, '0')}-$variant"
}

data class CausalGraphV5(
    val topologyFamily: TopologyFamilyV5,
    val nodes: List<SolutionContractNodeV5>,
    val edges: List<SolutionContractEdgeV5>,
) {
    init {
        val ids = nodes.map { it.id }
        require(ids.distinct().size == ids.size) { "Causal node IDs must be unique" }
        require(edges.all { it.fromNodeId in ids && it.toNodeId in ids }) {
            "Every causal edge must reference a declared node"
        }
        val incoming = ids.associateWith { id -> edges.count { it.toNodeId == id } }.toMutableMap()
        val queue = ArrayDeque(incoming.filterValues { it == 0 }.keys.sorted())
        var visited = 0
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            visited += 1
            edges.filter { it.fromNodeId == id }.forEach { edge ->
                val remaining = requireNotNull(incoming[edge.toNodeId]) - 1
                incoming[edge.toNodeId] = remaining
                if (remaining == 0) queue.addLast(edge.toNodeId)
            }
        }
        require(visited == ids.size) { "Causal graph must be acyclic" }
    }

    val deterministicFingerprint: String
        get() = sha256(
            buildList {
                add(topologyFamily.name)
                addAll(nodes.sortedBy { it.id }.map { "${it.id}:${it.role}:${it.objectKey}" })
                addAll(edges.sortedWith(compareBy(SolutionContractEdgeV5::fromNodeId, SolutionContractEdgeV5::toNodeId))
                    .map { "${it.fromNodeId}>${it.toNodeId}:${it.relationship}" })
            }.joinToString("|"),
        )
}

data class ProceduralCandidateRecordV5(
    val identity: LogicalLevelIdentityV5,
    val structuralFingerprint: String,
    val exactFingerprint: String,
    val certified: Boolean,
    val rejectionReasons: List<String> = emptyList(),
)

data class EmptyCellPolicyResultV5(
    val emptyCellCount: Int,
    val emptyCellRatio: Double,
    val purposeCount: Int,
    val violations: List<String>,
) {
    val accepted: Boolean get() = violations.isEmpty()
}

/** Empty cells are accepted only when each one maps to a declared, in-bounds causal purpose. */
object EmptyCellPolicyV5 {
    fun evaluate(
        level: LevelDefinition,
        purposes: List<PurposefulEmptyCellV5>,
        maximumRatio: Double,
    ): EmptyCellPolicyResultV5 {
        require(maximumRatio in 0.0..0.60)
        val occupied = buildSet {
            addAll(level.arrows.map { it.position })
            addAll(level.magnets.map { it.position })
            addAll(level.walls.map { it.position })
        }
        val empty = (1..level.height).flatMap { row ->
            (1..level.width).map { column -> Position(row, column) }
        }.filterNot { it in occupied }.toSet()
        val declared = purposes.map { it.position }.toSet()
        val cells = level.width * level.height
        val ratio = if (cells == 0) 0.0 else empty.size.toDouble() / cells
        val violations = buildList {
            if (ratio > maximumRatio) add("purposeful-empty-budget-exceeded")
            if (declared != empty) add("empty-cell-purpose-mismatch")
            if (purposes.map { it.position }.distinct().size != purposes.size) {
                add("duplicate-empty-cell-purpose")
            }
        }
        return EmptyCellPolicyResultV5(empty.size, ratio, purposes.size, violations)
    }
}

/** Deterministic candidate pool. Exact and structural duplicates are rejected before insertion. */
class CandidatePoolV5 {
    private val records = mutableListOf<ProceduralCandidateRecordV5>()
    private val exact = mutableSetOf<String>()
    private val structural = mutableSetOf<String>()

    fun offer(record: ProceduralCandidateRecordV5): Boolean {
        if (!record.certified || record.exactFingerprint in exact || record.structuralFingerprint in structural) {
            return false
        }
        exact += record.exactFingerprint
        structural += record.structuralFingerprint
        records += record
        return true
    }

    fun snapshot(): List<ProceduralCandidateRecordV5> = records.toList()
}

fun ConstructedCandidateV5.causalGraph(): CausalGraphV5 = CausalGraphV5(
    topologyFamily = topologyFamily,
    nodes = contract.nodes,
    edges = contract.edges,
)

fun interface BoardRealizerV5 {
    fun realize(
        request: GenerationRequestV5,
        seed: Long,
        topologyFamily: TopologyFamilyV5,
    ): ConstructedCandidateV5
}

class SolutionFirstBoardRealizerV5(
    private val constructor: SolutionFirstConstructorV5 = SolutionFirstConstructorV5(),
) : BoardRealizerV5 {
    override fun realize(
        request: GenerationRequestV5,
        seed: Long,
        topologyFamily: TopologyFamilyV5,
    ): ConstructedCandidateV5 = constructor.construct(
        request.copy(topologyFamily = topologyFamily),
        seed,
    )
}

/** Offline factory; UI code receives metadata/IDs and never invokes generation while rendering. */
class DeterministicLevelFactoryV5(
    profiles: List<GenerationProfileV5>,
    private val generator: LevelGeneratorV5 = LevelGeneratorV5(),
) {
    private val profilesById = profiles.associateBy { it.id }

    fun generate(identity: LogicalLevelIdentityV5): GenerationResultV5 {
        val profile = requireNotNull(profilesById[identity.profileId]) {
            "Unknown generation profile ${identity.profileId}"
        }
        require(identity.generatorVersion == GENERATOR_VERSION_V5) {
            "Unsupported generator version ${identity.generatorVersion}"
        }
        return generator.generate(
            GenerationRequestV5(
                stableId = identity.stableId,
                sequenceNumber = identity.levelNumber,
                title = "Level ${identity.levelNumber}",
                seed = identity.seed,
                profile = profile,
                topologyFamily = identity.topologyFamily,
            ),
        )
    }
}

/** High-band registry retains V5.1 and exposes two materially different causal prototypes. */
object HighBandTopologyRegistryV5 {
    val supportedFamilies: List<TopologyFamilyV5> = listOf(
        TopologyFamilyV5.ORDERED_POLARITY_V1,
        TopologyFamilyV5.CAUSAL_POLARITY_TAIL_V2,
        TopologyFamilyV5.ORDERED_POLARITY_STAIRCASE_V3,
        TopologyFamilyV5.ORDERED_LONG_RANGE_WEAVE_V4,
    )
    private val automaticFamilies: List<TopologyFamilyV5> = listOf(
        TopologyFamilyV5.ORDERED_LONG_RANGE_WEAVE_V4,
    )

    fun defaultFamily(profile: GenerationProfileV5): TopologyFamilyV5 = when (profile.id) {
        "v5-d2.1-expert", "v5-d2.1-master" -> TopologyFamilyV5.ORDERED_LONG_RANGE_WEAVE_V4
        "v5-campaign-v9-super-hard", "v5-campaign-v9-expert", "v5-campaign-v9-master" ->
            TopologyFamilyV5.ORDERED_LONG_RANGE_WEAVE_V4
        else -> TopologyFamilyV5.ORDERING_CHAIN
    }

    fun familyForAttempt(profile: GenerationProfileV5, attempt: Int): TopologyFamilyV5 {
        require(attempt >= 0)
        return if (
            profile.id == "v5-d2.1-expert" || profile.id == "v5-d2.1-master" ||
            profile.id.startsWith("v5-campaign-v9-")
        ) {
            // V5.2's purposeful-space weave is the only high-band family currently proven to
            // preserve replay/V4 completeness and pass every unchanged structural gate.
            automaticFamilies[attempt % automaticFamilies.size]
        } else {
            defaultFamily(profile)
        }
    }
}

internal fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray())
    .joinToString("") { byte -> "%02x".format(byte) }
