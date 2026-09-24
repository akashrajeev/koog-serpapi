package dev.serpapi.koog.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import dev.serpapi.koog.client.SerpApiClient
import dev.serpapi.koog.client.SerpEngine
import kotlinx.serialization.Serializable

/**
 * Koog tools backed by live SerpApi search data.
 *
 * Register everything in one line:
 * ```
 * val toolRegistry = SerpApiTools.registry(apiKey = System.getenv("SERPAPI_API_KEY"))
 * ```
 */
object SerpApiTools {

    /** A registry with a tool for every requested engine (default: all six). */
    fun registry(
        client: SerpApiClient,
        engines: Set<SerpEngine> = SerpEngine.entries.toSet(),
        maxResultsPerTool: Int = 5,
    ): ToolRegistry = ToolRegistry {
        engines.forEach { engine ->
            when (engine) {
                SerpEngine.GOOGLE -> tool(SerpWebSearchTool(client, maxResultsPerTool))
                SerpEngine.GOOGLE_MAPS -> tool(SerpLocalSearchTool(client, maxResultsPerTool))
                SerpEngine.GOOGLE_SHOPPING -> tool(SerpShoppingTool(client, maxResultsPerTool))
                SerpEngine.GOOGLE_FLIGHTS -> tool(SerpFlightsTool(client))
                SerpEngine.GOOGLE_SCHOLAR -> tool(SerpScholarTool(client, maxResultsPerTool))
                SerpEngine.GOOGLE_NEWS -> tool(SerpNewsTool(client, maxResultsPerTool))
            }
        }
    }

    fun registry(
        apiKey: String,
        engines: Set<SerpEngine> = SerpEngine.entries.toSet(),
        maxResultsPerTool: Int = 5,
    ): ToolRegistry = registry(SerpApiClient(apiKey), engines, maxResultsPerTool)
}

class SerpWebSearchTool(
    private val client: SerpApiClient,
    private val maxResults: Int = 5,
) : SimpleTool<SerpWebSearchTool.Args>(
    argsType = typeToken<Args>(),
    name = "serp_web_search",
    description = "Search the live web via Google (through SerpApi). Returns ranked results with " +
        "titles, links and snippets, plus Google's answer box when present. Use for current facts, " +
        "documentation, releases and anything that changes over time. Always cite the links you use.",
) {
    @Serializable
    data class Args(
        @property:LLMDescription("The search query, as you would type it into Google")
        val query: String,
        @property:LLMDescription("Optional location to search from, e.g. 'Pune, Maharashtra, India'")
        val location: String? = null,
        @property:LLMDescription("Optional two-letter country code for geo-targeted results, e.g. 'in'")
        val country: String? = null,
    )

    override suspend fun execute(args: Args): String =
        Format.web(client.searchWeb(args.query, args.location, args.country), maxResults)
}

class SerpLocalSearchTool(
    private val client: SerpApiClient,
    private val maxResults: Int = 5,
) : SimpleTool<SerpLocalSearchTool.Args>(
    argsType = typeToken<Args>(),
    name = "serp_local_search",
    description = "Find real places on Google Maps via SerpApi: restaurants, venues, services. " +
        "Returns names, addresses, ratings, review counts and hours. Use for anything local.",
) {
    @Serializable
    data class Args(
        @property:LLMDescription("What to look for, e.g. 'vegan restaurants'")
        val query: String,
        @property:LLMDescription("Optional place to search around, e.g. 'Kothrud, Pune'")
        val location: String? = null,
    )

    override suspend fun execute(args: Args): String =
        Format.local(client.searchLocal(args.query, args.location), maxResults)
}

class SerpShoppingTool(
    private val client: SerpApiClient,
    private val maxResults: Int = 5,
) : SimpleTool<SerpShoppingTool.Args>(
    argsType = typeToken<Args>(),
    name = "serp_shopping_search",
    description = "Search Google Shopping via SerpApi for products with live prices across sellers. " +
        "Returns titles, prices, stores, ratings and links. Use to compare prices or check availability.",
) {
    @Serializable
    data class Args(
        @property:LLMDescription("The product to look up, e.g. 'RTX 4060 laptop 16GB'")
        val query: String,
        @property:LLMDescription("Optional two-letter country code for local pricing, e.g. 'in'")
        val country: String? = null,
    )

    override suspend fun execute(args: Args): String =
        Format.shopping(client.searchShopping(args.query, args.country), maxResults)
}

class SerpFlightsTool(
    private val client: SerpApiClient,
) : SimpleTool<SerpFlightsTool.Args>(
    argsType = typeToken<Args>(),
    name = "serp_flights_search",
    description = "Search Google Flights via SerpApi for live flight options and prices. " +
        "Airports use IATA codes (e.g. PNQ, BOM, DEL). Dates are YYYY-MM-DD. " +
        "Returns price, duration, stops and legs per option.",
) {
    @Serializable
    data class Args(
        @property:LLMDescription("IATA code of the departure airport, e.g. 'PNQ'")
        val departureId: String,
        @property:LLMDescription("IATA code of the arrival airport, e.g. 'DEL'")
        val arrivalId: String,
        @property:LLMDescription("Outbound date, YYYY-MM-DD")
        val outboundDate: String,
        @property:LLMDescription("Optional return date for round trips, YYYY-MM-DD")
        val returnDate: String? = null,
    )

    override suspend fun execute(args: Args): String =
        Format.flights(
            client.searchFlights(args.departureId, args.arrivalId, args.outboundDate, args.returnDate),
            max = 4,
        )
}

class SerpScholarTool(
    private val client: SerpApiClient,
    private val maxResults: Int = 5,
) : SimpleTool<SerpScholarTool.Args>(
    argsType = typeToken<Args>(),
    name = "serp_scholar_search",
    description = "Search Google Scholar via SerpApi for academic papers. Returns titles, links, " +
        "publication info and citation counts. Use for research questions and literature lookups.",
) {
    @Serializable
    data class Args(
        @property:LLMDescription("The paper topic, title or author to search for")
        val query: String,
        @property:LLMDescription("Optional earliest publication year, e.g. 2023")
        val yearFrom: Int? = null,
    )

    override suspend fun execute(args: Args): String =
        Format.scholar(client.searchScholar(args.query, yearFrom = args.yearFrom), maxResults)
}

class SerpNewsTool(
    private val client: SerpApiClient,
    private val maxResults: Int = 5,
) : SimpleTool<SerpNewsTool.Args>(
    argsType = typeToken<Args>(),
    name = "serp_news_search",
    description = "Search Google News via SerpApi for fresh articles. Returns headlines, sources, " +
        "dates and links. Use for recent events and announcements.",
) {
    @Serializable
    data class Args(
        @property:LLMDescription("The topic to find news about")
        val query: String,
        @property:LLMDescription("Optional two-letter country code for regional news, e.g. 'in'")
        val country: String? = null,
    )

    override suspend fun execute(args: Args): String =
        Format.news(client.searchNews(args.query, country = args.country), maxResults)
}
