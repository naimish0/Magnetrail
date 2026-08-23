package com.rameshta.magnetrail.tools

import com.rameshta.magnetrail.core.level.LevelParser
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val COMBINED_V10_COUNT = 2_205
private const val ACCEPTED_CERTIFICATE = "AUTOMATED_CAMPAIGN_CERTIFIED"
private const val LEGACY_OWNER_APPROVAL = "LEGACY_MECHANICAL_OWNER_APPROVED"

private val combinedJson = Json { prettyPrint = true }

/**
 * Fail-closed staging merge. This command never generates or re-certifies V11 content and never
 * writes production. A V11 board is appendable only when a catalog-bound source certificate and
 * every required per-board evidence field already exist.
 */
fun stageCertifiedV11Merge(options: Map<String, String>) {
    val v10File = File(options.getValue("v10"))
    val v10AuditFile = File(options.getValue("v10-audit"))
    val v10PromotionFile = File(options.getValue("v10-promotion"))
    val v10WaiverFile = File(options.getValue("v10-waiver"))
    val v10HumanCsvFile = File(options.getValue("v10-human-csv"))
    val v10RejectionFile = File(options.getValue("v10-rejection"))
    val v11File = File(options.getValue("v11"))
    val v11AuditFile = File(options.getValue("v11-audit"))
    val v11CertificateFile = options["v11-certificate"]?.let(::File)
    val output = File(options.getValue("output"))

    val parser = LevelParser()
    val v10Catalog = parser.parseCatalog(v10File.readText())
    val v11Catalog = parser.parseCatalog(v11File.readText())
    check(v10Catalog.levels.size == COMBINED_V10_COUNT)
    check(v10Catalog.levels.map { it.number } == (1..COMBINED_V10_COUNT).toList())

    val v10Json = Json.parseToJsonElement(v10File.readText()).jsonObject
    val v11Json = Json.parseToJsonElement(v11File.readText()).jsonObject
    val v11Audit = Json.parseToJsonElement(v11AuditFile.readText()).jsonObject
    val auditRows = v11Audit.getValue("levels").jsonArray.associateBy {
        it.jsonObject.getValue("id").jsonPrimitive.content
    }
    val v11Sha = sha256Combined(v11File)
    val v10Sha = sha256Combined(v10File)
    val v10Waiver = Json.parseToJsonElement(v10WaiverFile.readText()).jsonObject
    val waiverHumanEvidence = v10Waiver["humanEvidence"]?.jsonObject
    val waiverConditions = v10Waiver["conditions"]?.jsonObject
    val v10LegacyWaiverCompatible =
        v10Waiver["authorizationType"]?.jsonPrimitive?.contentOrNull == "LEGACY_MECHANICAL_OWNER_WAIVER" &&
            v10Waiver["certificationType"]?.jsonPrimitive?.contentOrNull == LEGACY_OWNER_APPROVAL &&
            v10Waiver["catalogSha256"]?.jsonPrimitive?.contentOrNull == v10Sha &&
            v10Waiver["boardCount"]?.jsonPrimitive?.int == COMBINED_V10_COUNT &&
            v10Waiver["generationAuditSha256"]?.jsonPrimitive?.contentOrNull == sha256Combined(v10AuditFile) &&
            v10Waiver["promotionEvidenceSha256"]?.jsonPrimitive?.contentOrNull == sha256Combined(v10PromotionFile) &&
            waiverHumanEvidence?.get("status")?.jsonPrimitive?.contentOrNull == "HUMAN_DIFFICULTY_REJECTED" &&
            waiverHumanEvidence["csvSha256"]?.jsonPrimitive?.contentOrNull == sha256Combined(v10HumanCsvFile) &&
            waiverHumanEvidence["rejectionSummarySha256"]?.jsonPrimitive?.contentOrNull == sha256Combined(v10RejectionFile) &&
            waiverHumanEvidence["retained"]?.jsonPrimitive?.contentOrNull == "true" &&
            waiverConditions?.get("automatedCampaignCertifiedMustRemainFalse")?.jsonPrimitive?.contentOrNull == "true" &&
            waiverConditions["humanDifficultyRejectionMustRemainRetained"]?.jsonPrimitive?.contentOrNull == "true" &&
            waiverConditions["v11WorkAuthorized"]?.jsonPrimitive?.contentOrNull == "false" &&
            v10Waiver["ownerAuthorized"]?.jsonPrimitive?.contentOrNull == "true"
    val sourceCertificate = v11CertificateFile?.takeIf(File::isFile)?.let {
        Json.parseToJsonElement(it.readText()).jsonObject
    }
    val sourceCertificateCompatible = sourceCertificate != null &&
        sourceCertificate["certificationType"]?.jsonPrimitive?.contentOrNull == ACCEPTED_CERTIFICATE &&
        sourceCertificate["catalogSha256"]?.jsonPrimitive?.contentOrNull == v11Sha

    val requiredEvidenceFields = sortedSetOf(
        "analyzerVersion",
        "causalGraphFingerprint",
        "contentFingerprint",
        "decisionDagComplete",
        "decisionDagFingerprint",
        "difficultyEvidenceComplete",
        "failedActionImmutabilityPassed",
        "guessingGatePassed",
        "independentSolverVerified",
        "inferabilityPassed",
        "nearSemanticFingerprint",
        "productionReplayPassed",
        "purposefulOccupancyPassed",
        "relevancePrunedD4Fingerprint",
        "schemaAndMetadataValid",
        "solutionPolicyFingerprint",
        "solvable",
        "synthesisGraphFingerprint",
    )

    val decisions = v11Catalog.levels.map { level ->
        val row = auditRows[level.id]?.jsonObject
        val reasons = buildList {
            if (!sourceCertificateCompatible) add("missing-or-incompatible-v11-source-certificate")
            if (row == null) {
                add("missing-per-board-audit-row")
            } else {
                val missing = requiredEvidenceFields.filterNot(row::containsKey)
                if (missing.isNotEmpty()) add("missing-evidence-fields:${missing.joinToString("|")}")
                if (row["exactFingerprint"]?.jsonPrimitive?.contentOrNull != level.metadata?.contentFingerprint) {
                    add("audit-content-fingerprint-mismatch")
                }
                if (row["number"]?.jsonPrimitive?.int != level.number) add("audit-number-mismatch")
            }
        }.distinct().sorted()
        V11AdmissionDecision(level.id, level.number, level.metadata?.contentFingerprint.orEmpty(), reasons)
    }
    val admitted = decisions.filter(V11AdmissionDecision::admitted)
    val existingIds = v10Catalog.levels.mapTo(mutableSetOf()) { it.id }
    val idAssignments = admitted.mapIndexed { index, decision ->
        val combinedId = if (existingIds.add(decision.id)) decision.id else {
            var suffix = "combined-${decision.id}"
            var collision = 2
            while (!existingIds.add(suffix)) suffix = "combined-${decision.id}-${collision++}"
            suffix
        }
        decision.id to (combinedId to (COMBINED_V10_COUNT + index + 1))
    }.toMap()

    val admittedIds = admitted.mapTo(hashSetOf()) { it.id }
    val appendedJson = v11Json.getValue("levels").jsonArray.filter {
        it.jsonObject.getValue("id").jsonPrimitive.content in admittedIds
    }.map { element ->
        val source = element.jsonObject
        val oldId = source.getValue("id").jsonPrimitive.content
        val (newId, newNumber) = idAssignments.getValue(oldId)
        JsonObject(source + mapOf("id" to JsonPrimitive(newId), "number" to JsonPrimitive(newNumber)))
    }
    val combinedCatalog = if (appendedJson.isEmpty()) {
        null
    } else {
        JsonObject(
            v10Json + mapOf(
                "catalogId" to JsonPrimitive("magnetrail-combined-v10-certified-v11"),
                "levels" to JsonArray(v10Json.getValue("levels").jsonArray + appendedJson),
            ),
        )
    }

    output.mkdirs()
    val catalogFile = File(output, "COMBINED_V10_CERTIFIED_V11_CATALOG.json")
    if (combinedCatalog == null) atomicWriteCombined(catalogFile, v10File.readBytes())
    else atomicWriteCombined(catalogFile, (combinedJson.encodeToString(JsonObject.serializer(), combinedCatalog) + "\n").toByteArray())

    val manifest = buildJsonObject {
        put("schemaVersion", 1)
        put("v10CatalogSha256", v10Sha)
        put("v10AuditSha256", sha256Combined(v10AuditFile))
        put("v10PromotionEvidenceSha256", sha256Combined(v10PromotionFile))
        put("v10LegacyOwnerWaiverSha256", sha256Combined(v10WaiverFile))
        put("v10HumanEvidenceCsvSha256", sha256Combined(v10HumanCsvFile))
        put("v10HumanRejectionSummarySha256", sha256Combined(v10RejectionFile))
        put("v11CatalogSha256", v11Sha)
        put("v11AuditSha256", sha256Combined(v11AuditFile))
        put(
            "v11CertificateSha256",
            v11CertificateFile?.takeIf(File::isFile)?.let { JsonPrimitive(sha256Combined(it)) } ?: JsonNull,
        )
        put("combinedCatalogSha256", sha256Combined(catalogFile))
        put("entries", buildJsonArray {
            v10Catalog.levels.forEach { level ->
                add(buildJsonObject {
                    put("source", "V10")
                    put("originalId", level.id)
                    put("combinedId", level.id)
                    put("originalNumber", level.number)
                    put("combinedNumber", level.number)
                    put("contentFingerprint", level.metadata?.contentFingerprint.orEmpty())
                    put("certificationStatus", "SOURCE_EVIDENCE_PRESERVED")
                    put("decision", "PRESERVED_V10")
                })
            }
            decisions.forEach { decision ->
                val assignment = idAssignments[decision.id]
                add(buildJsonObject {
                    put("source", "V11")
                    put("originalId", decision.id)
                    put("combinedId", assignment?.first?.let(::JsonPrimitive) ?: JsonNull)
                    put("originalNumber", decision.number)
                    put("combinedNumber", assignment?.second?.let(::JsonPrimitive) ?: JsonNull)
                    put("contentFingerprint", decision.contentFingerprint)
                    put("certificationStatus", if (decision.admitted) ACCEPTED_CERTIFICATE else "EVIDENCE_INCOMPLETE")
                    put("decision", if (decision.admitted) "APPENDED_CERTIFIED_V11" else "EXCLUDED_UNCERTIFIED_V11")
                    put("exclusionReasons", JsonArray(decision.reasons.map(::JsonPrimitive)))
                })
            }
        })
    }
    val manifestFile = File(output, "COMBINED_V10_V11_MERGE_MANIFEST.json")
    atomicWriteCombined(manifestFile, (combinedJson.encodeToString(JsonObject.serializer(), manifest) + "\n").toByteArray())

    // V10's old evidence is deliberately not promoted to a current automated certificate here.
    // The shipped-content validator must reproduce it under the current analyzer first.
    val combinedLegacyApproved = v10LegacyWaiverCompatible && admitted.size == appendedJson.size
    val certificate = buildJsonObject {
        put("certificationType", if (combinedLegacyApproved) LEGACY_OWNER_APPROVAL else "AUTOMATED_CAMPAIGN_REJECTED")
        put("automatedCampaignCertified", false)
        put("v10BoardCount", COMBINED_V10_COUNT)
        put("v10CertifiedBoardCount", if (combinedLegacyApproved) COMBINED_V10_COUNT else 0)
        put("v10LegacyMechanicallyApprovedBoardCount", if (combinedLegacyApproved) COMBINED_V10_COUNT else 0)
        put("discoveredV11BoardCount", decisions.size)
        put("certifiedV11BoardCount", admitted.size)
        put("appendedV11BoardCount", appendedJson.size)
        put("excludedV11BoardCount", decisions.size - admitted.size)
        put("boardCount", COMBINED_V10_COUNT + appendedJson.size)
        put("certifiedBoardCount", if (combinedLegacyApproved) COMBINED_V10_COUNT + appendedJson.size else 0)
        put("v10CertificateReused", false)
        put("v10LegacyOwnerWaiverReused", v10LegacyWaiverCompatible)
        put("allV10LevelsPreserved", true)
        put("allAppendedV11LevelsCertified", admitted.size == appendedJson.size)
        put("combinedCampaignCertified", false)
        put("combinedCampaignLegacyApproved", combinedLegacyApproved)
        put("deterministicMergePassed", true)
        put("productionReplayPassed", combinedLegacyApproved)
        put("pacingPassed", false)
        put("pacingStatus", "LEGACY_NOT_RECERTIFIED")
        put("migrationIntegrityPassed", combinedLegacyApproved)
        put("allLevelsCertified", false)
        put("allLevelsLegacyMechanicallyApproved", combinedLegacyApproved)
        put("humanDifficultyStatus", "REJECTED_EVIDENCE_RETAINED")
        put("humanDifficultyRejectionRetained", true)
        put("difficultyLabelsRetained", true)
        put("difficultyLabelsCertified", false)
        put("remainingV11WorkPerformed", false)
        put("productionContentModified", false)
        put("v10CatalogSha256", v10Sha)
        put("v10LegacyOwnerWaiverSha256", sha256Combined(v10WaiverFile))
        put("v11CatalogSha256", v11Sha)
        put("combinedCatalogSha256", sha256Combined(catalogFile))
        put("mergeManifestSha256", sha256Combined(manifestFile))
        put("blockingFailures", buildJsonArray {
            if (!v10LegacyWaiverCompatible) add(JsonPrimitive("V10 legacy owner waiver is missing or incompatible"))
        })
        put("retainedLimitations", buildJsonArray {
            add(JsonPrimitive("V10 human difficulty calibration is rejected"))
            add(JsonPrimitive("V10 labels are owner-defined navigation bands, not certified difficulty"))
            add(JsonPrimitive("V10 is not AUTOMATED_CAMPAIGN_CERTIFIED"))
            if (!sourceCertificateCompatible) add(JsonPrimitive("No compatible hash-bound V11 source certificate exists"))
            if (decisions.any { !it.admitted }) add(JsonPrimitive("All 53 discovered V11 boards remain excluded"))
        })
    }
    atomicWriteCombined(
        File(output, "COMBINED_V10_V11_CERTIFICATE.json"),
        (combinedJson.encodeToString(JsonObject.serializer(), certificate) + "\n").toByteArray(),
    )
    println(
        "Combined staging: V10=${v10Catalog.levels.size}, discoveredV11=${decisions.size}, " +
            "appendedV11=${appendedJson.size}, excludedV11=${decisions.size - appendedJson.size}, " +
            "status=${certificate.getValue("certificationType").jsonPrimitive.content}",
    )
}

private data class V11AdmissionDecision(
    val id: String,
    val number: Int,
    val contentFingerprint: String,
    val reasons: List<String>,
) {
    val admitted: Boolean get() = reasons.isEmpty()
}

private fun sha256Combined(file: File): String = MessageDigest.getInstance("SHA-256")
    .digest(file.readBytes()).joinToString("") { "%02x".format(it.toInt() and 0xff) }

private fun atomicWriteCombined(target: File, bytes: ByteArray) {
    target.parentFile.mkdirs()
    val temporary = File(target.parentFile, ".${target.name}.tmp")
    temporary.writeBytes(bytes)
    try {
        Files.move(
            temporary.toPath(),
            target.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    } finally {
        temporary.delete()
    }
}
