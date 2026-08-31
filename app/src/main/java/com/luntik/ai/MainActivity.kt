package com.luntik.ai

import android.app.Application
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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.math.ln

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

enum class Personality(val title: String, val emoji: String) {
    NONE("Обычный ИИ", "🤖"),
    HORROR("Хоррор", "👻"),
    EGOIST("Эгоист", "👑"),
    VILLAIN("Злодей", "😈"),
    KIND("Добряк", "😇"),
    CUTE("Милый", "🥺"),
    HUMORIST("Юморист", "😂")
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

class TfIdfIndex {
    private var docs: List<Pair<String, List<String>>> = emptyList()
    private var df: Map<String, Int> = emptyMap()
    private var nDocs = 0

    fun build(sources: List<KnowledgeSource>) {
        docs = sources.map { it.id to tokenize(it.text) }
        nDocs = docs.size.coerceAtLeast(1)
        val dfMap = mutableMapOf<String, Int>()
        docs.forEach { (_, tokens) ->
            tokens.toSet().forEach { t -> dfMap[t] = (dfMap[t] ?: 0) + 1 }
        }
        df = dfMap
    }

    fun score(query: String, sourceId: String, text: String): Double {
        val qTokens = tokenize(query)
        if (qTokens.isEmpty()) return 0.0
        val docTokens = tokenize(text)
        if (docTokens.isEmpty()) return 0.0
        val tfMap = docTokens.groupingBy { it }.eachCount()
        var score = 0.0
        for (t in qTokens.toSet()) {
            val tf = (tfMap[t] ?: 0).toDouble() / docTokens.size
            val idf = ln((nDocs + 1.0) / ((df[t] ?: 0) + 1.0)) + 1.0
            score += tf * idf
        }
        if (text.lowercase().contains(query.lowercase().take(40))) score *= 1.3
        return score
    }

    companion object {
        fun tokenize(s: String): List<String> =
            s.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 1 }
    }
}

class LuntikViewModel(app: Application) : AndroidViewModel(app) {
    private val storeFile: File get() = File(getApplication<Application>().filesDir, "luntik_state.json")

    var messages by mutableStateOf(listOf<ChatMessage>())
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
    var showEditFileDialog by mutableStateOf(false)
    var editingFile by mutableStateOf<ChatFile?>(null)
    var newFileName by mutableStateOf("")
    var newFileContent by mutableStateOf("")
    var viewingFile by mutableStateOf<ChatFile?>(null)

    var showPersonalityDialog by mutableStateOf(false)
    var showHumorWarning by mutableStateOf(false)
    var showFeedbackDialog by mutableStateOf(false)
    var feedbackMsgId by mutableStateOf<String?>(null)
    var feedbackComment by mutableStateOf("")

    var showExportDialog by mutableStateOf(false)
    var showImportDialog by mutableStateOf(false)
    var exportText by mutableStateOf("")
    var importText by mutableStateOf("")

    private val feedbackNotes = mutableListOf<String>()
    private val index = TfIdfIndex()

    init {
        loadState()
        if (messages.isEmpty()) {
            messages = listOf(
                ChatMessage(
                    role = "system",
                    content = "Привет! LuntikAi v0.3\n\n• Знания + TF‑IDF\n• Файлы, сохранение, экспорт\n• Интернет: «открой https://…»\n• Личности, включая Юморист (с предупреждением)"
                )
            )
        }
    }

    private fun persist() {
        try {
            val o = JSONObject()
            o.put("personality", personality.name)
            o.put("isTrained", isTrained)
            o.put("feedbackNotes", JSONArray(feedbackNotes))
            val srcArr = JSONArray()
            sources.forEach { s ->
                srcArr.put(JSONObject().put("id", s.id).put("name", s.name).put("text", s.text))
            }
            o.put("sources", srcArr)
            val fileArr = JSONArray()
            chatFiles.forEach { f ->
                fileArr.put(JSONObject().put("id", f.id).put("name", f.name).put("content", f.content).put("createdAt", f.createdAt))
            }
            o.put("files", fileArr)
            val msgArr = JSONArray()
            messages.takeLast(80).forEach { m ->
                msgArr.put(
                    JSONObject().put("id", m.id).put("role", m.role).put("content", m.content)
                        .put("confidence", m.confidence ?: JSONObject.NULL)
                        .put("feedback", m.feedback ?: JSONObject.NULL)
                )
            }
            o.put("messages", msgArr)
            storeFile.writeText(o.toString())
        } catch (_: Exception) {
        }
    }

    private fun loadState() {
        try {
            if (!storeFile.exists()) return
            val o = JSONObject(storeFile.readText())
            personality = try {
                Personality.valueOf(o.optString("personality", "NONE"))
            } catch (_: Exception) {
                Personality.NONE
            }
            isTrained = o.optBoolean("isTrained", false)
            feedbackNotes.clear()
            val fn = o.optJSONArray("feedbackNotes")
            if (fn != null) for (i in 0 until fn.length()) feedbackNotes.add(fn.getString(i))
            val srcArr = o.optJSONArray("sources")
            if (srcArr != null) {
                val list = mutableListOf<KnowledgeSource>()
                for (i in 0 until srcArr.length()) {
                    val s = srcArr.getJSONObject(i)
                    list.add(KnowledgeSource(s.optString("id", UUID.randomUUID().toString()), s.optString("name"), s.optString("text")))
                }
                sources = list
            }
            val fileArr = o.optJSONArray("files")
            if (fileArr != null) {
                val list = mutableListOf<ChatFile>()
                for (i in 0 until fileArr.length()) {
                    val f = fileArr.getJSONObject(i)
                    list.add(ChatFile(f.optString("id", UUID.randomUUID().toString()), f.optString("name"), f.optString("content"), f.optLong("createdAt", System.currentTimeMillis())))
                }
                chatFiles = list
            }
            val msgArr = o.optJSONArray("messages")
            if (msgArr != null) {
                val list = mutableListOf<ChatMessage>()
                for (i in 0 until msgArr.length()) {
                    val m = msgArr.getJSONObject(i)
                    list.add(
                        ChatMessage(
                            m.optString("id", UUID.randomUUID().toString()),
                            m.optString("role"),
                            m.optString("content"),
                            if (m.isNull("confidence")) null else m.optInt("confidence"),
                            feedback = if (m.isNull("feedback")) null else m.optString("feedback")
                        )
                    )
                }
                messages = list
            }
            if (isTrained && sources.isNotEmpty()) index.build(sources)
        } catch (_: Exception) {
        }
    }

    fun addSource(name: String, text: String) {
        if (text.isBlank()) return
        sources = sources + KnowledgeSource(name = name.ifBlank { "Источник ${sources.size + 1}" }, text = text)
        isTrained = false
        messages = messages + ChatMessage(role = "system", content = "Добавлен источник. Нажми «Обучить».")
        persist()
    }

    fun updateSource(id: String, name: String, text: String) {
        sources = sources.map { if (it.id == id) it.copy(name = name.ifBlank { it.name }, text = text) else it }
        isTrained = false
        messages = messages + ChatMessage(role = "system", content = "Источник обновлён. Нужно переобучить.")
        persist()
    }

    fun deleteSource(id: String) {
        sources = sources.filter { it.id != id }
        isTrained = false
        messages = messages + ChatMessage(role = "system", content = "Источник удалён.")
        persist()
    }

    fun train() {
        if (sources.isEmpty()) {
            messages = messages + ChatMessage(role = "system", content = "Сначала добавь текст.")
            return
        }
        index.build(sources)
        isTrained = true
        messages = messages + ChatMessage(role = "system", content = "Обучение (TF‑IDF) готово! Источников: ${sources.size}.")
        persist()
    }

    fun createFile(name: String, content: String) {
        val n = name.ifBlank { "file_${chatFiles.size + 1}.txt" }
        chatFiles = chatFiles + ChatFile(name = n, content = content)
        messages = messages + ChatMessage(role = "system", content = "📄 Создан файл «$n»")
        persist()
    }

    fun updateFile(id: String, name: String, content: String) {
        chatFiles = chatFiles.map { if (it.id == id) it.copy(name = name.ifBlank { it.name }, content = content) else it }
        messages = messages + ChatMessage(role = "system", content = "Файл обновлён.")
        persist()
    }

    fun deleteFile(id: String) {
        val f = chatFiles.find { it.id == id }
        chatFiles = chatFiles.filter { it.id != id }
        if (f != null) messages = messages + ChatMessage(role = "system", content = "Файл «${f.name}» удалён.")
        persist()
    }

    fun requestPersonality(p: Personality) {
        if (p == Personality.HUMORIST && personality != Personality.HUMORIST) {
            showPersonalityDialog = false
            showHumorWarning = true
        } else {
            selectPersonality(p)
            showPersonalityDialog = false
        }
    }

    fun confirmHumorist() {
        showHumorWarning = false
        selectPersonality(Personality.HUMORIST)
    }

    fun selectPersonality(p: Personality) {
        personality = p
        messages = messages + ChatMessage(role = "system", content = "Личность: ${p.emoji} ${p.title}")
        persist()
    }

    fun buildExport(): String {
        val o = JSONObject()
        o.put("app", "LuntikAi").put("version", "0.3").put("personality", personality.name).put("isTrained", isTrained)
        o.put("feedbackNotes", JSONArray(feedbackNotes))
        val srcArr = JSONArray()
        sources.forEach { s -> srcArr.put(JSONObject().put("name", s.name).put("text", s.text)) }
        o.put("sources", srcArr)
        val fileArr = JSONArray()
        chatFiles.forEach { f -> fileArr.put(JSONObject().put("name", f.name).put("content", f.content)) }
        o.put("files", fileArr)
        return o.toString(2)
    }

    fun applyImport(json: String): Boolean {
        return try {
            val o = JSONObject(json)
            personality = try { Personality.valueOf(o.optString("personality", personality.name)) } catch (_: Exception) { personality }
            val srcArr = o.optJSONArray("sources")
            if (srcArr != null) {
                val list = mutableListOf<KnowledgeSource>()
                for (i in 0 until srcArr.length()) {
                    val s = srcArr.getJSONObject(i)
                    list.add(KnowledgeSource(name = s.optString("name"), text = s.optString("text")))
                }
                sources = list
            }
            val fileArr = o.optJSONArray("files")
            if (fileArr != null) {
                val list = mutableListOf<ChatFile>()
                for (i in 0 until fileArr.length()) {
                    val f = fileArr.getJSONObject(i)
                    list.add(ChatFile(name = f.optString("name"), content = f.optString("content")))
                }
                chatFiles = list
            }
            isTrained = false
            messages = messages + ChatMessage(role = "system", content = "Импорт ок. Нажми «Обучить».")
            persist()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun analyzeDialog(): String {
        val userMsgs = messages.filter { it.role == "user" }
        val aiMsgs = messages.filter { it.role == "ai" }
        return buildString {
            append("Анализ диалога:\n")
            append("• Сообщений: ${userMsgs.size}\n• Ответов ИИ: ${aiMsgs.size}\n")
            append("• Источников: ${sources.size}\n• Файлов: ${chatFiles.size}\n")
            append("• Личность: ${personality.title}\n")
            append("• Лайки: ${messages.count { it.feedback == "like" }} · Дизлайки: ${messages.count { it.feedback == "dislike" }}")
        }
    }

    private suspend fun fetchUrl(urlStr: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12000
                readTimeout = 15000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "LuntikAi/0.3")
            }
            if (conn.responseCode !in 200..299) return@withContext false to "HTTP ${conn.responseCode}"
            val raw = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).readText()
            conn.disconnect()
            val text = raw.replace(Regex("(?is)<script[^>]*>.*?</script>"), " ")
                .replace(Regex("(?is)<style[^>]*>.*?</style>"), " ")
                .replace(Regex("(?is)<[^>]+>"), " ")
                .replace(Regex("\\s+"), " ").trim().take(4000)
            if (text.isBlank()) false to "Пустая страница" else true to text
        } catch (e: Exception) {
            false to (e.message ?: "Ошибка сети")
        }
    }

    fun send() {
        val q = input.trim()
        if (q.isEmpty() || isThinking) return
        input = ""
        messages = messages + ChatMessage(role = "user", content = q)
        persist()
        val lower = q.lowercase()

        when {
            lower in listOf("привет", "хай", "здравствуй", "здравствуйте") -> {
                pushAi(styleReply("Привет! Рад тебя видеть. Чем займёмся?"), 92, listOf("Приветствие"))
                return
            }
            lower.contains("как дела") || lower.contains("как ты") -> {
                pushAi(styleReply("Всё хорошо. А у тебя?"), 90, listOf("Ответ о состоянии"))
                return
            }
            lower.contains("анализ") && (lower.contains("диалог") || lower.contains("чат")) -> {
                pushAi(styleReply(analyzeDialog()), 85, listOf("Анализ диалога"))
                return
            }
            lower.startsWith("создай файл") || lower.contains("сделай файл") -> {
                val name = Regex("файл[ае]?\\s+[«\"]?([\\wА-Яа-я.\\-]+)", RegexOption.IGNORE_CASE)
                    .find(q)?.groupValues?.getOrNull(1) ?: "note_${chatFiles.size + 1}.txt"
                val content = q.substringAfter(":").ifBlank { "Файл от агента." }
                createFile(name, content)
                pushAi(styleReply("Файл «$name» создан."), 88, listOf("Создал файл $name"))
                return
            }
            Regex("https?://\\S+").containsMatchIn(q) &&
                (lower.contains("открой") || lower.contains("открыть") || lower.contains("скачай") || lower.contains("прочитай")) -> {
                val url = Regex("https?://\\S+").find(q)?.value?.trimEnd('.', ',', ')', ']') ?: ""
                if (url.isBlank()) {
                    pushAi(styleReply("Не нашёл URL."), 20, listOf("Нет ссылки"))
                    return
                }
                isThinking = true
                viewModelScope.launch {
                    val actions = mutableListOf("Интернет", "Открываю $url")
                    val (ok, result) = fetchUrl(url)
                    if (ok) {
                        actions += "Скачал ${result.length} символов"
                        pushAi(styleReply("Содержимое:\n\n${result.take(900)}"), 70, actions)
                    } else {
                        pushAi(styleReply("Не открылось: $result"), 15, actions + result)
                    }
                    isThinking = false
                    persist()
                }
                return
            }
        }

        if (!isTrained || sources.isEmpty()) {
            pushAi(styleReply("Не обучен. Добавь знания и нажми «Обучить»."), 5, listOf("База пуста"))
            return
        }
        isThinking = true
    }

    private fun pushAi(content: String, confidence: Int, actions: List<String>, thinking: List<Pair<String, Int>>? = null) {
        messages = messages + ChatMessage(role = "ai", content = content, confidence = confidence, actions = actions, thinking = thinking)
        persist()
    }

    suspend fun finishThinking(question: String) {
        delay(400)
        val actions = mutableListOf("Вопрос", "Память", "TF‑IDF поиск")
        val tokens = TfIdfIndex.tokenize(question)
        val scored = sources.map { it to index.score(question, it.id, it.text) }.sortedByDescending { it.second }
        val top = scored.take(3).filter { it.second > 0.0001 }
        actions += if (top.isEmpty()) "Ничего не нашёл" else "Фрагментов: ${top.size}"
        actions += "Личность: ${personality.title}"

        val thinking = if (top.isEmpty()) listOf("Нет совпадений" to 70, "Мало данных" to 30)
        else {
            val total = top.sumOf { it.second }.coerceAtLeast(0.0001)
            top.map { (src, sc) ->
                (src.text.take(70).replace("\n", " ") + if (src.text.length > 70) "…" else "") to
                    ((sc / total) * 100).toInt().coerceIn(5, 90)
            }
        }
        val sum = thinking.sumOf { it.second }
        val normalized = if (sum > 0) thinking.mapIndexed { i, (t, p) ->
            if (i == 0) t to (p + (100 - sum)).coerceAtLeast(5) else t to p
        } else thinking

        val best = top.firstOrNull()
        val confidence = if (best == null) 12 else (20 + (best.second * 40).toInt()).coerceIn(10, 92)
        val recentUser = messages.filter { it.role == "user" }.takeLast(3).map { it.content }

        var answer = if (best == null) {
            "Мало данных по теме. Добавь тексты или уточни вопрос."
        } else {
            val raw = extractSnippet(best.first.text, tokens)
            val extra = top.getOrNull(1)?.let { extractSnippet(it.first.text, tokens) }
            paraphraseForDialog(question, raw, extra, recentUser, confidence)
        }
        answer = styleReply(answer)
        pushAi(answer, confidence, actions + "Ответ", normalized)
        isThinking = false
    }

    private fun paraphraseForDialog(
        question: String, knowledge: String, extra: String?, recentUser: List<String>, confidence: Int
    ): String {
        val qLower = question.lowercase()
        val fact = knowledge.trim().trimEnd('.', '!', '?')
        val lead = when {
            recentUser.size > 1 -> "Учитывая разговор, "
            qLower.startsWith("что такое") -> "Если коротко: "
            qLower.startsWith("как") -> "Вот как: "
            else -> "По вопросу: "
        }
        var body = lead + fact
        if (!body.endsWith('.') && !body.endsWith('!') && !body.endsWith('?')) body += "."
        if (!extra.isNullOrBlank() && extra.length > 20) body += " Ещё: ${extra.trimEnd('.', '!', '?')}."
        if (confidence < 35) body += " Данных маловато."
        return body
    }

    private fun styleReply(raw: String): String = when (personality) {
        Personality.NONE -> raw
        Personality.HORROR -> "В тишине шёпот…\n\n$raw\n\n…ты слышишь?"
        Personality.EGOIST -> "Очевидно:\n\n$raw\n\nЗапиши от лучшего."
        Personality.VILLAIN -> "Ха…\n\n$raw\n\nЛюбопытство имеет цену."
        Personality.KIND -> "С радостью 💛\n\n$raw\n\nСпрашивай ещё."
        Personality.CUTE -> "Хехе~\n\n$raw\n\nТы молодец 🥺✨"
        Personality.HUMORIST -> {
            val openers = listOf(
                "О, классика. Держи, гений мысли:",
                "Сейчас будет больно, но смешно:",
                "Лунтик одобрил этот рофл:",
                "Ну ты и выдал… ладно, отвечаю:",
                "Без обид, чисто юмор:"
            )
            val closers = listOf(
                "\n\nЕсли задело — это комплимент в кривом мире Лунтика.",
                "\n\nШутка. Или нет. Разберёшься.",
                "\n\nНе благодари, звезда локального чата.",
                "\n\n(Это развлечение. Не терапия.)"
            )
            "${openers.random()}\n\n$raw${closers.random()}"
        }
    }

    private fun extractSnippet(text: String, tokens: List<String>): String {
        val sentences = text.split(Regex("[.!?\n]+")).map { it.trim() }.filter { it.length > 15 }
        if (sentences.isEmpty()) return text.take(300)
        val best = sentences.maxByOrNull { s -> tokens.count { s.lowercase().contains(it) } } ?: sentences.first()
        return if (best.length > 400) best.take(400) + "…" else best
    }

    fun like(msgId: String) {
        messages = messages.map { if (it.id == msgId) it.copy(feedback = "like") else it }
        messages = messages + ChatMessage(role = "system", content = "👍 Учту.")
        persist()
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
            messages = messages + ChatMessage(role = "system", content = "Понял: «$comment».")
        } else {
            messages = messages + ChatMessage(role = "system", content = "Ок, не зашло.")
        }
        showFeedbackDialog = false
        persist()
    }
}

@Composable
fun LuntikTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent, background = Bg, surface = SurfaceC,
            onPrimary = Bg, onBackground = TextMain, onSurface = TextMain
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
            val lu = lastUser.lowercase()
            val isWeb = (lu.contains("http://") || lu.contains("https://")) &&
                (lu.contains("открой") || lu.contains("открыть") || lu.contains("скачай") || lu.contains("прочитай"))
            if (!isWeb) vm.finishThinking(lastUser)
        }
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(
            Modifier.fillMaxWidth().background(SurfaceC).statusBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🌱 LuntikAi", color = Accent, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.weight(1f))
            Text("${vm.personality.emoji} · ${if (vm.isTrained) "TF‑IDF" else "не обучен"}", color = TextMuted, fontSize = 11.sp)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(vm.messages, key = { it.id }) { msg ->
                MessageBubble(msg, { vm.like(msg.id) }, { vm.openDislike(msg.id) })
            }
            if (vm.isThinking) {
                item { Text("💭 Агент думает…", color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(8.dp)) }
            }
        }

        Row(
            Modifier.fillMaxWidth().background(SurfaceC).padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SmallBtn("Знания", Icons.Default.MenuBook) { vm.showKnowledgeList = true }
            SmallBtn("Файлы", Icons.Default.Folder) { vm.showFilesList = true }
            SmallBtn("Личность", Icons.Default.Face) { vm.showPersonalityDialog = true }
            SmallBtn("↓", Icons.Default.Download) { vm.exportText = vm.buildExport(); vm.showExportDialog = true }
            SmallBtn("↑", Icons.Default.Upload) { vm.importText = ""; vm.showImportDialog = true }
            Button(
                onClick = { vm.train() },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg)
            ) { Text("Обучить", fontSize = 11.sp) }
        }

        Row(
            Modifier.fillMaxWidth().background(SurfaceC).navigationBarsPadding().padding(10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = vm.input,
                onValueChange = { vm.input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Сообщение / файл / https…", color = TextMuted, fontSize = 12.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent, unfocusedBorderColor = Surface2,
                    focusedTextColor = TextMain, unfocusedTextColor = TextMain, cursorColor = Accent
                ),
                maxLines = 4
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { vm.send() }, colors = IconButtonDefaults.iconButtonColors(containerColor = Accent)) {
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
                    if (vm.sources.isEmpty()) Text("Пусто", color = TextMuted)
                    else vm.sources.forEach { src ->
                        Card(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                vm.editingSource = src; vm.newSourceName = src.name; vm.newSourceText = src.text
                                vm.showEditDialog = true; vm.showKnowledgeList = false
                            },
                            colors = CardDefaults.cardColors(containerColor = Surface2)
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text(src.name, fontWeight = FontWeight.SemiBold, color = TextMain)
                                Text(src.text.take(80) + if (src.text.length > 80) "…" else "", color = TextMuted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        vm.showKnowledgeList = false; vm.newSourceName = ""; vm.newSourceText = ""; vm.showAddDialog = true
                    }) { Text("Добавить") }
                    TextButton(onClick = { vm.showKnowledgeList = false }) { Text("Закрыть") }
                }
            }
        )
    }

    if (vm.showAddDialog) {
        SourceEditDialog("Новый источник", vm.newSourceName, vm.newSourceText,
            { vm.newSourceName = it }, { vm.newSourceText = it },
            {
                vm.addSource(vm.newSourceName, vm.newSourceText)
                vm.newSourceName = ""; vm.newSourceText = ""; vm.showAddDialog = false
            },
            { vm.showAddDialog = false })
    }

    if (vm.showEditDialog && vm.editingSource != null) {
        val src = vm.editingSource!!
        SourceEditDialog("Редактировать", vm.newSourceName, vm.newSourceText,
            { vm.newSourceName = it }, { vm.newSourceText = it },
            {
                vm.updateSource(src.id, vm.newSourceName, vm.newSourceText)
                vm.showEditDialog = false; vm.editingSource = null
            },
            { vm.showEditDialog = false; vm.editingSource = null },
            {
                vm.deleteSource(src.id); vm.showEditDialog = false; vm.editingSource = null
            })
    }

    if (vm.showFilesList) {
        AlertDialog(
            onDismissRequest = { vm.showFilesList = false },
            title = { Text("Файлы (${vm.chatFiles.size})") },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    if (vm.chatFiles.isEmpty()) Text("Пусто", color = TextMuted)
                    else vm.chatFiles.forEach { f ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Surface2)) {
                            Column(Modifier.padding(10.dp)) {
                                Text("📄 ${f.name}", fontWeight = FontWeight.SemiBold, color = TextMain)
                                Row {
                                    TextButton(onClick = { vm.viewingFile = f; vm.showFilesList = false }) { Text("Открыть") }
                                    TextButton(onClick = {
                                        vm.editingFile = f; vm.newFileName = f.name; vm.newFileContent = f.content
                                        vm.showEditFileDialog = true; vm.showFilesList = false
                                    }) { Text("Изменить") }
                                    TextButton(onClick = { vm.deleteFile(f.id) }) { Text("Удалить", color = Danger) }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        vm.showFilesList = false; vm.newFileName = ""; vm.newFileContent = ""; vm.showCreateFileDialog = true
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
                    OutlinedTextField(vm.newFileName, { vm.newFileName = it }, label = { Text("Имя") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(vm.newFileContent, { vm.newFileContent = it }, label = { Text("Текст") }, modifier = Modifier.fillMaxWidth().height(140.dp), maxLines = 8)
                }
            },
            confirmButton = {
                Button(onClick = { vm.createFile(vm.newFileName, vm.newFileContent); vm.showCreateFileDialog = false }) { Text("Создать") }
            },
            dismissButton = { TextButton(onClick = { vm.showCreateFileDialog = false }) { Text("Отмена") } }
        )
    }

    if (vm.showEditFileDialog && vm.editingFile != null) {
        val f = vm.editingFile!!
        AlertDialog(
            onDismissRequest = { vm.showEditFileDialog = false; vm.editingFile = null },
            title = { Text("Изменить файл") },
            text = {
                Column {
                    OutlinedTextField(vm.newFileName, { vm.newFileName = it }, label = { Text("Имя") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(vm.newFileContent, { vm.newFileContent = it }, label = { Text("Текст") }, modifier = Modifier.fillMaxWidth().height(160.dp), maxLines = 10)
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.updateFile(f.id, vm.newFileName, vm.newFileContent)
                    vm.showEditFileDialog = false; vm.editingFile = null
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { vm.showEditFileDialog = false; vm.editingFile = null }) { Text("Отмена") } }
        )
    }

    vm.viewingFile?.let { f ->
        AlertDialog(
            onDismissRequest = { vm.viewingFile = null },
            title = { Text("📄 ${f.name}") },
            text = {
                Text(f.content.ifBlank { "(пусто)" }, color = TextMain, modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(max = 360.dp))
            },
            confirmButton = { TextButton(onClick = { vm.viewingFile = null }) { Text("Закрыть") } }
        )
    }

    if (vm.showPersonalityDialog) {
        AlertDialog(
            onDismissRequest = { vm.showPersonalityDialog = false },
            title = { Text("Личность") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Personality.entries.forEach { p ->
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.requestPersonality(p) }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${p.emoji}  ${p.title}", color = TextMain, fontSize = 15.sp)
                            if (p == Personality.HUMORIST) {
                                Spacer(Modifier.width(8.dp))
                                Text("18+", color = Danger, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            if (vm.personality == p) {
                                Spacer(Modifier.weight(1f))
                                Text("✓", color = Accent)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.showPersonalityDialog = false }) { Text("Закрыть") } }
        )
    }

    // Предупреждение Юмориста
    if (vm.showHumorWarning) {
        AlertDialog(
            onDismissRequest = { vm.showHumorWarning = false },
            title = { Text("😂 Юморист — предупреждение", color = Danger) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 420.dp)) {
                    Text(
                        "Хотите увидеть юмор Лунтика (создатель)? Тогда включите эту личность.\n\n" +
                            "⚠ ПРЕДУПРЕЖДЕНИЕ\n\n" +
                            "Для людей с крайне плохой самооценкой, или если у вас плохое психическое состояние — " +
                            "крайне не рекомендуется.\n\n" +
                            "При этой личности ИИ может не специально задеть вас.\n\n" +
                            "Данная личность может шутить над вами и оскорблять — не чтобы унизить, " +
                            "а исключительно в развлекательных целях.\n\n" +
                            "Нажав «Продолжить», вы соглашаетесь с написанным выше.",
                        color = TextMain,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { vm.confirmHumorist() },
                    colors = ButtonDefaults.buttonColors(containerColor = Danger, contentColor = TextMain)
                ) { Text("Продолжить") }
            },
            dismissButton = {
                TextButton(onClick = { vm.showHumorWarning = false }) { Text("Отмена") }
            }
        )
    }

    if (vm.showExportDialog) {
        AlertDialog(
            onDismissRequest = { vm.showExportDialog = false },
            title = { Text("Экспорт") },
            text = {
                OutlinedTextField(vm.exportText, {}, readOnly = true, modifier = Modifier.fillMaxWidth().height(220.dp))
            },
            confirmButton = { TextButton(onClick = { vm.showExportDialog = false }) { Text("Закрыть") } }
        )
    }

    if (vm.showImportDialog) {
        AlertDialog(
            onDismissRequest = { vm.showImportDialog = false },
            title = { Text("Импорт") },
            text = {
                OutlinedTextField(vm.importText, { vm.importText = it }, placeholder = { Text("JSON") }, modifier = Modifier.fillMaxWidth().height(220.dp))
            },
            confirmButton = {
                Button(onClick = { vm.applyImport(vm.importText); vm.showImportDialog = false }) { Text("Импорт") }
            },
            dismissButton = { TextButton(onClick = { vm.showImportDialog = false }) { Text("Отмена") } }
        )
    }

    if (vm.showFeedbackDialog) {
        AlertDialog(
            onDismissRequest = { vm.showFeedbackDialog = false },
            title = { Text("Что не так?") },
            text = {
                OutlinedTextField(vm.feedbackComment, { vm.feedbackComment = it }, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = { Button(onClick = { vm.submitDislike() }) { Text("Отправить") } },
            dismissButton = { TextButton(onClick = { vm.showFeedbackDialog = false }) { Text("Отмена") } }
        )
    }
}

@Composable
fun SmallBtn(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)) {
        Icon(icon, null, Modifier.size(14.dp))
        Spacer(Modifier.width(3.dp))
        Text(label, fontSize = 10.sp)
    }
}

@Composable
fun SourceEditDialog(
    title: String, name: String, text: String,
    onName: (String) -> Unit, onText: (String) -> Unit,
    onSave: () -> Unit, onCancel: () -> Unit, onDelete: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(name, onName, label = { Text("Название") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(text, onText, label = { Text("Текст") }, modifier = Modifier.fillMaxWidth().height(180.dp), maxLines = 12)
            }
        },
        confirmButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Удалить", color = Danger) }
                Button(onClick = onSave) { Text("Сохранить") }
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Отмена") } }
    )
}

@Composable
fun MessageBubble(msg: ChatMessage, onLike: () -> Unit, onDislike: () -> Unit) {
    val isUser = msg.role == "user"
    val isSystem = msg.role == "system"
    Column(
        Modifier.fillMaxWidth(),
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
                    Text(if (isUser) "Ты" else "LuntikAi", color = TextMuted, fontSize = 10.sp)
                    Spacer(Modifier.height(3.dp))
                }
                if (!msg.actions.isNullOrEmpty()) {
                    Surface(color = ActionBg, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Column(Modifier.padding(8.dp)) {
                            Text("⚡ Действия", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            msg.actions.forEach { Text("• $it", color = TextMuted, fontSize = 11.sp) }
                        }
                    }
                }
                if (!msg.thinking.isNullOrEmpty()) {
                    Surface(color = Color.Black.copy(0.25f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Column(Modifier.padding(8.dp)) {
                            Text("💭 Раздумье", color = TextMuted, fontSize = 10.sp)
                            msg.thinking.forEach { (t, p) ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(t, color = TextMain, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                    Text("$p%", color = if (p >= 45) Accent else if (p >= 25) Warn else Danger, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
                Text(msg.content, color = if (isSystem) TextMuted else TextMain, fontSize = if (isSystem) 12.sp else 14.sp, lineHeight = 19.sp)
                if (msg.confidence != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Уверенность: ${msg.confidence}%",
                        color = if (msg.confidence >= 55) Accent else if (msg.confidence >= 25) Warn else Danger,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (msg.role == "ai") {
                    Spacer(Modifier.height(8.dp))
                    Text("Luntik-ai это ии и он может ошибаться.", color = TextMuted.copy(0.75f), fontSize = 10.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = onLike, contentPadding = PaddingValues(2.dp)) {
                            Icon(Icons.Default.ThumbUp, null, Modifier.size(14.dp), tint = if (msg.feedback == "like") Accent else TextMuted)
                            Spacer(Modifier.width(3.dp))
                            Text("Лайк", color = if (msg.feedback == "like") Accent else TextMuted, fontSize = 11.sp)
                        }
                        TextButton(onClick = onDislike, contentPadding = PaddingValues(2.dp)) {
                            Icon(Icons.Default.ThumbDown, null, Modifier.size(14.dp), tint = if (msg.feedback == "dislike") Danger else TextMuted)
                            Spacer(Modifier.width(3.dp))
                            Text("Дизлайк", color = if (msg.feedback == "dislike") Danger else TextMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
