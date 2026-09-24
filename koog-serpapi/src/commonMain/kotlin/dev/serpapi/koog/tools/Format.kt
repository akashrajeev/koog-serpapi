package dev.serpapi.koog.tools

import dev.serpapi.koog.models.FlightsResult
import dev.serpapi.koog.models.LocalSearchResult
import dev.serpapi.koog.models.NewsResult
import dev.serpapi.koog.models.ScholarResult
import dev.serpapi.koog.models.ShoppingResult
import dev.serpapi.koog.models.WebSearchResult

/** Markdown renderers: agents read these formats well, and links become citations. */
internal object Format {

    fun web(r: WebSearchResult, max: Int): String = buildString {
        r.answerBox?.let { appendLine("**Answer box:** $it"); appendLine() }
        if (r.organic.isEmpty()) appendLine("No organic results found.")
        r.organic.take(max).forEach { o ->
            appendLine("${o.position ?: "-"}. [${o.title}](${o.link})")
            o.snippet?.let { appendLine("   $it") }
            o.date?.let { appendLine("   _${it}_") }
        }
    }.trimEnd()

    fun local(r: LocalSearchResult, max: Int): String = buildString {
        if (r.places.isEmpty()) appendLine("No places found.")
        r.places.take(max).forEach { p ->
            appendLine("- **${p.title}**${p.type?.let { " ($it)" } ?: ""}")
            p.address?.let { appendLine("  $it") }
            val meta = buildList {
                p.rating?.let { add("rating $it${p.reviews?.let { r -> " ($r reviews)" } ?: ""}") }
                p.hours?.let { add(it) }
                p.phone?.let { add(it) }
            }
            if (meta.isNotEmpty()) appendLine("  ${meta.joinToString(" · ")}")
        }
    }.trimEnd()

    fun shopping(r: ShoppingResult, max: Int): String = buildString {
        if (r.items.isEmpty()) appendLine("No products found.")
        r.items.take(max).forEach { i ->
            val price = i.price ?: i.extractedPrice?.toString() ?: "price n/a"
            appendLine("- [${i.title}](${i.link ?: ""}) — $price${i.source?.let { " at $it" } ?: ""}")
            val meta = buildList {
                i.rating?.let { add("rating $it") }
                i.delivery?.let { add(it) }
            }
            if (meta.isNotEmpty()) appendLine("  ${meta.joinToString(" · ")}")
        }
    }.trimEnd()

    fun flights(r: FlightsResult, max: Int): String = buildString {
        if (r.options.isEmpty()) appendLine("No flights found.")
        r.options.take(max).forEach { f ->
            val price = f.price?.let { "~$it" } ?: "price n/a"
            val stops = when (f.stops) {
                null -> ""
                0 -> "nonstop"
                1 -> "1 stop"
                else -> "${f.stops} stops"
            }
            val dur = f.totalDurationMinutes?.let { "${it / 60}h ${it % 60}m" } ?: "?"
            appendLine("- $price · $dur${if (stops.isNotEmpty()) " · $stops" else ""}")
            f.legs.forEach { leg ->
                appendLine(
                    "  ${leg.airline ?: ""} ${leg.flightNumber ?: ""}: " +
                        "${leg.departureAirport ?: "?"} ${leg.departureTime ?: ""} → " +
                        "${leg.arrivalAirport ?: "?"} ${leg.arrivalTime ?: ""}".trim()
                )
            }
        }
    }.trimEnd()

    fun scholar(r: ScholarResult, max: Int): String = buildString {
        if (r.papers.isEmpty()) appendLine("No papers found.")
        r.papers.take(max).forEach { p ->
            appendLine("- [${p.title}](${p.link ?: ""})")
            p.publicationInfo?.let { appendLine("  $it") }
            val meta = buildList {
                p.citedByCount?.let { add("cited by $it") }
                p.year?.let { add(it) }
            }
            if (meta.isNotEmpty()) appendLine("  ${meta.joinToString(" · ")}")
            p.snippet?.let { appendLine("  $it") }
        }
    }.trimEnd()

    fun news(r: NewsResult, max: Int): String = buildString {
        if (r.articles.isEmpty()) appendLine("No news found.")
        r.articles.take(max).forEach { a ->
            appendLine("- [${a.title}](${a.link})")
            val meta = buildList {
                a.source?.let { add(it) }
                a.date?.let { add(it) }
            }
            if (meta.isNotEmpty()) appendLine("  ${meta.joinToString(" · ")}")
            a.snippet?.let { appendLine("  $it") }
        }
    }.trimEnd()
}
