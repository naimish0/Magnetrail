package com.rameshta.magnetrail.core.level

import com.rameshta.magnetrail.core.prototypeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelParserTest {
    private val parser = LevelParser()

    @Test
    fun `all prototype levels load from the authoritative docs resource`() {
        val catalog = prototypeCatalog()

        assertEquals(1, catalog.schemaVersion)
        assertEquals("magnetrail-core-1", catalog.ruleVersion)
        assertEquals("magnetrail-prototype-v1", catalog.catalogId)
        assertEquals((1..12).toList(), catalog.levels.map { it.number })
        assertEquals(12, catalog.levels.map { it.id }.distinct().size)
    }

    @Test
    fun `malformed JSON reports a parsing failure`() {
        val error = assertThrows(LevelParsingException::class.java) {
            parser.parseCatalog("{ not-json }")
        }

        assertTrue(error.message.orEmpty().contains("not valid JSON"))
    }

    @Test
    fun `unsupported schema and rule versions fail fast`() {
        assertValidationFailure(catalogJson(schemaVersion = 2), "schemaVersion")
        assertValidationFailure(catalogJson(ruleVersion = "future-rules"), "ruleVersion")
    }

    @Test
    fun `out of bounds entity fails validation`() {
        assertValidationFailure(
            catalogJson(arrows = """[{"id":"A","row":0,"column":1,"printedDirection":"E"}]"""),
            "outside",
        )
    }

    @Test
    fun `duplicate entity IDs fail validation`() {
        assertValidationFailure(
            catalogJson(
                arrows = """[{"id":"A","row":1,"column":1,"printedDirection":"E"}]""",
                magnets = """[{"id":"A","row":3,"column":3,"initialPolarity":"PULL"}]""",
            ),
            "duplicate entity ID",
        )
    }

    @Test
    fun `duplicate occupied cells fail validation`() {
        assertValidationFailure(
            catalogJson(
                arrows = """[{"id":"A","row":1,"column":1,"printedDirection":"E"}]""",
                walls = """[{"row":1,"column":1}]""",
            ),
            "multiple entities",
        )
    }

    @Test
    fun `invalid direction and polarity fail validation`() {
        assertValidationFailure(
            catalogJson(arrows = """[{"id":"A","row":1,"column":1,"printedDirection":"UP"}]"""),
            "invalid printedDirection",
        )
        assertValidationFailure(
            catalogJson(magnets = """[{"id":"M1","row":3,"column":3,"initialPolarity":"NEUTRAL"}]"""),
            "invalid initialPolarity",
        )
    }

    @Test
    fun `designed solution must reference every arrow exactly once`() {
        assertValidationFailure(
            catalogJson(
                arrows = """[
                    {"id":"A","row":1,"column":1,"printedDirection":"E"},
                    {"id":"B","row":2,"column":1,"printedDirection":"E"}
                ]""".trimIndent(),
                designedSolutions = """[["A","A"]]""",
            ),
            "every arrow ID exactly once",
        )
    }

    private fun assertValidationFailure(source: String, expectedMessagePart: String) {
        val error = assertThrows(LevelValidationException::class.java) {
            parser.parseCatalog(source)
        }
        assertTrue(
            "Expected '${error.message}' to contain '$expectedMessagePart'",
            error.message.orEmpty().contains(expectedMessagePart),
        )
    }

    private fun catalogJson(
        schemaVersion: Int = 1,
        ruleVersion: String = "magnetrail-core-1",
        arrows: String = """[{"id":"A","row":1,"column":1,"printedDirection":"E"}]""",
        magnets: String = "[]",
        walls: String = "[]",
        designedSolutions: String = """[["A"]]""",
    ): String = """
        {
          "schemaVersion": $schemaVersion,
          "ruleVersion": "$ruleVersion",
          "catalogId": "test-catalog",
          "levels": [{
            "id": "test-001",
            "number": 1,
            "title": "Test level",
            "width": 4,
            "height": 4,
            "arrows": $arrows,
            "magnets": $magnets,
            "walls": $walls,
            "designedSolutions": $designedSolutions
          }]
        }
    """.trimIndent()
}
