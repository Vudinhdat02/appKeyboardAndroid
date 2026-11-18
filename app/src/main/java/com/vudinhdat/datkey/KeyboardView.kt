package com.vudinhdat.datkey

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Bảng màu bàn phím. tint = màu người dùng chọn (null = mặc định). */
class Palette(val dark: Boolean, val tint: Int? = null) {
    private fun mix(a: Int, b: Int, t: Float) = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt())

    private val baseBg = if (dark) Color.rgb(43, 43, 46) else Color.rgb(209, 212, 218)
    private val baseKey = if (dark) Color.rgb(107, 107, 110) else Color.WHITE
    private val baseFn = if (dark) Color.rgb(70, 70, 73) else Color.rgb(171, 177, 186)
    private fun t(c: Int, amount: Float) = if (tint != null) mix(c, tint, amount) else c

    val bg = t(baseBg, if (dark) 0.28f else 0.30f)
    val bgTop = bg
    val bgBottom = bg
    val key = t(baseKey, if (dark) 0.22f else 0.06f)
    val fnKey = t(baseFn, if (dark) 0.28f else 0.32f)
    val keyPressed = fnKey
    val fnPressed = key
    val text = if (dark) Color.WHITE else Color.BLACK
    val shadow = if (dark) Color.argb(160, 0, 0, 0) else t(Color.rgb(137, 138, 141), 0.3f)
    val separator = if (dark) Color.argb(70, 255, 255, 255) else Color.argb(60, 0, 0, 0)
    val highlight = if (dark) mix(bg, Color.WHITE, 0.2f) else Color.argb(220, 255, 255, 255)
    val popup = key
    val accent = tint ?: Color.rgb(10, 132, 255)
    val dim = if (dark) Color.argb(150, 255, 255, 255) else Color.argb(140, 0, 0, 0)

    companion object {
        /** Các màu cho phép chọn: khoá, tên, màu. */
        val TINTS: List<Triple<String, String, Int?>> = listOf(
            Triple("default", "Mặc định", null),
            Triple("blue", "Xanh dương", Color.rgb(10, 132, 255)),
            Triple("purple", "Tím", Color.rgb(175, 82, 222)),
            Triple("pink", "Hồng", Color.rgb(255, 55, 135)),
            Triple("red", "Đỏ", Color.rgb(255, 69, 58)),
            Triple("orange", "Cam", Color.rgb(255, 149, 0)),
            Triple("green", "Xanh lá", Color.rgb(48, 209, 88)),
            Triple("teal", "Ngọc", Color.rgb(48, 176, 199)),
        )
        fun tintOf(key: String): Int? = TINTS.firstOrNull { it.first == key }?.third
    }
}

class Suggestion(val text: String, val quoted: Boolean = false, val highlighted: Boolean = false)

enum class ShiftState { OFF, ON, LOCKED }

class KeyboardView(context: Context) : View(context) {

    interface Listener {
        fun onChar(text: String)
        fun onDelete(repeat: Int)
        fun onSpace()
        fun onEnter()
        fun onShiftTap()
        fun onPageChange(page: Page)
        fun onEmoji()
        fun onGlobe()
        fun onGlobeLong()
        fun onHide()
        fun onOpenSettings()
        fun onSuggestion(s: Suggestion)
        fun onTrackpad(active: Boolean)
        fun onCursorMove(dx: Int, dy: Int)
        fun onKeyFeedback(type: KeyType)
    }

    var listener: Listener? = null
    var palette = Palette(false); set(v) { field = v; invalidate() }
    var vietnamese = true; set(v) { field = v; invalidate() }
    var showPreview = true
    var numberRow = false; set(v) { if (field != v) { field = v; refreshLayout(); requestLayout() } }
    var showEmoji = true; set(v) { if (field != v) { field = v; refreshLayout() } }
    var showGlobe = true; set(v) { field = v; invalidate() }
    var showStrip = true; set(v) { if (field != v) { field = v; requestLayout(); layoutKeys(); invalidate() } }
    var capsLockEnabled = true
    var heightScale = 1f; set(v) { field = v; requestLayout() }
    var enterLabel = ""; set(v) { field = v; invalidate() }
    var enterAccent = false; set(v) { field = v; invalidate() }
    var shift = ShiftState.OFF; set(v) { field = v; invalidate() }
    var suggestions: List<Suggestion> = emptyList(); set(v) { field = v; invalidate() }

    var page = Page.LETTERS; private set
    private var mode = FieldMode.TEXT
    private var rows: List<Row> = Layouts.build(page, mode, vietnamese)

    private val d = resources.displayMetrics.density
    private fun dp(v: Float) = v * d

    private val stripH get() = if (showStrip) dp(44f) else dp(6f)
    private val keyH get() = dp(42f) * heightScale
    private val vGap get() = dp(11f) * heightScale
    private val hGap get() = dp(6f)
    private val side get() = dp(3f)
    private val topPad get() = dp(6f)
    private val bottomStripH get() = dp(54f)
    private val radius get() = dp(5.5f)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val rect = RectF()
    private val path = Path()

    private val handler = Handler(Looper.getMainLooper())

    fun setLayout(p: Page, m: FieldMode = mode) {
        page = p; mode = m
        rows = Layouts.build(page, mode, vietnamese, numberRow, showEmoji)
        requestLayout(); layoutKeys(); invalidate()
    }

    fun refreshLayout() = setLayout(page, mode)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val rowsH = rows.sumOf { (keyH * it.heightFactor).toDouble() }.toFloat() + (rows.size - 1) * vGap
        val h = stripH + topPad + rowsH + dp(8f) + bottomStripH
        setMeasuredDimension(w, h.toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { layoutKeys() }

    private fun layoutKeys() {
        val width = width.toFloat()
        if (width <= 0) return
        val unit = (width - 2 * side) / 10f
        var y = stripH + topPad
        for (row in rows) {
            val total = row.keys.sumOf { it.width.toDouble() }.toFloat()
            val anchored = total < 10f && row.keys.size > 2 && row.keys.first().isFunction && row.keys.last().isFunction && row.indent == 0f
            if (anchored) {
                val first = row.keys.first(); val last = row.keys.last()
                place(first, side, y, first.width * unit)
                place(last, width - side - last.width * unit, y, last.width * unit)
                val mid = row.keys.subList(1, row.keys.size - 1)
                val midW = mid.sumOf { it.width.toDouble() }.toFloat() * unit
                var x = (width - midW) / 2f
                for (k in mid) { place(k, x, y, k.width * unit); x += k.width * unit }
            } else {
                var x = (width - total * unit) / 2f
                for (k in row.keys) { place(k, x, y, k.width * unit); x += k.width * unit }
            }
            for (k in row.keys) k.h = keyH * row.heightFactor
            y += keyH * row.heightFactor + vGap
        }
    }

    private fun place(k: Key, x: Float, y: Float, slot: Float) {
        k.x = x + hGap / 2; k.y = y; k.w = slot - hGap; k.h = keyH
    }

    // ------------------------------------------------------------------ Vẽ
    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(palette.bg)
        drawStrip(canvas)
        for (row in rows) for (k in row.keys) drawKey(canvas, k)
        drawBottomStrip(canvas)
        if (!trackpad) {
            for (p in presses.values) {
                val k = p.key ?: continue
                if (p.alts != null) drawAlternates(canvas, k, p)
                else if (k.type == KeyType.CHAR && previewFits(k) && !p.cancelled) drawPreview(canvas, k)
            }
        }
    }

    private fun drawStrip(c: Canvas) {
        val list = suggestions
        if (!showStrip || list.isEmpty() || trackpad) return
        val n = 3
        val sw = width / n.toFloat()
        tp.textSize = dp(16.5f)
        for (i in 0 until min(n, list.size)) {
            val s = list[i]
            val cx = sw * i + sw / 2
            if (s.highlighted) {
                fill.color = palette.highlight
                rect.set(sw * i + dp(4f), dp(5f), sw * (i + 1) - dp(4f), stripH - dp(5f))
                c.drawRoundRect(rect, dp(6f), dp(6f), fill)
            }
            tp.color = palette.text
            tp.typeface = if (i == 1 && !s.quoted) Typeface.DEFAULT else Typeface.DEFAULT
            val label = if (s.quoted) "“${s.text}”" else s.text
            val t = TextUtils.ellipsize(label, tp, sw - dp(12f), TextUtils.TruncateAt.MIDDLE).toString()
            c.drawText(t, cx, stripH / 2 - (tp.descent() + tp.ascent()) / 2, tp)
        }
        fill.color = palette.separator
        for (i in 1 until n) {
            val x = sw * i
            val hl = list.getOrNull(i)?.highlighted == true || list.getOrNull(i - 1)?.highlighted == true
            if (!hl) c.drawRect(x - dp(0.5f), stripH * 0.28f, x + dp(0.5f), stripH * 0.72f, fill)
        }
    }

    private fun keyColor(k: Key, pressed: Boolean): Int = when {
        trackpad -> palette.fnKey.let { if (k.isFunction) it else palette.key }
        k.type == KeyType.ENTER && enterAccent -> if (pressed) palette.fnPressed else palette.accent
        k.type == KeyType.SHIFT && shift != ShiftState.OFF -> palette.fnPressed
        k.isFunction -> if (pressed) palette.fnPressed else palette.fnKey
        pressed && !(k.type == KeyType.CHAR && previewFits(k)) -> palette.keyPressed
        else -> palette.key
    }

    /** Hàng trên cùng không đủ chỗ vẽ bóng phóng to khi tắt thanh gợi ý: chỉ tô sẫm phím. */
    private fun previewFits(k: Key) = showPreview && k.y >= dp(36f)

    private fun isPressed(k: Key) = presses.values.any { it.key === k && !it.cancelled }

    private fun drawKey(c: Canvas, k: Key) {
        val pressed = isPressed(k)
        fill.color = palette.shadow
        rect.set(k.x, k.y + dp(1f), k.x + k.w, k.y + k.h + dp(1f))
        c.drawRoundRect(rect, radius, radius, fill)
        fill.color = keyColor(k, pressed)
        rect.set(k.x, k.y, k.x + k.w, k.y + k.h)
        c.drawRoundRect(rect, radius, radius, fill)
        if (trackpad) return

        val cx = k.x + k.w / 2; val cy = k.y + k.h / 2
        val fg = if (k.type == KeyType.ENTER && enterAccent && !pressed) Color.WHITE else palette.text
        tp.color = fg; tp.typeface = Typeface.DEFAULT
        when (k.type) {
            KeyType.CHAR -> {
                val label = if (page == Page.LETTERS && shift != ShiftState.OFF) k.text.uppercase() else k.text
                tp.textSize = if (label.length > 1) dp(16f) else if (page == Page.LETTERS) dp(23f) else dp(21f)
                val off = if (page == Page.LETTERS && shift == ShiftState.OFF && label.length == 1) dp(1.5f) else 0f
                c.drawText(label, cx, cy - (tp.descent() + tp.ascent()) / 2 - off, tp)
            }
            KeyType.SPACE -> {
                tp.textSize = dp(16f)
                val label = if (vietnamese) "dấu cách" else "space"
                c.drawText(label, cx, cy - (tp.descent() + tp.ascent()) / 2, tp)
            }
            KeyType.ENTER -> when (enterLabel) {
                "" -> drawReturn(c, cx, cy, fg)
                "→" -> drawArrowRight(c, cx, cy, fg)
                else -> {
                    tp.textSize = dp(16f)
                    val t = TextUtils.ellipsize(enterLabel, tp, k.w - dp(6f), TextUtils.TruncateAt.END).toString()
                    c.drawText(t, cx, cy - (tp.descent() + tp.ascent()) / 2, tp)
                }
            }
            KeyType.NUMBERS, KeyType.LETTERS, KeyType.SYMBOLS -> {
                tp.textSize = dp(16f)
                c.drawText(k.text, cx, cy - (tp.descent() + tp.ascent()) / 2, tp)
            }
            KeyType.SHIFT -> drawShift(c, cx, cy, fg)
            KeyType.DELETE -> drawDelete(c, cx, cy, fg, pressed)
            KeyType.EMOJI -> drawSmiley(c, cx, cy, fg)
        }
    }

    private fun drawShift(c: Canvas, cx: Float, cy: Float, color: Int) {
        val s = dp(1f)
        path.reset()
        val top = cy - 10 * s
        path.moveTo(cx, top)
        path.lineTo(cx + 9 * s, top + 9 * s)
        path.lineTo(cx + 4.5f * s, top + 9 * s)
        path.lineTo(cx + 4.5f * s, top + 16 * s)
        path.lineTo(cx - 4.5f * s, top + 16 * s)
        path.lineTo(cx - 4.5f * s, top + 9 * s)
        path.lineTo(cx - 9 * s, top + 9 * s)
        path.close()
        if (shift == ShiftState.OFF) {
            stroke.color = color; stroke.strokeWidth = 1.6f * s
            c.drawPath(path, stroke)
        } else {
            fill.color = color; c.drawPath(path, fill)
            if (shift == ShiftState.LOCKED) c.drawRect(cx - 4.5f * s, top + 18.5f * s, cx + 4.5f * s, top + 20.5f * s, fill)
        }
    }

    private fun drawDelete(c: Canvas, cx: Float, cy: Float, color: Int, pressed: Boolean) {
        val s = dp(1f)
        path.reset()
        val l = cx - 11 * s; val r = cx + 10 * s; val t = cy - 8 * s; val b = cy + 8 * s
        path.moveTo(l, cy); path.lineTo(l + 7 * s, t); path.lineTo(r, t); path.lineTo(r, b); path.lineTo(l + 7 * s, b); path.close()
        stroke.color = color; stroke.strokeWidth = 1.6f * s
        if (pressed) { fill.color = color; c.drawPath(path, fill); stroke.color = keyColor(Key(KeyType.DELETE), true) }
        else c.drawPath(path, stroke)
        val ix = cx + 1.5f * s
        c.drawLine(ix - 3.5f * s, cy - 3.5f * s, ix + 3.5f * s, cy + 3.5f * s, stroke)
        c.drawLine(ix - 3.5f * s, cy + 3.5f * s, ix + 3.5f * s, cy - 3.5f * s, stroke)
    }

    private fun drawReturn(c: Canvas, cx: Float, cy: Float, color: Int) {
        val s = dp(1f)
        stroke.color = color; stroke.strokeWidth = 1.8f * s
        path.reset()
        path.moveTo(cx + 9 * s, cy - 7 * s); path.lineTo(cx + 9 * s, cy + 2 * s); path.lineTo(cx - 9 * s, cy + 2 * s)
        c.drawPath(path, stroke)
        c.drawLine(cx - 9 * s, cy + 2 * s, cx - 4.5f * s, cy - 2.5f * s, stroke)
        c.drawLine(cx - 9 * s, cy + 2 * s, cx - 4.5f * s, cy + 6.5f * s, stroke)
    }

    private fun drawArrowRight(c: Canvas, cx: Float, cy: Float, color: Int) {
        val s = dp(1f)
        stroke.color = color; stroke.strokeWidth = 2f * s
        c.drawLine(cx - 9 * s, cy, cx + 9 * s, cy, stroke)
        c.drawLine(cx + 9 * s, cy, cx + 3.5f * s, cy - 5.5f * s, stroke)
        c.drawLine(cx + 9 * s, cy, cx + 3.5f * s, cy + 5.5f * s, stroke)
    }

    private fun drawSmiley(c: Canvas, cx: Float, cy: Float, color: Int) {
        val s = dp(1f)
        stroke.color = color; stroke.strokeWidth = 1.6f * s
        c.drawCircle(cx, cy, 10.5f * s, stroke)
        fill.color = color
        c.drawCircle(cx - 3.5f * s, cy - 3 * s, 1.4f * s, fill)
        c.drawCircle(cx + 3.5f * s, cy - 3 * s, 1.4f * s, fill)
        rect.set(cx - 5.5f * s, cy - 3 * s, cx + 5.5f * s, cy + 6 * s)
        c.drawArc(rect, 20f, 140f, false, stroke)
    }

    private val globeRect = RectF()
    private val hideRect = RectF()

    private fun drawBottomStrip(c: Canvas) {
        val s = dp(1f)
        val top = height - bottomStripH
        globeRect.set(dp(30f), top, dp(82f), top + dp(40f))
        hideRect.set(width - dp(82f), top, width - dp(30f), top + dp(40f))
        val color = if (trackpad) palette.dim else palette.dim
        stroke.color = color; stroke.strokeWidth = 1.5f * s
        // Quả địa cầu
        if (showGlobe) {
        val gx = globeRect.left + dp(20f); val gy = globeRect.centerY() - dp(2f); val r = 11 * s
        c.drawCircle(gx, gy, r, stroke)
        rect.set(gx - r * 0.45f, gy - r, gx + r * 0.45f, gy + r); c.drawOval(rect, stroke)
        c.drawLine(gx, gy - r, gx, gy + r, stroke)
        c.drawLine(gx - r, gy, gx + r, gy, stroke)
        c.drawLine(gx - r * 0.85f, gy - r * 0.5f, gx + r * 0.85f, gy - r * 0.5f, stroke)
        c.drawLine(gx - r * 0.85f, gy + r * 0.5f, gx + r * 0.85f, gy + r * 0.5f, stroke)
        }
        // Ẩn bàn phím
        val hx = hideRect.right - dp(20f); val hy = hideRect.centerY() - dp(4f)
        rect.set(hx - 11 * s, hy - 7 * s, hx + 11 * s, hy + 6 * s)
        c.drawRoundRect(rect, 2 * s, 2 * s, stroke)
        for (i in 0..3) c.drawPoint(hx - 6 * s + i * 4 * s, hy - 2.5f * s, stroke)
        c.drawLine(hx - 5 * s, hy + 2 * s, hx + 5 * s, hy + 2 * s, stroke)
        c.drawLine(hx - 4 * s, hy + 9 * s, hx, hy + 12 * s, stroke)
        c.drawLine(hx, hy + 12 * s, hx + 4 * s, hy + 9 * s, stroke)
    }

    private fun balloonPath(k: Key, headW: Float, headH: Float, headLeft: Float): Path {
        val p = Path()
        val headTop = k.y - headH - dp(6f)
        val head = Path().apply { addRoundRect(RectF(headLeft, headTop, headLeft + headW, headTop + headH + dp(6f)), dp(9f), dp(9f), Path.Direction.CW) }
        val body = Path().apply { addRoundRect(RectF(k.x, k.y - dp(4f), k.x + k.w, k.y + k.h), radius, radius, Path.Direction.CW) }
        val neck = Path().apply {
            moveTo(headLeft, headTop + headH)
            cubicTo(headLeft, headTop + headH + dp(8f), k.x, k.y - dp(10f), k.x, k.y)
            lineTo(k.x + k.w, k.y)
            cubicTo(k.x + k.w, k.y - dp(10f), headLeft + headW, headTop + headH + dp(8f), headLeft + headW, headTop + headH)
            close()
        }
        p.op(head, Path.Op.UNION); p.op(neck, Path.Op.UNION); p.op(body, Path.Op.UNION)
        return p
    }

    private fun drawPreview(c: Canvas, k: Key) {
        val headW = k.w + dp(22f)
        val headH = min(dp(52f), max(dp(30f), k.y - dp(7f)))
        var left = k.x - dp(11f)
        left = left.coerceIn(dp(1f), width - headW - dp(1f))
        val p = balloonPath(k, headW, headH, left)
        fill.color = palette.shadow
        c.save(); c.translate(0f, dp(1f)); c.drawPath(p, fill); c.restore()
        fill.color = palette.popup
        c.drawPath(p, fill)
        val label = if (page == Page.LETTERS && shift != ShiftState.OFF) k.text.uppercase() else k.text
        tp.color = palette.text; tp.textSize = dp(34f); tp.typeface = Typeface.DEFAULT
        val headTop = k.y - headH - dp(6f)
        c.drawText(label, left + headW / 2, headTop + headH / 2 + dp(4f) - (tp.descent() + tp.ascent()) / 2, tp)
    }

    private fun altGeometry(k: Key, n: Int): Pair<Float, Float> {
        val cellW = max(k.w, dp(30f))
        val total = cellW * n + dp(10f)
        var left = k.x - dp(5f)
        if (left + total > width - dp(2f)) left = k.x + k.w + dp(5f) - total
        left = left.coerceIn(dp(2f), max(dp(2f), width - total - dp(2f)))
        return left to cellW
    }

    private fun drawAlternates(c: Canvas, k: Key, p: Press) {
        val alts = p.alts ?: return
        val (left, cellW) = altGeometry(k, alts.size)
        val headW = cellW * alts.size + dp(10f)
        val headH = min(dp(52f), max(dp(34f), k.y - dp(7f)))
        val path = balloonPath(k, headW, headH, left)
        fill.color = palette.shadow
        c.save(); c.translate(0f, dp(1f)); c.drawPath(path, fill); c.restore()
        fill.color = palette.popup; c.drawPath(path, fill)
        val headTop = k.y - headH - dp(6f)
        tp.textSize = dp(24f)
        for ((i, a) in alts.withIndex()) {
            val x0 = left + dp(5f) + i * cellW
            if (i == p.altIndex) {
                fill.color = palette.accent
                rect.set(x0, headTop + dp(6f), x0 + cellW, headTop + headH)
                c.drawRoundRect(rect, dp(6f), dp(6f), fill)
                tp.color = Color.WHITE
            } else tp.color = palette.text
            c.drawText(a, x0 + cellW / 2, headTop + dp(6f) + (headH - dp(6f)) / 2 - (tp.descent() + tp.ascent()) / 2, tp)
        }
    }

    // ------------------------------------------------------------------ Chạm
    inner class Press(var key: Key?, val downX: Float, val downY: Float) {
        var cancelled = false
        var alts: List<String>? = null
        var altIndex = 0
        var longFired = false
        var fromNumbersSlide = false
        var menu = false
        var suggestionSlot = -1
        var bottomTarget = 0 // 1 = globe, 2 = hide
        var repeatCount = 0
        var lastX = downX; var lastY = downY
        var accX = 0f; var accY = 0f
        val longRunnable = Runnable { onLongPress(this) }
        val repeatRunnable: Runnable = object : Runnable {
            override fun run() {
                repeatCount++
                listener?.onDelete(repeatCount)
                listener?.onKeyFeedback(KeyType.DELETE)
                handler.postDelayed(this, if (repeatCount > 12) 70L else 95L)
            }
        }
    }

    private val presses = LinkedHashMap<Int, Press>()
    private var trackpad = false
    private var lastShiftTap = 0L

    private fun keyAt(x: Float, y: Float): Key? {
        val top = stripH + topPad - vGap / 2
        if (y < top - dp(4f) || y > height - bottomStripH + dp(4f)) return null
        var bestRow: Row? = null
        var bestDy = Float.MAX_VALUE
        for (row in rows) {
            val k = row.keys.first()
            val dy = abs(y - (k.y + k.h / 2))
            if (dy < bestDy) { bestDy = dy; bestRow = row }
        }
        val row = bestRow ?: return null
        var best: Key? = null; var bestDx = Float.MAX_VALUE
        for (k in row.keys) {
            val dx = when { x < k.x -> k.x - x; x > k.x + k.w -> x - (k.x + k.w); else -> 0f }
            if (dx < bestDx) { bestDx = dx; best = k }
        }
        return best
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val idx = e.actionIndex
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (trackpad) return true
                // Gõ nhanh nhiều ngón: hoàn tất phím trước
                for ((id, p) in presses.toList()) {
                    if (p.key?.type == KeyType.CHAR && p.alts == null) { release(p); presses.remove(id) }
                }
                onDown(e.getPointerId(idx), e.getX(idx), e.getY(idx))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val p = presses[e.getPointerId(i)] ?: continue
                    onMove(p, e.getX(i), e.getY(i))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val id = e.getPointerId(idx)
                val p = presses.remove(id)
                if (p != null) {
                    onMove(p, e.getX(idx), e.getY(idx))
                    release(p)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                for (p in presses.values) clearCallbacks(p)
                presses.clear()
                if (trackpad) { trackpad = false; listener?.onTrackpad(false) }
            }
        }
        invalidate()
        return true
    }

    private fun onDown(id: Int, x: Float, y: Float) {
        val p = Press(null, x, y)
        presses[id] = p
        if (showStrip && y < stripH) {
            val n = 3
            p.suggestionSlot = (x / (width / n.toFloat())).toInt().coerceIn(0, n - 1)
            return
        }
        if (y > height - bottomStripH + dp(4f)) {
            p.bottomTarget = when {
                x < dp(110f) && showGlobe -> 1
                x > width - dp(110f) -> 2
                else -> 0
            }
            if (p.bottomTarget == 1) handler.postDelayed(p.longRunnable, 450)
            return
        }
        val k = keyAt(x, y) ?: return
        p.key = k
        listener?.onKeyFeedback(k.type)
        when (k.type) {
            KeyType.DELETE -> {
                listener?.onDelete(0)
                handler.postDelayed(p.repeatRunnable, 420)
            }
            KeyType.SHIFT -> {
                val now = System.currentTimeMillis()
                if (capsLockEnabled && now - lastShiftTap < 320) {
                    shift = ShiftState.LOCKED; lastShiftTap = 0
                    p.longFired = true
                } else {
                    lastShiftTap = now
                    listener?.onShiftTap()
                }
            }
            KeyType.CHAR, KeyType.SPACE -> handler.postDelayed(p.longRunnable, if (k.type == KeyType.SPACE) 380 else 380)
            KeyType.EMOJI -> handler.postDelayed(p.longRunnable, 400)
            else -> {}
        }
    }

    private fun onMove(p: Press, x: Float, y: Float) {
        if (trackpad && p.key?.type == KeyType.SPACE) {
            p.accX += x - p.lastX; p.accY += y - p.lastY
            p.lastX = x; p.lastY = y
            val stepX = dp(9f); val stepY = dp(26f)
            val dx = (p.accX / stepX).toInt(); val dy = (p.accY / stepY).toInt()
            if (dx != 0 || dy != 0) {
                p.accX -= dx * stepX; p.accY -= dy * stepY
                listener?.onCursorMove(dx, dy)
            }
            return
        }
        if (!trackpad && p.key?.type == KeyType.SPACE && abs(x - p.downX) > dp(12f)) {
            // Kéo phím cách sang trái/phải: di chuyển con trỏ ngay
            handler.removeCallbacks(p.longRunnable)
            p.longFired = true
            trackpad = true
            p.accX = 0f; p.accY = 0f
            p.lastX = x; p.lastY = y
            listener?.onTrackpad(true)
            listener?.onKeyFeedback(KeyType.SHIFT)
            invalidate()
            return
        }
        p.lastX = x; p.lastY = y
        val alts = p.alts
        if (alts != null) {
            val k = p.key ?: return
            val (left, cellW) = altGeometry(k, alts.size)
            p.altIndex = ((x - left - dp(5f)) / cellW).toInt().coerceIn(0, alts.size - 1)
            return
        }
        val k = p.key ?: return
        when (k.type) {
            KeyType.CHAR -> {
                val nk = keyAt(x, y)
                if (nk == null) return
                if (nk !== k && nk.type == KeyType.CHAR) {
                    handler.removeCallbacks(p.longRunnable)
                    p.key = nk
                    handler.postDelayed(p.longRunnable, 380)
                }
            }
            KeyType.NUMBERS, KeyType.SYMBOLS, KeyType.LETTERS -> {
                // Giữ "123" rồi trượt sang phím: gõ ký tự rồi quay về bàn chữ
                if (!k.contains(x, y) && abs(y - p.downY) + abs(x - p.downX) > dp(12f)) {
                    if (k.type == KeyType.NUMBERS && page == Page.LETTERS) {
                        listener?.onPageChange(Page.NUMBERS); p.fromNumbersSlide = true
                    } else return
                    val nk = keyAt(x, y)
                    p.key = if (nk?.type == KeyType.CHAR) nk else null
                    if (p.key == null) p.cancelled = true
                }
            }
            else -> {}
        }
        if (p.fromNumbersSlide) {
            val nk = keyAt(x, y)
            p.key = if (nk?.type == KeyType.CHAR) nk else p.key
        }
    }

    private fun onLongPress(p: Press) {
        if (p.bottomTarget == 1) { p.longFired = true; listener?.onGlobeLong(); return }
        val k = p.key ?: return
        when (k.type) {
            KeyType.SPACE -> {
                p.longFired = true
                trackpad = true
                listener?.onTrackpad(true)
                listener?.onKeyFeedback(KeyType.SHIFT)
                invalidate()
            }
            KeyType.EMOJI -> {
                p.alts = listOf("⚙", "😀"); p.altIndex = 0; p.menu = true; p.longFired = true
                listener?.onKeyFeedback(KeyType.SHIFT)
                invalidate()
            }
            KeyType.CHAR -> {
                val upper = page == Page.LETTERS && shift != ShiftState.OFF
                val alts = Layouts.alternates(k.text, vietnamese, upper) ?: return
                p.alts = alts; p.altIndex = 0; p.longFired = true
                listener?.onKeyFeedback(KeyType.SHIFT)
                invalidate()
            }
            else -> {}
        }
    }

    private fun clearCallbacks(p: Press) {
        handler.removeCallbacks(p.longRunnable)
        handler.removeCallbacks(p.repeatRunnable)
    }

    private fun release(p: Press) {
        clearCallbacks(p)
        if (p.suggestionSlot >= 0) {
            val n = 3
            val slot = (p.lastX / (width / n.toFloat())).toInt().coerceIn(0, n - 1)
            if (slot == p.suggestionSlot && p.lastY < stripH + dp(10f)) {
                suggestions.getOrNull(slot)?.let { listener?.onKeyFeedback(KeyType.CHAR); listener?.onSuggestion(it) }
            }
            return
        }
        if (p.bottomTarget != 0) {
            if (!p.longFired) {
                listener?.onKeyFeedback(KeyType.SHIFT)
                if (p.bottomTarget == 1) listener?.onGlobe() else listener?.onHide()
            }
            return
        }
        val k = p.key ?: return
        if (p.cancelled) return
        if (trackpad && k.type == KeyType.SPACE) {
            trackpad = false
            listener?.onTrackpad(false)
            invalidate(); return
        }
        val alts = p.alts
        if (alts != null) {
            if (p.menu) { if (p.altIndex == 0) listener?.onOpenSettings() else listener?.onEmoji() }
            else listener?.onChar(alts[p.altIndex])
            return
        }
        when (k.type) {
            KeyType.CHAR -> {
                val t = if (page == Page.LETTERS && shift != ShiftState.OFF) k.text.uppercase() else k.text
                listener?.onChar(t)
                if (p.fromNumbersSlide) listener?.onPageChange(Page.LETTERS)
            }
            KeyType.SPACE -> if (!p.longFired) listener?.onSpace()
            KeyType.ENTER -> listener?.onEnter()
            KeyType.NUMBERS -> if (!p.fromNumbersSlide) listener?.onPageChange(Page.NUMBERS)
            KeyType.LETTERS -> if (!p.fromNumbersSlide) listener?.onPageChange(Page.LETTERS)
            KeyType.SYMBOLS -> listener?.onPageChange(Page.SYMBOLS)
            KeyType.EMOJI -> listener?.onEmoji()
            else -> {}
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacksAndMessages(null)
        presses.clear(); trackpad = false
    }
}
