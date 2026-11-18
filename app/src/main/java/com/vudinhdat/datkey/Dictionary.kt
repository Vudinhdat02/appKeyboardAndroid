package com.vudinhdat.datkey

import android.content.Context
import java.io.File

/**
 * Từ điển gợi ý: danh sách từ tiếng Việt và tiếng Anh kèm tần suất,
 * cộng với dữ liệu học từ người dùng (từ đã gõ và cặp từ liền nhau).
 */
class Dictionary(private val context: Context) {

    private class Entry(val word: String, val key: String, val freq: Int)

    @Volatile private var vi: Array<Entry> = emptyArray()
    @Volatile private var en: Array<Entry> = emptyArray()
    @Volatile private var enSet: HashMap<String, Int> = HashMap()
    @Volatile private var viSet: HashSet<String> = HashSet()

    private val userWords = HashMap<String, Int>()
    private val bigrams = HashMap<String, HashMap<String, Int>>()
    private var dirty = false
    private val userFile get() = File(context.filesDir, "user_dict.tsv")

    fun load() {
        Thread {
            en = readAsset("en.txt") { it }
            vi = readAsset("vi.txt") { VietEngine.strip(it) }
            enSet = HashMap<String, Int>(en.size * 2).apply { en.forEach { put(it.word, it.freq) } }
            viSet = HashSet<String>(vi.size * 2).apply { vi.forEach { add(it.word) } }
            loadUser()
        }.start()
    }

    private fun readAsset(name: String, keyOf: (String) -> String): Array<Entry> {
        val list = ArrayList<Entry>(40000)
        context.assets.open(name).bufferedReader().useLines { lines ->
            lines.forEach { l ->
                val tab = l.indexOf('\t')
                if (tab > 0) {
                    val w = l.substring(0, tab)
                    list.add(Entry(w, keyOf(w), l.substring(tab + 1).toIntOrNull() ?: 0))
                }
            }
        }
        list.sortBy { it.key }
        return list.toTypedArray()
    }

    private fun lowerBound(arr: Array<Entry>, key: String): Int {
        var lo = 0; var hi = arr.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (arr[mid].key < key) lo = mid + 1 else hi = mid
        }
        return lo
    }

    fun isKnownEnglish(w: String) = enSet.containsKey(w.lowercase()) || userWords.containsKey(w.lowercase())
    fun isKnownVietnamese(w: String) = viSet.contains(w.lowercase()) || userWords.containsKey(w.lowercase())

    /** Gợi ý hoàn thành từ cho tiền tố đang gõ. */
    fun suggest(typed: String, vietnamese: Boolean, max: Int = 3): List<String> {
        if (typed.isEmpty()) return emptyList()
        val lower = typed.lowercase()
        val key = if (vietnamese) VietEngine.strip(lower) else lower
        val scored = HashMap<String, Double>()
        fun add(w: String, score: Double) {
            val cur = scored[w]
            if (cur == null || cur < score) scored[w] = score
        }
        val arrays = if (vietnamese) listOf(vi, en) else listOf(en)
        for ((ai, arr) in arrays.withIndex()) {
            var i = lowerBound(arr, key)
            var scanned = 0
            while (i < arr.size && arr[i].key.startsWith(key) && scanned < 4000) {
                val e = arr[i]
                var s = e.freq.toDouble()
                if (e.word.startsWith(lower)) s += 150 // khớp cả dấu
                if (e.word == lower) s += 400
                if (ai == 1) s -= 120 // tiếng Anh xếp sau khi đang gõ tiếng Việt
                s -= (e.key.length - key.length) * 12.0
                add(e.word, s)
                i++; scanned++
            }
        }
        synchronized(userWords) {
            for ((w, c) in userWords) {
                val k = if (vietnamese) VietEngine.strip(w) else w
                if (k.startsWith(key)) add(w, 520.0 + c * 25 + (if (w.startsWith(lower)) 150 else 0) - (k.length - key.length) * 12)
            }
        }
        return scored.entries.sortedByDescending { it.value }.take(max).map { matchCase(it.key, typed) }
    }

    /** Sửa lỗi chính tả tiếng Anh: tìm từ cách 1 thao tác có tần suất cao nhất. */
    fun correct(word: String): String? {
        val lower = word.lowercase()
        if (lower.length < 2 || !lower.all { it in 'a'..'z' || it == '\'' }) return null
        if (isKnownEnglish(lower)) return null
        var best: String? = null; var bestF = 0
        fun check(c: String) {
            val f = (enSet[c] ?: 0) + (userWords[c] ?: 0) * 50
            if (f > bestF) { bestF = f; best = c }
        }
        val alpha = "abcdefghijklmnopqrstuvwxyz"
        for (i in 0..lower.length) {
            if (i < lower.length) check(lower.removeRange(i, i + 1))
            if (i < lower.length - 1) check(lower.substring(0, i) + lower[i + 1] + lower[i] + lower.substring(i + 2))
            for (c in alpha) {
                if (i < lower.length) check(lower.substring(0, i) + c + lower.substring(i + 1))
                check(lower.substring(0, i) + c + lower.substring(i))
            }
        }
        if (bestF < 250) return null
        return best?.let { matchCase(it, word) }
    }

    /** Dự đoán từ tiếp theo từ dữ liệu đã học. */
    fun predict(prev: String, vietnamese: Boolean, max: Int = 3): List<String> {
        val res = ArrayList<String>()
        synchronized(userWords) {
            bigrams[prev.lowercase()]?.entries?.sortedByDescending { it.value }?.take(max)?.forEach { res.add(it.key) }
        }
        val fallback = if (prev.isEmpty()) {
            if (vietnamese) listOf("Tôi", "Mình", "Em") else listOf("I", "The", "I'm")
        } else {
            if (vietnamese) listOf("là", "và", "không") else listOf("the", "to", "and")
        }
        for (f in fallback) if (res.size < max && f !in res) res.add(f)
        return res
    }

    fun learn(prev: String?, word: String) {
        val w = word.lowercase()
        if (w.isBlank() || w.length > 30 || !w.any { it.isLetter() }) return
        synchronized(userWords) {
            userWords[w] = (userWords[w] ?: 0) + 1
            if (!prev.isNullOrBlank()) {
                val m = bigrams.getOrPut(prev.lowercase()) { HashMap() }
                m[w] = (m[w] ?: 0) + 1
            }
            dirty = true
        }
    }

    fun forget(word: String) {
        synchronized(userWords) { userWords.remove(word.lowercase()); dirty = true }
    }

    fun clearUser() {
        synchronized(userWords) { userWords.clear(); bigrams.clear(); dirty = false }
        userFile.delete()
    }

    private fun loadUser() {
        val f = userFile
        if (!f.exists()) return
        synchronized(userWords) {
            f.forEachLine { l ->
                val p = l.split('\t')
                if (p.size == 3 && p[0] == "w") userWords[p[1]] = p[2].toIntOrNull() ?: 1
                if (p.size == 4 && p[0] == "b") bigrams.getOrPut(p[1]) { HashMap() }[p[2]] = p[3].toIntOrNull() ?: 1
            }
        }
    }

    fun save() {
        val text: String
        synchronized(userWords) {
            if (!dirty) return
            dirty = false
            val sb = StringBuilder()
            userWords.entries.sortedByDescending { it.value }.take(5000).forEach { sb.append("w\t${it.key}\t${it.value}\n") }
            bigrams.entries.take(5000).forEach { (a, m) ->
                m.entries.sortedByDescending { it.value }.take(8).forEach { sb.append("b\t$a\t${it.key}\t${it.value}\n") }
            }
            text = sb.toString()
        }
        Thread { runCatching { userFile.writeText(text) } }.start()
    }

    companion object {
        fun matchCase(word: String, typed: String): String = when {
            typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } -> word.uppercase()
            typed.isNotEmpty() && typed[0].isUpperCase() -> word.replaceFirstChar { it.uppercaseChar() }
            else -> word
        }
    }
}
