package com.luntik.ai

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class LuntikViewModel(app: Application) : AndroidViewModel(app) {
    private val store = File(getApplication<Application>().filesDir, "luntik_state.json")
    private val index = TfIdfIndex()
    private val feedbackNotes = mutableListOf<String>()

    var sessions by mutableStateOf(listOf(ChatSession(title = "Новый чат")))
        private set
    var activeSessionId by mutableStateOf(sessions.first().id)
        private set

    var messages by mutableStateOf(
        listOf(
            ChatMessage(
                role = "system",
                content = "LuntikAi v0.7\n\nЖивой диалог. Пиши как человеку. Команды: /create skill имя | описание"
            )
        )
    )
        private set

    var sources by mutableStateOf(listOf<KnowledgeSource>())
        private set
    var sandbox by mutableStateOf(listOf<SandboxFile>())
        private set
    var userSkills by mutableStateOf(listOf<UserSkill>())
        private set
    var isTrained by mutableStateOf(false)
        private set
    var isThinking by mutableStateOf(false)
        private set
    var personality by mutableStateOf(Personality.NONE)
    var input by mutableStateOf("")
    var theme by mutableStateOf(ThemePrefs())
        private set

    var showDrawer by mutableStateOf(false)
    var showKnowledgeList by mutableStateOf(false)
    var showSandbox by mutableStateOf(false)
    var viewingSandbox by mutableStateOf<SandboxFile?>(null)
    var showAddDialog by mutableStateOf(false)
    var showEditDialog by mutableStateOf(false)
    var editingSource by mutableStateOf<KnowledgeSource?>(null)
    var newSourceName by mutableStateOf("")
    var newSourceText by mutableStateOf("")
    var showPersonalityDialog by mutableStateOf(false)
    var showHumorWarning by mutableStateOf(false)
    var showFeedbackDialog by mutableStateOf(false)
    var feedbackMsgId by mutableStateOf<String?>(null)
    var feedbackComment by mutableStateOf("")
    var showProfile by mutableStateOf(false)

    fun onOcrText(text: String) {
        if (text.isBlank()) {
            messages = messages + ChatMessage(role = "system", content = "На фото текст не найден.")
            return
        }
        addSource("OCR ${System.currentTimeMillis() % 100000}", text)
        messages = messages + ChatMessage(role = "system", content = "Текст с фото добавлен (${text.length} символов). Нажми Обучить.")
        persist()
    }

    fun onSpeechText(text: String) {
        if (text.isNotBlank()) input = text
    }

    private fun allSources(): List<KnowledgeSource> {
        val skillSources = userSkills.map {
            KnowledgeSource(name = "Скилл: ${it.name}", text = "${it.name}. ${it.description}", builtin = true)
        }
        return BuiltinSkills.all() + skillSources + sources.filter { !it.builtin }
    }

    init {
        load()
        applyTheme()
    }

    private fun applyTheme() {
        Accent = Color(theme.accent)
        Bg = Color(theme.background)
        SurfaceC = Color(theme.surface)
        Surface2 = Color(
            red = (Color(theme.surface).red * 1.12f).coerceAtMost(1f),
            green = (Color(theme.surface).green * 1.12f).coerceAtMost(1f),
            blue = (Color(theme.surface).blue * 1.12f).coerceAtMost(1f)
        )
        UserBubble = Color(
            Color(theme.surface).red * 0.9f + 0.1f,
            Color(theme.surface).green * 0.9f + 0.12f,
            Color(theme.surface).blue * 0.95f + 0.2f
        )
        AiBubble = Color(theme.surface)
    }

    fun updateTheme(
        accent: Long? = null,
        background: Long? = null,
        surface: Long? = null,
        liquidGlass: Boolean? = null,
        displayName: String? = null
    ) {
        theme = theme.copy(
            accent = accent ?: theme.accent,
            background = background ?: theme.background,
            surface = surface ?: theme.surface,
            liquidGlass = liquidGlass ?: theme.liquidGlass,
            displayName = displayName ?: theme.displayName
        )
        applyTheme()
        persist()
    }

    private fun persistSessionMessages() {
        sessions = sessions.map {
            if (it.id == activeSessionId) it.copy(messages = messages) else it
        }
    }

    private fun persist() {
        try {
            persistSessionMessages()
            val o = JSONObject()
            o.put("personality", personality.name)
            o.put("isTrained", isTrained)
            o.put("activeSessionId", activeSessionId)
            o.put(
                "theme",
                JSONObject()
                    .put("accent", theme.accent)
                    .put("background", theme.background)
                    .put("surface", theme.surface)
                    .put("liquidGlass", theme.liquidGlass)
                    .put("displayName", theme.displayName)
            )
            val sa = JSONArray()
            sources.forEach { sa.put(JSONObject().put("id", it.id).put("name", it.name).put("text", it.text)) }
            o.put("sources", sa)
            val sba = JSONArray()
            sandbox.forEach {
                sba.put(JSONObject().put("id", it.id).put("name", it.name).put("language", it.language).put("content", it.content))
            }
            o.put("sandbox", sba)
            val sk = JSONArray()
            userSkills.forEach {
                sk.put(JSONObject().put("id", it.id).put("name", it.name).put("description", it.description))
            }
            o.put("skills", sk)
            val sessArr = JSONArray()
            sessions.forEach { s ->
                val ma = JSONArray()
                s.messages.takeLast(40).forEach { m ->
                    ma.put(JSONObject().put("id", m.id).put("role", m.role).put("content", m.content).put("confidence", m.confidence ?: JSONObject.NULL))
                }
                sessArr.put(JSONObject().put("id", s.id).put("title", s.title).put("messages", ma))
            }
            o.put("sessions", sessArr)
            store.writeText(o.toString())
        } catch (_: Exception) {
        }
    }

    private fun load() {
        try {
            if (!store.exists()) return
            val o = JSONObject(store.readText())
            personality = try {
                Personality.valueOf(o.optString("personality", "NONE"))
            } catch (_: Exception) {
                Personality.NONE
            }
            isTrained = o.optBoolean("isTrained", false)
            o.optJSONObject("theme")?.let { t ->
                theme = ThemePrefs(
                    accent = t.optLong("accent", 0xFF6EE7B7),
                    background = t.optLong("background", 0xFF0C0E12),
                    surface = t.optLong("surface", 0xFF161A22),
                    liquidGlass = t.optBoolean("liquidGlass", true),
                    displayName = t.optString("displayName", "Пользователь")
                )
            }
            o.optJSONArray("sources")?.let { arr ->
                sources = (0 until arr.length()).map { i ->
                    val s = arr.getJSONObject(i)
                    KnowledgeSource(s.optString("id", UUID.randomUUID().toString()), s.optString("name"), s.optString("text"))
                }
            }
            o.optJSONArray("sandbox")?.let { arr ->
                sandbox = (0 until arr.length()).map { i ->
                    val f = arr.getJSONObject(i)
                    SandboxFile(f.optString("id", UUID.randomUUID().toString()), f.optString("name"), f.optString("language", "text"), f.optString("content"))
                }
            }
            o.optJSONArray("skills")?.let { arr ->
                userSkills = (0 until arr.length()).map { i ->
                    val s = arr.getJSONObject(i)
                    UserSkill(s.optString("id", UUID.randomUUID().toString()), s.optString("name"), s.optString("description"))
                }
            }
            o.optJSONArray("sessions")?.let { arr ->
                if (arr.length() > 0) {
                    val list = mutableListOf<ChatSession>()
                    for (i in 0 until arr.length()) {
                        val s = arr.getJSONObject(i)
                        val ma = s.optJSONArray("messages")
                        val msgs = if (ma != null) {
                            (0 until ma.length()).map { j ->
                                val m = ma.getJSONObject(j)
                                ChatMessage(
                                    m.optString("id", UUID.randomUUID().toString()),
                                    m.optString("role"),
                                    m.optString("content"),
                                    if (m.isNull("confidence")) null else m.optInt("confidence")
                                )
                            }
                        } else emptyList()
                        list.add(ChatSession(s.optString("id", UUID.randomUUID().toString()), s.optString("title", "Чат"), msgs))
                    }
                    sessions = list
                    activeSessionId = o.optString("activeSessionId", list.first().id)
                    messages = list.find { it.id == activeSessionId }?.messages
                        ?: listOf(ChatMessage(role = "system", content = "Новый чат"))
                }
            }
            if (isTrained) index.build(allSources())
        } catch (_: Exception) {
        }
    }

    fun newChat() {
        persistSessionMessages()
        val s = ChatSession(title = "Чат ${sessions.size + 1}", messages = listOf(ChatMessage(role = "system", content = "Новый чат. Просто пиши.")))
        sessions = listOf(s) + sessions
        activeSessionId = s.id
        messages = s.messages
        persist()
    }

    fun selectChat(id: String) {
        if (id == activeSessionId) {
            showDrawer = false
            return
        }
        persistSessionMessages()
        activeSessionId = id
        messages = sessions.find { it.id == id }?.messages ?: listOf(ChatMessage(role = "system", content = "Пустой чат"))
        showDrawer = false
        persist()
    }

    fun deleteChat(id: String) {
        if (sessions.size <= 1) return
        sessions = sessions.filter { it.id != id }
        if (activeSessionId == id) {
            activeSessionId = sessions.first().id
            messages = sessions.first().messages
        }
        persist()
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
        isTrained = false
        messages = messages + ChatMessage(role = "system", content = "Обновлено. Переобучи.")
        persist()
    }

    fun deleteSource(id: String) {
        sources = sources.filter { it.id != id }
        isTrained = false
        persist()
    }

    fun train() {
        index.build(allSources())
        isTrained = true
        messages = messages + ChatMessage(role = "system", content = "Обучено. Скиллы: ${userSkills.size}, источников: ${sources.size}.")
        persist()
    }

    fun createSkill(name: String, description: String) {
        val n = name.ifBlank { "skill_${userSkills.size + 1}" }
        val d = description.ifBlank { "Описание не задано" }
        userSkills = userSkills + UserSkill(name = n, description = d)
        index.build(allSources())
        isTrained = true
        messages = messages + ChatMessage(role = "system", content = "Скилл «$n» создан.\n$d")
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
        messages = messages + ChatMessage(role = "system", content = "Личность: ${p.title}")
        persist()
    }

    fun send() {
        val q = input.trim()
        if (q.isEmpty() || isThinking) return
        input = ""
        messages = messages + ChatMessage(role = "user", content = q)
        if (messages.count { it.role == "user" } == 1) {
            sessions = sessions.map { if (it.id == activeSessionId) it.copy(title = q.take(28)) else it }
        }
        persist()
        val lower = q.lowercase().trim()

        if (lower.startsWith("/create skill") || lower.startsWith("/createskill")) {
            val body = q.removePrefix("/create skill").removePrefix("/createskill").removePrefix("/CREATE SKILL").trim()
            val name: String
            val desc: String
            when {
                "|" in body -> {
                    name = body.substringBefore("|").trim()
                    desc = body.substringAfter("|").trim()
                }
                ":" in body -> {
                    name = body.substringBefore(":").trim()
                    desc = body.substringAfter(":").trim()
                }
                else -> {
                    name = body.take(40).ifBlank { "custom" }
                    desc = body
                }
            }
            createSkill(name, desc)
            return
        }

        when {
            lower.contains("вежливее") -> {
                selectPersonality(Personality.KIND)
                pushAi(style("Хорошо, буду мягче."), 88, listOf("Смена тона"), suggestions = defaultSuggestions())
                return
            }
            lower.contains("песочниц") || lower == "sandbox" -> {
                val list = if (sandbox.isEmpty()) "Песочница пуста. Пример: создай файл hello.py : print(\"hi\")"
                else sandbox.joinToString("\n") { "- ${it.name} (${it.language})" }
                pushAi(style(list), 88, listOf("Песочница"), suggestions = listOf("создай файл hello.py : print(\"hi\")"))
                return
            }
            lower.startsWith("создай файл") || lower.startsWith("создать файл") || lower.contains("сделай файл") -> {
                val nameRegex = Regex("файл[ае]?\\s+(\\S+)", RegexOption.IGNORE_CASE)
                val name = nameRegex.find(q)?.groupValues?.getOrNull(1)?.trim('«', '»', '"') ?: "snippet.txt"
                val code = if (":" in q) q.substringAfter(":").trim() else q.substringAfter(name).trim().ifBlank { "// empty" }
                val lang = when {
                    name.endsWith(".py") || code.contains("print(") -> "python"
                    name.endsWith(".js") || code.contains("console.log") -> "javascript"
                    name.endsWith(".cpp") || code.contains("iostream") -> "cpp"
                    else -> "text"
                }
                saveSandbox(name, lang, code)
                pushAi(style("Создал «$name» ($lang). Можно: запусти $name"), 90, listOf("Песочница"), suggestions = listOf("запусти $name"))
                return
            }
            lower.startsWith("запусти ") || lower.contains("запусти файл") -> {
                val name = q.substringAfter("запусти").substringAfter("файл").trim().trim('«', '»', '"', '\'')
                val f = sandbox.find { it.name.equals(name, true) || it.name.contains(name, true) }
                if (f == null) pushAi(style("Нет файла «$name»."), 20, listOf("Не найден"), suggestions = defaultSuggestions())
                else runSandboxUi(f)
                return
            }
        }

        if (!isTrained) {
            index.build(allSources())
            isTrained = true
        }
        isThinking = true
        viewModelScope.launch { finishThinking(q) }
    }

    private fun recentContext(): String {
        return messages.filter { it.role == "user" || it.role == "ai" }
            .takeLast(6)
            .joinToString("\n") { m ->
                val who = if (m.role == "user") "Человек" else "Я"
                "$who: ${m.content.take(200)}"
            }
    }

    private fun composeReply(question: String, facts: List<String>, conf: Int): String {
        val q = question.trim()
        val qLower = q.lowercase()
        val ctx = recentContext()
        val factBlock = facts.filter { it.isNotBlank() }.take(3)

        val isGreeting = qLower in listOf("привет", "хай", "здравствуй", "здравствуйте", "ку", "йо")
        val isHowAreYou = listOf("как дела", "как ты", "что делаешь", "как жизнь").any { it in qLower }
        val isWho = listOf("кто ты", "что ты", "ты кто", "представься").any { it in qLower }
        val isThanks = listOf("спасибо", "благодарю", "спс").any { it in qLower }
        val isOpinion = listOf("как думаешь", "твоё мнение", "что думаешь", "как считаешь").any { it in qLower }
        val isContinue = listOf("продолжай", "ещё", "дальше", "расскажи ещё").any { it in qLower }

        val base = when {
            isGreeting -> listOf(
                "Привет. Слушаю тебя — пиши о чём угодно.",
                "Здорово. Я на связи, давай разговор.",
                "Привет. Чем займёмся?"
            ).random()
            isHowAreYou -> listOf(
                "Нормально, работаю у тебя на телефоне. А у тебя как?",
                "Всё ок. Готов болтать или помочь по делу — как скажешь.",
                "Держусь. Расскажи, что у тебя."
            ).random()
            isWho -> "Я LuntikAi — локальный собеседник на твоём устройстве. Не облако. Умею помнить диалог, опираться на знания и отвечать по-человечески. Можешь просто говорить со мной."
            isThanks -> listOf(
                "Пожалуйста. Если ещё что-то нужно — пиши.",
                "Всегда пожалуйста. Продолжаем?",
                "Не за что. Я рядом."
            ).random()
            isOpinion -> {
                if (factBlock.isNotEmpty()) {
                    "Смотри, как я это вижу. ${factBlock.first()}. Если копнуть глубже — ${factBlock.getOrNull(1) ?: "можем разобрать на примерах"}."
                } else {
                    "Честно: по этой теме у меня мало своих данных, так что скажу осторожно. Могу ошибаться. Расскажи подробнее, что именно тебя волнует — подстроюсь."
                }
            }
            isContinue -> {
                if (factBlock.isNotEmpty()) {
                    "Продолжаю. ${factBlock.joinToString(" ")} Если нужно ещё конкретнее — уточни."
                } else {
                    "Могу развить тему. Ты про что именно: детали, примеры или другой угол?"
                }
            }
            factBlock.isNotEmpty() -> {
                val openers = listOf("Ок, давай по делу.", "Слушай.", "Коротко и по существу.", "Вот как это обычно выглядит.", "Разберём.")
                val closer = listOf(
                    "Если что-то непонятно — переспроси своими словами.",
                    "Могу привести пример, если нужно.",
                    "Хочешь углубимся или хватит такого уровня?",
                    "Пиши, если нужно иначе объяснить."
                )
                val body = factBlock.joinToString(" ") { s ->
                    val t = s.trim().trimEnd('.')
                    if (t.length > 180) t.take(180).trimEnd() + "…" else t
                }
                "${openers.random()} $body. ${closer.random()}"
            }
            else -> {
                val open = listOf("Понял вопрос.", "Хм, интересная тема.", "Ок, давай так.", "Слушаю.").random()
                val mid = when {
                    qLower.endsWith("?") -> "Прямого готового ответа в базе нет, но могу рассуждать вместе с тобой. Расскажи контекст: зачем тебе это и что уже пробовал."
                    q.length < 20 -> "Маловато деталей. Напиши чуть шире — о чём именно речь."
                    else -> "Точного фрагмента в базе нет, поэтому не буду выдумывать. Можем: добавить текст в знания, разобрать вопрос по шагам, или просто поговорить в общем виде. Что ближе?"
                }
                "$open $mid"
            }
        }

        val withCtx = if (ctx.contains("Человек:") && !isGreeting && conf < 40 && factBlock.isEmpty()) {
            base + " Кстати, я вижу наш недавний диалог — можешь опереться на то, что уже писали."
        } else base

        return style(withCtx)
    }

    fun finishThinking(question: String) {
        viewModelScope.launch {
            delay(280)
            if (!isTrained) {
                index.build(allSources())
                isTrained = true
            }
            val scored = allSources().map { it to index.score(question, it.text) }.sortedByDescending { it.second }
            val top = scored.take(4).filter { it.second > 0.00008 }
            val thinking = if (top.isEmpty()) {
                listOf("Контекст диалога" to 55, "Общие знания" to 45)
            } else {
                val total = top.sumOf { it.second }.coerceAtLeast(0.0001)
                top.map { (s, sc) -> s.name.take(36) to ((sc / total) * 100).toInt().coerceIn(8, 92) }
            }
            val best = top.firstOrNull()
            val conf = if (best == null) 28 else (25 + (best.second * 40).toInt()).coerceIn(18, 94)

            val tokens = TfIdfIndex.tokenize(question)
            val facts = top.flatMap { src ->
                src.first.text.split(Regex("[.\\n]+"))
                    .map { it.trim() }
                    .filter { it.length in 15..220 }
                    .sortedByDescending { line -> tokens.count { t -> line.lowercase().contains(t) } }
                    .take(2)
            }.distinct().take(4)

            val ans = composeReply(question, facts, conf)
            pushAi(ans, conf, listOf("Диалог", "Контекст", "Личность: ${personality.title}"), thinking, defaultSuggestions())
            isThinking = false
        }
    }

    private fun defaultSuggestions() = listOf(
        "как дела?",
        "кто ты?",
        "расскажи что-нибудь"
    )

    fun saveSandbox(name: String, language: String, content: String) {
        val n = name.ifBlank { "file_${sandbox.size + 1}" }
        val old = sandbox.find { it.name == n }
        sandbox = if (old != null) sandbox.map {
            if (it.id == old.id) it.copy(content = content, language = language) else it
        } else sandbox + SandboxFile(name = n, language = language, content = content)
        messages = messages + ChatMessage(role = "system", content = "Песочница: «$n» ($language)")
        persist()
    }

    fun runSandbox(file: SandboxFile): String {
        val c = file.content
        val outs = Regex("""(?:print|console\\.log|cout\\s*<<)\\s*\\(?\\s*[\"']([^\"']*)[\"']""").findAll(c).map { it.groupValues[1] }.toList()
        return if (outs.isNotEmpty()) "Вывод (${file.name}):\n" + outs.joinToString("\n")
        else "Файл «${file.name}». Код:\n${c.take(400)}"
    }

    fun runSandboxUi(file: SandboxFile) {
        pushAi(style(runSandbox(file)), 72, listOf("Песочница", "Запуск ${file.name}"), suggestions = defaultSuggestions())
    }

    fun deleteSandbox(id: String) {
        sandbox = sandbox.filter { it.id != id }
        persist()
    }

    private fun pushAi(
        c: String,
        conf: Int,
        actions: List<String>,
        thinking: List<Pair<String, Int>>? = null,
        suggestions: List<String>? = null
    ) {
        messages = messages + ChatMessage(
            role = "ai",
            content = c,
            confidence = conf,
            actions = actions,
            thinking = thinking,
            suggestions = suggestions
        )
        persist()
    }

    private fun style(raw: String) = when (personality) {
        Personality.NONE -> raw
        Personality.HORROR -> "Тихо.\n\n$raw"
        Personality.EGOIST -> "Очевидно.\n\n$raw"
        Personality.VILLAIN -> "Хм.\n\n$raw"
        Personality.KIND -> "С радостью.\n\n$raw"
        Personality.CUTE -> "Хех.\n\n$raw"
        Personality.HUMORIST -> "Чисто юмор:\n\n$raw\n\n(Развлечение.)"
    }

    fun like(id: String) {
        messages = messages.map { if (it.id == id) it.copy(feedback = "like") else it }
        persist()
    }

    fun openDislike(id: String) {
        feedbackMsgId = id
        feedbackComment = ""
        showFeedbackDialog = true
    }

    fun submitDislike() {
        val id = feedbackMsgId ?: return
        messages = messages.map { if (it.id == id) it.copy(feedback = "dislike") else it }
        if (feedbackComment.isNotBlank()) feedbackNotes.add(feedbackComment.lowercase())
        showFeedbackDialog = false
        persist()
    }

    fun applySuggestion(text: String) {
        input = text
        send()
    }
}
