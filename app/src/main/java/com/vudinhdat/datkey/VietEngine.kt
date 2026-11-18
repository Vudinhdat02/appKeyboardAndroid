package com.vudinhdat.datkey

/**
 * Bộ gõ tiếng Việt Telex / VNI xử lý từng phím cho một từ đang soạn.
 *
 * Trạng thái của từ gồm: chữ cái gốc (đã có dấu mũ/móc/trăng nhưng chưa có thanh),
 * thanh điệu của cả từ, và chuỗi phím thô để quay lại khi từ không còn là tiếng Việt hợp lệ
 * (ví dụ gõ tiếng Anh "text" sẽ giữ nguyên "text").
 */
class VietEngine {
    enum class Method { TELEX, VNI }

    var method = Method.TELEX
    var modernTone = true

    private val letters = StringBuilder()
    private var tone = 0
    private val raw = StringBuilder()
    private var literal = false

    fun isEmpty() = letters.isEmpty()

    fun rawText(): String = raw.toString()

    fun reset() {
        letters.setLength(0); raw.setLength(0); tone = 0; literal = false; lastW = false
    }

    fun composed(): String = if (literal) letters.toString() else render(letters, tone, modernTone)

    /** Đặt từ đang soạn từ một chuỗi có sẵn (dùng khi xoá lùi). */
    fun load(word: String) {
        reset()
        var t = 0
        for (c in word) {
            val (base, ct) = splitTone(c)
            if (ct != 0) t = ct
            letters.append(base)
        }
        tone = if (hasVowel(letters)) t else 0
        raw.append(word)
    }

    /** Thêm một ký tự nguyên văn, không xử lý dấu (dùng cho ký tự chọn từ bảng nhấn giữ). */
    fun appendLiteral(c: Char) {
        if (!literal) {
            val cur = composed()
            letters.setLength(0); letters.append(cur); tone = 0; literal = true
        }
        letters.append(c); raw.append(c)
    }

    fun backspace(): Boolean {
        if (letters.isEmpty()) return false
        val cur = composed()
        if (cur.length <= 1) { reset(); return true }
        val wasLiteral = literal
        val keep = cur.substring(0, cur.length - 1)
        if (wasLiteral) {
            letters.setLength(0); letters.append(keep)
            if (raw.isNotEmpty()) raw.setLength(raw.length - 1)
        } else {
            load(keep)
        }
        return true
    }

    /** Xử lý một ký tự gõ vào. Trả về false nếu ký tự không thuộc về từ (dấu câu, khoảng trắng...). */
    fun process(key: Char): Boolean {
        val isDigit = key in '0'..'9'
        if (!key.isLetter() && !(method == Method.VNI && isDigit && letters.isNotEmpty())) return false
        raw.append(key)
        if (literal) { letters.append(key); return true }

        val lk = key.lowercaseChar()
        val prevW = lastW
        lastW = false
        if (method == Method.TELEX && lk == 'w' && prevW && letters.isNotEmpty() &&
            letters[letters.length - 1].lowercaseChar() == 'ư') {
            letters.setLength(letters.length - 1); undo(key); return true
        }
        val handled = if (method == Method.TELEX) telex(key, lk) else vni(key, lk)
        if (!handled) letters.append(key)

        if (!literal && hasVowel(letters) && (tone != 0 || hasShape(letters)) && !isValid(letters, tone)) {
            // Không phải tiếng Việt: trả lại đúng các phím đã gõ
            letters.setLength(0); letters.append(raw); tone = 0; literal = true
        }
        return true
    }

    // ---------------- Telex ----------------
    private fun telex(key: Char, lk: Char): Boolean {
        val vr = vowelRange(letters)
        when (lk) {
            's', 'f', 'r', 'x', 'j' -> {
                if (vr == null) return false
                val t = when (lk) { 's' -> 1; 'f' -> 2; 'r' -> 3; 'x' -> 4; else -> 5 }
                if (tone == t) { undo(key); return true }
                tone = t; return true
            }
            'z' -> {
                if (vr == null || tone == 0) return false
                tone = 0; return true
            }
            'a', 'e', 'o' -> {
                if (vr == null) return false
                val target = when (lk) { 'a' -> "aăâ"; 'e' -> "eê"; else -> "oơô" }
                val hat = when (lk) { 'a' -> 'â'; 'e' -> 'ê'; else -> 'ô' }
                for (i in vr.last downTo vr.first) {
                    val c = letters[i].lowercaseChar()
                    if (c == hat) { setAt(i, lk); undo(key); return true }
                    if (c in target) { setAt(i, hat); return true }
                }
                return false
            }
            'w' -> {
                if (vr == null) {
                    letters.append(if (key.isUpperCase()) 'Ư' else 'ư'); lastW = true; return true
                }
                return applyHorn(vr, key)
            }
            'd' -> {
                if (letters.length == 1 && letters[0].lowercaseChar() == 'd') { setAt(0, 'đ'); return true }
                if (letters.length == 1 && letters[0].lowercaseChar() == 'đ') { setAt(0, 'd'); undo(key); return true }
                return false
            }
        }
        return false
    }

    private var lastW = false

    private fun applyHorn(vr: IntRange, key: Char): Boolean {
        val s = letters.substring(vr.first, vr.last + 1).lowercase()
        val base = s.map { baseOf(it) }.joinToString("")
        // ươ
        val uo = base.indexOf("uo")
        if (uo >= 0) {
            val i = vr.first + uo
            val a = letters[i].lowercaseChar(); val b = letters[i + 1].lowercaseChar()
            if (a == 'ư' && b == 'ơ') { setAt(i, 'u'); setAt(i + 1, 'o'); undo(key); return true }
            setAt(i, 'ư'); setAt(i + 1, 'ơ'); return true
        }
        // Đã có dấu móc/trăng -> bỏ
        for (i in vr.last downTo vr.first) {
            val c = letters[i].lowercaseChar()
            if (c == 'ư' || c == 'ơ' || c == 'ă') {
                setAt(i, baseOf(c)); undo(key); return true
            }
        }
        val pick: Int = when {
            base == "a" || base == "oa" -> base.lastIndexOf('a')
            base.contains('o') -> base.lastIndexOf('o')
            base.contains('u') -> base.indexOf('u')
            base.contains('a') -> base.lastIndexOf('a')
            else -> -1
        }
        if (pick < 0) return false
        val i = vr.first + pick
        val nc = when (letters[i].lowercaseChar()) { 'a', 'â' -> 'ă'; 'o', 'ô' -> 'ơ'; else -> 'ư' }
        setAt(i, nc); return true
    }

    // ---------------- VNI ----------------
    private fun vni(key: Char, lk: Char): Boolean {
        val vr = vowelRange(letters)
        when (lk) {
            in '1'..'5' -> {
                if (vr == null) return false
                val t = lk - '0'
                if (tone == t) { undo(key); return true }
                tone = t; return true
            }
            '0' -> { if (vr == null || tone == 0) return false; tone = 0; return true }
            '6' -> {
                if (vr == null) return false
                for (i in vr.last downTo vr.first) {
                    val c = letters[i].lowercaseChar()
                    if (c == 'â' || c == 'ê' || c == 'ô') { setAt(i, baseOf(c)); undo(key); return true }
                    if (c in "aăeoơ") { setAt(i, when (baseOf(c)) { 'a' -> 'â'; 'e' -> 'ê'; else -> 'ô' }); return true }
                }
                return false
            }
            '7' -> {
                if (vr == null) return false
                val base = letters.substring(vr.first, vr.last + 1).lowercase().map { baseOf(it) }.joinToString("")
                val uo = base.indexOf("uo")
                if (uo >= 0) {
                    val i = vr.first + uo
                    if (letters[i].lowercaseChar() == 'ư' && letters[i + 1].lowercaseChar() == 'ơ') {
                        setAt(i, 'u'); setAt(i + 1, 'o'); undo(key); return true
                    }
                    setAt(i, 'ư'); setAt(i + 1, 'ơ'); return true
                }
                for (i in vr.last downTo vr.first) {
                    val c = letters[i].lowercaseChar()
                    if (c == 'ơ' || c == 'ư') { setAt(i, baseOf(c)); undo(key); return true }
                }
                val o = base.lastIndexOf('o'); val u = base.indexOf('u')
                val p = if (o >= 0) o else u
                if (p < 0) return false
                setAt(vr.first + p, if (p == o) 'ơ' else 'ư'); return true
            }
            '8' -> {
                if (vr == null) return false
                for (i in vr.last downTo vr.first) {
                    val c = letters[i].lowercaseChar()
                    if (c == 'ă') { setAt(i, 'a'); undo(key); return true }
                    if (c == 'a' || c == 'â') { setAt(i, 'ă'); return true }
                }
                return false
            }
            '9' -> {
                if (letters.isNotEmpty() && letters[0].lowercaseChar() == 'd') { setAt(0, 'đ'); return true }
                if (letters.isNotEmpty() && letters[0].lowercaseChar() == 'đ') { setAt(0, 'd'); undo(key); return true }
                return false
            }
        }
        return false
    }

    /** Huỷ thao tác vừa rồi: phím được gõ ra nguyên văn và ngừng xử lý dấu cho từ này. */
    private fun undo(key: Char) {
        val tmpTone = if (tone != 0 && (key.lowercaseChar() in "sfrxj12345")) 0 else tone
        val cur = render(letters, tmpTone, modernTone)
        letters.setLength(0); letters.append(cur).append(key)
        tone = 0; literal = true
    }

    private fun setAt(i: Int, lower: Char) {
        letters.setCharAt(i, if (letters[i].isUpperCase()) lower.uppercaseChar() else lower)
    }

    companion object {
        const val VOWELS = "aăâeêioôơuưy"
        private const val SHAPED = "ăâêôơưđ"
        private val TONE_TABLE = mapOf(
            'a' to "aáàảãạ", 'ă' to "ăắằẳẵặ", 'â' to "âấầẩẫậ", 'e' to "eéèẻẽẹ", 'ê' to "êếềểễệ",
            'i' to "iíìỉĩị", 'o' to "oóòỏõọ", 'ô' to "ôốồổỗộ", 'ơ' to "ơớờởỡợ", 'u' to "uúùủũụ",
            'ư' to "ưứừửữự", 'y' to "yýỳỷỹỵ"
        )
        private val REVERSE: Map<Char, Pair<Char, Int>> = buildMap {
            for ((b, s) in TONE_TABLE) for (t in s.indices) {
                put(s[t], b to t); put(s[t].uppercaseChar(), b.uppercaseChar() to t)
            }
        }
        private val INITIALS = setOf(
            "", "b", "c", "ch", "d", "đ", "g", "gh", "gi", "h", "k", "kh", "l", "m", "n", "ng", "ngh",
            "nh", "p", "ph", "qu", "q", "r", "s", "t", "th", "tr", "v", "x"
        )
        private val FINALS = setOf("", "c", "ch", "m", "n", "ng", "nh", "p", "t")
        private val CLUSTERS = listOf(
            "a", "ă", "â", "e", "ê", "i", "o", "ô", "ơ", "u", "ư", "y",
            "ai", "ao", "au", "âu", "ay", "ây", "eo", "êu", "ia", "iê", "iu", "oa", "oă", "oe", "oi", "ôi",
            "ơi", "oo", "ua", "uâ", "uê", "ui", "uô", "uơ", "uy", "ưa", "ưi", "ưu", "ươ", "yê",
            "iêu", "oai", "oay", "oeo", "uây", "uôi", "uya", "uyê", "uyu", "ươi", "ươu", "yêu"
        )

        fun isVowel(c: Char) = c.lowercaseChar() in VOWELS

        fun baseOf(c: Char): Char = when (c) {
            'ă', 'â' -> 'a'; 'ê' -> 'e'; 'ô', 'ơ' -> 'o'; 'ư' -> 'u'; 'đ' -> 'd'; else -> c
        }

        fun splitTone(c: Char): Pair<Char, Int> = REVERSE[c] ?: (c to 0)

        /** Bỏ hết dấu: "Đường" -> "duong" */
        fun strip(s: String): String {
            val sb = StringBuilder(s.length)
            for (c in s) sb.append(baseOf(splitTone(c).first.lowercaseChar()))
            return sb.toString()
        }

        fun hasVowel(s: CharSequence) = s.any { isVowel(it) }
        private fun hasShape(s: CharSequence) = s.any { it.lowercaseChar() in SHAPED }

        fun vowelRange(s: CharSequence): IntRange? {
            var start = -1
            for (i in s.indices) if (isVowel(s[i])) { start = i; break }
            if (start < 0) return null
            var end = start
            while (end + 1 < s.length && isVowel(s[end + 1])) end++
            return start..end
        }

        private fun matchCluster(typed: String): Boolean = CLUSTERS.any { c ->
            c.length == typed.length && c.indices.all { typed[it] == c[it] || typed[it] == baseOf(c[it]) }
        }

        fun isValid(s: CharSequence, tone: Int): Boolean {
            val w = s.toString().lowercase()
            val vr = vowelRange(w) ?: return INITIALS.contains(w)
            var vs = vr.first
            val initial0 = w.substring(0, vs)
            var cluster = w.substring(vs, vr.last + 1)
            var initial = initial0
            if ((initial == "q" && cluster.startsWith("u") && cluster.length > 1) ||
                (initial == "g" && cluster.startsWith("i") && cluster.length > 1)) {
                initial += cluster[0]; cluster = cluster.substring(1); vs++
            }
            val fin = w.substring(vr.last + 1)
            if (initial !in INITIALS) return false
            if (fin !in FINALS) return false
            if (!matchCluster(cluster) && !(initial0 == "g" && matchCluster(w.substring(vr.first, vr.last + 1)))) return false
            if (fin in setOf("c", "ch", "p", "t") && tone in 2..4) return false
            return true
        }

        fun render(s: CharSequence, tone: Int, modern: Boolean): String {
            if (tone == 0) return s.toString()
            val vr = vowelRange(s) ?: return s.toString()
            val lower = s.toString().lowercase()
            var st = vr.first
            val en = vr.last + 1
            if (en - st > 1) {
                if (lower[st] == 'u' && st > 0 && lower[st - 1] == 'q') st++
                else if (lower[st] == 'i' && st > 0 && lower[st - 1] == 'g') st++
            }
            val cluster = lower.substring(st, en)
            val hasFinal = en < lower.length
            var pos = -1
            for (i in cluster.indices.reversed()) if (cluster[i] in "ăâêôơư") { pos = st + i; break }
            if (pos < 0) {
                pos = when (cluster.length) {
                    1 -> st
                    2 -> if (hasFinal) st + 1 else if (modern && cluster in setOf("oa", "oe", "uy")) st + 1 else st
                    else -> st + 1
                }
            }
            val sb = StringBuilder(s)
            val c = s[pos]
            val table = TONE_TABLE[c.lowercaseChar()] ?: return s.toString()
            val tc = table[tone]
            sb.setCharAt(pos, if (c.isUpperCase()) tc.uppercaseChar() else tc)
            return sb.toString()
        }
    }
}
