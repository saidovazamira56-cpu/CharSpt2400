package com.example.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ChatRepository
    private val firestoreSyncRepository: FirestoreSyncRepository

    // Current Firebase Auth user
    val currentUser = MutableStateFlow<FirebaseUser?>(Firebase.auth.currentUser)

    init {
        val database = AppDatabase.getDatabase(application)
        repository = ChatRepository(database.chatDao())
        firestoreSyncRepository = FirestoreSyncRepository(application)

        // Listen for Firebase auth state changes
        Firebase.auth.addAuthStateListener { auth ->
            currentUser.value = auth.currentUser
            val user = auth.currentUser
            if (user != null) {
                viewModelScope.launch {
                    val name = user.displayName ?: user.email ?: "Пользователь"
                    firestoreSyncRepository.saveUserProfile(name)
                }
            }
        }
    }

    // System instruction (prompts chatbot roles)
    val systemInstruction = MutableStateFlow("Ты ИИ-ассистент ChatSptAi, невероятно умный и отзывчивый собеседник. Отвечай подробно, профессионально и понятно на русском языке.")
    
    // Default model to run (Gemini 3.8 Flash is the recommended default)
    val activeModel = MutableStateFlow("gemini-3.8-flash")

    // Active design theme choice
    val activeTheme = MutableStateFlow("DARK")

    // User name setting
    val userName = MutableStateFlow("")

    // List of chat sessions from history
    val sessions: StateFlow<List<ChatSessionEntity>> = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Chat Session ID
    val activeSessionId = MutableStateFlow<Long?>(null)

    // Current state showing whether Gemini is generating text
    val isGenerating = MutableStateFlow(false)

    // Reactive Flow of messages of current session
    val activeMessages: StateFlow<List<MessageEntity>> = activeSessionId
        .flatMapLatest { sessionId ->
            if (sessionId != null) {
                repository.getMessagesForSession(sessionId)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Load saved configurations
        viewModelScope.launch {
            systemInstruction.value = repository.getSetting(
                "system_instruction",
                "Ты ИИ-ассистент ChatSptAi, невероятно умный и отзывчивый собеседник. Отвечай подробно, профессионально и понятно на русском языке."
            )
            activeModel.value = repository.getSetting("active_model", "gemini-3.8-flash")
            val savedTheme = repository.getSetting("active_theme", "DARK")
            // Migration check if old theme key was saved
            activeTheme.value = if (savedTheme in listOf("DARK", "LIGHT", "DARK_OLED")) savedTheme else "DARK"
            userName.value = repository.getSetting("user_name", "")
        }

        // Automatically load or create active session
        viewModelScope.launch {
            sessions.collect { list ->
                if (activeSessionId.value == null) {
                    if (list.isNotEmpty()) {
                        activeSessionId.value = list.first().id
                    } else {
                        createNewSession("Новый диалог")
                    }
                }
            }
        }
    }

    fun selectSession(id: Long) {
        activeSessionId.value = id
    }

    fun createNewSession(title: String) {
        viewModelScope.launch {
            val newId = repository.createNewSession(title)
            activeSessionId.value = newId
            if (currentUser.value != null) {
                firestoreSyncRepository.syncSession(newId, title, activeModel.value)
            }
        }
    }

    fun deleteSession(session: ChatSessionEntity) {
        viewModelScope.launch {
            repository.deleteSession(session)
            if (activeSessionId.value == session.id) {
                activeSessionId.value = null
            }
        }
    }

    fun saveSettings(instruction: String, model: String) {
        viewModelScope.launch {
            systemInstruction.value = instruction
            activeModel.value = model
            repository.saveSetting("system_instruction", instruction)
            repository.saveSetting("active_model", model)
        }
    }

    fun saveTheme(themeKey: String) {
        viewModelScope.launch {
            activeTheme.value = themeKey
            repository.saveSetting("active_theme", themeKey)
        }
    }

    fun saveUserName(name: String) {
        viewModelScope.launch {
            userName.value = name
            repository.saveSetting("user_name", name)
            if (currentUser.value != null) {
                firestoreSyncRepository.saveUserProfile(name)
            }
        }
    }

    fun sendMessage(text: String) {
        val sessionId = activeSessionId.value ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty() || isGenerating.value) return

        viewModelScope.launch {
            // Save prompt locally
            val userMsgId = repository.insertMessage(
                MessageEntity(
                    sessionId = sessionId,
                    sender = "user",
                    text = trimmed
                )
            )

            if (currentUser.value != null) {
                firestoreSyncRepository.syncMessage(sessionId, userMsgId, "user", trimmed)
            }

            // Auto-rename session on the first message from default name
            val currentSession = sessions.value.find { it.id == sessionId }
            if (currentSession != null && currentSession.title == "Новый диалог") {
                val shortTitle = if (trimmed.length > 25) trimmed.substring(0, 22) + "..." else trimmed
                repository.createNewSession(shortTitle)
                if (currentUser.value != null) {
                    firestoreSyncRepository.syncSession(sessionId, shortTitle, activeModel.value)
                }
            }

            isGenerating.value = true

            // Send to Gemini via Firebase AI Logic
            val result = repository.sendMessageToGemini(
                sessionId = sessionId,
                modelName = activeModel.value,
                systemInstructionText = systemInstruction.value,
                userMessageText = trimmed
            )

            result.onSuccess { modelReply ->
                if (currentUser.value != null) {
                    firestoreSyncRepository.syncMessage(sessionId, System.currentTimeMillis(), "model", modelReply)
                }
            }

            isGenerating.value = false
        }
    }
}
