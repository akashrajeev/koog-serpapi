package dev.serpapi.koog.client

import dev.serpapi.koog.models.FlightLeg
import dev.serpapi.koog.models.FlightOption
import dev.serpapi.koog.models.FlightsResult
import dev.serpapi.koog.models.LocalSearchResult
import dev.serpapi.koog.models.NewsArticle
import dev.serpapi.koog.models.NewsResult
import dev.serpapi.koog.models.OrganicResult
import dev.serpapi.koog.models.Place
import dev.serpapi.koog.models.ScholarPaper
import dev.serpapi.koog.models.ScholarResult
import dev.serpapi.koog.models.ShoppingItem
import dev.serpapi.koog.models.ShoppingResult
import dev.serpapi.koog.models.WebSearchResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Typed, multiplatform client for the SerpApi `/search` endpoint.
 *
 * Parsing is deliberately tolerant: SerpApi payloads evolve, so unknown fields
 * are ignored and everything we do not model stays available on [dev.serpapi.koog.models.SerpResult.raw].
 */
class SerpApiClient(
    private val apiKey: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val http: HttpClient = defaultHttpClient(),
    private val onEngineCall: ((SerpEngine) -> Unit)? = null,
) {
    init {
        require(apiKey.isNotBlank()) { "SerpApi API key must not be blank" }
    }

    /** Run any engine with raw string parameters and get the untouched JSON back. */
    suspend fun raw(engine: SerpEngine, params: Map<String, String> = emptyMap()): JsonObject {
        onEngineCall?.invoke(engine)
        val response = http.get("$baseUrl/search") {
            parameter("engine", engine.engine)
            parameter("api_key", apiKey)
            params.forEach { (k, v) -> parameter(k, v) }
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            val detail = runCatching {
                Json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.content
            }.getOrNull()
            throw SerpApiException(
                "SerpApi request failed with HTTP ${response.status.value}" +
                    if (detail != null) ": $detail" else "",
                statusCode = response.status.value,
            )
        }
        val json = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrElse {
            throw SerpApiException("SerpApi returned a non-JSON response")
        }
        json["error"]?.jsonPrimitive?.content?.let {
            throw SerpApiException("SerpApi error: $it")
        }
        return json
    }

    suspend fun searchWeb(
        query: String,
        location: String? = null,
        country: String? = null,
        language: String? = null,
        num: Int? = null,
    ): WebSearchResult {
        val raw = raw(
            SerpEngine.GOOGLE,
            buildMap {
                put("q", query)
                location?.let { put("location", it) }
                country?.let { put("gl", it) }
                language?.let { put("hl", it) }
                num?.let { put("num", it.toString()) }
            },
        )
        return WebSearchResult(
            organic = raw.arr("organic_results")?.mapNotNull { it.asObj() }?.map { e ->
                OrganicResult(
                    position = e.int("position"),
                    title = e.str("title").orEmpty(),
                    link = e.str("link").orEmpty(),
                    snippet = e.str("snippet"),
                    date = e.str("date"),
                )
            }.orEmpty().filter { it.title.isNotBlank() },
            answerBox = raw["answer_box"]?.let { box ->
                if (box is JsonObject) {
                    box.str("answer") ?: box.str("snippet") ?: box.str("result") ?: box.str("title")
                } else box.jsonPrimitive.content
            },
            raw = raw,
        )
    }

    suspend fun searchLocal(
        query: String,
        location: String? = null,
        gps: String? = null,
    ): LocalSearchResult {
        val raw = raw(
            SerpEngine.GOOGLE_MAPS,
            buildMap {
                put("q", query)
                location?.let { put("location", it) }
                gps?.let { put("ll", it) }
            },
        )
        // google_maps returns local_results as an array; google with local pack
        // wraps it in an object with `places`. Handle both.
        val list = when (val lr = raw["local_results"]) {
            is JsonArray -> lr
            is JsonObject -> lr["places"] as? JsonArray
            else -> null
        }
        return LocalSearchResult(
            places = list?.mapNotNull { it.asObj() }?.map { e ->
                Place(
                    position = e.int("position"),
                    title = e.str("title").orEmpty(),
                    address = e.str("address"),
                    rating = e.dbl("rating"),
                    reviews = e.int("reviews"),
                    type = e.str("type"),
                    phone = e.str("phone"),
                    hours = e.str("hours"),
                )
            }.orEmpty().filter { it.title.isNotBlank() },
            raw = raw,
        )
    }

    suspend fun searchShopping(
        query: String,
        country: String? = null,
        location: String? = null,
        num: Int? = null,
    ): ShoppingResult {
        val raw = raw(
            SerpEngine.GOOGLE_SHOPPING,
            buildMap {
                put("q", query)
                country?.let { put("gl", it) }
                location?.let { put("location", it) }
                num?.let { put("num", it.toString()) }
            },
        )
        return ShoppingResult(
            items = raw.arr("shopping_results")?.mapNotNull { it.asObj() }?.map { e ->
                ShoppingItem(
                    position = e.int("position"),
                    title = e.str("title").orEmpty(),
                    link = e.str("link") ?: e.str("product_link"),
                    price = e.str("price"),
                    extractedPrice = e.dbl("extracted_price"),
                    source = e.str("source"),
                    rating = e.dbl("rating"),
                    reviews = e.int("reviews"),
                    delivery = e.str("delivery"),
                )
            }.orEmpty().filter { it.title.isNotBlank() },
            raw = raw,
        )
    }

    suspend fun searchFlights(
        departureId: String,
        arrivalId: String,
        outboundDate: String,
        returnDate: String? = null,
        currency: String? = null,
        adults: Int? = null,
    ): FlightsResult {
        val raw = raw(
            SerpEngine.GOOGLE_FLIGHTS,
            buildMap {
                put("departure_id", departureId)
                put("arrival_id", arrivalId)
                put("outbound_date", outboundDate)
                returnDate?.let { put("return_date", it) }
                currency?.let { put("currency", it) }
                adults?.let { put("adults", it.toString()) }
            },
        )
        val options = (raw.arr("best_flights").orEmpty() + raw.arr("other_flights").orEmpty())
            .mapNotNull { it.asObj() }
            .map { e ->
                val legs = e.arr("flights")?.mapNotNull { it.asObj() }?.map { leg ->
                    FlightLeg(
                        airline = leg.str("airline"),
                        flightNumber = leg.str("flight_number"),
                        departureAirport = leg.obj("departure_airport")?.str("name")
                            ?: leg.obj("departure_airport")?.str("id"),
                        departureTime = leg.obj("departure_airport")?.str("time"),
                        arrivalAirport = leg.obj("arrival_airport")?.str("name")
                            ?: leg.obj("arrival_airport")?.str("id"),
                        arrivalTime = leg.obj("arrival_airport")?.str("time"),
                        durationMinutes = leg.int("duration"),
                    )
                }.orEmpty()
                FlightOption(
                    legs = legs,
                    totalDurationMinutes = e.int("total_duration"),
                    price = e["price"]?.jsonPrimitive?.longOrNull,
                    currency = null,
                    stops = legs.size.takeIf { it > 0 }?.minus(1),
                    carbonEmissionsKg = e.obj("carbon_emissions")?.dbl("this_flight")?.div(1000.0),
                )
            }
        return FlightsResult(options = options, raw = raw)
    }

    suspend fun searchScholar(
        query: String,
        num: Int? = null,
        yearFrom: Int? = null,
    ): ScholarResult {
        val raw = raw(
            SerpEngine.GOOGLE_SCHOLAR,
            buildMap {
                put("q", query)
                num?.let { put("num", it.toString()) }
                yearFrom?.let { put("as_ylo", it.toString()) }
            },
        )
        return ScholarResult(
            papers = raw.arr("organic_results")?.mapNotNull { it.asObj() }?.map { e ->
                ScholarPaper(
                    position = e.int("position"),
                    title = e.str("title").orEmpty(),
                    link = e.str("link"),
                    snippet = e.str("snippet"),
                    publicationInfo = e.obj("publication_info")?.str("summary"),
                    citedByCount = e.obj("inline_links")?.obj("cited_by")?.int("total"),
                    year = e.str("year"),
                )
            }.orEmpty().filter { it.title.isNotBlank() },
            raw = raw,
        )
    }

    suspend fun searchNews(
        query: String,
        location: String? = null,
        country: String? = null,
    ): NewsResult {
        val raw = raw(
            SerpEngine.GOOGLE_NEWS,
            buildMap {
                put("q", query)
                location?.let { put("location", it) }
                country?.let { put("gl", it) }
            },
        )
        return NewsResult(
            articles = raw.arr("news_results")?.mapNotNull { it.asObj() }?.map { e ->
                NewsArticle(
                    position = e.int("position"),
                    title = e.str("title").orEmpty(),
                    link = e.str("link").orEmpty(),
                    source = e.obj("source")?.str("name") ?: e.str("source"),
                    date = e.str("date"),
                    snippet = e.str("snippet"),
                )
            }.orEmpty().filter { it.title.isNotBlank() },
            raw = raw,
        )
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://serpapi.com"

        fun defaultHttpClient(): HttpClient = HttpClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(HttpTimeout) { requestTimeoutMillis = 30_000 }
        }
    }
}

// Small tolerant accessors used by every parser above.
private fun JsonObject.str(key: String): String? =
    this[key]?.let { if (it is JsonObject || it is JsonArray) null else it.jsonPrimitive.content }

private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull
private fun JsonObject.dbl(key: String): Double? = this[key]?.jsonPrimitive?.doubleOrNull
private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
private fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray
private fun JsonElement.asObj(): JsonObject? = this as? JsonObject
