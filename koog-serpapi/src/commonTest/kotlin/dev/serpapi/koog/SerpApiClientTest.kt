package dev.serpapi.koog

import dev.serpapi.koog.client.SerpApiClient
import dev.serpapi.koog.client.SerpApiException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SerpApiClientTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun clientReturning(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        captured: MutableList<Url> = mutableListOf(),
    ): SerpApiClient {
        val engine = MockEngine { request ->
            captured.add(request.url)
            respond(content = body, status = status, headers = jsonHeaders)
        }
        return SerpApiClient(apiKey = "test-key", http = HttpClient(engine))
    }

    @Test
    fun requestCarriesEngineKeyAndQuery() = runTest {
        val captured = mutableListOf<Url>()
        val client = clientReturning("""{"organic_results": []}""", captured = captured)
        client.searchWeb("koog kotlin", location = "Pune, India", country = "in")
        val url = captured.single()
        assertEquals("google", url.parameters["engine"])
        assertEquals("test-key", url.parameters["api_key"])
        assertEquals("koog kotlin", url.parameters["q"])
        assertEquals("Pune, India", url.parameters["location"])
        assertEquals("in", url.parameters["gl"])
    }

    @Test
    fun parsesOrganicResultsAndAnswerBox() = runTest {
        val client = clientReturning(
            """
            {
              "answer_box": {"answer": "Kotlin 2.4.20"},
              "organic_results": [
                {"position": 1, "title": "Koog docs", "link": "https://docs.koog.ai", "snippet": "Agent framework"},
                {"position": 2, "title": "Repo", "link": "https://github.com/JetBrains/koog"}
              ]
            }
            """.trimIndent()
        )
        val result = client.searchWeb("latest kotlin")
        assertEquals("Kotlin 2.4.20", result.answerBox)
        assertEquals(2, result.organic.size)
        assertEquals("Koog docs", result.organic[0].title)
        assertEquals("https://docs.koog.ai", result.organic[0].link)
        assertEquals("Agent framework", result.organic[0].snippet)
        assertEquals(null, result.organic[1].snippet)
    }

    @Test
    fun parsesLocalResultsArrayForm() = runTest {
        val client = clientReturning(
            """
            {"local_results": [
              {"position": 1, "title": "Cafe", "address": "FC Road, Pune",
               "rating": 4.3, "reviews": 812, "type": "Cafe", "hours": "Open · Closes 11 PM"}
            ]}
            """.trimIndent()
        )
        val result = client.searchLocal("cafes", location = "Pune")
        assertEquals(1, result.places.size)
        assertEquals("Cafe", result.places[0].title)
        assertEquals(4.3, result.places[0].rating)
        assertEquals(812, result.places[0].reviews)
    }

    @Test
    fun parsesLocalResultsObjectForm() = runTest {
        val client = clientReturning(
            """{"local_results": {"places": [{"position": 1, "title": "Mall", "address": "Wakad"}]}}"""
        )
        val result = client.searchLocal("malls near me")
        assertEquals("Mall", result.places.single().title)
    }

    @Test
    fun parsesShoppingResults() = runTest {
        val client = clientReturning(
            """
            {"shopping_results": [
              {"position": 1, "title": "Laptop X", "link": "https://example.in/p",
               "price": "₹74,990", "extracted_price": 74990.0, "source": "Flipkart",
               "rating": 4.5, "reviews": 1200, "delivery": "Free delivery"}
            ]}
            """.trimIndent()
        )
        val item = client.searchShopping("rtx 4060 laptop", country = "in").items.single()
        assertEquals("Laptop X", item.title)
        assertEquals("₹74,990", item.price)
        assertEquals(74990.0, item.extractedPrice)
        assertEquals("Flipkart", item.source)
    }

    @Test
    fun parsesFlightOptionsFromBestAndOther() = runTest {
        val client = clientReturning(
            """
            {
              "best_flights": [{
                "total_duration": 125, "price": 5234,
                "flights": [{
                  "airline": "IndiGo", "flight_number": "6E 123", "duration": 125,
                  "departure_airport": {"name": "Pune Airport", "time": "06:10"},
                  "arrival_airport": {"name": "Delhi Airport", "time": "08:15"}
                }],
                "carbon_emissions": {"this_flight": 98000}
              }],
              "other_flights": [{
                "total_duration": 240, "price": 4100,
                "flights": [
                  {"airline": "A", "flight_number": "A1", "duration": 90,
                   "departure_airport": {"id": "PNQ", "time": "06:00"},
                   "arrival_airport": {"id": "BOM", "time": "07:30"}},
                  {"airline": "B", "flight_number": "B2", "duration": 110,
                   "departure_airport": {"id": "BOM", "time": "08:00"},
                   "arrival_airport": {"id": "DEL", "time": "09:50"}}
                ]
              }]
            }
            """.trimIndent()
        )
        val result = client.searchFlights("PNQ", "DEL", "2026-10-20")
        assertEquals(2, result.options.size)
        val nonstop = result.options[0]
        assertEquals(5234, nonstop.price)
        assertEquals(0, nonstop.stops)
        assertEquals("IndiGo", nonstop.legs[0].airline)
        assertEquals(98.0, nonstop.carbonEmissionsKg)
        val oneStop = result.options[1]
        assertEquals(1, oneStop.stops)
        assertEquals("BOM", oneStop.legs[1].departureAirport)
    }

    @Test
    fun parsesScholarResults() = runTest {
        val client = clientReturning(
            """
            {"organic_results": [{
              "position": 1, "title": "Attention Is All You Need",
              "link": "https://arxiv.org/abs/1706.03762",
              "snippet": "The dominant sequence transduction models...",
              "publication_info": {"summary": "A Vaswani… - Advances in neural information processing systems, 2017"},
              "inline_links": {"cited_by": {"total": 150000}},
              "year": "2017"
            }]}
            """.trimIndent()
        )
        val paper = client.searchScholar("transformers").papers.single()
        assertEquals("Attention Is All You Need", paper.title)
        assertEquals(150000, paper.citedByCount)
        assertTrue(paper.publicationInfo!!.contains("2017"))
    }

    @Test
    fun parsesNewsResults() = runTest {
        val client = clientReturning(
            """
            {"news_results": [{
              "position": 1, "title": "Big sale starts", "link": "https://news.example/1",
              "source": {"name": "NDTV"}, "date": "2 hours ago", "snippet": "..."
            }]}
            """.trimIndent()
        )
        val article = client.searchNews("flipkart sale", country = "in").articles.single()
        assertEquals("NDTV", article.source)
        assertEquals("2 hours ago", article.date)
    }

    @Test
    fun httpErrorBecomesSerpApiException() = runTest {
        val client = clientReturning(
            """{"error": "Invalid API key"}""",
            status = HttpStatusCode.Unauthorized,
        )
        val e = assertFailsWith<SerpApiException> { client.searchWeb("anything") }
        assertEquals(401, e.statusCode)
        assertTrue(e.message!!.contains("Invalid API key"))
    }

    @Test
    fun bodyLevelErrorBecomesSerpApiException() = runTest {
        val client = clientReturning("""{"error": "Your searches are exhausted"}""")
        val e = assertFailsWith<SerpApiException> { client.searchWeb("anything") }
        assertTrue(e.message!!.contains("exhausted"))
    }

    @Test
    fun blankApiKeyRejected() {
        assertFailsWith<IllegalArgumentException> { SerpApiClient(apiKey = "  ") }
    }
}
