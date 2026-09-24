package dev.serpapi.koog.client

/** Failure returned by the SerpApi API or the transport below it. */
class SerpApiException(
    message: String,
    val statusCode: Int? = null,
    cause: Throwable? = null,
) : Exception(message, cause)
