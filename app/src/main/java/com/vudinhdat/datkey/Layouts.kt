package com.vudinhdat.datkey

enum class KeyType { CHAR, SHIFT, DELETE, NUMBERS, LETTERS, SYMBOLS, EMOJI, SPACE, ENTER }

class Key(
    val type: KeyType,
    val text: String = "",
    val width: Float = 1f,
    val shiftText: String? = null,
) {
    // Toạ độ được tính khi bố trí
    var x = 0f; var y = 0f; var w = 0f; var h = 0f
    val isFunction get() = type != KeyType.CHAR && type != KeyType.SPACE
    fun contains(px: Float, py: Float) = px >= x && px < x + w && py >= y && py < y + h
}

class Row(val keys: List<Key>, val indent: Float = 0f, val heightFactor: Float = 1f)

enum class Page { LETTERS, NUMBERS, SYMBOLS }
enum class FieldMode { TEXT, EMAIL, URL }

object Layouts {
    private fun chars(s: String) = s.split(' ').map { Key(KeyType.CHAR, it) }

    private fun bottom(mode: FieldMode, page: Page, showEmoji: Boolean): Row {
        val switch = if (page == Page.LETTERS) Key(KeyType.NUMBERS, "123", 1.25f) else Key(KeyType.LETTERS, "ABC", 1.25f)
        val row = when (mode) {
            FieldMode.TEXT -> Row(listOf(switch, Key(KeyType.EMOJI, "", 1.25f), Key(KeyType.SPACE, "", 5f), Key(KeyType.ENTER, "", 2.5f)))
            FieldMode.EMAIL -> Row(listOf(switch, Key(KeyType.EMOJI, "", 1.25f), Key(KeyType.SPACE, "", 3.5f),
                Key(KeyType.CHAR, "@", 1.25f), Key(KeyType.CHAR, ".", 1.25f), Key(KeyType.ENTER, "", 1.75f)))
            FieldMode.URL -> Row(listOf(switch, Key(KeyType.EMOJI, "", 1.25f), Key(KeyType.SPACE, "", 4f),
                Key(KeyType.CHAR, ".", 1.25f), Key(KeyType.ENTER, "", 2.25f)))
        }
        if (showEmoji) return row
        // Ẩn nút emoji: phím cách rộng thêm phần đó
        return Row(row.keys.filter { it.type != KeyType.EMOJI }.map {
            if (it.type == KeyType.SPACE) Key(KeyType.SPACE, "", it.width + 1.25f) else it
        })
    }

    fun build(page: Page, mode: FieldMode, vietnamese: Boolean, numberRow: Boolean = false, showEmoji: Boolean = true): List<Row> = when (page) {
        Page.LETTERS -> (if (numberRow) listOf(Row(chars("1 2 3 4 5 6 7 8 9 0"), heightFactor = 0.82f)) else emptyList()) + listOf(
            Row(chars("q w e r t y u i o p")),
            Row(chars("a s d f g h j k l"), indent = 0.5f),
            Row(listOf(Key(KeyType.SHIFT, "", 1.4f)) + chars("z x c v b n m").map { Key(KeyType.CHAR, it.text, 1f) } +
                listOf(Key(KeyType.DELETE, "", 1.4f)), indent = 0f),
            bottom(mode, page, showEmoji),
        )
        Page.NUMBERS -> listOf(
            Row(chars("1 2 3 4 5 6 7 8 9 0")),
            Row(chars(if (vietnamese) "- / : ; ( ) ₫ & @ \"" else "- / : ; ( ) $ & @ \"")),
            Row(listOf(Key(KeyType.SYMBOLS, "#+=", 1.4f)) + chars(". , ? ! '").map { Key(KeyType.CHAR, it.text, 1.38f) } +
                listOf(Key(KeyType.DELETE, "", 1.4f))),
            bottom(mode, page, showEmoji),
        )
        Page.SYMBOLS -> listOf(
            Row(chars("[ ] { } # % ^ * + =")),
            Row(chars("_ \\ | ~ < > € £ ¥ •")),
            Row(listOf(Key(KeyType.NUMBERS, "123", 1.4f)) + chars(". , ? ! '").map { Key(KeyType.CHAR, it.text, 1.38f) } +
                listOf(Key(KeyType.DELETE, "", 1.4f))),
            bottom(mode, page, showEmoji),
        )
    }

    private val EN_ALTS = mapOf(
        "a" to "à á â ä æ ã å ā", "e" to "è é ê ë ē ė ę", "i" to "î ï í ī į ì", "o" to "ô ö ò ó œ ø ō õ",
        "u" to "û ü ù ú ū", "c" to "ç ć č", "n" to "ñ ń", "s" to "ß ś š", "y" to "ÿ", "z" to "ž ź ż", "l" to "ł",
    )
    private val VI_ALTS = mapOf(
        "a" to "á à ả ã ạ ă â", "e" to "é è ẻ ẽ ẹ ê", "i" to "í ì ỉ ĩ ị", "o" to "ó ò ỏ õ ọ ô ơ",
        "u" to "ú ù ủ ũ ụ ư", "y" to "ý ỳ ỷ ỹ ỵ", "d" to "đ", "w" to "ư",
    )
    private val SYM_ALTS = mapOf(
        "0" to "°", "-" to "– — •", "/" to "\\", "$" to "₫ $ € £ ¥ ₩ ₽ ¢", "₫" to "₫ $ € £ ¥ ₩ ₽ ¢", "&" to "§",
        "\"" to "„ “ ” « »", "." to "…", "?" to "¿", "!" to "¡", "'" to "‘ ’ `", "%" to "‰",
        ".com" to ".vn .com.vn .net .org .edu",
    )

    fun alternates(key: String, vietnamese: Boolean, upper: Boolean): List<String>? {
        val lower = key.lowercase()
        val s = (if (vietnamese) VI_ALTS[lower] else EN_ALTS[lower]) ?: SYM_ALTS[key] ?: return null
        return s.split(' ').map { if (upper) it.uppercase() else it }
    }
}
