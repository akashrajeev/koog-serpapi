package dev.serpapi.koog

import dev.serpapi.koog.client.SerpApiClient
import dev.serpapi.koog.client.SerpEngine
import dev.serpapi.koog.tools.SerpApiTools
import dev.serpapi.koog.tools.SerpNewsTool
import dev.serpapi.koog.tools.SerpWebSearchTool
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SerpApiToolsTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun clientWith(body: String): SerpApiClient =
        SerpApiClient(apiKey = "test-key", http = HttpClient(MockEngine { respond(body, HttpStatusCode.OK, jsonHeaders) }))

    @Test
    fun registryContainsAllSixTools() {
        val registry = SerpApiTools.registry(clientWith("{}"))
        val names = registry.tools.map { it.name }.toSet()
        assertEquals(
            setOf(
                "serp_web_search", "serp_local_search", "serp_shopping_search",
                "serp_flights_search", "serp_scholar_search", "serp_news_search",
            ),
            names,
        )
    }

    @Test
    fun registryRespectsEngineSubset() {
        val registry = SerpApiTools.registry(
            clientWith("{}"),
            engines = setOf(SerpEngine.GOOGLE, SerpEngine.GOOGLE_NEWS),
        )
        assertEquals(setOf("serp_web_search", "serp_news_search"), registry.tools.map { it.name }.toSet())
    }

    @Test
    fun everyToolHasNameAndDescription() {
        SerpApiTools.registry(clientWith("{}")).tools.forEach { tool ->
            assertTrue(tool.name.startsWith("serp_"), "tool name should be namespaced: ${tool.name}")
            assertTrue(tool.descriptor.description.length > 40, "tool needs a real description: ${tool.name}")
        }
    }

    @Test
    fun webSearchToolRendersMarkdownWithCitations() = runTest {
        val tool = SerpWebSearchTool(
            clientWith(
                """
                {
                  "answer_box": {"answer": "42"},
                  "organic_results": [
                    {"position": 1, "title": "The Answer", "link": "https://example.com/a", "snippet": "Deep thought"}
                  ]
                }
                """.trimIndent()
            )
        )
        val out = tool.execute(SerpWebSearchTool.Args(query = "meaning of life"))
        assertTrue(out.contains("**Answer box:** 42"))
        assertTrue(out.contains("[The Answer](https://example.com/a)"))
        assertTrue(out.contains("Deep thought"))
    }

    @Test
    fun newsToolRendersSourceAndDate() = runTest {
        val tool = SerpNewsTool(
            clientWith(
                """{"news_results": [{"position": 1, "title": "Launch", "link": "https://n.example/1", "source": {"name": "TechCrunch"}, "date": "1 hour ago"}]}"""
            )
        )
        val out = tool.execute(SerpNewsTool.Args(query = "koog"))
        assertTrue(out.contains("[Launch](https://n.example/1)"))
        assertTrue(out.contains("TechCrunch"))
        assertTrue(out.contains("1 hour ago"))
    }

    @Test
    fun toolArgsDeserializeFromAgentJson() {
        val json = Json { ignoreUnknownKeys = true }
        val args = json.decodeFromString(
            SerpWebSearchTool.Args.serializer(),
            """{"query": "serpapi india hackathon", "country": "in"}""",
        )
        assertEquals("serpapi india hackathon", args.query)
        assertEquals("in", args.country)
        assertEquals(null, args.location)
    }

    @Test
    fun emptyResultsRenderFriendlyMessage() = runTest {
        val tool = SerpWebSearchTool(clientWith("""{"organic_results": []}"""))
        assertTrue(tool.execute(SerpWebSearchTool.Args(query = "zzz")).contains("No organic results"))
    }

    @Test
    fun descriptorIsDiscoverableThroughRegistry() {
        val registry = SerpApiTools.registry(clientWith("{}"))
        val tool = registry.getToolOrNull("serp_flights_search")
        assertNotNull(tool)
        assertTrue(tool.descriptor.requiredParameters.isNotEmpty(), "flights tool must declare required params")
    }
}
