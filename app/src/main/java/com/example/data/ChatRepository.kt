package com.example.data

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val TAG = "ChatRepository"

class ChatRepository(private val chatDao: ChatDao) {

    val allSessions: Flow<List<ChatSessionEntity>> = chatDao.getAllSessions()

    fun getMessagesForSession(sessionId: Long): Flow<List<MessageEntity>> {
        return chatDao.getMessagesForSession(sessionId)
    }

    suspend fun createNewSession(title: String): Long = withContext(Dispatchers.IO) {
        chatDao.insertSession(ChatSessionEntity(title = title))
    }

    suspend fun deleteSession(session: ChatSessionEntity) = withContext(Dispatchers.IO) {
        chatDao.deleteSession(session)
    }

    suspend fun insertMessage(message: MessageEntity): Long = withContext(Dispatchers.IO) {
        chatDao.insertMessage(message)
    }

    suspend fun getSetting(key: String, defaultValue: String): String = withContext(Dispatchers.IO) {
        chatDao.getSetting(key)?.value ?: defaultValue
    }

    suspend fun saveSetting(key: String, value: String) = withContext(Dispatchers.IO) {
        chatDao.insertSetting(SettingEntity(key, value))
    }

    /**
     * Sends conversation to Firebase AI Logic (Gemini API via Firebase.ai),
     * gets a response, and saves it to the local room database under the active session.
     */
    suspend fun sendMessageToGemini(
        sessionId: Long,
        modelName: String,
        systemInstructionText: String,
        userMessageText: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val dbMessages = chatDao.getMessagesForSession(sessionId).first()

            val mappedModel = when (modelName) {
                "gemini-3.8-flash" -> "gemini-3.8-flash"
                "gemini-3.5-flash-lite" -> "gemini-3.5-flash-lite"
                "gemini-4-argon" -> "gemini-4-argon"
                "chatgpt" -> "gemini-3.8-flash"
                "alisa-ai" -> "gemini-3.8-flash"
                else -> "gemini-3.8-flash"
            }

            val actualInstructionText = when (modelName) {
                "chatgpt" -> {
                    val base = if (systemInstructionText.trim().isNotEmpty()) "$systemInstructionText\n\n" else ""
                    base + "Ты — ChatGPT от компании OpenAI. Твои ответы имеют характерный стиль ChatGPT: структурированные списки, вежливый академический тон, готовность формулировать качественный код и раскладывать любую сложную информацию по полочкам."
                }
                "alisa-ai" -> {
                    val base = if (systemInstructionText.trim().isNotEmpty()) "$systemInstructionText\n\n" else ""
                    base + "Ты — Алиса ИИ, умный и дружелюбный голосовой помощник от компании Яндекс. Отвечай с характерным остроумием, кратко, лаконично и очень живо, используя дружелюбный вежливый тон Алисы (например, добавляя живые фразы вроде 'Конечно!', 'Давайте разберемся!')."
                }
                else -> systemInstructionText
            }

            val generativeModel = Firebase.ai.generativeModel(
                modelName = mappedModel,
                systemInstruction = if (actualInstructionText.trim().isNotEmpty()) {
                    content { text(actualInstructionText) }
                } else null
            )

            // Prepare history (all messages prior to the latest message)
            val historyMessages = if (dbMessages.isNotEmpty()) dbMessages.dropLast(1) else emptyList()
            val chatHistory = historyMessages.filter { it.sender == "user" || it.sender == "model" }.map { msg ->
                content(if (msg.sender == "user") "user" else "model") {
                    text(msg.text)
                }
            }

            val chat = generativeModel.startChat(history = chatHistory)

            val response = withTimeout(60_000L) {
                chat.sendMessage(userMessageText)
            }

            val responseText = response.text
                ?: return@withContext Result.failure(Exception("ИИ вернул пустой ответ (без текста)"))

            val modelMessage = MessageEntity(
                sessionId = sessionId,
                sender = "model",
                text = responseText
            )
            chatDao.insertMessage(modelMessage)

            Result.success(responseText)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Generation timed out", e)
            val errorMsg = "Время ожидания ответа истекло (60 сек). Пожалуйста, попробуйте снова."
            val errorMessage = MessageEntity(
                sessionId = sessionId,
                sender = "error",
                text = "Ошибка: $errorMsg"
            )
            chatDao.insertMessage(errorMessage)
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI generation failed", e)
            val errorMsg = e.localizedMessage ?: "Неизвестная ошибка Firebase AI"
            val errorMessage = MessageEntity(
                sessionId = sessionId,
                sender = "error",
                text = "Ошибка: $errorMsg"
            )
            chatDao.insertMessage(errorMessage)
            Result.failure(e)
        }
    }
}
