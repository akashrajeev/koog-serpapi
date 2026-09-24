package dev.serpapi.koog.models

import kotlinx.serialization.json.JsonObject

/** One organic web result. */
data class OrganicResult(
    val position: Int?,
    val title: String,
    val link: String,
    val snippet: String?,
    val date: String? = null,
)

/** One place from Google Maps / local results. */
data class Place(
    val position: Int?,
    val title: String,
    val address: String?,
    val rating: Double?,
    val reviews: Int?,
    val type: String?,
    val phone: String?,
    val hours: String?,
)

/** One product from Google Shopping. */
data class ShoppingItem(
    val position: Int?,
    val title: String,
    val link: String?,
    val price: String?,
    val extractedPrice: Double?,
    val source: String?,
    val rating: Double?,
    val reviews: Int?,
    val delivery: String?,
)

/** One leg inside a flight option. */
data class FlightLeg(
    val airline: String?,
    val flightNumber: String?,
    val departureAirport: String?,
    val departureTime: String?,
    val arrivalAirport: String?,
    val arrivalTime: String?,
    val durationMinutes: Int?,
)

/** One bookable flight option (possibly several legs). */
data class FlightOption(
    val legs: List<FlightLeg>,
    val totalDurationMinutes: Int?,
    val price: Long?,
    val currency: String?,
    val stops: Int?,
    val carbonEmissionsKg: Double?,
)

/** One paper from Google Scholar. */
data class ScholarPaper(
    val position: Int?,
    val title: String,
    val link: String?,
    val snippet: String?,
    val publicationInfo: String?,
    val citedByCount: Int?,
    val year: String?,
)

/** One article from Google News. */
data class NewsArticle(
    val position: Int?,
    val title: String,
    val link: String,
    val source: String?,
    val date: String?,
    val snippet: String?,
)

sealed interface SerpResult {
    /** The untouched JSON payload, for callers that need fields we do not model. */
    val raw: JsonObject
}

data class WebSearchResult(
    val organic: List<OrganicResult>,
    val answerBox: String?,
    override val raw: JsonObject,
) : SerpResult

data class LocalSearchResult(
    val places: List<Place>,
    override val raw: JsonObject,
) : SerpResult

data class ShoppingResult(
    val items: List<ShoppingItem>,
    override val raw: JsonObject,
) : SerpResult

data class FlightsResult(
    val options: List<FlightOption>,
    override val raw: JsonObject,
) : SerpResult

data class ScholarResult(
    val papers: List<ScholarPaper>,
    override val raw: JsonObject,
) : SerpResult

data class NewsResult(
    val articles: List<NewsArticle>,
    override val raw: JsonObject,
) : SerpResult
