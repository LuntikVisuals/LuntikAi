package com.luntik.ai

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
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun LuntikApp(vm: LuntikViewModel = viewModel()) {
    val listState = rememberLazyListState()
    LaunchedEffect(vm.messages.size) { if (vm.messages.isNotEmpty()) listState.animateScrollToItem(vm.messages.lastIndex) }
    LaunchedEffect(vm.isThinking) { if (vm.isThinking) vm.finishThinking(vm.messages.lastOrNull { it.role == "user" }?.content ?: "") }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(Modifier.fillMaxWidth().background(SurfaceC).statusBarsPadding().padding(12.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🌱 LuntikAi", color = Accent, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.weight(1f))
            Text("${vm.personality.emoji} · ${if (vm.isTrained) "готов" else "авто"}", color = TextMuted, fontSize = 11.sp)
        }
        LazyColumn(listState, Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(vm.messages, key = { it.id }) { msg -> MsgBubble(msg, { vm.like(msg.id) }, { vm.openDislike(msg.id) }) }
            if (vm.isThinking) item { Text("💭 Думаю…", color = TextMuted, fontSize = 13.sp) }
        }
        Row(Modifier.fillMaxWidth().background(SurfaceC).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = { vm.showKnowledgeList = true }, contentPadding = PaddingValues(8.dp, 6.dp)) { Text("Знания", fontSize = 11.sp) }
            OutlinedButton(onClick = { vm.showSandbox = true }, contentPadding = PaddingValues(8.dp, 6.dp)) { Text("Песочница", fontSize = 11.sp) }
            OutlinedButton(onClick = { vm.showPersonalityDialog = true }, contentPadding = PaddingValues(8.dp, 6.dp)) { Text("Личность", fontSize = 11.sp) }
            Button(onClick = { vm.train() }, contentPadding = PaddingValues(10.dp, 6.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg)) { Text("Обучить", fontSize = 11.sp) }
        }
        Row(Modifier.fillMaxWidth().background(SurfaceC).navigationBarsPadding().padding(10.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(vm.input, { vm.input = it }, Modifier.weight(1f), placeholder = { Text("Вопрос / создай файл…", color = TextMuted) },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = Surface2, focusedTextColor = TextMain, unfocusedTextColor = TextMain, cursorColor = Accent), maxLines = 4)
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { vm.send() }, colors = IconButtonDefaults.iconButtonColors(containerColor = Accent)) {
                Icon(Icons.AutoMirrored.Filled.Send, null, tint = Bg)
            }
        }
    }

    if (vm.showKnowledgeList) {
        AlertDialog(onDismissRequest = { vm.showKnowledgeList = false }, title = { Text("Знания (${vm.sources.size} + навыки)") }, text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text("Встроено: Python, JavaScript, C++", color = Accent, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                if (vm.sources.isEmpty()) Text("Своих источников пока нет", color = TextMuted)
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

    if (vm.showSandbox) {
        AlertDialog(onDismissRequest = { vm.showSandbox = false }, title = { Text("Песочница (${vm.sandbox.size})") }, text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                Text("создай файл hello.py : print(\"hi\")\nзапусти hello.py", color = TextMuted, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                if (vm.sandbox.isEmpty()) Text("Пусто", color = TextMuted)
                vm.sandbox.forEach { f ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Surface2)) {
                        Column(Modifier.padding(10.dp)) {
                            Text("${f.name} · ${f.language}", color = TextMain, fontWeight = FontWeight.SemiBold)
                            Row {
                                TextButton(onClick = { vm.viewingSandbox = f; vm.showSandbox = false }) { Text("Открыть") }
                                TextButton(onClick = { vm.runSandboxUi(f); vm.showSandbox = false }) { Text("Запуск") }
                                TextButton(onClick = { vm.deleteSandbox(f.id) }) { Text("Удал.", color = Danger) }
                            }
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { vm.showSandbox = false }) { Text("Закрыть") } })
    }
    vm.viewingSandbox?.let { f ->
        AlertDialog(onDismissRequest = { vm.viewingSandbox = null }, title = { Text(f.name) }, text = {
            Text(f.content, color = TextMain, fontSize = 12.sp, modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(max = 320.dp))
        }, confirmButton = {
            Row {
                TextButton(onClick = { vm.runSandboxUi(f); vm.viewingSandbox = null }) { Text("Запуск") }
                TextButton(onClick = { vm.viewingSandbox = null }) { Text("Закрыть") }
            }
        })
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
