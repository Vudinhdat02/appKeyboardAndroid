package com.vudinhdat.datkey

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import java.io.File

@Suppress("DEPRECATION")
class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var content: LinearLayout
    private var dark = false
    private val d by lazy { resources.displayMetrics.density }
    private fun dp(v: Float) = (v * d).toInt()

    private val bg get() = if (dark) Color.BLACK else Color.rgb(242, 242, 247)
    private val card get() = if (dark) Color.rgb(28, 28, 30) else Color.WHITE
    private val textColor get() = if (dark) Color.WHITE else Color.BLACK
    private val secondary get() = if (dark) Color.rgb(142, 142, 147) else Color.rgb(109, 109, 114)
    private val sepColor get() = if (dark) Color.rgb(56, 56, 58) else Color.rgb(198, 198, 200)
    private val blue = Color.rgb(10, 132, 255)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
    }

    override fun onResume() {
        super.onResume()
        build()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) build()
    }

    private fun isEnabled(): Boolean {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        return imm.enabledInputMethodList.any { it.packageName == packageName }
    }

    private fun isDefault(): Boolean {
        val id = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: return false
        return id.startsWith("$packageName/")
    }

    private fun build() {
        dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        window.statusBarColor = bg
        window.navigationBarColor = bg
        if (!dark) window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        else window.decorView.systemUiVisibility = 0

        val scroll = ScrollView(this).apply { setBackgroundColor(bg); fitsSystemWindows = true }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16f), dp(12f), dp(16f), dp(32f)) }
        scroll.addView(content)

        content.addView(TextView(this).apply {
            text = "DatKey"
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 34f); typeface = Typeface.DEFAULT_BOLD; setTextColor(textColor)
            setPadding(dp(4f), dp(16f), 0, 0)
        })

        // ---- Cài đặt ban đầu
        val enabled = isEnabled(); val default = isDefault()
        section("Bắt đầu", null) {
            actionRow(it, "1. Bật bàn phím DatKey", if (enabled) "✓ Đã bật" else "Mở cài đặt", enabled) {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
            actionRow(it, "2. Chọn DatKey làm bàn phím", if (default) "✓ Đang dùng" else "Chọn", default) {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            }
            val et = EditText(this).apply {
                hint = "Gõ thử"
                setTextColor(textColor); setHintTextColor(secondary)
                background = null
                setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
                minLines = 2; gravity = Gravity.TOP
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            }
            addSep(it); it.addView(et)
        }

        section("Chiều cao bàn phím", null) {
            val label = TextView(this).apply {
                setTextColor(textColor); setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
                setPadding(dp(16f), dp(12f), dp(16f), 0)
                text = "Chiều cao: ${prefs.sp.getInt("height", 100)}%"
            }
            it.addView(label)
            it.addView(SeekBar(this).apply {
                max = 80; progress = prefs.sp.getInt("height", 100).coerceIn(70, 150) - 70
                setPadding(dp(20f), dp(10f), dp(20f), dp(14f))
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                        label.text = "Chiều cao: ${p + 70}%"
                        prefs.sp.edit().putInt("height", p + 70).apply()
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            })
        }

        // ---- Kiểu gõ
        section("Kiểu gõ", null) {
            segmented(it, listOf("Telex", "VNI"), if (prefs.vni) 1 else 0) { i ->
                prefs.sp.edit().putString("method", if (i == 1) "vni" else "telex").apply()
            }
            switchRow(it, "Dấu kiểu mới (hoà, thuý)", "modern_tone", true)
        }

        section("Bàn phím", null) {
            switchRow(it, "Hàng phím số", "number_row", false)
            switchRow(it, "Nút chuyển ngôn ngữ", "show_globe", true)
            switchRow(it, "Nút emoji", "show_emoji", true)
            switchRow(it, "Tự động viết hoa", "auto_cap", true)
            switchRow(it, "Tự sửa lỗi tiếng Anh", "auto_correct", true)
            switchRow(it, "Gợi ý từ", "predictive", true)
            switchRow(it, "Bật Caps Lock", "caps_lock", true)
            switchRow(it, "Phím tắt \".\"", "double_space", true)
            switchRow(it, "Dấu câu thông minh", "smart_punct", true)
            switchRow(it, "Xem trước ký tự", "preview", true)
            switchRow(it, "Học từ mới", "learn", true)
        }

        section("Phản hồi", null) {
            switchRow(it, "Âm thanh phím", "sound", true)
            switchRow(it, "Rung phản hồi", "haptic", true)
        }

        section("Giao diện", null) {
            val themes = listOf("system", "light", "dark")
            segmented(it, listOf("Theo máy", "Sáng", "Tối"), themes.indexOf(prefs.theme).coerceAtLeast(0)) { i ->
                prefs.sp.edit().putString("theme", themes[i]).apply()
            }
            colorPicker(it)
        }

        section("Thay thế văn bản", null) { box ->
            val map = prefs.shortcuts
            for ((k, v) in map.toSortedMap()) {
                actionRow(box, v, k, false) { editShortcut(k, v) }
            }
            actionRow(box, "Thêm cụm thay thế…", "", false, accent = true) { editShortcut(null, null) }
            actionRow(box, "Nhập từ file Excel", "", false, accent = true) {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(XLSX_MIME, "application/octet-stream", "application/zip"))
                }, REQ_IMPORT)
            }
            actionRow(box, "Xuất ra file Excel", "", false, accent = true) {
                startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = XLSX_MIME
                    putExtra(Intent.EXTRA_TITLE, "datkey-cum-tu.xlsx")
                }, REQ_EXPORT)
            }
        }

        section("Khác", null) {
            actionRow(it, "Xoá dữ liệu từ đã học", "", false, accent = true) {
                AlertDialog.Builder(this).setTitle("Xoá dữ liệu từ đã học?")
                    .setMessage("Các từ và cụm từ DatKey đã học từ cách gõ của bạn sẽ bị xoá.")
                    .setNegativeButton("Huỷ", null)
                    .setPositiveButton("Xoá") { _, _ ->
                        File(filesDir, "user_dict.tsv").delete()
                        prefs.sp.edit().putLong("user_reset", System.currentTimeMillis()).apply()
                    }.show()
            }
        }

        setContentView(scroll)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        try {
            when (requestCode) {
                REQ_EXPORT -> {
                    val map = prefs.shortcuts
                    contentResolver.openOutputStream(uri, "wt")!!.use { XlsxShortcuts.write(map, it) }
                    toast("Đã xuất ${map.size} cụm từ")
                }
                REQ_IMPORT -> {
                    val imported = contentResolver.openInputStream(uri)!!.use { XlsxShortcuts.read(it) }
                    if (imported.isEmpty()) { toast("File không có cụm từ nào"); return }
                    prefs.shortcuts = prefs.shortcuts + imported
                    toast("Đã nhập ${imported.size} cụm từ")
                    build()
                }
            }
        } catch (e: Exception) {
            toast(if (requestCode == REQ_IMPORT) "Không đọc được file Excel này" else "Không ghi được file")
        }
    }

    private fun toast(msg: String) = android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()

    private fun editShortcut(key: String?, value: String?) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20f), dp(8f), dp(20f), 0) }
        val phrase = EditText(this).apply { hint = "Cụm từ (vd. Cảm ơn bạn nhiều!)"; setText(value ?: "") }
        val sc = EditText(this).apply { hint = "Viết tắt (vd. tks)"; setText(key ?: ""); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS }
        box.addView(phrase); box.addView(sc)
        val b = AlertDialog.Builder(this).setTitle(if (key == null) "Thêm cụm thay thế" else "Sửa cụm thay thế")
            .setView(box)
            .setNegativeButton("Huỷ", null)
            .setPositiveButton("Lưu") { _, _ ->
                val m = prefs.shortcuts.toMutableMap()
                if (key != null) m.remove(key)
                val k = sc.text.toString().trim().lowercase()
                val v = phrase.text.toString()
                if (k.isNotEmpty() && v.isNotEmpty() && !k.contains(' ')) m[k] = v
                prefs.shortcuts = m
                build()
            }
        if (key != null) b.setNeutralButton("Xoá") { _, _ ->
            prefs.shortcuts = prefs.shortcuts.toMutableMap().apply { remove(key) }
            build()
        }
        b.show()
    }

    // ------------------------------------------------------------ Thành phần giao diện

    private fun section(title: String, footer: String?, fill: (LinearLayout) -> Unit) {
        content.addView(TextView(this).apply {
            text = title.uppercase()
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f); setTextColor(secondary)
            setPadding(dp(16f), dp(22f), dp(16f), dp(6f))
        })
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(card); cornerRadius = 12 * d }
            clipToOutline = true
        }
        fill(box)
        content.addView(box)
        if (footer != null) content.addView(TextView(this).apply {
            text = footer
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f); setTextColor(secondary)
            setPadding(dp(16f), dp(6f), dp(16f), 0)
        })
    }

    private fun addSep(box: LinearLayout) {
        if (box.childCount == 0) return
        box.addView(View(this).apply { setBackgroundColor(sepColor) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxOf(1, (0.6f * d).toInt())).apply { leftMargin = dp(16f) })
    }

    private fun switchRow(box: LinearLayout, label: String, key: String, def: Boolean) {
        addSep(box)
        val sw = Switch(this).apply {
            text = label
            setTextColor(textColor); setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
            isChecked = prefs.sp.getBoolean(key, def)
            setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
            setOnCheckedChangeListener { _, c -> prefs.sp.edit().putBoolean(key, c).apply() }
        }
        box.addView(sw)
    }

    private fun actionRow(box: LinearLayout, label: String, value: String, ok: Boolean, accent: Boolean = false, onClick: () -> Unit) {
        addSep(box)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
            val tv = TypedValue(); theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
            foreground = getDrawable(tv.resourceId)
            setOnClickListener { onClick() }
        }
        row.addView(TextView(this).apply {
            text = label; setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
            setTextColor(if (accent) blue else textColor)
            maxLines = 2
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(this).apply {
            text = if (value.isEmpty() || ok) value else "$value  ›"
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
            setTextColor(if (ok) Color.rgb(52, 199, 89) else secondary)
        })
        box.addView(row)
    }

    private fun colorPicker(box: LinearLayout) {
        addSep(box)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12f), dp(12f), dp(12f), dp(14f))
        }
        val current = prefs.color
        val dots = ArrayList<View>()
        fun paint(sel: String) {
            for ((i, t) in Palette.TINTS.withIndex()) {
                val c = t.third ?: if (dark) Color.rgb(72, 72, 76) else Color.rgb(222, 224, 229)
                dots[i].background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL; setColor(c)
                    if (t.first == sel) setStroke(dp(3f), if (dark) Color.WHITE else Color.rgb(30, 30, 32))
                    else if (t.third == null) setStroke(dp(1f), sepColor)
                }
            }
        }
        for (t in Palette.TINTS) {
            val v = View(this).apply {
                contentDescription = t.second
                setOnClickListener { prefs.sp.edit().putString("color", t.first).apply(); paint(t.first) }
            }
            dots.add(v)
            row.addView(v, LinearLayout.LayoutParams(dp(30f), dp(30f)).apply { marginStart = dp(5f); marginEnd = dp(5f) })
        }
        paint(current)
        val scroller = android.widget.HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) }
        box.addView(TextView(this).apply {
            text = "Màu bàn phím"; setTextColor(textColor); setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
            setPadding(dp(16f), dp(12f), dp(16f), 0)
        })
        box.addView(scroller)
    }

    private fun segmented(box: LinearLayout, items: List<String>, selected: Int, onSelect: (Int) -> Unit) {
        addSep(box)
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(3f), dp(3f), dp(3f), dp(3f))
            background = GradientDrawable().apply {
                setColor(if (dark) Color.rgb(44, 44, 46) else Color.rgb(228, 228, 233)); cornerRadius = 9 * d
            }
        }
        val views = ArrayList<TextView>()
        fun paint(sel: Int) {
            for ((i, v) in views.withIndex()) {
                v.background = if (i == sel) GradientDrawable().apply {
                    setColor(if (dark) Color.rgb(99, 99, 102) else Color.WHITE); cornerRadius = 7 * d
                } else null
                v.typeface = if (i == sel) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
        }
        for ((i, s) in items.withIndex()) {
            val tv = TextView(this).apply {
                text = s; gravity = Gravity.CENTER; setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
                setPadding(0, dp(7f), 0, dp(7f))
                setOnClickListener { paint(i); onSelect(i) }
            }
            views.add(tv)
            outer.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        paint(selected)
        val wrap = LinearLayout(this).apply { setPadding(dp(12f), dp(10f), dp(12f), dp(10f)) }
        wrap.addView(outer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        box.addView(wrap)
    }

    companion object {
        private const val REQ_IMPORT = 41
        private const val REQ_EXPORT = 42
        private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    }
}
