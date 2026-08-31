package com.luntik.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LuntikTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
                    LuntikApp()
                }
            }
        }
    }
}

// Colors
val Bg = Color(0xFF0C0E12)
val Surface = Color(0xFF161A22)
val Surface2 = Color(0xFF1E2430)
val Accent = Color(0xFF6EE7B7)
val TextMain = Color(0xFFE6E9EF)
val TextMuted = Color(0xFF8B93A7)
val UserBubble = Color(0xFF2D3A55)
val AiBubble = Color(0xFF1A2030)
val Danger = Color(0xFFF87171)
val Warn = Color(0xFFFBBF24)

@Composable
fun LuntikTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            background = Bg,
            surface = Surface,
            onPrimary = Bg,
            onBackground = TextMain,
            onSurface = TextMain
        ),
        content = content
    )
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String, // user / ai / system
    val content: String,
    val confidence: Int? = null,
    val thinking: List<Pair<String, Int>>? = null,
    val feedback: String? = null
)

data class KnowledgeSource(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val text: String
)

class LuntikViewModel : ViewModel() {
    var messages by mutableStateOf(
        listOf(
            ChatMessage(
                role = "system",
                content = "Привет! Я LuntikAi.\n\n1. Нажми + и добавь тексты\n2. Нажми «Обучить»\n3. Задавай вопросы\n\nЯ буду думать с вероятностями и учитывать лайки/дизлайки."
            )
        )
    )
        private set

    var sources by mutableStateOf(listOf<KnowledgeSource>())
        private set

    var isTrained by mutableStateOf(false)
        private set

    var isThinking by mutableStateOf(false)
        private set

    var input by mutableStateOf("")

    var showAddDialog by mutableStateOf(false)
    var newSourceName by mutableStateOf("")
    var newSourceText by mutableStateOf("")

    var showFeedbackDialog by mutableStateOf(false)
    var feedbackMsgId by mutableStateOf<String?>(null)
    var feedbackComment by mutableStateOf("")

    private val feedbackNotes = mutableListOf<String>()

    fun addSource(name: String, text: String) {
        if (text.isBlank()) return
        sources = sources + KnowledgeSource(name = name.ifBlank { "Источник ${sources.size + 1}" }, text = text)
        isTrained = false
        messages = messages + ChatMessage(role = "system", content = "Добавлен источник «${name.ifBlank { "без названия" }}». Нажми «Обучить».")
    }

    fun train() {
        if (sources.isEmpty()) {
            messages = messages + ChatMessage(role = "system", content = "Сначала добавь хотя бы один текст.")
            return
        }
        isTrained = true
        messages = messages + ChatMessage(
            role = "system",
            content = "Обучение завершено! Источников: ${sources.size}. Можно задавать вопросы."
        )
    }

    fun send() {
        val q = input.trim()
        if (q.isEmpty() || isThinking) return
        input = ""
        messages = messages + ChatMessage(role = "user", content = q)

        // Простые приветствия
        val lower = q.lowercase()
        if (lower in listOf("привет", "хай", "здравствуй", "здравствуйте")) {
            messages = messages + ChatMessage(role = "ai", content = "Привет! Рад тебя видеть. Как дела?", confidence = 92)
            return
        }
        if (lower.contains("как дела") || lower.contains("как ты")) {
            messages = messages + ChatMessage(role = "ai", content = "У меня всё хорошо, спасибо! А у тебя как?", confidence = 90)
            return
        }

        if (!isTrained || sources.isEmpty()) {
            messages = messages + ChatMessage(
                role = "ai",
                content = "Я ещё не обучен. Добавь тексты через + и нажми «Обучить».",
                confidence = 5
            )
            return
        }

        // Имитация раздумья
        isThinking = true
    }

    suspend fun finishThinking(question: String) {
        delay(400)

        val tokens = question.lowercase().split(Regex("[^\\p{L}\\p{N}]+").toRegex()).filter { it.length > 1 }
        val scored = sources.map { src ->
            val textLower = src.text.lowercase()
            val score = tokens.count { textLower.contains(it) }.toDouble() / (tokens.size.coerceAtLeast(1))
            src to score
        }.sortedByDescending { it.second }

        val top = scored.take(3).filter { it.second > 0 }

        val thinking = if (top.isEmpty()) {
            listOf("Прямого совпадения нет" to 70, "Мало данных по теме" to 30)
        } else {
            val total = top.sumOf { it.second }.coerceAtLeast(0.01)
            top.mapIndexed { i, (src, sc) ->
                val pct = ((sc / total) * 100).toInt().coerceIn(5, 90)
                val snippet = src.text.take(80).replace("\n", " ") + if (src.text.length > 80) "…" else ""
                snippet to pct
            }
        }

        // Нормализация процентов примерно к 100
        val sum = thinking.sumOf { it.second }
        val normalized = if (sum > 0) {
            thinking.mapIndexed { i, (t, p) ->
                if (i == 0) t to (p + (100 - sum)).coerceAtLeast(5) else t to p
            }
        } else thinking

        val best = top.firstOrNull()
        val confidence = if (best == null) 12 else ((best.second * 80) + 15).toInt().coerceIn(10, 92)

        var answer = if (best == null) {
            "Пока не нашёл достаточно близкой информации. Добавь больше текстов по теме."
        } else {
            val snippet = extractSnippet(best.first.text, tokens)
            var text = snippet
            if (feedbackNotes.any { it.contains("сухо") || it.contains("непонятно") }) {
                text = "Попробую объяснить понятнее:\n\n$text"
            }
            if (confidence >= 60) {
                text += listOf("", "\n\nМогу рассказать подробнее.", "\n\nА что ты об этом думаешь?").random()
            } else if (confidence < 35) {
                text += "\n\n(Уверенность пока невысокая)"
            }
            text
        }

        messages = messages + ChatMessage(
            role = "ai",
            content = answer,
            confidence = confidence,
            thinking = normalized
        )
        isThinking = false
    }

    private fun extractSnippet(text: String, tokens: List<String>): String {
        val sentences = text.split(Regex("[.!?\n]+")).map { it.trim() }.filter { it.length > 15 }
        if (sentences.isEmpty()) return text.take(300)
        val best = sentences.maxByOrNull { s ->
            tokens.count { s.lowercase().contains(it) }
        } ?: sentences.first()
        return if (best.length > 400) best.take(400) + "…" else best
    }

    fun like(msgId: String) {
        messages = messages.map {
            if (it.id == msgId) it.copy(feedback = "like") else it
        }
        messages = messages + ChatMessage(role = "system", content = "👍 Спасибо! Буду стараться отвечать так же.")
    }

    fun openDislike(msgId: String) {
        feedbackMsgId = msgId
        feedbackComment = ""
        showFeedbackDialog = true
    }

    fun submitDislike() {
        val id = feedbackMsgId ?: return
        val comment = feedbackComment.trim()
        messages = messages.map {
            if (it.id == id) it.copy(feedback = "dislike") else it
        }
        if (comment.isNotEmpty()) {
            feedbackNotes.add(comment.lowercase())
            messages = messages + ChatMessage(
                role = "system",
                content = "Понял замечание: «$comment». Буду учитывать."
            )
        } else {
            messages = messages + ChatMessage(role = "system", content = "Понял, ответ не зашёл.")
        }
        showFeedbackDialog = false
    }
}

@Composable
fun LuntikApp(vm: LuntikViewModel = viewModel()) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(vm.messages.size) {
        if (vm.messages.isNotEmpty()) {
            listState.animateScrollToItem(vm.messages.lastIndex)
        }
    }

    // Когда isThinking стал true — запускаем finishThinking
    LaunchedEffect(vm.isThinking) {
        if (vm.isThinking) {
            val lastUser = vm.messages.lastOrNull { it.role == "user" }?.content ?: ""
            vm.finishThinking(lastUser)
        }
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        // Top bar
        Row(
            Modifier
                .fillMaxWidth()
                .background(Surface)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🌱 LuntikAi", color = Accent, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.weight(1f))
            Text(
                if (vm.isTrained) "обучен · ${vm.sources.size}" else "не обучен",
                color = TextMuted,
                fontSize = 12.sp
            )
        }

        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(vm.messages, key = { it.id }) { msg ->
                MessageBubble(
                    msg = msg,
                    onLike = { vm.like(msg.id) },
                    onDislike = { vm.openDislike(msg.id) }
                )
            }
            if (vm.isThinking) {
                item {
                    Text("💭 Думаю…", color = TextMuted, fontSize = 14.sp, modifier = Modifier.padding(8.dp))
                }
            }
        }

        // Bottom actions
        Row(
            Modifier
                .fillMaxWidth()
                .background(Surface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = { vm.showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Знания")
            }
            Button(
                onClick = { vm.train() },
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg)
            ) {
                Text("Обучить")
            }
        }

        // Input
        Row(
            Modifier
                .fillMaxWidth()
                .background(Surface)
                .navigationBarsPadding()
                .padding(12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = vm.input,
                onValueChange = { vm.input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Напиши сообщение…", color = TextMuted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Surface2,
                    focusedTextColor = TextMain,
                    unfocusedTextColor = TextMain,
                    cursorColor = Accent
                ),
                maxLines = 4
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { vm.send() },
                colors = IconButtonDefaults.iconButtonColors(containerColor = Accent)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить", tint = Bg)
            }
        }
    }

    // Add source dialog
    if (vm.showAddDialog) {
        AlertDialog(
            onDismissRequest = { vm.showAddDialog = false },
            title = { Text("Добавить знания") },
            text = {
                Column {
                    OutlinedTextField(
                        value = vm.newSourceName,
                        onValueChange = { vm.newSourceName = it },
                        label = { Text("Название") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = vm.newSourceText,
                        onValueChange = { vm.newSourceText = it },
                        label = { Text("Текст") },
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        maxLines = 8
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.addSource(vm.newSourceName, vm.newSourceText)
                    vm.newSourceName = ""
                    vm.newSourceText = ""
                    vm.showAddDialog = false
                }) { Text("Добавить") }
            },
            dismissButton = {
                TextButton(onClick = { vm.showAddDialog = false }) { Text("Отмена") }
            }
        )
    }

    // Dislike comment dialog
    if (vm.showFeedbackDialog) {
        AlertDialog(
            onDismissRequest = { vm.showFeedbackDialog = false },
            title = { Text("Что не так?") },
            text = {
                OutlinedTextField(
                    value = vm.feedbackComment,
                    onValueChange = { vm.feedbackComment = it },
                    placeholder = { Text("Например: слишком сухо, не по теме…") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = { vm.submitDislike() }) { Text("Отправить") }
            },
            dismissButton = {
                TextButton(onClick = { vm.showFeedbackDialog = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
fun MessageBubble(
    msg: ChatMessage,
    onLike: () -> Unit,
    onDislike: () -> Unit
) {
    val isUser = msg.role == "user"
    val isSystem = msg.role == "system"

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = when {
            isUser -> Alignment.End
            isSystem -> Alignment.CenterHorizontally
            else -> Alignment.Start
        }
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = when {
                isUser -> UserBubble
                isSystem -> Color.Transparent
                else -> AiBubble
            },
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Column(Modifier.padding(12.dp)) {
                if (!isSystem) {
                    Text(
                        if (isUser) "Ты" else "LuntikAi",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(4.dp))
                }

                // Thinking block
                if (!msg.thinking.isNullOrEmpty()) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text("💭 Раздумье", color = TextMuted, fontSize = 11.sp)
                            Spacer(Modifier.height(6.dp))
                            msg.thinking.forEach { (text, pct) ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text, color = TextMain, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                    Text(
                                        "$pct%",
                                        color = when {
                                            pct >= 45 -> Accent
                                            pct >= 25 -> Warn
                                            else -> Danger
                                        },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    msg.content,
                    color = if (isSystem) TextMuted else TextMain,
                    fontSize = if (isSystem) 13.sp else 15.sp,
                    lineHeight = 20.sp
                )

                if (msg.confidence != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Уверенность: ${msg.confidence}%",
                        color = when {
                            msg.confidence >= 55 -> Accent
                            msg.confidence >= 25 -> Warn
                            else -> Danger
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (msg.role == "ai") {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onLike, contentPadding = PaddingValues(4.dp)) {
                            Icon(Icons.Default.ThumbUp, null, Modifier.size(16.dp), tint = if (msg.feedback == "like") Accent else TextMuted)
                            Spacer(Modifier.width(4.dp))
                            Text("Лайк", color = if (msg.feedback == "like") Accent else TextMuted, fontSize = 12.sp)
                        }
                        TextButton(onClick = onDislike, contentPadding = PaddingValues(4.dp)) {
                            Icon(Icons.Default.ThumbDown, null, Modifier.size(16.dp), tint = if (msg.feedback == "dislike") Danger else TextMuted)
                            Spacer(Modifier.width(4.dp))
                            Text("Дизлайк", color = if (msg.feedback == "dislike") Danger else TextMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
