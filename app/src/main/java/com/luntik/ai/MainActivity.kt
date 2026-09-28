package com.luntik.ai

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
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Accent,
                    background = Bg,
                    surface = SurfaceC,
                    onPrimary = Bg,
                    onBackground = TextMain,
                    onSurface = TextMain
                )
            ) {
                Surface(Modifier.fillMaxSize(), color = Bg) { LuntikApp() }
            }
        }
    }
}

var Bg = Color(0xFF0C0E12)
var SurfaceC = Color(0xFF161A22)
var Surface2 = Color(0xFF1E2430)
var Accent = Color(0xFF6EE7B7)
val TextMain = Color(0xFFE6E9EF)
val TextMuted = Color(0xFF8B93A7)
var UserBubble = Color(0xFF2D3A55)
var AiBubble = Color(0xFF1A2030)
val Danger = Color(0xFFF87171)
val Warn = Color(0xFFFBBF24)
val ActionBg = Color(0xFF1A2A22)

enum class Personality(val title: String) {
    NONE("Обычный"),
    HORROR("Хоррор"),
    EGOIST("Эгоист"),
    VILLAIN("Злодей"),
    KIND("Добряк"),
    CUTE("Милый"),
    HUMORIST("Юморист")
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val content: String,
    val confidence: Int? = null,
    val thinking: List<Pair<String, Int>>? = null,
    val actions: List<String>? = null,
    val feedback: String? = null,
    val suggestions: List<String>? = null
)

data class KnowledgeSource(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val text: String,
    val builtin: Boolean = false
)

data class SandboxFile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val language: String,
    val content: String
)

data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    var title: String,
    var messages: List<ChatMessage> = emptyList()
)

data class UserSkill(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String
)

data class ThemePrefs(
    val accent: Long = 0xFF6EE7B7,
    val background: Long = 0xFF0C0E12,
    val surface: Long = 0xFF161A22,
    val liquidGlass: Boolean = true,
    val displayName: String = "Пользователь"
)

object BuiltinSkills {
    // Expanded corpus to increase APK knowledge weight
    private val python = """
Python. Базовый синтаксис. print("text"). Переменные: x = 10, name = "Luntik". Типы: int, float, str, bool, list, dict, tuple, set.
if x > 0: ... elif x == 0: ... else: ...
for i in range(5): print(i). while condition: ...
def add(a, b): return a + b. lambda x: x * 2.
nums = [1, 2, 3]; nums.append(4); nums[0]; len(nums); nums[-1].
d = {"a": 1}; d["b"] = 2; d.get("a").
class Cat:
    def __init__(self, name):
        self.name = name
    def meow(self):
        return self.name
try: ... except Exception as e: print(e). finally: ...
import math; math.sqrt(16). from collections import Counter.
with open("f.txt") as f: data = f.read().
list comprehension: [x*x for x in range(10) if x % 2 == 0].
""".trim()

    private val javascript = """
JavaScript. console.log("text"). let x = 10; const name = "Luntik"; var old = 1.
if (x > 0) { } else if (x === 0) { } else { }.
for (let i = 0; i < 5; i++) { }. while (cond) { }. for (const item of arr) { }.
function add(a, b) { return a + b; }. const add = (a, b) => a + b.
arr = [1, 2]; arr.push(3); arr.map(x => x * 2); arr.filter(x => x > 1); arr.find(x => x === 2).
const obj = { a: 1 }; obj.b = 2; Object.keys(obj).
class Cat { constructor(name) { this.name = name; } meow() { return this.name; } }.
try { } catch (e) { } finally { }.
Promise: fetch(url).then(r => r.json()). async function f() { const r = await fetch(url); }.
JSON.parse(str); JSON.stringify(obj). localStorage.setItem(k, v).
""".trim()

    private val cpp = """
C++. #include <iostream>. #include <vector>. #include <string>. using namespace std;
int main() { cout << "Hello" << endl; return 0; }
int x = 10; double y = 3.14; string s = "text"; bool flag = true;
if (x > 0) { } else if (x == 0) { } else { }
for (int i = 0; i < 5; i++) { }. while (cond) { }. for (auto& v : vec) { }
int add(int a, int b) { return a + b; }
vector<int> v = {1, 2, 3}; v.push_back(4); v.size(); v[0];
class Cat { public: string name; Cat(string n): name(n) {} string meow() { return name; } };
try { } catch (const exception& e) { cout << e.what(); }
""".trim()

    private val mathBasics = """
Математика. Сложение a+b, вычитание a-b, умножение a*b, деление a/b.
Степень a^n или pow(a,n). Корень sqrt(x). Модуль abs(x).
Проценты: p% от x = x * p / 100. Уравнение ax+b=0 => x = -b/a.
Площадь прямоугольника a*b, круга pi*r*r, треугольника 0.5*a*h.
Периметр, объём куба a^3, цилиндра pi*r*r*h.
Простые числа, факториал n! = 1*2*...*n. Среднее арифметическое.
""".trim()

    private val general = """
LuntikAi — локальный агент на устройстве. Работает без облака: TF-IDF поиск по знаниям, перефраз ответов, песочница кода, навыки пользователя, OCR текста с фото, распознавание речи.
Команды: /create skill имя | описание. создай файл name.py : код. запусти name.py.
Личности меняют стиль ответа. Данные сохраняются локально в filesDir.
OCR: распознаёт только текст на изображении (ML Kit). Речь: системный SpeechRecognizer Android.
Это не нейросеть LLM, а RAG-агент с расширенным корпусом знаний.
""".trim()

    private val androidBasics = """
Android. Activity, Fragment, Service. Intent для переходов. Permission в Manifest.
Jetpack Compose: @Composable, Column, Row, LazyColumn, Text, Button, OutlinedTextField.
ViewModel хранит состояние. Coroutines: viewModelScope.launch.
SharedPreferences или файлы для хранения. Room для БД. Retrofit для сети.
minSdk, targetSdk, compileSdk в build.gradle.
""".trim()

    private val algorithms = """
Алгоритмы. Сортировка пузырьком, быстрая (quicksort), слиянием (mergesort).
Поиск линейный O(n), бинарный O(log n) на отсортированном массиве.
Стек LIFO, очередь FIFO, хеш-таблица, дерево, граф.
Рекурсия: функция вызывает себя с базовым случаем.
Сложность Big-O: O(1), O(log n), O(n), O(n log n), O(n^2).
""".trim()

    fun all(): List<KnowledgeSource> = listOf(
        KnowledgeSource(name = "Python", text = python, builtin = true),
        KnowledgeSource(name = "JavaScript", text = javascript, builtin = true),
        KnowledgeSource(name = "C++", text = cpp, builtin = true),
        KnowledgeSource(name = "Математика", text = mathBasics, builtin = true),
        KnowledgeSource(name = "LuntikAi и возможности", text = general, builtin = true),
        KnowledgeSource(name = "Android основы", text = androidBasics, builtin = true),
        KnowledgeSource(name = "Алгоритмы", text = algorithms, builtin = true)
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
        val q = tokenize(query)
        if (q.isEmpty()) return 0.0
        val d = tokenize(text)
        if (d.isEmpty()) return 0.0
        val tf = d.groupingBy { it }.eachCount()
        return q.toSet().sumOf { t ->
            ((tf[t] ?: 0).toDouble() / d.size) * (ln((nDocs + 1.0) / ((df[t] ?: 0) + 1.0)) + 1.0)
        }
    }
    companion object {
        fun tokenize(s: String) =
            s.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 1 }
    }
}
