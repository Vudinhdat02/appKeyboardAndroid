package com.vudinhdat.datkey

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import org.robolectric.RuntimeEnvironment
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.time.Duration

/** Chụp ảnh giao diện bàn phím để kiểm tra bằng mắt (ảnh lưu ở app/build/screens). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {
    private val ctx get() = RuntimeEnvironment.getApplication()
    private val out = File("build/screens").apply { mkdirs() }

    private fun save(v: View, name: String) {
        val w = (393 * 3)
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(3000, View.MeasureSpec.AT_MOST))
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
        val b = Bitmap.createBitmap(v.measuredWidth, v.measuredHeight, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(b))
        File(out, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun kb(dark: Boolean, tint: Int? = null) = KeyboardView(ctx).apply {
        palette = Palette(dark, tint)
        measure(View.MeasureSpec.makeMeasureSpec(393 * 3, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(3000, View.MeasureSpec.AT_MOST))
        layout(0, 0, measuredWidth, measuredHeight)
    }

    private fun touch(v: View, action: Int, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        v.dispatchTouchEvent(MotionEvent.obtain(t, t, action, x, y, 0))
    }

    @Test fun lettersLight() {
        val k = kb(false)
        k.shift = ShiftState.ON
        k.suggestions = listOf(Suggestion("Vieejt", quoted = true), Suggestion("Việt", highlighted = false), Suggestion("Viết"))
        save(k, "1_letters_light")
    }

    @Test fun pressPreview() {
        val k = kb(false)
        k.suggestions = listOf(Suggestion("tôi", quoted = true), Suggestion("tối"), Suggestion("tới"))
        val d = ctx.resources.displayMetrics.density
        // phím "g" hàng 2
        touch(k, MotionEvent.ACTION_DOWN, k.width * 0.45f, d * (44 + 6 + 42 + 11 + 21))
        save(k, "2_press_preview")
    }

    @Test fun longPressAlternates() {
        val k = kb(false)
        val d = ctx.resources.displayMetrics.density
        touch(k, MotionEvent.ACTION_DOWN, k.width * 0.08f, d * (44 + 6 + 42 + 11 + 21))
        ShadowLooper.idleMainLooper(500, java.util.concurrent.TimeUnit.MILLISECONDS)
        touch(k, MotionEvent.ACTION_MOVE, k.width * 0.22f, d * (44 + 6 + 30))
        save(k, "3_long_press_a")
    }

    @Test fun numbersDark() {
        val k = kb(true)
        k.setLayout(Page.NUMBERS)
        k.enterLabel = "gửi"; k.enterAccent = true
        save(k, "4_numbers_dark")
    }

    @Test fun symbolsEnglishDark() {
        val k = kb(true)
        k.vietnamese = false
        k.enterLabel = "return"
        k.shift = ShiftState.LOCKED
        k.suggestions = listOf(Suggestion("teh", quoted = true), Suggestion("the", highlighted = true), Suggestion("tech"))
        save(k, "5_english_dark_capslock")
    }

    @Test fun tinted() {
        save(kb(true, Palette.tintOf("purple")).apply { suggestions = listOf(Suggestion("xin", quoted = true), Suggestion("xinh"), Suggestion("xin chào")) }, "9_dark_purple")
        save(kb(false, Palette.tintOf("pink")), "10_light_pink")
        save(kb(false, Palette.tintOf("blue")), "11_light_blue")
    }

    @Test fun numberRowGo() {
        val k = kb(false)
        k.numberRow = true
        k.measure(View.MeasureSpec.makeMeasureSpec(393 * 3, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(3000, View.MeasureSpec.AT_MOST))
        k.layout(0, 0, k.measuredWidth, k.measuredHeight)
        k.enterLabel = "→"; k.enterAccent = true
        save(k, "12_number_row_go")
    }

    @Test fun minimal() {
        val k = kb(false)
        k.showStrip = false; k.showGlobe = false; k.showEmoji = false
        k.measure(View.MeasureSpec.makeMeasureSpec(393 * 3, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(3000, View.MeasureSpec.AT_MOST))
        k.layout(0, 0, k.measuredWidth, k.measuredHeight)
        val d = ctx.resources.displayMetrics.density
        touch(k, MotionEvent.ACTION_DOWN, k.width * 0.25f, d * (6 + 6 + 21))
        save(k, "15_minimal")
    }

    @Test fun settingsScreen() {
        val a = org.robolectric.Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        val root = a.window.decorView
        val w = 393 * 3
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(852 * 3, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, w, 852 * 3)
        val b = Bitmap.createBitmap(w, 852 * 3, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(b))
        File(out, "16_settings.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun xlsxRoundTrip() {
        val bos = java.io.ByteArrayOutputStream()
        XlsxShortcuts.write(mapOf("tks" to "Cảm ơn bạn nhiều!", "omw" to "On my way & <ok>"), bos)
        File(out, "test.xlsx").writeBytes(bos.toByteArray())
        val back = XlsxShortcuts.read(java.io.ByteArrayInputStream(bos.toByteArray()))
        org.junit.Assert.assertEquals(mapOf("omw" to "On my way & <ok>", "tks" to "Cảm ơn bạn nhiều!"), back)
    }

    @Test fun xlsxFromExcel() {
        val back = XlsxShortcuts.read(javaClass.classLoader!!.getResourceAsStream("excel_shared.xlsx"))
        org.junit.Assert.assertEquals(mapOf("ko" to "không", "dc" to "được"), back)
    }

    @Test fun emojiMenu() {
        val k = kb(false)
        val d = ctx.resources.displayMetrics.density
        touch(k, MotionEvent.ACTION_DOWN, k.width * 0.19f, d * (44 + 6 + 3 * 53 + 21))
        ShadowLooper.idleMainLooper(500, java.util.concurrent.TimeUnit.MILLISECONDS)
        save(k, "13_emoji_menu")
    }

    @Test fun urlField() {
        val k = kb(false)
        k.setLayout(Page.LETTERS, FieldMode.URL)
        k.enterLabel = "đi"; k.enterAccent = true
        save(k, "8_chrome_url")
    }

    @Test fun trackpad() {
        val k = kb(false)
        val d = ctx.resources.displayMetrics.density
        touch(k, MotionEvent.ACTION_DOWN, k.width * 0.5f, d * (44 + 6 + 3 * 53 + 21))
        ShadowLooper.idleMainLooper(500, java.util.concurrent.TimeUnit.MILLISECONDS)
        save(k, "6_trackpad")
    }

    @Test fun emoji() {
        val e = EmojiView(ctx, Prefs(ctx))
        e.show(Palette(false), (300 * 3))
        save(e, "7_emoji")
    }
}
