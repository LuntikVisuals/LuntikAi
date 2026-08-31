package com.luntik.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
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

val Bg = Color(0xFF0C0E12)
val SurfaceC = Color(0xFF161A22)
val Surface2 = Color(0xFF1E2430)
val Accent = Color(0xFF6EE7B7)
val TextMain = Color(0xFFE6E9EF)
val TextMuted = Color(0xFF8B93A7)
val UserBubble = Color(0xFF2D3A55)
val AiBubble = Color(0xFF1A2030)
val Danger = Color(0xFFF87171)
val Warn = Color(0xFFFBBF24)
val ActionBg = Color(0xFF1A2A22)

enum class Personality(val title: String, val emoji: String, val style: String) {
    NONE("Обычный ИИ", "🤖", "нейтральный, полезный, без лишней эмоции"),
    HORROR("Хоррор", "👻", "мрачный, жуткий, атмосферный, иногда пугающий"),
    EGOIST("Эгоист", "👑", "самоуверенный, ставит себя выше, снисходительный"),
    VILLAIN("Злодей", "😈", "злорадный, хитрый, говорит как антагонист"),
    KIND("Добряк", "😇", "добрый, заботливый, поддерживающий, мягкий"),
    CUTE("Милый", "🥺", "милый, нежный, использует тёплые слова и эмодзи")
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val content: String,
    val confidence: Int? = null,
    val thinking: List<Pair<String, Int>>? = null,
    val actions: List<String>? = null,
    val feedback: String? = null
)

data class KnowledgeSource(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val text: String
)

data class ChatFile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

class LuntikViewModel : ViewModel() {
    var messages by mutableStateOf(
        listOf(
            ChatMessage(
                role = "system",
                content = "Привет! Я LuntikAi v0.2\n\n• Добавляй и редактируй знания\n• Я помню диалог и могу его анализировать\n• Работаю как агент — пишу действия перед ответом\n• Могу создавать файлы в чате\n• Выбери личность или оставь обычный режим"
            )
        )
    )
        private set

    var sources by mutableStateOf(listOf<KnowledgeSource>())
        private set

    var chatFiles by mutableStateOf(listOf<ChatFile>())
        private set

    var isTrained by mutableStateOf(false)
        private set

    var isThinking by mutableStateOf(false)
        private set

    var personality by mutableStateOf(Personality.NONE)

    var input by mutableStateOf("")

    var showKnowledgeList by mutableStateOf(false)
    var showAddDialog by mutableStateOf(false)
    var showEditDialog by mutableStateOf(false)
    var editingSource by mutableStateOf<KnowledgeSource?>(null)
    var newSourceName by mutableStateOf("")
    var newSourceText by mutableStateOf("")

    var showFilesList by mutableStateOf(false)
    var showCreateFileDialog by mutableStateOf(false)
    var newFileName by mutableStateOf("")
    var newFileContent by mutableStateOf("")
    var viewingFile by mutableStateOf<ChatFile?>(null)

    var showPersonalityDialog by mutableStateOf(false)
    var showFeedbackDialog by mutableStateOf(false)
    var feedbackMsgId by mutableStateOf<String?>(null)
    var feedbackComment by mutableStateOf("")

    private val feedbackNotes = mutableListOf<String>()

    fun addSource(name: String, text: String) {
        if (text.isBlank()) return
        sources = sources + KnowledgeSource(
            name = name.ifBlank { "Источник ${sources.size + 1}" },
            text = text
        )
        isTrained = false
        messages = messages + ChatMessage(
            role = "system",
            content = "Добавлен источник «${name.ifBlank { "без названия" }}». Нажми «Обучить»."
        )
    }

    fun updateSource(id: String, name: String, text: String) {
        sources = sources.map {
            if (it.id == id) it.copy(name = name.ifBlank { it.name }, text = text) else it
        }
        isTrained = false
        messages = messages + ChatMessage(role = "system", content = "Источник обновлён. Нужно переобучить.")
    }

    fun deleteSource(id: String) {
        sources = sources.filter { it.id != id }
        isTrained = false
        messages = messages + ChatMessage(role = "system", content = "Источник удалён. Нужно переобучить.")
    }

    fun train() {
        if (sources.isEmpty()) {
            messages = messages + ChatMessage(role = "system", content = "Сначала добавь хотя бы один текст.")
            return
        }
        isTrained = true
        messages = messages + ChatMessage(
            role = "system",
            content = "Обучение завершено! Источников: ${sources.size}. Файлов: ${chatFiles.size}."
        )
    }

    fun createFile(name: String, content: String) {
        val n = name.ifBlank { "file_${chatFiles.size + 1}.txt" }
        val file = ChatFile(name = n, content = content)
        chatFiles = chatFiles + file
        messages = messages + ChatMessage(
            role = "system",
            content = "📄 Создан файл «$n» (${content.length} символов)"
        )
    }

    fun deleteFile(id: String) {
        val f = chatFiles.find { it.id == id }
        chatFiles = chatFiles.filter { it.id != id }
        if (f != null) {
            messages = messages + ChatMessage(role = "system", content = "Файл «${f.name}» удалён.")
        }
    }

    fun selectPersonality(p: Personality) {
        personality = p
        messages = messages + ChatMessage(
            role = "system",
            content = "Личность: ${p.emoji} ${p.title}"
        )
    }

    fun analyzeDialog(): String {
        val userMsgs = messages.filter { it.role == "user" }
        val aiMsgs = messages.filter { it.role == "ai" }
        val topics = userMsgs.takeLast(10).joinToString(" | ") { it.content.take(40) }
        return buildString {
            append("Анализ диалога:\n")
            append("• Сообщений пользователя: ${userMsgs.size}\n")
            append("• Ответов ИИ: ${aiMsgs.size}\n")
            append("• Источников знаний: ${sources.size}\n")
            append("• Файлов в чате: ${chatFiles.size}\n")
            append("• Личность: ${personality.title}\n")
            if (userMsgs.isNotEmpty()) {
                append("• Последние темы: ${topics.ifBlank { "—" }}\n")
            }
            val likes = messages.count { it.feedback == "like" }
            val dislikes = messages.count { it.feedback == "dislike" }
            append("• Лайки: $likes · Дизлайки: $dislikes")
        }
    }

    fun send() {
        val q = input.trim()
        if (q.isEmpty() || isThinking) return
        input = ""
        messages = messages + ChatMessage(role = "user", content = q)

        val lower = q.lowercase()

        when {
            lower in listOf("привет", "хай", "здравствуй", "здравствуйте") -> {
                messages = messages + ChatMessage(
                    role = "ai",
                    content = styleReply("Привет! Рад тебя видеть. Чем займёмся?"),
                    confidence = 92,
                    actions = listOf("Распознал приветствие", "Выбрал дружелюбный тон")
                )
                return
            }
            lower.contains("как дела") || lower.contains("как ты") -> {
                messages = messages + ChatMessage(
                    role = "ai",
                    content = styleReply("Всё хорошо, на связи и готов работать. А у тебя как?"),
                    confidence = 90,
                    actions = listOf("Прочитал вопрос о состоянии", "Сформировал ответ")
                )
                return
            }
            lower.contains("анализ") && (lower.contains("диалог") || lower.contains("чат") || lower.contains("разговор")) -> {
                messages = messages + ChatMessage(
                    role = "ai",
                    content = styleReply(analyzeDialog()),
                    confidence = 85,
                    actions = listOf(
                        "Открыл историю диалога",
                        "Подсчитал сообщения",
                        "Проанализировал темы",
                        "Собрал статистику фидбека"
                    )
                )
                return
            }
            lower.startsWith("создай файл") || lower.startsWith("создать файл") || lower.contains("сделай файл") -> {
                val name = Regex("файл[ае]?\\s+[«\"]?([\\wА-Яа-я.\\-]+)", RegexOption.IGNORE_CASE)
                    .find(q)?.groupValues?.getOrNull(1) ?: "note_${chatFiles.size + 1}.txt"
                val content = q.substringAfter(":").ifBlank {
                    q.substringAfter("файл").trim().ifBlank { "Пустой файл, созданный агентом." }
                }
                createFile(name, content)
                messages = messages + ChatMessage(
                    role = "ai",
                    content = styleReply("Готово. Файл «$name» создан в чате. Можешь открыть его в списке файлов."),
                    confidence = 88,
                    actions = listOf(
                        "Понял запрос на создание файла",
                        "Сгенерировал имя: $name",
                        "Записал содержимое",
                        "Сохранил файл в чат"
                    )
                )
                return
            }
        }

        if (!isTrained || sources.isEmpty()) {
            messages = messages + ChatMessage(
                role = "ai",
                content = styleReply("Я ещё не обучен. Добавь тексты в знания и нажми «Обучить»."),
                confidence = 5,
                actions = listOf("Проверил базу знаний", "База пуста — обучение не выполнено")
            )
            return
        }

        isThinking = true
    }

    suspend fun finishThinking(question: String) {
        delay(500)

        val actions = mutableListOf<String>()
        actions += "Получил вопрос пользователя"
        actions += "Открыл память диалога (${messages.count { it.role == "user" }} сообщений)"
        actions += "Просканировал базу знаний (${sources.size} источников)"

        val tokens = question.lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length > 1 }

        actions += "Токенизировал запрос (${tokens.size} слов)"

        val scored = sources.map { src ->
            val textLower = src.text.lowercase()
            val score = if (tokens.isEmpty()) 0.0
            else tokens.count { textLower.contains(it) }.toDouble() / tokens.size
            src to score
        }.sortedByDescending { it.second }

        val top = scored.take(3).filter { it.second > 0 }
        actions += if (top.isEmpty()) "Релевантных знаний не найдено"
        else "Нашёл ${top.size} релевантных фрагмента"

        val fileHits = chatFiles.filter { f ->
            tokens.any { f.name.lowercase().contains(it) || f.content.lowercase().contains(it) }
        }
        if (fileHits.isNotEmpty()) {
            actions += "Проверил файлы чата — совпадений: ${fileHits.size}"
        }

        val recentUser = messages.filter { it.role == "user" }.takeLast(3).map { it.content }
        if (recentUser.size > 1) {
            actions += "Учёл предыдущий контекст диалога"
        }

        actions += "Перефразировал знания под вопрос и контекст"
        actions += "Применил личность: ${personality.title}"
        actions += "Сформировал гипотезы с вероятностями"

        val thinking = if (top.isEmpty()) {
            listOf("Прямого совпадения нет" to 70, "Мало данных по теме" to 30)
        } else {
            val total = top.sumOf { it.second }.coerceAtLeast(0.01)
            top.map { (src, sc) ->
                val pct = ((sc / total) * 100).toInt().coerceIn(5, 90)
                val snippet = src.text.take(70).replace("\n", " ") + if (src.text.length > 70) "…" else ""
                snippet to pct
            }
        }

        val sum = thinking.sumOf { it.second }
        val normalized = if (sum > 0 && thinking.isNotEmpty()) {
            thinking.mapIndexed { i, (t, p) ->
                if (i == 0) t to (p + (100 - sum)).coerceAtLeast(5) else t to p
            }
        } else thinking

        val best = top.firstOrNull()
        val confidence = if (best == null) 12
        else ((best.second * 80) + 15).toInt().coerceIn(10, 92)

        val secondSnippet = top.getOrNull(1)?.let { extractSnippet(it.first.text, tokens) }

        var answer = if (best == null) {
            "Пока не нашёл достаточно близкой информации. Добавь больше текстов или уточни вопрос."
        } else {
            val rawSnippet = extractSnippet(best.first.text, tokens)
            paraphraseForDialog(
                question = question,
                knowledge = rawSnippet,
                extraKnowledge = secondSnippet,
                recentUser = recentUser,
                confidence = confidence,
                fileName = fileHits.firstOrNull()?.name
            )
        }

        if (feedbackNotes.any { it.contains("сухо") || it.contains("непонятно") }) {
            answer = "Попробую объяснить проще и понятнее.\n\n$answer"
        }

        answer = styleReply(answer)
        actions += "Отправил ответ пользователю"

        messages = messages + ChatMessage(
            role = "ai",
            content = answer,
            confidence = confidence,
            thinking = normalized,
            actions = actions
        )
        isThinking = false
    }

    /** Перефразирует знания под вопрос и контекст диалога, а не вставляет сырой кусок. */
    private fun paraphraseForDialog(
        question: String,
        knowledge: String,
        extraKnowledge: String?,
        recentUser: List<String>,
        confidence: Int,
        fileName: String?
    ): String {
        val q = question.trim()
        val qLower = q.lowercase()
        val fact = knowledge.trim().trimEnd('.', '!', '?')
        val extra = extraKnowledge?.trim()?.trimEnd('.', '!', '?')

        val lead = when {
            recentUser.size > 1 -> "Учитывая, о чём мы говорили, "
            qLower.startsWith("что такое") || qLower.startsWith("что это") -> "Если коротко по твоему вопросу: "
            qLower.startsWith("как") -> "По твоему вопросу «как…» вот как это можно понять: "
            qLower.startsWith("почему") || qLower.startsWith("зачем") -> "По сути причина такая: "
            qLower.contains("расскажи") || qLower.contains("объясни") -> "Объясню своими словами: "
            else -> "По твоему вопросу вот что получается: "
        }

        var body = lead + fact
        if (!body.endsWith('.') && !body.endsWith('!') && !body.endsWith('?')) {
            body += "."
        }

        if (!extra.isNullOrBlank() && extra.length > 20 && !fact.contains(extra.take(30))) {
            body += " Ещё важный момент: $extra."
        }

        if (!fileName.isNullOrBlank()) {
            body += " К этой теме ещё относится файл «$fileName»."
        }

        body += when {
            confidence >= 60 -> listOf(
                "",
                " Если нужно — раскрою подробнее.",
                " Можем копнуть глубже, если хочешь."
            ).random()
            confidence < 35 -> " Честно, уверенность здесь невысокая — данных маловато."
            else -> ""
        }

        return body
    }

    private fun styleReply(raw: String): String {
        return when (personality) {
            Personality.NONE -> raw
            Personality.HORROR -> "В тишине раздаётся шёпот…\n\n$raw\n\n…ты ведь это слышишь?"
            Personality.EGOIST -> "Очевидно же. Слушай внимательно, я объясню как есть:\n\n$raw\n\nЗапомни, ты услышал это от лучшего."
            Personality.VILLAIN -> "Ха… интересный вопрос.\n\n$raw\n\nНадеюсь, ты готов к последствиям своих любопытств."
            Personality.KIND -> "Конечно, с радостью помогу 💛\n\n$raw\n\nЕсли что-то непонятно — спрашивай, я рядом."
            Personality.CUTE -> "Хехе, давай разберём вместе~\n\n$raw\n\nТы молодец, что спросил 🥺✨"
        }
    }

    private fun extractSnippet(text: String, tokens: List<String>): String {
        val sentences = text.split(Regex("[.!?\n]+"))
            .map { it.trim() }
            .filter { it.length > 15 }
        if (sentences.isEmpty()) return text.take(300)
        val best = sentences.maxByOrNull { s ->
            tokens.count { s.lowercase().contains(it) }
        } ?: sentences.first()
        return if (best.length > 400) best.take(400) + "…" else best
    }

    fun like(msgId: String) {
        messages = messages.map { if (it.id == msgId) it.copy(feedback = "like") else it }
        messages = messages + ChatMessage(role = "system", content = "👍 Спасибо! Учту.")
    }

    fun openDislike(msgId: String) {
        feedbackMsgId = msgId
        feedbackComment = ""
        showFeedbackDialog = true
    }

    fun submitDislike() {
        val id = feedbackMsgId ?: return
        val comment = feedbackComment.trim()
        messages = messages.map { if (it.id == id) it.copy(feedback = "dislike") else it }
        if (comment.isNotEmpty()) {
            feedbackNotes.add(comment.lowercase())
            messages = messages + ChatMessage(
                role = "system",
                content = "Понял: «$comment». Буду учитывать."
            )
        } else {
            messages = messages + ChatMessage(role = "system", content = "Понял, ответ не зашёл.")
        }
        showFeedbackDialog = false
    }
}

@Composable
fun LuntikTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            background = Bg,
            surface = SurfaceC,
            onPrimary = Bg,
            onBackground = TextMain,
            onSurface = TextMain
        ),
        content = content
    )
}

@Composable
fun LuntikApp(vm: LuntikViewModel = viewModel()) {
    val listState = rememberLazyListState()

    LaunchedEffect(vm.messages.size) {
        if (vm.messages.isNotEmpty()) listState.animateScrollToItem(vm.messages.lastIndex)
    }

    LaunchedEffect(vm.isThinking) {
        if (vm.isThinking) {
            val lastUser = vm.messages.lastOrNull { it.role == "user" }?.content ?: ""
            vm.finishThinking(lastUser)
        }
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(
            Modifier.fillMaxWidth().background(SurfaceC).statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🌱 LuntikAi", color = Accent, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.weight(1f))
            Text(
                "${vm.personality.emoji} · ${if (vm.isTrained) "обучен" else "не обучен"}",
                color = TextMuted,
                fontSize = 11.sp
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(vm.messages, key = { it.id }) { msg ->
                MessageBubble(msg, onLike = { vm.like(msg.id) }, onDislike = { vm.openDislike(msg.id) })
            }
            if (vm.isThinking) {
                item {
                    Text("💭 Агент думает…", color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().background(SurfaceC).padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SmallBtn("Знания", Icons.Default.MenuBook) { vm.showKnowledgeList = true }
            SmallBtn("Файлы", Icons.Default.Folder) { vm.showFilesList = true }
            SmallBtn("Личность", Icons.Default.Face) { vm.showPersonalityDialog = true }
            Button(
                onClick = { vm.train() },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg)
            ) { Text("Обучить", fontSize = 12.sp) }
        }

        Row(
            Modifier.fillMaxWidth().background(SurfaceC).navigationBarsPadding().padding(10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = vm.input,
                onValueChange = { vm.input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Сообщение или «создай файл …»", color = TextMuted, fontSize = 13.sp) },
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
                Icon(Icons.AutoMirrored.Filled.Send, "Отправить", tint = Bg)
            }
        }
    }

    if (vm.showKnowledgeList) {
        AlertDialog(
            onDismissRequest = { vm.showKnowledgeList = false },
            title = { Text("Знания (${vm.sources.size})") },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    if (vm.sources.isEmpty()) {
                        Text("Пока пусто", color = TextMuted)
                    } else {
                        vm.sources.forEach { src ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                    vm.editingSource = src
                                    vm.newSourceName = src.name
                                    vm.newSourceText = src.text
                                    vm.showEditDialog = true
                                    vm.showKnowledgeList = false
                                },
                                colors = CardDefaults.cardColors(containerColor = Surface2)
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(src.name, fontWeight = FontWeight.SemiBold, color = TextMain)
                                    Text(
                                        src.text.take(80) + if (src.text.length > 80) "…" else "",
                                        color = TextMuted,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        vm.showKnowledgeList = false
                        vm.newSourceName = ""
                        vm.newSourceText = ""
                        vm.showAddDialog = true
                    }) { Text("Добавить") }
                    TextButton(onClick = { vm.showKnowledgeList = false }) { Text("Закрыть") }
                }
            }
        )
    }

    if (vm.showAddDialog) {
        SourceEditDialog(
            title = "Новый источник",
            name = vm.newSourceName,
            text = vm.newSourceText,
            onName = { vm.newSourceName = it },
            onText = { vm.newSourceText = it },
            onSave = {
                vm.addSource(vm.newSourceName, vm.newSourceText)
                vm.newSourceName = ""
                vm.newSourceText = ""
                vm.showAddDialog = false
            },
            onCancel = { vm.showAddDialog = false }
        )
    }

    if (vm.showEditDialog && vm.editingSource != null) {
        val src = vm.editingSource!!
        SourceEditDialog(
            title = "Редактировать",
            name = vm.newSourceName,
            text = vm.newSourceText,
            onName = { vm.newSourceName = it },
            onText = { vm.newSourceText = it },
            onSave = {
                vm.updateSource(src.id, vm.newSourceName, vm.newSourceText)
                vm.showEditDialog = false
                vm.editingSource = null
            },
            onCancel = {
                vm.showEditDialog = false
                vm.editingSource = null
            },
            onDelete = {
                vm.deleteSource(src.id)
                vm.showEditDialog = false
                vm.editingSource = null
            }
        )
    }

    if (vm.showFilesList) {
        AlertDialog(
            onDismissRequest = { vm.showFilesList = false },
            title = { Text("Файлы чата (${vm.chatFiles.size})") },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    if (vm.chatFiles.isEmpty()) {
                        Text("Файлов пока нет. Напиши: создай файл заметка.txt", color = TextMuted, fontSize = 13.sp)
                    } else {
                        vm.chatFiles.forEach { f ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                    vm.viewingFile = f
                                    vm.showFilesList = false
                                },
                                colors = CardDefaults.cardColors(containerColor = Surface2)
                            ) {
                                Row(
                                    Modifier.padding(10.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text("📄 ${f.name}", fontWeight = FontWeight.SemiBold, color = TextMain)
                                        Text("${f.content.length} символов", color = TextMuted, fontSize = 11.sp)
                                    }
                                    IconButton(onClick = { vm.deleteFile(f.id) }) {
                                        Icon(Icons.Default.Delete, null, tint = Danger, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        vm.showFilesList = false
                        vm.newFileName = ""
                        vm.newFileContent = ""
                        vm.showCreateFileDialog = true
                    }) { Text("Создать") }
                    TextButton(onClick = { vm.showFilesList = false }) { Text("Закрыть") }
                }
            }
        )
    }

    if (vm.showCreateFileDialog) {
        AlertDialog(
            onDismissRequest = { vm.showCreateFileDialog = false },
            title = { Text("Новый файл") },
            text = {
                Column {
                    OutlinedTextField(
                        value = vm.newFileName,
                        onValueChange = { vm.newFileName = it },
                        label = { Text("Имя файла") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = vm.newFileContent,
                        onValueChange = { vm.newFileContent = it },
                        label = { Text("Содержимое") },
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                        maxLines = 8
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.createFile(vm.newFileName, vm.newFileContent)
                    vm.showCreateFileDialog = false
                }) { Text("Создать") }
            },
            dismissButton = {
                TextButton(onClick = { vm.showCreateFileDialog = false }) { Text("Отмена") }
            }
        )
    }

    vm.viewingFile?.let { f ->
        AlertDialog(
            onDismissRequest = { vm.viewingFile = null },
            title = { Text("📄 ${f.name}") },
            text = {
                Text(
                    f.content.ifBlank { "(пусто)" },
                    color = TextMain,
                    modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(max = 360.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.viewingFile = null }) { Text("Закрыть") }
            }
        )
    }

    if (vm.showPersonalityDialog) {
        AlertDialog(
            onDismissRequest = { vm.showPersonalityDialog = false },
            title = { Text("Личность") },
            text = {
                Column {
                    Personality.entries.forEach { p ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.selectPersonality(p)
                                    vm.showPersonalityDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${p.emoji}  ${p.title}", color = TextMain, fontSize = 15.sp)
                            if (vm.personality == p) {
                                Spacer(Modifier.weight(1f))
                                Text("✓", color = Accent)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.showPersonalityDialog = false }) { Text("Закрыть") }
            }
        )
    }

    if (vm.showFeedbackDialog) {
        AlertDialog(
            onDismissRequest = { vm.showFeedbackDialog = false },
            title = { Text("Что не так?") },
            text = {
                OutlinedTextField(
                    value = vm.feedbackComment,
                    onValueChange = { vm.feedbackComment = it },
                    placeholder = { Text("слишком сухо, не по теме…") },
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
fun SmallBtn(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(icon, null, Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp)
    }
}

@Composable
fun SourceEditDialog(
    title: String,
    name: String,
    text: String,
    onName: (String) -> Unit,
    onText: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = onName,
                    label = { Text("Название") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = onText,
                    label = { Text("Текст") },
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    maxLines = 12
                )
            }
        },
        confirmButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Удалить", color = Danger)
                    }
                }
                Button(onClick = onSave) { Text("Сохранить") }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Отмена") }
        }
    )
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
            shape = RoundedCornerShape(14.dp),
            color = when {
                isUser -> UserBubble
                isSystem -> Color.Transparent
                else -> AiBubble
            },
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Column(Modifier.padding(11.dp)) {
                if (!isSystem) {
                    Text(
                        if (isUser) "Ты" else "LuntikAi",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(3.dp))
                }

                if (!msg.actions.isNullOrEmpty()) {
                    Surface(
                        color = ActionBg,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            Text("⚡ Действия агента", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            msg.actions.forEach { a ->
                                Text("• $a", color = TextMuted, fontSize = 11.sp, lineHeight = 15.sp)
                            }
                        }
                    }
                }

                if (!msg.thinking.isNullOrEmpty()) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            Text("💭 Раздумье", color = TextMuted, fontSize = 10.sp)
                            Spacer(Modifier.height(4.dp))
                            msg.thinking.forEach { (t, p) ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 1.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(t, color = TextMain, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                    Text(
                                        "$p%",
                                        color = when {
                                            p >= 45 -> Accent
                                            p >= 25 -> Warn
                                            else -> Danger
                                        },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    msg.content,
                    color = if (isSystem) TextMuted else TextMain,
                    fontSize = if (isSystem) 12.sp else 14.sp,
                    lineHeight = 19.sp
                )

                if (msg.confidence != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Уверенность: ${msg.confidence}%",
                        color = when {
                            msg.confidence >= 55 -> Accent
                            msg.confidence >= 25 -> Warn
                            else -> Danger
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Дисклеймер после ответа ИИ
                if (msg.role == "ai") {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Luntik-ai это ии и он может ошибаться.",
                        color = TextMuted.copy(alpha = 0.75f),
                        fontSize = 10.sp,
                        lineHeight = 13.sp
                    )
                }

                if (msg.role == "ai") {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = onLike, contentPadding = PaddingValues(2.dp)) {
                            Icon(
                                Icons.Default.ThumbUp, null, Modifier.size(14.dp),
                                tint = if (msg.feedback == "like") Accent else TextMuted
                            )
                            Spacer(Modifier.width(3.dp))
                            Text("Лайк", color = if (msg.feedback == "like") Accent else TextMuted, fontSize = 11.sp)
                        }
                        TextButton(onClick = onDislike, contentPadding = PaddingValues(2.dp)) {
                            Icon(
                                Icons.Default.ThumbDown, null, Modifier.size(14.dp),
                                tint = if (msg.feedback == "dislike") Danger else TextMuted
                            )
                            Spacer(Modifier.width(3.dp))
                            Text("Дизлайк", color = if (msg.feedback == "dislike") Danger else TextMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
