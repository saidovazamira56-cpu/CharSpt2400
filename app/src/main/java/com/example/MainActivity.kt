package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.ChatSessionEntity
import com.example.data.ChatViewModel
import com.example.data.MessageEntity
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.LocalAppThemeColors
import com.example.util.configureAppCheck
import com.example.util.GoogleAuthManager
import androidx.credentials.CredentialManager
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        setContent {
            val viewModel: ChatViewModel = viewModel()
            val activeTheme by viewModel.activeTheme.collectAsState()
            MyApplicationTheme(themeKey = activeTheme) {
                MainContentScreen(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContentScreen(viewModel: ChatViewModel) {
    val colors = LocalAppThemeColors.current
    val sessions by viewModel.sessions.collectAsState()
    val activeSessionId by viewModel.activeSessionId.collectAsState()
    val messages by viewModel.activeMessages.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val systemInstruction by viewModel.systemInstruction.collectAsState()
    val activeModel by viewModel.activeModel.collectAsState()
    val activeTheme by viewModel.activeTheme.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    LaunchedEffect(Unit) {
        GoogleAuthManager.attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {},
            onUnauthenticated = {},
            scope = coroutineScope
        )
    }

    var showNameDialog by remember { mutableStateOf(false) }
    var showModelMenu by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // Active session details
    val activeSession = sessions.find { it.id == activeSessionId }

    // Navigation drawer to open history and settings overlays beautifully
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight(),
                drawerContainerColor = colors.background,
                drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
            ) {
                DrawerPanelContent(
                    sessions = sessions,
                    activeSessionId = activeSessionId,
                    onSelectSession = { id ->
                        viewModel.selectSession(id)
                        coroutineScope.launch { drawerState.close() }
                    },
                    onDeleteSession = { session ->
                        viewModel.deleteSession(session)
                    },
                    onCreateSession = {
                        viewModel.createNewSession("Новый диалог")
                        coroutineScope.launch { drawerState.close() }
                    },
                    currentModel = activeModel,
                    currentInstruction = systemInstruction,
                    onSaveSettings = { instruction, model ->
                        viewModel.saveSettings(instruction, model)
                    },
                    currentTheme = activeTheme,
                    onSelectTheme = { key ->
                        viewModel.saveTheme(key)
                    },
                    currentUser = currentUser
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Box {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showModelMenu = true }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "ChatSptAi",
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                val shortModelDisplay = when (activeModel) {
                                    "gemini-3.8-flash" -> "Flash 3.8"
                                    "gemini-3.5-flash-lite" -> "Lite 3.5"
                                    "gemini-4-argon" -> "Argon 4.0"
                                    "chatgpt" -> "ChatGPT"
                                    "alisa-ai" -> "Алиса ИИ"
                                    else -> "Flash 3.8"
                                }
                                Text(
                                    text = shortModelDisplay,
                                    color = colors.textSecondary.copy(alpha = 0.8f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Выбрать модель",
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            
                            DropdownMenu(
                                expanded = showModelMenu,
                                onDismissRequest = { showModelMenu = false },
                                modifier = Modifier.background(colors.surface)
                            ) {
                                val modelList = listOf(
                                    "gemini-3.8-flash" to "ChatSpt Gemini 3.8 Flash (Стандарт)",
                                    "gemini-3.5-flash-lite" to "ChatSpt Gemini 3.5 Flash-Lite (Быстрый)",
                                    "gemini-4-argon" to "ChatSpt Gemini 4.0 Argon (Сложные задачи)",
                                    "chatgpt" to "ChatGPT (Стиль OpenAI)",
                                    "alisa-ai" to "Алиса ИИ (Стиль Yandex)"
                                )
                                modelList.forEach { (key, name) ->
                                    DropdownMenuItem(
                                        text = { Text(name, color = if (activeModel == key) colors.primary else colors.textPrimary, fontSize = 14.sp) },
                                        onClick = {
                                            viewModel.saveSettings(systemInstruction, key)
                                            showModelMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                }
                            },
                            modifier = Modifier.testTag("menu_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Настройки и Промпты",
                                tint = colors.textPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colors.background,
                        titleContentColor = colors.textPrimary
                    ),
                    actions = {
                        // Profile Avatar circle which opens name dialog on click
                        Box(
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(colors.primary, colors.secondary)
                                    )
                                )
                                .clickable { showNameDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            if (userName.trim().isNotEmpty()) {
                                Text(
                                    text = userName.trim().take(1).uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Личный Профиль",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                )
            },
            containerColor = colors.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Background ambient glow (subtle radial circle)
                AmbientGlowBackground()

                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Chat Message Feed
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        if (messages.isEmpty()) {
                            ChatEmptyState(
                                userName = userName,
                                onPromptClicked = { promptText ->
                                    viewModel.sendMessage(promptText)
                                }
                            )
                        } else {
                            val lazyListState = rememberLazyListState()
                            
                            // Scroll to latest message automatically
                            LaunchedEffect(messages.size, isGenerating) {
                                val target = messages.size + if (isGenerating) 1 else 0
                                if (target > 0) {
                                    lazyListState.animateScrollToItem(target - 1)
                                }
                            }

                            LazyColumn(
                                state = lazyListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(messages) { message ->
                                    ChatMessageItem(message = message)
                                }
                                if (isGenerating) {
                                    item {
                                        TypingIndicator()
                                    }
                                }
                            }
                        }
                    }

                    // Network generation progress feedback indicator list
                    if (isGenerating) {
                        LinearProgressIndicator(
                            color = colors.primary,
                            trackColor = colors.secondary.copy(alpha = 0.15f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                        )
                    }

                    // Liquid Glassmorphic input panel at the bottom
                    BottomInputPanel(
                        onSendMessage = { text ->
                            viewModel.sendMessage(text)
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        },
                        isGenerating = isGenerating
                    )
                }
            }
        }
    }

    if (userName.trim().isEmpty()) {
        OnboardingNameDialog(
            currentName = "",
            onSave = { newName ->
                viewModel.saveUserName(newName)
            }
        )
    } else if (showNameDialog) {
        ChangeNameDialog(
            currentName = userName,
            onDismiss = { showNameDialog = false },
            onSave = { newName ->
                viewModel.saveUserName(newName)
            }
        )
    }
}

@Composable
fun DrawerPanelContent(
    sessions: List<ChatSessionEntity>,
    activeSessionId: Long?,
    onSelectSession: (Long) -> Unit,
    onDeleteSession: (ChatSessionEntity) -> Unit,
    onCreateSession: () -> Unit,
    currentModel: String,
    currentInstruction: String,
    onSaveSettings: (String, String) -> Unit,
    currentTheme: String,
    onSelectTheme: (String) -> Unit,
    currentUser: FirebaseUser?
) {
    val colors = LocalAppThemeColors.current
    var isSettingsTabSelected by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Upper Logo
        Text(
            text = "ChatSptAi",
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = colors.primary,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        // Panel Switch tabs to change between History & Configs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surface)
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val tabWeight = Modifier.weight(1f)
            
            // Tab 1: History
            Box(
                contentAlignment = Alignment.Center,
                modifier = tabWeight
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (!isSettingsTabSelected) colors.primary.copy(alpha = 0.15f) else Color.Transparent)
                    .clickable { isSettingsTabSelected = false }
                    .padding(vertical = 10.dp)
            ) {
                Text(
                    text = "История чатов",
                    color = if (!isSettingsTabSelected) colors.primary else colors.textPrimary.copy(alpha = 0.6f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }

            // Tab 2: Settings
            Box(
                contentAlignment = Alignment.Center,
                modifier = tabWeight
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSettingsTabSelected) colors.primary.copy(alpha = 0.15f) else Color.Transparent)
                    .clickable { isSettingsTabSelected = true }
                    .padding(vertical = 10.dp)
            ) {
                Text(
                    text = "Настройки",
                    color = if (isSettingsTabSelected) colors.primary else colors.textPrimary.copy(alpha = 0.6f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (!isSettingsTabSelected) {
            // HISTORY TAB
            Column(modifier = Modifier.weight(1f)) {
                // Button to launch new Chat dialogues
                Button(
                    onClick = onCreateSession,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.surface),
                    border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_create_chat")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Создать новый чат", color = colors.textPrimary)
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (sessions.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "История диалогов пуста.\nНачните общение!",
                            color = colors.textSecondary.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(sessions) { session ->
                            val isActive = session.id == activeSessionId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isActive) colors.glassBg else Color.Transparent)
                                    .border(
                                        width = if (isActive) 1.dp else 0.dp,
                                        color = if (isActive) colors.primary.copy(alpha = 0.4f) else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { onSelectSession(session.id) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.List,
                                    contentDescription = null,
                                    tint = if (isActive) colors.primary else colors.textSecondary.copy(alpha = 0.5f),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = session.title,
                                    color = if (isActive) colors.primary else colors.textPrimary.copy(alpha = 0.8f),
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { onDeleteSession(session) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Удалить чат",
                                        tint = colors.textSecondary.copy(alpha = 0.4f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // SETTINGS TAB
            var localInstruction by remember { mutableStateOf(currentInstruction) }
            val localModel = currentModel
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // ==================== THEME SELECTOR ====================
                Text(
                    text = "Оформление (Тема)",
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                com.example.ui.theme.ThemeOptionsList.forEach { option ->
                    val isThemeSelected = currentTheme == option.key
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isThemeSelected) colors.primary.copy(alpha = 0.15f) else colors.surface)
                            .border(
                                width = if (isThemeSelected) 1.5.dp else 0.dp,
                                color = if (isThemeSelected) colors.primary else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelectTheme(option.key) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = option.name,
                                color = if (isThemeSelected) colors.primary else colors.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = option.description,
                                color = colors.textSecondary.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        // Theme color dot indicators (Background, Primary, Secondary)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Background dot
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(option.colors.background)
                                    .border(1.dp, colors.textSecondary.copy(alpha = 0.2f), CircleShape)
                            )
                            // Primary dot
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(option.colors.primary)
                            )
                            // Secondary dot
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(option.colors.secondary)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Роль ИИ (Системная инструкция)",
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Presets of roles choice
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val presets = listOf(
                        "По умолчанию" to "Ты ИИ-ассистент S-Ai, невероятно умный и отзывчивый собеседник. Отвечай подробно, профессионально и понятно на русском языке.",
                        "Кодер" to "Ты опытный программист. Пиши качественный, понятный код с подробными и структурированными комментариями.",
                        "Переводчик" to "Ты профессиональный переводчик. Помогай переводить фразы и слова на любые языки, объясняя грамматические тонкости."
                    )
                    presets.forEach { (name, prompt) ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.surface)
                                .clickable { localInstruction = prompt }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = name,
                                fontSize = 11.sp,
                                color = colors.textPrimary.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = localInstruction,
                    onValueChange = { localInstruction = it },
                    textStyle = TextStyle(color = colors.textPrimary, fontSize = 13.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(115.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colors.background,
                        unfocusedContainerColor = colors.background,
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.textSecondary.copy(alpha = 0.25f)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        onSaveSettings(localInstruction, localModel)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Done,
                        contentDescription = null,
                        tint = if (colors.isDark) Color.Black else Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Применить изменения",
                        color = if (colors.isDark) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        GoogleAuthCard(currentUser = currentUser)
    }
}

@Composable
fun GoogleAuthCard(
    currentUser: FirebaseUser?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val colors = LocalAppThemeColors.current
    var isSigningIn by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, if (currentUser != null) colors.primary.copy(alpha = 0.4f) else colors.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colors.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (currentUser != null) Icons.Default.CloudDone else Icons.Default.CloudQueue,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (currentUser != null) (currentUser.displayName ?: "Google Аккаунт") else "Firebase Облако",
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = if (currentUser != null) (currentUser.email ?: "Синхронизировано") else "Войдите для синхронизации",
                        color = colors.textSecondary.copy(alpha = 0.8f),
                        fontSize = 11.sp
                    )
                }
            }

            if (authError != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = authError!!,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (currentUser == null) {
                Button(
                    onClick = {
                        isSigningIn = true
                        authError = null
                        GoogleAuthManager.onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = { isSigningIn = false },
                            onAuthError = { err ->
                                isSigningIn = false
                                authError = err
                            },
                            scope = coroutineScope,
                            onAuthCancelled = { isSigningIn = false }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .testTag("google_sign_in_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                    shape = RoundedCornerShape(10.dp),
                    enabled = !isSigningIn
                ) {
                    if (isSigningIn) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = if (colors.isDark) Color.Black else Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = if (colors.isDark) Color.Black else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Войти через Google",
                            color = if (colors.isDark) Color.Black else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                OutlinedButton(
                    onClick = {
                        GoogleAuthManager.signOut(credentialManager, coroutineScope)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .testTag("google_sign_out_button"),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "Выйти из аккаунта",
                        color = colors.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun ChatEmptyState(
    userName: String,
    onPromptClicked: (String) -> Unit
) {
    val colors = LocalAppThemeColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Liquid Glass Spt Emblem Logo
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colors.primary.copy(alpha = 0.25f),
                            colors.secondary.copy(alpha = 0.10f),
                            Color.Transparent
                        )
                    )
                )
                .border(
                    BorderStroke(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.5f),
                                colors.primary.copy(alpha = 0.3f),
                                Color.White.copy(alpha = 0.1f)
                            )
                        )
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Spt",
                color = colors.textPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 26.sp,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = (-0.5).sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        val displayName = userName.trim()
        val greetingText = if (displayName.isNotEmpty()) "Привет, $displayName!" else "Привет!"

        Text(
            text = greetingText,
            color = colors.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Чем могу помочь сегодня?",
            color = colors.textSecondary,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Prompt Cards requested by user
        val promptSuggestions = listOf(
            "📐 Помоги решить математику",
            "📚 Помоги с дзшкой",
            "💻 Помоги создать скрипт/код",
            "💡 Объясни сложную тему простыми словами"
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
        ) {
            promptSuggestions.forEach { suggestion ->
                val cleanPrompt = suggestion.substring(2).trim()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.glassBg)
                        .border(
                            BorderStroke(
                                1.dp,
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.2f),
                                        colors.primary.copy(alpha = 0.15f),
                                        Color.White.copy(alpha = 0.05f)
                                    )
                                )
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable { onPromptClicked(cleanPrompt) }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = suggestion,
                            color = colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatMessageItem(message: MessageEntity) {
    val colors = LocalAppThemeColors.current
    val isUser = message.sender == "user"
    val isError = message.sender == "error"

    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bubbleColor = when {
        isUser -> colors.primary.copy(alpha = 0.18f) // Thematic bubble glow
        isError -> Color(0x33E53935) // Elegant red error block
        else -> colors.glassBg // Matte ambient glass
    }
    
    val bubbleBorderColor = when {
        isUser -> colors.primary.copy(alpha = 0.5f)
        isError -> Color(0xFFE53935).copy(alpha = 0.5f)
        else -> colors.primary.copy(alpha = 0.25f) // Thin high-contrast border
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        // Sender Metadata Header Label
        Text(
            text = when {
                isUser -> "Вы"
                isError -> "Служба S-Ai (Ошибка)"
                else -> "S-Ai"
            },
            color = if (isUser) colors.secondary else colors.primary,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 4.dp, start = 8.dp, end = 8.dp)
        )

        // Glassmorph Bubble Container
        Box(
            modifier = Modifier
                .widthIn(max = 290.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 2.dp,
                        bottomEnd = if (isUser) 2.dp else 16.dp
                    )
                )
                .background(bubbleColor)
                .border(
                    width = 1.dp,
                    color = bubbleBorderColor,
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 2.dp,
                        bottomEnd = if (isUser) 2.dp else 16.dp
                    )
                )
                .padding(12.dp)
        ) {
            Column {
                // Parsing markdown elements (simplified parser for code snippets)
                val textParts = message.text.split("```")
                textParts.forEachIndexed { index, part ->
                    val isCodeBlock = index % 2 != 0
                    if (isCodeBlock) {
                        // Display clean monospace graphite box showing structured code blocks beautifully
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.background)
                                .border(0.5.dp, colors.textSecondary.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = part.trim(),
                                color = colors.primary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        if (part.isNotEmpty()) {
                            Text(
                                text = part,
                                color = colors.textPrimary,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Fine printed visual timestamp
                Text(
                    text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
                    color = colors.textSecondary.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
fun TypingIndicator() {
    val colors = LocalAppThemeColors.current
    val infiniteTransition = rememberInfiniteTransition(label = "typing_dots")
    
    @Composable
    fun animateDotOffset(delayMillis: Int): State<Float> {
        return infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = -8f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 600
                    0f at 0 with LinearOutSlowInEasing
                    -8f at 200 with FastOutLinearInEasing
                    0f at 400 with LinearEasing
                    0f at 600
                },
                repeatMode = RepeatMode.Restart,
                initialStartOffset = StartOffset(delayMillis)
            ),
            label = "dot_bounce"
        )
    }

    val dot1 by animateDotOffset(0)
    val dot2 by animateDotOffset(150)
    val dot3 by animateDotOffset(300)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("typing_indicator")
    ) {
        Column {
            Text(
                text = "S-Ai печатает...",
                color = colors.primary.copy(alpha = 0.7f),
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 4.dp, start = 8.dp)
            )

            Box(
                modifier = Modifier
                    .width(76.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 2.dp, bottomEnd = 16.dp))
                    .background(colors.glassBg)
                    .border(
                        width = 1.dp,
                        color = colors.primary.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 2.dp, bottomEnd = 16.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    val dotColor = colors.primary
                    val dotSize = 7.dp

                    Box(
                        modifier = Modifier
                            .offset(y = dot1.dp)
                            .size(dotSize)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Box(
                        modifier = Modifier
                            .offset(y = dot2.dp)
                            .size(dotSize)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Box(
                        modifier = Modifier
                            .offset(y = dot3.dp)
                            .size(dotSize)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }
            }
        }
    }
}

@Composable
fun AudioRecordingWaveform() {
    val colors = LocalAppThemeColors.current
    val infiniteTransition = rememberInfiniteTransition(label = "audio_wave")
    
    @Composable
    fun animateBarHeight(delay: Int, duration: Int): State<Float> {
        return infiniteTransition.animateFloat(
            initialValue = 4f,
            targetValue = 28f,
            animationSpec = infiniteRepeatable(
                animation = tween(duration, delay, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar_pulse"
        )
    }
    
    val h1 by animateBarHeight(0, 300)
    val h2 by animateBarHeight(100, 400)
    val h3 by animateBarHeight(50, 350)
    val h4 by animateBarHeight(150, 450)
    val h5 by animateBarHeight(200, 380)

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 8.dp)
    ) {
        val color = colors.primary
        val width = 3.dp
        Box(modifier = Modifier.size(width, h1.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Box(modifier = Modifier.size(width, h2.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Box(modifier = Modifier.size(width, h3.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Box(modifier = Modifier.size(width, h4.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Box(modifier = Modifier.size(width, h5.dp).clip(RoundedCornerShape(3.dp)).background(color))
    }
}

@Composable
fun AttachmentOptionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppThemeColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(0.5.dp, colors.textSecondary.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = colors.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = label,
                color = colors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun BottomInputPanel(
    onSendMessage: (String) -> Unit,
    isGenerating: Boolean
) {
    val colors = LocalAppThemeColors.current
    var query by remember { mutableStateOf("") }
    var showAttachmentUi by remember { mutableStateOf(false) }
    var selectedAttachmentType by remember { mutableStateOf<String?>(null) }
    var selectedAttachmentName by remember { mutableStateOf<String?>(null) }

    // Voice recording states
    var isRecordingVoice by remember { mutableStateOf(false) }
    var recordingTimer by remember { mutableStateOf(0) }

    val russianVoicePrompts = listOf(
        "Придумай футуристический рассказ про квантовый искусственный интеллект S-Ai",
        "Напиши рецепт космического коктейля с неоновым свечением",
        "Как разработать высокопроизводительное edge-to-edge приложение в Jetpack Compose?",
        "Объясни теорию относительности Эйнштейна за 30 секунд для детей",
        "Какие главные тренды в мобильной веб-разработке в 2026 году?"
    )

    LaunchedEffect(isRecordingVoice) {
        if (isRecordingVoice) {
            recordingTimer = 0
            while (isRecordingVoice) {
                kotlinx.coroutines.delay(1000)
                recordingTimer++
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding() // Notch & gesture safe space
            .imePadding() // Adjust layout when keyboard mounts
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Sliding attachment UI ("нажав на плюсик откроется окно выбора в проге,Фото,видео,медиа")
        AnimatedVisibility(
            visible = showAttachmentUi,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .testTag("attachment_chooser_panel"),
                colors = CardDefaults.cardColors(containerColor = colors.glassBg),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.2.dp, colors.primary.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Выберите вложение",
                            color = colors.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        IconButton(
                            onClick = { showAttachmentUi = false },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Закрыть",
                                tint = colors.textSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AttachmentOptionButton(
                            icon = Icons.Default.Image,
                            label = "Фото",
                            onClick = {
                                selectedAttachmentType = "Фото"
                                selectedAttachmentName = "IMG_SPACESHIP_CONCEPT.PNG"
                                showAttachmentUi = false
                            },
                            modifier = Modifier.weight(1f)
                        )
                        AttachmentOptionButton(
                            icon = Icons.Default.Videocam,
                            label = "Видео",
                            onClick = {
                                selectedAttachmentType = "Видео"
                                selectedAttachmentName = "TRAILER_MARS_COLONY.MP4"
                                showAttachmentUi = false
                            },
                            modifier = Modifier.weight(1f)
                        )
                        AttachmentOptionButton(
                            icon = Icons.Default.Folder,
                            label = "Медиа",
                            onClick = {
                                selectedAttachmentType = "Медиа"
                                selectedAttachmentName = "DATA_METRICS_ARCHIVE.ZIP"
                                showAttachmentUi = false
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Active Attachment Pill Indicator
        if (selectedAttachmentType != null && selectedAttachmentName != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.primary.copy(alpha = 0.12f))
                    .border(0.5.dp, colors.primary.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = when(selectedAttachmentType) {
                            "Фото" -> Icons.Default.Image
                            "Видео" -> Icons.Default.Videocam
                            else -> Icons.Default.Folder
                        },
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "$selectedAttachmentType: $selectedAttachmentName",
                        color = colors.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                IconButton(
                    onClick = {
                        selectedAttachmentType = null
                        selectedAttachmentName = null
                    },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Удалить вложение",
                        tint = colors.textSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // Liquid Glass container ("форма квадрат закругленный не так сильно" + "Liquid glass")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(colors.surface.copy(alpha = 0.65f)) // Translucent glass base
                .border(
                    border = BorderStroke(
                        width = 1.2.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                colors.primary.copy(alpha = 0.45f),
                                colors.secondary.copy(alpha = 0.15f)
                            )
                        )
                    ),
                    shape = RoundedCornerShape(32.dp)
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isRecordingVoice) {
                // RED pulsing recording indicator
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color.Red)
                )
                Spacer(modifier = Modifier.width(8.dp))

                // Timer
                val minutes = recordingTimer / 60
                val seconds = recordingTimer % 60
                Text(
                    text = String.format("%02d:%02d", minutes, seconds),
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )

                // Sound wave pulsed visualizer bars
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    AudioRecordingWaveform()
                }

                // Voice Recording action panel
                TextButton(
                    onClick = { isRecordingVoice = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.textSecondary.copy(alpha = 0.6f))
                ) {
                    Text("Отмена", fontSize = 13.sp)
                }

                IconButton(
                    onClick = {
                        isRecordingVoice = false
                        // Inject random voice prompt translation simulated
                        query = russianVoicePrompts.random()
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colors.primary)
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Готово",
                        tint = if (colors.isDark) Color.Black else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                // 1. Plus Button which opens sliding gallery
                IconButton(
                    onClick = { showAttachmentUi = !showAttachmentUi },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("btn_plus_attachment")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Вложить медиа",
                        tint = colors.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // 2. Clear input field
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 10.dp)
                        .testTag("chat_input_text_field"),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if ((query.trim().isNotEmpty() || selectedAttachmentType != null) && !isGenerating) {
                                val fullText = if (selectedAttachmentType != null) {
                                    "📎 [Вложение: $selectedAttachmentType ($selectedAttachmentName)]\n$query"
                                } else {
                                    query
                                }
                                onSendMessage(fullText)
                                query = ""
                                selectedAttachmentType = null
                                selectedAttachmentName = null
                            }
                        }
                    ),
                    textStyle = TextStyle(
                        color = colors.textPrimary,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.SansSerif
                    ),
                    cursorBrush = SolidColor(colors.primary),
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (query.isEmpty()) {
                                Text(
                                    text = "Спросить ChatSptAi...",
                                    color = colors.textSecondary.copy(alpha = 0.45f),
                                    fontSize = 15.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                Spacer(modifier = Modifier.width(4.dp))

                // 3. Microphone Button
                IconButton(
                    onClick = { isRecordingVoice = true },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("btn_voice_microphone")
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Голосовой ввод",
                        tint = colors.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // 4. Send arrow button
                val canSubmit = (query.trim().isNotEmpty() || selectedAttachmentType != null) && !isGenerating
                IconButton(
                    onClick = {
                        if (canSubmit) {
                            val fullText = if (selectedAttachmentType != null) {
                                "📎 [Вложение: $selectedAttachmentType ($selectedAttachmentName)]\n$query"
                             } else {
                                query
                             }
                            onSendMessage(fullText)
                            query = ""
                            selectedAttachmentType = null
                            selectedAttachmentName = null
                        }
                    },
                    enabled = canSubmit,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (canSubmit) colors.primary else colors.surface
                        )
                        .testTag("send_msg_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Отправить",
                        tint = if (canSubmit) (if (colors.isDark) Color.Black else Color.White) else colors.textSecondary.copy(alpha = 0.3f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AmbientGlowBackground() {
    val colors = LocalAppThemeColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .blur(80.dp)
    ) {
        // Glowing cyan planetary orbit blob
        Box(
            modifier = Modifier
                .size(220.dp)
                .offset(x = (-40).dp, y = 140.dp)
                .clip(CircleShape)
                .background(colors.primary.copy(alpha = 0.08f))
        )
        // Glowing blue planetary orbit blob
        Box(
            modifier = Modifier
                .size(280.dp)
                .offset(x = 180.dp, y = 300.dp)
                .clip(CircleShape)
                .background(colors.secondary.copy(alpha = 0.12f))
        )
    }
}

@Composable
fun GradientFourPointStar(
    modifier: Modifier = Modifier,
    colorsList: List<Color> = listOf(
        Color(0xFF4285F4), // Clean Google Blue
        Color(0xFF9B51E0), // Purple aura
        Color(0xFFF2C94C), // Amber starburst
        Color(0xFFFF5722)  // Celestial orange/pink
    )
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(width / 2f, 0f)
            quadraticBezierTo(width / 2f, height / 2f, width, height / 2f)
            quadraticBezierTo(width / 2f, height / 2f, width / 2f, height)
            quadraticBezierTo(width / 2f, height / 2f, 0f, height / 2f)
            quadraticBezierTo(width / 2f, height / 2f, width / 2f, 0f)
            close()
        }
        
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = colorsList,
                start = androidx.compose.ui.geometry.Offset(0f, 0f),
                end = androidx.compose.ui.geometry.Offset(width, height)
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingNameDialog(
    currentName: String,
    onSave: (String) -> Unit
) {
    val colors = LocalAppThemeColors.current
    var nameInput by remember { mutableStateOf(currentName) }

    BasicAlertDialog(
        onDismissRequest = { /* mandatory onboarding */ },
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surface)
            .border(
                BorderStroke(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.4f),
                            colors.primary.copy(alpha = 0.3f),
                            Color.White.copy(alpha = 0.1f)
                        )
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(colors.primary.copy(alpha = 0.15f))
                    .border(1.dp, colors.primary.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Spt",
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Введи Своё Имя",
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Добро пожаловать в ChatSptAi! Как к тебе обращаться?",
                color = colors.textSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                placeholder = { Text("Введи имя...", color = colors.textSecondary.copy(alpha = 0.5f)) },
                singleLine = true,
                textStyle = TextStyle(color = colors.textPrimary, fontSize = 15.sp),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.background,
                    unfocusedContainerColor = colors.background,
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.textSecondary.copy(alpha = 0.3f),
                    cursorColor = colors.primary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val finalName = if (nameInput.trim().isNotEmpty()) nameInput.trim() else "Пользователь"
                    onSave(finalName)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "Начать общение",
                    color = if (colors.isDark) Color.Black else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeNameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val colors = LocalAppThemeColors.current
    var nameInput by remember { mutableStateOf(currentName) }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surface)
            .border(
                BorderStroke(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.35f),
                            colors.primary.copy(alpha = 0.25f)
                        )
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Введи Своё Имя",
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                placeholder = { Text("Введи имя...", color = colors.textSecondary.copy(alpha = 0.5f)) },
                singleLine = true,
                textStyle = TextStyle(color = colors.textPrimary, fontSize = 14.sp),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.background,
                    unfocusedContainerColor = colors.background,
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.textSecondary.copy(alpha = 0.3f),
                    cursorColor = colors.primary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.textSecondary)
                ) {
                    Text("Отмена", fontWeight = FontWeight.Medium)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        onSave(nameInput.trim())
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Сохранить",
                        color = if (colors.isDark) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
