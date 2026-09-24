package dev.serpapi.koog.demo

import ai.koog.agents.core.agent.AIAgent
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import dev.serpapi.koog.tools.SerpApiTools
import kotlinx.coroutines.runBlocking

/**
 * Demo agent: a research assistant that answers with live data from SerpApi.
 *
 * Required env:
 *   SERPAPI_API_KEY - your SerpApi key (https://serpapi.com/manage_api_key)
 *   plus ONE of:
 *   GOOGLE_API_KEY  - Gemini key (uses gemini-2.5-flash)
 *   OPENAI_API_KEY  - any OpenAI-compatible key. Optional: OPENAI_BASE_URL
 *                     (e.g. https://api.groq.com/openai/v1) and OPENAI_MODEL.
 */
fun main() = runBlocking {
    val serpApiKey = System.getenv("SERPAPI_API_KEY")
        ?: error("SERPAPI_API_KEY is not set")

    val (executor, model) = buildExecutor()

    val agent = AIAgent(
        promptExecutor = executor,
        llmModel = model,
        toolRegistry = SerpApiTools.registry(serpApiKey),
        systemPrompt = """
            You are the koog-serpapi demo assistant: a research helper with live search tools.
            Rules:
            - Use the serp_* tools whenever a question depends on current, local, priced or published information.
            - When asked for options, give up to 5, each with its key facts (price, rating, timing) and the exact link the tool returned for it.
            - Answer in short paragraphs or tight lists. Never invent facts or links. If a tool returns nothing useful, say so.
        """.trimIndent(),
    )

    println("koog-serpapi demo agent. Ask anything with a live-data angle. 'exit' to quit.")
    while (true) {
        print("\n> ")
        val question = readlnOrNull()?.trim() ?: break
        if (question.equals("exit", ignoreCase = true) || question.isEmpty()) break
        try {
            println(agent.run(question))
        } catch (e: Exception) {
            println("Error: ${e.message}")
        }
    }
}

fun buildExecutor(): Pair<MultiLLMPromptExecutor, LLModel> {
    val httpFactory = KtorKoogHttpClient.Factory()

    System.getenv("GOOGLE_API_KEY")?.takeIf { it.isNotBlank() }?.let { key ->
        return MultiLLMPromptExecutor(
            LLMProvider.Google to GoogleLLMClient(apiKey = key, httpClientFactory = httpFactory)
        ) to GoogleModels.Gemini2_5Flash
    }

    System.getenv("OPENAI_API_KEY")?.takeIf { it.isNotBlank() }?.let { key ->
        val baseUrl = System.getenv("OPENAI_BASE_URL") // e.g. https://api.groq.com/openai/v1
        val client = OpenAILLMClient(
            apiKey = key,
            settings = if (baseUrl != null) OpenAIClientSettings(baseUrl = baseUrl) else OpenAIClientSettings(),
            httpClientFactory = httpFactory,
        )
        val modelId = System.getenv("OPENAI_MODEL")
        val model = if (modelId != null) {
            LLModel(
                provider = LLMProvider.OpenAI,
                id = modelId,
                capabilities = listOf(
                    LLMCapability.Temperature,
                    LLMCapability.Completion,
                    LLMCapability.Tools,
                    LLMCapability.ToolChoice,
                ),
                contextLength = 131_072,
            )
        } else {
            OpenAIModels.Chat.GPT4oMini
        }
        return MultiLLMPromptExecutor(LLMProvider.OpenAI to client) to model
    }

    error("Set GOOGLE_API_KEY or OPENAI_API_KEY (see README)")
}
