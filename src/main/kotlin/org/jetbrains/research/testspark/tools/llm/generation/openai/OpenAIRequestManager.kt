package org.jetbrains.research.testspark.tools.llm.generation.openai

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.intellij.openapi.project.Project
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.HttpRequests.HttpStatusException
import org.jetbrains.research.testspark.bundles.llm.LLMMessagesBundle
import org.jetbrains.research.testspark.core.data.ChatMessage
import org.jetbrains.research.testspark.core.monitor.ErrorMonitor
import org.jetbrains.research.testspark.core.progress.CustomProgressIndicator
import org.jetbrains.research.testspark.core.test.TestsAssembler
import org.jetbrains.research.testspark.tools.ToolUtils
import org.jetbrains.research.testspark.tools.llm.LlmSettingsArguments
import org.jetbrains.research.testspark.tools.llm.error.LLMErrorManager
import org.jetbrains.research.testspark.tools.llm.generation.IJRequestManager
import java.net.HttpURLConnection

/**
 * This class represents a manager for making requests to the LLM (Large Language Model).
 */
class OpenAIRequestManager(project: Project) : IJRequestManager(project) {
    private val url = "https://api.openai.com/v1/chat/completions"

    private val httpRequest = HttpRequests.post(url, "application/json").tuner {
        it.setRequestProperty("Authorization", "Bearer $token")
    }

    private val llmErrorManager = LLMErrorManager()

    override fun send(
        prompt: String,
        indicator: CustomProgressIndicator,
        testsAssembler: TestsAssembler,
        errorMonitor: ErrorMonitor,
    ): SendResult {
        // Prepare the chat
        val messages = chatHistory.map {
            val role = when (it.role) {
                ChatMessage.ChatRole.User -> "user"
                ChatMessage.ChatRole.Assistant -> "assistant"
            }
            OpenAIChatMessage(role, it.content)
        }

        val llmRequestBody = OpenAIRequestBody(LlmSettingsArguments(project).getModel(), messages)

        var sendResult = SendResult.OK

        try {
            httpRequest.connect { request ->
                // send request to OpenAI API
                request.write(GsonBuilder().create().toJson(llmRequestBody))

                val connection = request.connection as HttpURLConnection

                // check response
                when (val responseCode = connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        assembleLlmResponse(request, testsAssembler, indicator)
                    }

                    HttpURLConnection.HTTP_INTERNAL_ERROR -> {
                        llmErrorManager.errorProcess(
                            LLMMessagesBundle.get("serverProblems"),
                            project,
                            errorMonitor,
                        )
                        sendResult = SendResult.OTHER
                    }

                    HttpURLConnection.HTTP_BAD_REQUEST -> {
                        llmErrorManager.warningProcess(
                            LLMMessagesBundle.get("tooLongPrompt"),
                            project,
                        )
                        sendResult = SendResult.PROMPT_TOO_LONG
                    }

                    HttpURLConnection.HTTP_UNAUTHORIZED -> {
                        llmErrorManager.errorProcess(
                            LLMMessagesBundle.get("wrongToken"),
                            project,
                            errorMonitor,
                        )
                        sendResult = SendResult.OTHER
                    }

                    else -> {
                        llmErrorManager.errorProcess(
                            llmErrorManager.createRequestErrorMessage(responseCode),
                            project,
                            errorMonitor,
                        )
                        sendResult = SendResult.OTHER
                    }
                }
            }
        } catch (e: HttpStatusException) {
            log.info { "Error in sending request: ${e.message}" }
        }

        return sendResult
    }

    /**
     * Receives the LLM's response text and feeds it to the provided `TestsAssembler`.
     *
     * @param httpRequest the httpRequest sent to OpenAI
     * @param indicator UI indicator that it checked for cancellation while parsing the LLM's response
     * @param testsAssembler the test assembler to which the response is fed
     */
    private fun assembleLlmResponse(
        httpRequest: HttpRequests.Request,
        testsAssembler: TestsAssembler,
        indicator: CustomProgressIndicator,
    ) {
        while (true) {
            if (ToolUtils.isProcessCanceled(indicator)) return

            var text = httpRequest.reader.readLine()

            if (text.isEmpty()) continue

            text = text.removePrefix("data: ")

            val choices =
                Gson().fromJson(
                    JsonParser.parseString(text)
                        .asJsonObject["choices"]
                        .asJsonArray[0].asJsonObject,
                    OpenAIChoice::class.java,
                )

            if (choices.finishedReason == "stop") break

            // Feed the response to the assembler
            testsAssembler.consume(choices.delta.content)
        }

        log.debug { testsAssembler.getContent() }
    }
}
