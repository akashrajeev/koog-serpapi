package dev.serpapi.koog.client

/**
 * SerpApi engines exposed by this library. The [engine] value is the exact
 * `engine` parameter sent to the SerpApi `/search` endpoint.
 */
enum class SerpEngine(val engine: String) {
    GOOGLE("google"),
    GOOGLE_MAPS("google_maps"),
    GOOGLE_SHOPPING("google_shopping"),
    GOOGLE_FLIGHTS("google_flights"),
    GOOGLE_SCHOLAR("google_scholar"),
    GOOGLE_NEWS("google_news"),
}
