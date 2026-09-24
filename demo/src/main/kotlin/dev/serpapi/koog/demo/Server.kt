package dev.serpapi.koog.demo

import ai.koog.agents.core.agent.AIAgent
import dev.serpapi.koog.client.SerpApiClient
import dev.serpapi.koog.client.SerpEngine
import dev.serpapi.koog.tools.SerpApiTools
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Web demo: a small chat UI backed by a Koog agent wired to SerpApi tools.
 * Run with: ./gradlew :demo:run   (env: SERPAPI_API_KEY + GOOGLE_API_KEY or OPENAI_*)
 */
fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port) { demoApp() }.start(wait = true)
}

@Serializable
data class AskRequest(val question: String)

@Serializable
data class AskResponse(val answer: String, val tools: List<String>, val elapsedMs: Long)

private val engineLabels = mapOf(
    SerpEngine.GOOGLE to "Google Search",
    SerpEngine.GOOGLE_MAPS to "Google Maps",
    SerpEngine.GOOGLE_SHOPPING to "Google Shopping",
    SerpEngine.GOOGLE_FLIGHTS to "Google Flights",
    SerpEngine.GOOGLE_SCHOLAR to "Google Scholar",
    SerpEngine.GOOGLE_NEWS to "Google News",
)

fun Application.demoApp() {
    val serpApiKey = System.getenv("SERPAPI_API_KEY")
        ?: error("SERPAPI_API_KEY is not set")

    val calledEngines = mutableListOf<SerpEngine>()
    val serpClient = SerpApiClient(serpApiKey) { engine -> calledEngines.add(engine) }
    val (executor, model) = buildExecutor()

    val agent = AIAgent(
        promptExecutor = executor,
        llmModel = model,
        toolRegistry = SerpApiTools.registry(serpClient),
        systemPrompt = """
            You are the koog-serpapi demo assistant: a research helper with live search tools.
            Rules:
            - Use the serp_* tools whenever a question depends on current, local, priced or published information.
            - Answer in short paragraphs or tight lists; cite the links the tools returned, inline.
            - Never invent facts. If a tool returns nothing useful, say so.
        """.trimIndent(),
    )
    val agentMutex = Mutex()

    install(ServerContentNegotiation) { json(Json { prettyPrint = false }) }

    routing {
        get("/") {
            val html = this::class.java.getResource("/index.html")!!.readText()
            call.respondText(html, ContentType.Text.Html)
        }
        get("/health") { call.respondText("ok") }
        post("/api/ask") {
            val req = call.receive<AskRequest>()
            if (req.question.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "empty question"))
                return@post
            }
            val started = System.currentTimeMillis()
            try {
                val answer = agentMutex.withLock {
                    calledEngines.clear()
                    agent.run(req.question.trim())
                }
                val tools = calledEngines.mapNotNull { engineLabels[it] }.distinct()
                call.respond(AskResponse(answer, tools, System.currentTimeMillis() - started))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: e::class.simpleName.orEmpty())),
                )
            }
        }
    }
}
