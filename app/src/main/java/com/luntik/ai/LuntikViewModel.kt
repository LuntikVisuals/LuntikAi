package com.luntik.ai

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class LuntikViewModel(app: Application) : AndroidViewModel(app) {
    private val store = File(getApplication<Application>().filesDir, "luntik_state.json")
    var messages by mutableStateOf(listOf(ChatMessage(role = "system", content = "LuntikAi v0.4 — Python/JS/C++, песочница, перефраз под диалог, знания, личности.")))
        private set
    var sources by mutableStateOf(listOf<KnowledgeSource>())
        private set
    var sandbox by mutableStateOf(listOf<SandboxFile>())
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
    var showSandbox by mutableStateOf(false)
    var viewingSandbox by mutableStateOf<SandboxFile?>(null)
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
    private fun allSources() = BuiltinSkills.all() + sources.filter { !it.builtin }
    private val feedbackNotes = mutableListOf<String>()

    init { load() }

    private fun persist() {
        try {
            val o = JSONObject().put("personality", personality.name).put("isTrained", isTrained)
            val sa = JSONArray(); sources.forEach { sa.put(JSONObject().put("id", it.id).put("name", it.name).put("text", it.text)) }
            o.put("sources", sa)
            val sba = JSONArray(); sandbox.forEach { sba.put(JSONObject().put("id", it.id).put("name", it.name).put("language", it.language).put("content", it.content)) }; o.put("sandbox", sba)
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
            o.optJSONArray("sandbox")?.let { arr ->
                sandbox = (0 until arr.length()).map { i ->
                    val f = arr.getJSONObject(i)
                    SandboxFile(f.optString("id", UUID.randomUUID().toString()), f.optString("name"), f.optString("language", "text"), f.optString("content"))
                }
            }
            o.optJSONArray("files")?.let { arr ->
                chatFiles = (0 until arr.length()).map { i -> val f = arr.getJSONObject(i); ChatFile(f.optString("id", UUID.randomUUID().toString()), f.optString("name"), f.optString("content")) }
            }
            o.optJSONArray("messages")?.let { arr ->
                if (arr.length() > 0) messages = (0 until arr.length()).map { i -> val m = arr.getJSONObject(i); ChatMessage(m.optString("id", UUID.randomUUID().toString()), m.optString("role"), m.optString("content")) }
            }
            if (isTrained) index.build(allSources())
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
        index.build(allSources()); isTrained = true
        messages = messages + ChatMessage(role = "system", content = "Готово. Навыки Python/JS/C++ + твоих источников: ${sources.size}. Песочница: ${sandbox.size}"); persist()
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
            lower in listOf("привет", "хай", "здравствуй") -> {
                pushAi(style("Привет! Есть навыки Python/JS/C++, песочница и знания. Чем помочь?"), 90, listOf("Приветствие")); return
            }
            lower.contains("анализ") && lower.contains("диалог") -> {
                pushAi(style("Сообщений: ${messages.count { it.role == \"user\" }}, источников: ${sources.size}, песочница: ${sandbox.size}, личность: ${personality.title}"), 80, listOf("Анализ")); return
            }
            lower.contains("песочниц") || lower == "sandbox" -> {
                val list = if (sandbox.isEmpty()) "Песочница пуста. Пример: создай файл hello.py : print(\"hi\")"
                else sandbox.joinToString("\n") { "• ${it.name} (${it.language})" }
                pushAi(style(list), 88, listOf("Песочница")); return
            }
            lower.startsWith("создай файл") || lower.startsWith("создать файл") || lower.contains("сделай файл") -> {
                val name = Regex("""файл[ае]?\\s+[«\"]?([\\wА-Яа-я.\\-]+)""", RegexOption.IGNORE_CASE).find(q)?.groupValues?.getOrNull(1) ?: "snippet.txt"
                val code = when {
                    ":" in q -> q.substringAfter(":").trim()
                    else -> q.substringAfter(name).trim().ifBlank { "// empty" }
                }
                val lang = when {
                    name.endsWith(".py") || "print(" in code -> "python"
                    name.endsWith(".js") || "console.log" in code -> "javascript"
                    name.endsWith(".cpp") || "iostream" in code -> "cpp"
                    else -> "text"
                }
                saveSandbox(name, lang, code)
                pushAi(style("Создал «$name» в песочнице ($lang). Напиши: запусти $name"), 90, listOf("Песочница", "Создал $name"))
                return
            }
            lower.startsWith("запусти ") || lower.contains("запусти файл") -> {
                val name = q.substringAfter("запусти").substringAfter("файл").trim().trim('«','»','"','\'')
                val f = sandbox.find { it.name.equals(name, true) || it.name.contains(name, true) }
                if (f == null) pushAi(style("Нет файла «$name» в песочнице."), 20, listOf("Не найден"))
                else runSandboxUi(f)
                return
            }
        }
        val codeQ = listOf("python","питон","javascript","js","c++","cpp","код","функци","print","console").any { lower.contains(it) }
        if (!isTrained) { index.build(allSources()); isTrained = true }
        isThinking = true
    }

    suspend fun finishThinking(question: String) {
        kotlinx.coroutines.delay(350)
        if (!isTrained) { index.build(allSources()); isTrained = true }
        val scored = allSources().map { it to index.score(question, it.text) }.sortedByDescending { it.second }
        val top = scored.take(3).filter { it.second > 0.0001 }
        val thinking = if (top.isEmpty()) listOf("Нет совпадений" to 70, "Общие навыки" to 30)
        else {
            val total = top.sumOf { it.second }.coerceAtLeast(0.0001)
            top.map { (s, sc) -> s.name.take(40) to ((sc / total) * 100).toInt().coerceIn(5, 90) }
        }
        val best = top.firstOrNull()
        val conf = if (best == null) 15 else (22 + (best.second * 38).toInt()).coerceIn(12, 93)
        val qLower = question.lowercase()
        val lead = when {
            qLower.startsWith("что такое") || qLower.startsWith("что это") -> "Если коротко: "
            qLower.startsWith("как ") -> "Сделать можно так: "
            qLower.contains("python") || qLower.contains("питон") -> "По Python: "
            qLower.contains("javascript") || qLower.contains(" js") -> "По JavaScript: "
            qLower.contains("c++") || qLower.contains("cpp") -> "По C++: "
            qLower.contains("пример") -> "Пример в простом виде: "
            else -> "По твоему вопросу: "
        }
        var ans = if (best == null) {
            "Мало точных данных. Спроси про Python, JS, C++ или добавь текст в знания."
        } else {
            val tokens = TfIdfIndex.tokenize(question)
            val bits = top.take(2).flatMap { src ->
                src.first.text.split(Regex("[.\n]+")).map { it.trim() }.filter { it.length > 10 }
                    .sortedByDescending { line -> tokens.count { line.lowercase().contains(it) } }.take(2)
            }.distinct().take(4)
            val body = bits.joinToString(". ") { it.trim().trimEnd('.') }
            lead + body + ". Могу накидать пример в песочницу, если нужно."
        }
        ans = style(ans)
        pushAi(ans, conf, listOf("Поиск по навыкам/знаниям", "Перефраз под диалог", "Личность: ${personality.title}"), thinking)
        isThinking = false
    }

    fun saveSandbox(name: String, language: String, content: String) {
        val n = name.ifBlank { "file_${sandbox.size + 1}" }
        val old = sandbox.find { it.name == n }
        sandbox = if (old != null) sandbox.map { if (it.id == old.id) it.copy(content = content, language = language) else it }
        else sandbox + SandboxFile(name = n, language = language, content = content)
        messages = messages + ChatMessage(role = "system", content = "💻 Песочница: «$n» ($language)")
        persist()
    }

    fun runSandbox(file: SandboxFile): String {
        val c = file.content
        val outs = Regex("""(?:print|console\\.log|cout\\s*<<)\\s*\\(?\\s*[\"']([^\"']*)[\"']""").findAll(c).map { it.groupValues[1] }.toList()
        return if (outs.isNotEmpty()) "Вывод (${file.name}):\n" + outs.joinToString("\n")
        else "Файл «${file.name}» есть. Полный интерпретатор на телефоне ограничен — видны простые print/log. Код:\n${c.take(400)}"
    }

    fun runSandboxUi(file: SandboxFile) {
        pushAi(style(runSandbox(file)), 72, listOf("Песочница", "Запуск ${file.name}"))
    }

    fun deleteSandbox(id: String) {
        sandbox = sandbox.filter { it.id != id }; persist()
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
