package com.luntik.ai

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
data class KnowledgeSource(val id: String = UUID.randomUUID().toString(), val name: String, val text: String, val builtin: Boolean = false)
data class SandboxFile(val id: String = UUID.randomUUID().toString(), val name: String, val language: String, val content: String)
data class ChatFile(val id: String = UUID.randomUUID().toString(), val name: String, val content: String)

object BuiltinSkills {
    val python = "Язык Python. print(\"Hello\"). x=10. if x>0: ... for i in range(5): ... def add(a,b): return a+b. nums=[1,2]; nums.append(3). class Cat: def __init__(self,name): self.name=name"
    val javascript = "Язык JavaScript. console.log(\"Hello\"). let x=10. if(x>0){}. for(let i=0;i<5;i++){}. const add=(a,b)=>a+b. arr=[1,2]; arr.push(3). class Cat { constructor(name){ this.name=name } }"
    val cpp = "Язык C++. #include <iostream>. int main(){ std::cout<<\"Hello\"<<std::endl; return 0; }. int x=10. for(int i=0;i<5;i++){}. int add(int a,int b){ return a+b; }"
    fun all(): List<KnowledgeSource> = listOf(
        KnowledgeSource(name="Python — база", text=python, builtin=true),
        KnowledgeSource(name="JavaScript — база", text=javascript, builtin=true),
        KnowledgeSource(name="C++ — база", text=cpp, builtin=true)
    )
}

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
