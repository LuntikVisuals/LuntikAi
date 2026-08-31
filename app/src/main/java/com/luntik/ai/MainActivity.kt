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
import androidx.lifecycle.viewmodel.compose.viewModel
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.math.ln

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = SurfaceC, onPrimary = Bg, onBackground = TextMain, onSurface = TextMain)) {
                Surface(Modifier.fillMaxSize(), color = Bg) { LuntikApp() }
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
    NONE("Обычный ИИ", "🤖"), HORROR("Хоррор", "👻"), EGOIST("Эгоист", "👑"),
    VILLAIN("Злодей", "😈"), KIND("Добряк", "😇"), CUTE("Милый", "🥺"), HUMORIST("Юморист", "😂")
}

data class ChatMessage(val id: String = UUID.randomUUID().toString(), val role: String, val content: String, val confidence: Int? = null, val thinking: List<Pair<String, Int>>? = null, val actions: List<String>? = null, val feedback: String? = null)
data class KnowledgeSource(val id: String = UUID.randomUUID().toString(), val name: String, val text: String)
data class ChatFile(val id: String = UUID.randomUUID().toString(), val name: String, val content: String)

class TfIdfIndex {
    private var df: Map<String, Int> = emptyMap()
    private var nDocs = 1
    fun build(sources: List<KnowledgeSource>) {
        nDocs = sources.size.coerceAtLeast(1)
        val m = mutableMapOf<String, Int>()
        sources.forEach { s -> tokenize(s.text).toSet().forEach { t -> m[t] = (m[t] ?: 0) + 1 } }
        df = m
    }
    fun score(query: String, text: String): Double {
        val q = tokenize(query); if (q.isEmpty()) return 0.0
        val d = tokenize(text); if (d.isEmpty()) return 0.0
        val tf = d.groupingBy { it }.eachCount()
        return q.toSet().sumOf { t -> ((tf[t] ?: 0).toDouble() / d.size) * (ln((nDocs + 1.0) / ((df[t] ?: 0) + 1.0)) + 1.0) }
    }
    companion object { fun tokenize(s: String) = s.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 1 } }
}

class LuntikViewModel(app: Application) : AndroidViewModel(app) {
    private val store = File(getApplication<Application>().filesDir, "luntik_state.json")
    var messages by mutableStateOf(listOf(ChatMessage(role = "system", content = "LuntikAi v0.3 — знания, TF‑IDF, файлы, личности (Юморист с предупреждением).")))
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
    var showPersonalityDialog by mutableStateOf(false)
    var showHumorWarning by mutableStateOf(false)
    var showFeedbackDialog by mutableStateOf(false)
    var feedbackMsgId by mutableStateOf<String?>(null)
    var feedbackComment by mutableStateOf("")
    private val index = TfIdfIndex()
    private val feedbackNotes = mutableListOf<String>()

    init { load() }

    private fun persist() {
        try {
            val o = JSONObject().put("personality", personality.name).put("isTrained", isTrained)
            val sa = JSONArray(); sources.forEach { sa.put(JSONObject().put("id", it.id).put("name", it.name).put("text", it.text)) }
            o.put("sources", sa)
            val fa = JSONArray(); chatFiles.forEach { fa.put(JSONObject().put("id", it.id).put("name", it.name).put("content", it.content)) }
            o.put("files", fa)
            val ma = JSONArray(); messages.takeLast(60).forEach { ma.put(JSONObject().put("id", it.id).put("role", it.role).put("content", it.content)) }
            o.put("messages", ma)
            store.writeText(o.toString())
        } catch (_: Exception) {}
    }

    private fun load() {
        try {
            if (!store.exists()) return
            val o = JSONObject(store.readText())
            personality = try { Personality.valueOf(o.optString("personality", "NONE")) } catch (_: Exception) { Personality.NONE }
            isTrained = o.optBoolean("isTrained", false)
            o.optJSONArray("sources")?.let { arr ->
                sources = (0 until arr.length()).map { i -> val s = arr.getJSONObject(i); KnowledgeSource(s.optString("id", UUID.randomUUID().toString()), s.optString("name"), s.optString("text")) }
            }
            o.optJSONArray("files")?.let { arr ->
                chatFiles = (0 until arr.length()).map { i -> val f = arr.getJSONObject(i); ChatFile(f.optString("id", UUID.randomUUID().toString()), f.optString("name"), f.optString("content")) }
            }
            o.optJSONArray("messages")?.let { arr ->
                if (arr.length() > 0) messages = (0 until arr.length()).map { i -> val m = arr.getJSONObject(i); ChatMessage(m.optString("id", UUID.randomUUID().toString()), m.optString("role"), m.optString("content")) }
            }
            if (isTrained && sources.isNotEmpty()) index.build(sources)
        } catch (_: Exception) {}
    }

    fun addSource(name: String, text: String) {
        if (text.isBlank()) return
        sources = sources + KnowledgeSource(name = name.ifBlank { "Источник ${sources.size + 1}" }, text = text)
        isTrained = false
        messages = messages + ChatMessage(role = "system", content = "Источник добавлен. Нажми Обучить.")
        persist()
    }
    fun updateSource(id: String, name: String, text: String) {
        sources = sources.map { if (it.id == id) it.copy(name = name.ifBlank { it.name }, text = text) else it }
        isTrained = false; messages = messages + ChatMessage(role = "system", content = "Обновлено. Переобучи."); persist()
    }
    fun deleteSource(id: String) {
        sources = sources.filter { it.id != id }; isTrained = false; persist()
    }
    fun train() {
        if (sources.isEmpty()) { messages = messages + ChatMessage(role = "system", content = "Нет текстов."); return }
        index.build(sources); isTrained = true
        messages = messages + ChatMessage(role = "system", content = "TF‑IDF готов. Источников: ${sources.size}"); persist()
    }
    fun requestPersonality(p: Personality) {
        if (p == Personality.HUMORIST && personality != Personality.HUMORIST) {
            showPersonalityDialog = false; showHumorWarning = true
        } else { selectPersonality(p); showPersonalityDialog = false }
    }
    fun confirmHumorist() { showHumorWarning = false; selectPersonality(Personality.HUMORIST) }
    fun selectPersonality(p: Personality) {
        personality = p; messages = messages + ChatMessage(role = "system", content = "Личность: ${p.emoji} ${p.title}"); persist()
    }

    fun send() {
        val q = input.trim(); if (q.isEmpty() || isThinking) return
        input = ""; messages = messages + ChatMessage(role = "user", content = q); persist()
        val lower = q.lowercase()
        when {
            lower in listOf("привет", "хай") -> { pushAi(style("Привет! Чем займёмся?"), 90, listOf("Приветствие")); return }
            lower.contains("анализ") && lower.contains("диалог") -> { pushAi(style("Сообщений: ${messages.count { it.role == "user" }}, источников: ${sources.size}, личность: ${personality.title}"), 80, listOf("Анализ")); return }
        }
        if (!isTrained || sources.isEmpty()) { pushAi(style("Не обучен. Добавь знания → Обучить."), 5, listOf("Пусто")); return }
        isThinking = true
    }

    suspend fun finishThinking(question: String) {
        kotlinx.coroutines.delay(350)
        val scored = sources.map { it to index.score(question, it.text) }.sortedByDescending { it.second }
        val top = scored.take(3).filter { it.second > 0.0001 }
        val thinking = if (top.isEmpty()) listOf("Нет совпадений" to 70, "Мало данных" to 30)
        else {
            val total = top.sumOf { it.second }.coerceAtLeast(0.0001)
            top.map { (s, sc) -> (s.text.take(60).replace("\n", " ") + "…") to ((sc / total) * 100).toInt().coerceIn(5, 90) }
        }
        val best = top.firstOrNull()
        val conf = if (best == null) 12 else (20 + (best.second * 40).toInt()).coerceIn(10, 92)
        var ans = if (best == null) "Мало данных. Добавь тексты."
        else {
            val fact = best.first.text.split(Regex("[.!?\n]+")).map { it.trim() }.filter { it.length > 15 }
                .maxByOrNull { s -> TfIdfIndex.tokenize(question).count { s.lowercase().contains(it) } } ?: best.first.text.take(300)
            "По твоему вопросу: ${fact.trim().trimEnd('.', '!', '?')}."
        }
        ans = style(ans)
        pushAi(ans, conf, listOf("TF‑IDF", "Перефраз", "Личность: ${personality.title}"), thinking)
        isThinking = false
    }

    private fun pushAi(c: String, conf: Int, actions: List<String>, thinking: List<Pair<String, Int>>? = null) {
        messages = messages + ChatMessage(role = "ai", content = c, confidence = conf, actions = actions, thinking = thinking); persist()
    }

    private fun style(raw: String) = when (personality) {
        Personality.NONE -> raw
        Personality.HORROR -> "Шёпот…\n\n$raw"
        Personality.EGOIST -> "Очевидно:\n\n$raw"
        Personality.VILLAIN -> "Ха…\n\n$raw"
        Personality.KIND -> "С радостью 💛\n\n$raw"
        Personality.CUTE -> "Хехе~\n\n$raw 🥺"
        Personality.HUMORIST -> listOf("Без обид, чисто юмор:", "Лунтик одобрил:", "Сейчас будет смешно (или больно):").random() + "\n\n$raw\n\n(Это развлечение.)"
    }

    fun like(id: String) { messages = messages.map { if (it.id == id) it.copy(feedback = "like") else it }; persist() }
    fun openDislike(id: String) { feedbackMsgId = id; feedbackComment = ""; showFeedbackDialog = true }
    fun submitDislike() {
        val id = feedbackMsgId ?: return
        messages = messages.map { if (it.id == id) it.copy(feedback = "dislike") else it }
        if (feedbackComment.isNotBlank()) feedbackNotes.add(feedbackComment.lowercase())
        showFeedbackDialog = false; persist()
    }
}

@Composable
fun LuntikApp(vm: LuntikViewModel = viewModel()) {
    val listState = rememberLazyListState()
    LaunchedEffect(vm.messages.size) { if (vm.messages.isNotEmpty()) listState.animateScrollToItem(vm.messages.lastIndex) }
    LaunchedEffect(vm.isThinking) { if (vm.isThinking) vm.finishThinking(vm.messages.lastOrNull { it.role == "user" }?.content ?: "") }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(Modifier.fillMaxWidth().background(SurfaceC).statusBarsPadding().padding(12.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🌱 LuntikAi", color = Accent, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.weight(1f))
            Text("${vm.personality.emoji} · ${if (vm.isTrained) "TF‑IDF" else "не обучен"}", color = TextMuted, fontSize = 11.sp)
        }
        LazyColumn(listState, Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(vm.messages, key = { it.id }) { msg -> MsgBubble(msg, { vm.like(msg.id) }, { vm.openDislike(msg.id) }) }
            if (vm.isThinking) item { Text("💭 Думаю…", color = TextMuted, fontSize = 13.sp) }
        }
        Row(Modifier.fillMaxWidth().background(SurfaceC).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = { vm.showKnowledgeList = true }, contentPadding = PaddingValues(8.dp, 6.dp)) { Text("Знания", fontSize = 11.sp) }
            OutlinedButton(onClick = { vm.showPersonalityDialog = true }, contentPadding = PaddingValues(8.dp, 6.dp)) { Text("Личность", fontSize = 11.sp) }
            Button(onClick = { vm.train() }, contentPadding = PaddingValues(10.dp, 6.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg)) { Text("Обучить", fontSize = 11.sp) }
        }
        Row(Modifier.fillMaxWidth().background(SurfaceC).navigationBarsPadding().padding(10.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(vm.input, { vm.input = it }, Modifier.weight(1f), placeholder = { Text("Сообщение…", color = TextMuted) },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = Surface2, focusedTextColor = TextMain, unfocusedTextColor = TextMain, cursorColor = Accent), maxLines = 4)
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { vm.send() }, colors = IconButtonDefaults.iconButtonColors(containerColor = Accent)) {
                Icon(Icons.AutoMirrored.Filled.Send, null, tint = Bg)
            }
        }
    }

    if (vm.showKnowledgeList) {
        AlertDialog(onDismissRequest = { vm.showKnowledgeList = false }, title = { Text("Знания (${vm.sources.size})") }, text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                if (vm.sources.isEmpty()) Text("Пусто", color = TextMuted)
                vm.sources.forEach { s ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                        vm.editingSource = s; vm.newSourceName = s.name; vm.newSourceText = s.text; vm.showEditDialog = true; vm.showKnowledgeList = false
                    }, colors = CardDefaults.cardColors(containerColor = Surface2)) {
                        Column(Modifier.padding(10.dp)) {
                            Text(s.name, fontWeight = FontWeight.SemiBold, color = TextMain)
                            Text(s.text.take(80), color = TextMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }, confirmButton = {
            Row {
                TextButton(onClick = { vm.showKnowledgeList = false; vm.newSourceName = ""; vm.newSourceText = ""; vm.showAddDialog = true }) { Text("Добавить") }
                TextButton(onClick = { vm.showKnowledgeList = false }) { Text("Закрыть") }
            }
        })
    }

    if (vm.showAddDialog) {
        AlertDialog(onDismissRequest = { vm.showAddDialog = false }, title = { Text("Новый источник") }, text = {
            Column {
                OutlinedTextField(vm.newSourceName, { vm.newSourceName = it }, label = { Text("Название") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(vm.newSourceText, { vm.newSourceText = it }, label = { Text("Текст") }, modifier = Modifier.fillMaxWidth().height(160.dp), maxLines = 10)
            }
        }, confirmButton = {
            Button(onClick = { vm.addSource(vm.newSourceName, vm.newSourceText); vm.showAddDialog = false }) { Text("Добавить") }
        }, dismissButton = { TextButton(onClick = { vm.showAddDialog = false }) { Text("Отмена") } })
    }

    if (vm.showEditDialog && vm.editingSource != null) {
        val s = vm.editingSource!!
        AlertDialog(onDismissRequest = { vm.showEditDialog = false }, title = { Text("Редактировать") }, text = {
            Column {
                OutlinedTextField(vm.newSourceName, { vm.newSourceName = it }, label = { Text("Название") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(vm.newSourceText, { vm.newSourceText = it }, label = { Text("Текст") }, modifier = Modifier.fillMaxWidth().height(160.dp), maxLines = 10)
            }
        }, confirmButton = {
            Row {
                TextButton(onClick = { vm.deleteSource(s.id); vm.showEditDialog = false }) { Text("Удалить", color = Danger) }
                Button(onClick = { vm.updateSource(s.id, vm.newSourceName, vm.newSourceText); vm.showEditDialog = false }) { Text("Сохранить") }
            }
        }, dismissButton = { TextButton(onClick = { vm.showEditDialog = false }) { Text("Отмена") } })
    }

    if (vm.showPersonalityDialog) {
        AlertDialog(onDismissRequest = { vm.showPersonalityDialog = false }, title = { Text("Личность") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Personality.entries.forEach { p ->
                    Row(Modifier.fillMaxWidth().clickable { vm.requestPersonality(p) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${p.emoji}  ${p.title}", color = TextMain, fontSize = 15.sp)
                        if (p == Personality.HUMORIST) { Spacer(Modifier.width(8.dp)); Text("18+", color = Danger, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        if (vm.personality == p) { Spacer(Modifier.weight(1f)); Text("✓", color = Accent) }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { vm.showPersonalityDialog = false }) { Text("Закрыть") } })
    }

    if (vm.showHumorWarning) {
        HumorWarningDialog(onConfirm = { vm.confirmHumorist() }, onDismiss = { vm.showHumorWarning = false })
    }

    if (vm.showFeedbackDialog) {
        AlertDialog(onDismissRequest = { vm.showFeedbackDialog = false }, title = { Text("Что не так?") }, text = {
            OutlinedTextField(vm.feedbackComment, { vm.feedbackComment = it }, modifier = Modifier.fillMaxWidth())
        }, confirmButton = { Button(onClick = { vm.submitDislike() }) { Text("Ок") } }, dismissButton = { TextButton(onClick = { vm.showFeedbackDialog = false }) { Text("Отмена") } })
    }
}

@Composable
fun MsgBubble(msg: ChatMessage, onLike: () -> Unit, onDislike: () -> Unit) {
    val isUser = msg.role == "user"; val isSystem = msg.role == "system"
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (isUser) Alignment.End else if (isSystem) Alignment.CenterHorizontally else Alignment.Start) {
        Surface(shape = RoundedCornerShape(14.dp), color = if (isUser) UserBubble else if (isSystem) Color.Transparent else AiBubble, modifier = Modifier.widthIn(max = 340.dp)) {
            Column(Modifier.padding(11.dp)) {
                if (!isSystem) { Text(if (isUser) "Ты" else "LuntikAi", color = TextMuted, fontSize = 10.sp); Spacer(Modifier.height(3.dp)) }
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
                    Text("Уверенность: ${msg.confidence}%", color = if (msg.confidence >= 55) Accent else if (msg.confidence >= 25) Warn else Danger, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                if (msg.role == "ai") {
                    Spacer(Modifier.height(8.dp))
                    Text("Luntik-ai это ии и он может ошибаться.", color = TextMuted.copy(0.75f), fontSize = 10.sp)
                    Spacer(Modifier.height(6.dp))
                    Row {
                        TextButton(onClick = onLike, contentPadding = PaddingValues(2.dp)) {
                            Icon(Icons.Default.ThumbUp, null, Modifier.size(14.dp), tint = if (msg.feedback == "like") Accent else TextMuted)
                            Text(" Лайк", color = if (msg.feedback == "like") Accent else TextMuted, fontSize = 11.sp)
                        }
                        TextButton(onClick = onDislike, contentPadding = PaddingValues(2.dp)) {
                            Icon(Icons.Default.ThumbDown, null, Modifier.size(14.dp), tint = if (msg.feedback == "dislike") Danger else TextMuted)
                            Text(" Дизлайк", color = if (msg.feedback == "dislike") Danger else TextMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
