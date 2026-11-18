package com.vudinhdat.datkey

import android.content.Context
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout

class DatKeyService : InputMethodService(), KeyboardView.Listener, EmojiView.Listener {

    private lateinit var prefs: Prefs
    private lateinit var dict: Dictionary
    private val engine = VietEngine()

    private var root: LinearLayout? = null
    private var kb: KeyboardView? = null
    private var emoji: EmojiView? = null

    private var vietnamese = true
    private var telexAllowed = true
    private var suggestAllowed = true
    private var learnAllowed = true
    private var correctAllowed = true
    private var fieldMode = FieldMode.TEXT

    private var prevWord: String? = null
    private var autoSpaced = false
    private var lastWasSpace = false
    private var lastCorrection: Pair<String, String>? = null // (gốc, đã sửa + dấu cách)
    private var shortcuts: Map<String, String> = emptyMap()

    private var audio: AudioManager? = null
    private var lastReset = 0L
    private var lastEdit = 0L
    private var vibrator: Vibrator? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        dict = Dictionary(this).also { it.load() }
        lastReset = prefs.sp.getLong("user_reset", 0L)
        audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else @Suppress("DEPRECATION") (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
    }

    override fun onCreateInputView(): View {
        val r = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val k = KeyboardView(this).also { it.listener = this }
        val e = EmojiView(this, prefs).also { it.listener = this; it.visibility = View.GONE }
        r.addView(k, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        r.addView(e, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0))
        root = r; kb = k; emoji = e
        return r
    }

    private fun isDark(): Boolean = when (prefs.theme) {
        "dark" -> true
        "light" -> false
        else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val k = kb ?: return
        val reset = prefs.sp.getLong("user_reset", 0L)
        if (reset > lastReset) { lastReset = reset; dict.clearUser() }
        engine.reset()
        engine.method = if (prefs.vni) VietEngine.Method.VNI else VietEngine.Method.TELEX
        engine.modernTone = prefs.modernTone
        vietnamese = prefs.vietnamese
        shortcuts = prefs.shortcuts
        prevWord = null; autoSpaced = false; lastWasSpace = false; lastCorrection = null

        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        val password = cls == InputType.TYPE_CLASS_TEXT && variation in setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        ) || (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        val email = cls == InputType.TYPE_CLASS_TEXT && (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)
        val uri = cls == InputType.TYPE_CLASS_TEXT && variation == InputType.TYPE_TEXT_VARIATION_URI
        val noSuggest = info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0
        fieldMode = when { email -> FieldMode.EMAIL; uri -> FieldMode.URL; else -> FieldMode.TEXT }
        telexAllowed = !password && !email
        suggestAllowed = prefs.predictive && !password && !email && cls == InputType.TYPE_CLASS_TEXT
        correctAllowed = prefs.autoCorrect && suggestAllowed && !noSuggest && !uri
        learnAllowed = prefs.learn && !password && (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) == 0

        k.palette = Palette(isDark(), Palette.tintOf(prefs.color))
        k.showPreview = prefs.preview
        k.capsLockEnabled = prefs.capsLock
        k.numberRow = prefs.numberRow
        k.showEmoji = prefs.showEmoji
        k.showGlobe = prefs.showGlobe
        k.showStrip = prefs.predictive
        k.heightScale = prefs.heightScale
        k.vietnamese = vietnamese
        updateEnterKey(info)
        val numeric = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE || cls == InputType.TYPE_CLASS_DATETIME
        k.setLayout(if (numeric) Page.NUMBERS else Page.LETTERS, fieldMode)
        showKeyboardPanel()
        k.shift = ShiftState.OFF
        updateAutoShift()
        updateSuggestions()
    }

    private fun updateEnterKey(info: EditorInfo) {
        val k = kb ?: return
        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        val noAction = info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        val vi = vietnamese
        val (label, accent) = if (noAction) "" to false else when (action) {
            EditorInfo.IME_ACTION_SEARCH -> (if (vi) "tìm" else "search") to true
            EditorInfo.IME_ACTION_GO -> "→" to true
            EditorInfo.IME_ACTION_SEND -> (if (vi) "gửi" else "send") to true
            EditorInfo.IME_ACTION_NEXT -> (if (vi) "tiếp" else "next") to false
            EditorInfo.IME_ACTION_DONE -> (if (vi) "xong" else "done") to true
            EditorInfo.IME_ACTION_PREVIOUS -> (if (vi) "trước" else "previous") to false
            else -> "" to false
        }
        k.enterLabel = label; k.enterAccent = accent
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        engine.reset()
        dict.save()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        engine.reset()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        val recentEdit = android.os.SystemClock.uptimeMillis() - lastEdit < 300
        if (!engine.isEmpty() && !recentEdit && (candidatesStart < 0 || newSelStart != newSelEnd || newSelEnd != candidatesEnd)) {
            // Người dùng di chuyển con trỏ ra chỗ khác
            engine.reset()
            currentInputConnection?.finishComposingText()
            lastCorrection = null; autoSpaced = false
            updateSuggestions()
        }
        if (engine.isEmpty()) updateAutoShift()
    }

    // ------------------------------------------------------------ Hiển thị

    private fun showKeyboardPanel() {
        kb?.visibility = View.VISIBLE
        emoji?.visibility = View.GONE
    }

    private fun updateComposing() {
        val ic = currentInputConnection ?: return
        ic.setComposingText(engine.composed(), 1)
        updateSuggestions()
    }

    private fun updateAutoShift() {
        val k = kb ?: return
        if (k.shift == ShiftState.LOCKED) return
        if (k.page != Page.LETTERS || !prefs.autoCap || !engine.isEmpty()) {
            if (k.shift == ShiftState.ON && !engine.isEmpty()) k.shift = ShiftState.OFF
            return
        }
        val ei = currentInputEditorInfo ?: return
        val caps = currentInputConnection?.getCursorCapsMode(ei.inputType) ?: 0
        k.shift = if (caps != 0) ShiftState.ON else ShiftState.OFF
    }

    private fun correctionFor(word: String): String? {
        shortcuts[word.lowercase()]?.let { return it }
        if (!correctAllowed) return null
        if (vietnamese && telexAllowed) return null
        CONTRACTIONS[word.lowercase()]?.let { return Dictionary.matchCase(it, word) }
        if (word == "i") return "I"
        if (word.length > 1 && word.any { it.isUpperCase() } && word.drop(1).any { it.isUpperCase() }) return null
        return dict.correct(word)
    }

    private fun updateSuggestions() {
        val k = kb ?: return
        if (!suggestAllowed) { k.suggestions = emptyList(); return }
        val w = engine.composed()
        val list = ArrayList<Suggestion>()
        if (w.isNotEmpty()) {
            val corr = correctionFor(w)
            val vi = vietnamese && telexAllowed
            val cands = dict.suggest(w, vi, 4).filter { it != w && it != corr }.toMutableList()
            if (vi) {
                val raw = engine.rawText()
                if (raw != w && raw.length > 1 && dict.isKnownEnglish(raw)) { cands.remove(raw); cands.add(0, raw) }
            }
            list.add(Suggestion(w, quoted = true))
            if (corr != null) list.add(Suggestion(corr, highlighted = true))
            for (c in cands) { if (list.size >= 3) break; list.add(Suggestion(c)) }
        } else {
            val before = currentInputConnection?.getTextBeforeCursor(1, 0)?.toString() ?: ""
            if (before.isEmpty() || before == " " || before == "\n") {
                dict.predict(if (before == " ") prevWord ?: "" else "", vietnamese).forEach { list.add(Suggestion(it)) }
            }
        }
        k.suggestions = list
    }

    // ------------------------------------------------------------ Soạn chữ

    private fun isWordChar(c: Char): Boolean =
        c.isLetter() || (c == '\'' && !engine.isEmpty()) ||
            (vietnamese && telexAllowed && engine.method == VietEngine.Method.VNI && c in '0'..'9' && !engine.isEmpty())

    /** Chốt từ đang soạn. applyCorrection = có tự sửa / thay thế văn bản hay không. */
    private fun commitWord(applyCorrection: Boolean, separator: String) {
        if (engine.isEmpty()) return
        val ic = currentInputConnection ?: return
        val word = engine.composed()
        var out = word
        if (applyCorrection) correctionFor(word)?.let { out = it }
        ic.commitText(out, 1)
        engine.reset()
        lastCorrection = if (out != word) word to (out + separator) else null
        if (learnAllowed && suggestAllowed) dict.learn(prevWord, out)
        prevWord = out.substringAfterLast(' ')
    }

    override fun onChar(text: String) {
        val ic = currentInputConnection ?: return
        lastEdit = android.os.SystemClock.uptimeMillis()
        val k = kb ?: return
        lastWasSpace = false
        if (text.length == 1) {
            val c = text[0]
            if (autoSpaced && prefs.smartPunct && c in ".,?!:;)" ) {
                val before = ic.getTextBeforeCursor(1, 0)
                if (before == " ") ic.deleteSurroundingText(1, 0)
            }
            autoSpaced = false
            if (isWordChar(c)) {
                lastCorrection = null
                if (vietnamese && telexAllowed) {
                    if (engine.isEmpty()) recomposeWordBeforeCursor()
                    if (!engine.process(c)) engine.appendLiteral(c)
                } else engine.appendLiteral(c)
                updateComposing()
            } else {
                commitWord(true, text)
                ic.commitText(text, 1)
                if (c == '\'' && k.page != Page.LETTERS) k.setLayout(Page.LETTERS)
                updateSuggestions()
            }
        } else {
            // Ký tự nhiều điểm mã (emoji, ".com", chữ có dấu từ bảng nhấn giữ)
            autoSpaced = false
            if (text.all { it.isLetter() }) {
                text.forEach { engine.appendLiteral(it) }
                updateComposing()
            } else {
                commitWord(false, "")
                ic.commitText(text, 1)
                updateSuggestions()
            }
        }
        if (k.shift == ShiftState.ON) k.shift = ShiftState.OFF
        if (engine.isEmpty()) updateAutoShift()
    }

    override fun onDelete(repeat: Int) {
        val ic = currentInputConnection ?: return
        lastEdit = android.os.SystemClock.uptimeMillis()
        lastWasSpace = false; autoSpaced = false
        val corr = lastCorrection
        if (corr != null && repeat == 0 && engine.isEmpty()) {
            lastCorrection = null
            val before = ic.getTextBeforeCursor(corr.second.length, 0)?.toString()
            if (before == corr.second) {
                ic.deleteSurroundingText(corr.second.length, 0)
                ic.commitText(corr.first, 1)
                if (learnAllowed) dict.learn(null, corr.first)
                updateSuggestions(); updateAutoShift()
                return
            }
        }
        lastCorrection = null
        if (!engine.isEmpty()) {
            if (repeat > 14) engine.reset() else engine.backspace()
            if (engine.isEmpty()) ic.commitText("", 1) else ic.setComposingText(engine.composed(), 1)
        } else {
            val sel = ic.getSelectedText(0)
            if (!sel.isNullOrEmpty()) ic.commitText("", 1)
            else if (repeat > 14) deleteWordBefore()
            else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }
        updateSuggestions()
        if (engine.isEmpty()) updateAutoShift()
    }

    /**
     * Con trỏ đang đứng ngay sau một từ (vd. vừa xoá dấu cách): nạp lại từ đó vào bộ gõ
     * để phím tiếp theo có thể thêm dấu cho nó.
     */
    private fun recomposeWordBeforeCursor() {
        val ic = currentInputConnection ?: return
        if (!ic.getSelectedText(0).isNullOrEmpty()) return
        val after = ic.getTextAfterCursor(1, 0)?.toString() ?: ""
        if (after.isNotEmpty() && after[0].isLetter()) return
        val before = ic.getTextBeforeCursor(32, 0)?.toString() ?: return
        var i = before.length
        while (i > 0 && before[i - 1].isLetter()) i--
        val word = before.substring(i)
        if (word.isEmpty() || word.length > 20) return
        val et = ic.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0) ?: return
        val end = et.startOffset + et.selectionStart
        if (et.selectionStart < 0 || end < word.length) return
        if (!ic.setComposingRegion(end - word.length, end)) return
        engine.load(word)
        lastEdit = android.os.SystemClock.uptimeMillis()
    }

    private fun deleteWordBefore() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(64, 0)?.toString() ?: return
        if (before.isEmpty()) return
        var i = before.length
        while (i > 0 && before[i - 1].isWhitespace()) i--
        while (i > 0 && !before[i - 1].isWhitespace()) i--
        ic.deleteSurroundingText(before.length - i, 0)
    }

    override fun onSpace() {
        val ic = currentInputConnection ?: return
        lastEdit = android.os.SystemClock.uptimeMillis()
        val k = kb ?: return
        if (engine.isEmpty() && lastWasSpace && prefs.doubleSpacePeriod) {
            val before = ic.getTextBeforeCursor(2, 0)?.toString() ?: ""
            if (before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()) {
                ic.deleteSurroundingText(1, 0)
                ic.commitText(". ", 1)
                lastWasSpace = false
                updateAutoShift(); updateSuggestions()
                return
            }
        }
        commitWord(true, " ")
        ic.commitText(" ", 1)
        lastWasSpace = true
        autoSpaced = false
        if (k.page != Page.LETTERS) k.setLayout(Page.LETTERS)
        updateAutoShift()
        updateSuggestions()
    }

    override fun onEnter() {
        val ic = currentInputConnection ?: return
        val ei = currentInputEditorInfo
        lastWasSpace = false; autoSpaced = false
        commitWord(true, "\n")
        lastCorrection = null
        val action = (ei?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        val noAction = ((ei?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (noAction || action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        } else ic.performEditorAction(action)
        prevWord = null
        updateAutoShift(); updateSuggestions()
    }

    override fun onShiftTap() {
        val k = kb ?: return
        k.shift = if (k.shift == ShiftState.OFF) ShiftState.ON else ShiftState.OFF
    }

    override fun onPageChange(page: Page) {
        val k = kb ?: return
        k.setLayout(page)
        if (page == Page.LETTERS) updateAutoShift()
    }

    override fun onEmoji() {
        val k = kb ?: return
        commitWord(true, "")
        val h = k.height
        k.visibility = View.GONE
        emoji?.visibility = View.VISIBLE
        emoji?.show(k.palette, h)
    }

    override fun onGlobe() {
        val k = kb ?: return
        commitWord(false, "")
        vietnamese = !vietnamese
        prefs.vietnamese = vietnamese
        k.vietnamese = vietnamese
        k.refreshLayout()
        currentInputEditorInfo?.let { updateEnterKey(it) }
        updateSuggestions()
    }

    override fun onGlobeLong() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    override fun onOpenSettings() {
        commitWord(false, "")
        requestHideSelf(0)
        startActivity(android.content.Intent(this, SettingsActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun onHide() {
        commitWord(false, "")
        requestHideSelf(0)
    }

    override fun onSuggestion(s: Suggestion) {
        val ic = currentInputConnection ?: return
        lastEdit = android.os.SystemClock.uptimeMillis()
        val text = s.text
        ic.commitText("$text ", 1)
        engine.reset()
        lastCorrection = null
        if (learnAllowed) dict.learn(prevWord, text)
        prevWord = text.substringAfterLast(' ')
        autoSpaced = true; lastWasSpace = false
        kb?.let { if (it.shift == ShiftState.ON) it.shift = ShiftState.OFF }
        updateAutoShift()
        updateSuggestions()
    }

    override fun onTrackpad(active: Boolean) {
        if (active) {
            commitWord(false, "")
            currentInputConnection?.finishComposingText()
            kb?.suggestions = emptyList()
        } else updateSuggestions()
    }

    override fun onCursorMove(dx: Int, dy: Int) {
        val code = if (dx < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        repeat(kotlin.math.abs(dx)) { sendDownUpKeyEvents(code) }
        val vcode = if (dy < 0) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN
        repeat(kotlin.math.abs(dy)) { sendDownUpKeyEvents(vcode) }
        if (dx != 0 || dy != 0) onKeyFeedback(KeyType.SPACE)
    }

    override fun onKeyFeedback(type: KeyType) {
        if (prefs.haptic) {
            val v = vibrator
            if (v != null && v.hasVibrator()) {
                runCatching {
                    if (Build.VERSION.SDK_INT >= 29) v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                    else v.vibrate(VibrationEffect.createOneShot(12, 90))
                }
            }
        }
        if (prefs.sound) {
            val a = audio ?: return
            if (a.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
            val fx = when (type) {
                KeyType.DELETE -> AudioManager.FX_KEYPRESS_DELETE
                KeyType.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                KeyType.ENTER -> AudioManager.FX_KEYPRESS_RETURN
                else -> AudioManager.FX_KEYPRESS_STANDARD
            }
            a.playSoundEffect(fx, -1f)
        }
    }

    // Emoji
    override fun onEmojiPicked(e: String) {
        currentInputConnection?.commitText(e, 1)
        lastWasSpace = false; autoSpaced = false
    }

    override fun onEmojiDelete() = onDelete(0)

    override fun onEmojiBack() {
        showKeyboardPanel()
        kb?.setLayout(Page.LETTERS)
        updateAutoShift(); updateSuggestions()
    }

    companion object {
        val CONTRACTIONS = mapOf(
            "dont" to "don't", "cant" to "can't", "wont" to "won't", "im" to "I'm", "ive" to "I've",
            "youre" to "you're", "theyre" to "they're", "isnt" to "isn't", "didnt" to "didn't",
            "doesnt" to "doesn't", "wasnt" to "wasn't", "shouldnt" to "shouldn't", "couldnt" to "couldn't",
            "wouldnt" to "wouldn't", "thats" to "that's", "whats" to "what's", "arent" to "aren't",
            "havent" to "haven't", "hasnt" to "hasn't", "werent" to "weren't", "youve" to "you've",
            "theyve" to "they've", "itll" to "it'll", "youll" to "you'll", "theres" to "there's"
        )
    }
}
