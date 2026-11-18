package com.vudinhdat.datkey

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.TextView

object EmojiData {
    val categories: List<Pair<String, String>> = listOf(
        "😀" to "Mặt cười & Người",
        "🐻" to "Động vật & Thiên nhiên",
        "🍔" to "Đồ ăn & Đồ uống",
        "⚽" to "Hoạt động",
        "🚗" to "Du lịch & Địa điểm",
        "💡" to "Đồ vật",
        "❤️" to "Biểu tượng",
        "🏳️" to "Cờ",
    )

    val emojis: List<List<String>> = listOf(
        ("😀 😃 😄 😁 😆 😅 🤣 😂 🙂 🙃 🫠 😉 😊 😇 🥰 😍 🤩 😘 😗 ☺️ 😚 😙 🥲 😋 😛 😜 🤪 😝 🤑 🤗 🤭 🫢 🫣 🤫 🤔 🫡 " +
            "🤐 🤨 😐 😑 😶 🫥 😏 😒 🙄 😬 😮‍💨 🤥 😌 😔 😪 🤤 😴 😷 🤒 🤕 🤢 🤮 🤧 🥵 🥶 🥴 😵 🤯 🤠 🥳 🥸 😎 🤓 🧐 " +
            "😕 🫤 😟 🙁 ☹️ 😮 😯 😲 😳 🥺 🥹 😦 😧 😨 😰 😥 😢 😭 😱 😖 😣 😞 😓 😩 😫 🥱 😤 😡 😠 🤬 😈 👿 💀 ☠️ 💩 " +
            "🤡 👹 👺 👻 👽 👾 🤖 😺 😸 😹 😻 😼 😽 🙀 😿 😾 🙈 🙉 🙊 👋 🤚 🖐️ ✋ 🖖 🫱 🫲 👌 🤌 🤏 ✌️ 🤞 🫰 🤟 🤘 🤙 " +
            "👈 👉 👆 🖕 👇 ☝️ 🫵 👍 👎 ✊ 👊 🤛 🤜 👏 🙌 🫶 👐 🤲 🤝 🙏 ✍️ 💅 🤳 💪 🦾 🦵 🦶 👂 👃 🧠 🫀 🫁 🦷 🦴 👀 " +
            "👁️ 👅 👄 🫦 👶 🧒 👦 👧 🧑 👱 👨 🧔 👩 🧓 👴 👵 🙍 🙎 🙅 🙆 💁 🙋 🧏 🙇 🤦 🤷 👮 🕵️ 💂 👷 🤴 👸 👳 👲 🧕 " +
            "🤵 👰 🤰 🤱 👼 🎅 🤶 🦸 🦹 🧙 🧚 🧛 🧜 🧝 🧞 🧟 💆 💇 🚶 🧍 🧎 🏃 💃 🕺 👯 🧖 🧗 🤺 🏇 ⛷️ 🏂 🏌️ 🏄 🚣 🏊 " +
            "👫 👬 👭 💏 💑 👪 🗣️ 👤 👥 👣").split(' '),
        ("🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐻‍❄️ 🐨 🐯 🦁 🐮 🐷 🐽 🐸 🐵 🐔 🐧 🐦 🐤 🐣 🐥 🦆 🦅 🦉 🦇 🐺 🐗 🐴 🦄 🐝 🪱 🐛 🦋 " +
            "🐌 🐞 🐜 🪰 🪲 🪳 🦟 🦗 🕷️ 🦂 🐢 🐍 🦎 🦖 🦕 🐙 🦑 🦐 🦞 🦀 🐡 🐠 🐟 🐬 🐳 🐋 🦈 🐊 🐅 🐆 🦓 🦍 🦧 🐘 🦛 " +
            "🦏 🐪 🐫 🦒 🦘 🦬 🐃 🐂 🐄 🐎 🐖 🐏 🐑 🦙 🐐 🦌 🐕 🐩 🐈 🐓 🦃 🦚 🦜 🦢 🦩 🕊️ 🐇 🦝 🦨 🦡 🦫 🦦 🦥 🐁 🐀 🐿️ " +
            "🦔 🐾 🐉 🐲 🌵 🎄 🌲 🌳 🌴 🪵 🌱 🌿 ☘️ 🍀 🎍 🪴 🎋 🍃 🍂 🍁 🍄 🐚 🪨 🌾 💐 🌷 🌹 🥀 🌺 🌸 🌼 🌻 🌞 🌝 🌛 " +
            "🌜 🌚 🌕 🌖 🌗 🌘 🌑 🌒 🌓 🌔 🌙 🌎 🌍 🌏 🪐 💫 ⭐ 🌟 ✨ ⚡ ☄️ 💥 🔥 🌪️ 🌈 ☀️ 🌤️ ⛅ 🌥️ ☁️ 🌦️ 🌧️ ⛈️ 🌩️ 🌨️ " +
            "❄️ ☃️ ⛄ 🌬️ 💨 💧 💦 🫧 ☔ ☂️ 🌊 🌫️").split(' '),
        ("🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🫐 🍈 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🍆 🥑 🥦 🥬 🥒 🌶️ 🫑 🌽 🥕 🫒 🧄 🧅 🥔 🍠 🥐 🥯 🍞 " +
            "🥖 🥨 🧀 🥚 🍳 🧈 🥞 🧇 🥓 🥩 🍗 🍖 🦴 🌭 🍔 🍟 🍕 🫓 🥪 🥙 🧆 🌮 🌯 🫔 🥗 🥘 🫕 🥫 🍝 🍜 🍲 🍛 🍣 🍱 🥟 " +
            "🦪 🍤 🍙 🍚 🍘 🍥 🥠 🥮 🍢 🍡 🍧 🍨 🍦 🥧 🧁 🍰 🎂 🍮 🍭 🍬 🍫 🍿 🍩 🍪 🌰 🥜 🍯 🥛 🍼 🫖 ☕ 🍵 🧃 🥤 🧋 " +
            "🍶 🍺 🍻 🥂 🍷 🥃 🍸 🍹 🧉 🍾 🧊 🥄 🍴 🍽️ 🥣 🥡 🥢 🧂").split(' '),
        ("⚽ 🏀 🏈 ⚾ 🥎 🎾 🏐 🏉 🥏 🎱 🪀 🏓 🏸 🏒 🏑 🥍 🏏 🪃 🥅 ⛳ 🪁 🏹 🎣 🤿 🥊 🥋 🎽 🛹 🛼 🛷 ⛸️ 🥌 🎿 ⛷️ 🏂 " +
            "🪂 🏋️ 🤼 🤸 ⛹️ 🤺 🤾 🏌️ 🏇 🧘 🏄 🏊 🤽 🚣 🧗 🚵 🚴 🏆 🥇 🥈 🥉 🏅 🎖️ 🏵️ 🎗️ 🎫 🎟️ 🎪 🤹 🎭 🩰 🎨 🎬 🎤 " +
            "🎧 🎼 🎹 🥁 🪘 🎷 🎺 🪗 🎸 🪕 🎻 🎲 ♟️ 🎯 🎳 🎮 🎰 🧩").split(' '),
        ("🚗 🚕 🚙 🚌 🚎 🏎️ 🚓 🚑 🚒 🚐 🛻 🚚 🚛 🚜 🦯 🦽 🦼 🛴 🚲 🛵 🏍️ 🛺 🚨 🚔 🚍 🚘 🚖 🚡 🚠 🚟 🚃 🚋 🚞 🚝 🚄 " +
            "🚅 🚈 🚂 🚆 🚇 🚊 🚉 ✈️ 🛫 🛬 🛩️ 💺 🛰️ 🚀 🛸 🚁 🛶 ⛵ 🚤 🛥️ 🛳️ ⛴️ 🚢 ⚓ 🪝 ⛽ 🚧 🚦 🚥 🚏 🗺️ 🗿 🗽 🗼 🏰 " +
            "🏯 🏟️ 🎡 🎢 🎠 ⛲ ⛱️ 🏖️ 🏝️ 🏜️ 🌋 ⛰️ 🏔️ 🗻 🏕️ ⛺ 🛖 🏠 🏡 🏘️ 🏚️ 🏗️ 🏭 🏢 🏬 🏣 🏤 🏥 🏦 🏨 🏪 🏫 🏩 💒 🏛️ " +
            "⛪ 🕌 🕍 🛕 🕋 ⛩️ 🛤️ 🛣️ 🗾 🎑 🏞️ 🌅 🌄 🌠 🎇 🎆 🌇 🌆 🏙️ 🌃 🌌 🌉 🌁").split(' '),
        ("⌚ 📱 📲 💻 ⌨️ 🖥️ 🖨️ 🖱️ 🖲️ 🕹️ 🗜️ 💽 💾 💿 📀 📼 📷 📸 📹 🎥 📽️ 🎞️ 📞 ☎️ 📟 📠 📺 📻 🎙️ 🎚️ 🎛️ 🧭 ⏱️ ⏲️ ⏰ " +
            "🕰️ ⌛ ⏳ 📡 🔋 🪫 🔌 💡 🔦 🕯️ 🪔 🧯 🛢️ 💸 💵 💴 💶 💷 🪙 💰 💳 💎 ⚖️ 🪜 🧰 🪛 🔧 🔨 ⚒️ 🛠️ ⛏️ 🪚 🔩 ⚙️ 🪤 " +
            "🧱 ⛓️ 🧲 🔫 💣 🧨 🪓 🔪 🗡️ ⚔️ 🛡️ 🚬 ⚰️ 🪦 ⚱️ 🏺 🔮 📿 🧿 💈 ⚗️ 🔭 🔬 🕳️ 🩹 🩺 💊 💉 🩸 🧬 🦠 🧫 🧪 🌡️ 🧹 " +
            "🪠 🧺 🧻 🚽 🚰 🚿 🛁 🛀 🧼 🪥 🪒 🧽 🪣 🧴 🛎️ 🔑 🗝️ 🚪 🪑 🛋️ 🛏️ 🛌 🧸 🪆 🖼️ 🪞 🪟 🛍️ 🛒 🎁 🎈 🎏 🎀 🪄 🪅 " +
            "🎊 🎉 🎎 🏮 🎐 🧧 ✉️ 📩 📨 📧 💌 📥 📤 📦 🏷️ 🪧 📪 📫 📬 📭 📮 📯 📜 📃 📄 📑 🧾 📊 📈 📉 🗒️ 🗓️ 📆 📅 🗑️ " +
            "📇 🗃️ 🗳️ 🗄️ 📋 📁 📂 🗂️ 🗞️ 📰 📓 📔 📒 📕 📗 📘 📙 📚 📖 🔖 🧷 🔗 📎 🖇️ 📐 📏 🧮 📌 📍 ✂️ 🖊️ 🖋️ ✒️ 🖌️ " +
            "🖍️ 📝 ✏️ 🔍 🔎 🔏 🔐 🔒 🔓").split(' '),
        ("❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❤️‍🔥 ❤️‍🩹 ❣️ 💕 💞 💓 💗 💖 💘 💝 💟 ☮️ ✝️ ☪️ 🕉️ ☸️ ✡️ 🔯 🕎 ☯️ ☦️ 🛐 ⛎ ♈ ♉ " +
            "♊ ♋ ♌ ♍ ♎ ♏ ♐ ♑ ♒ ♓ 🆔 ⚛️ 🉑 ☢️ ☣️ 📴 📳 🈶 🈚 🈸 🈺 🈷️ ✴️ 🆚 💮 🉐 ㊙️ ㊗️ 🈴 🈵 🈹 🈲 🅰️ 🅱️ 🆎 " +
            "🆑 🅾️ 🆘 ❌ ⭕ 🛑 ⛔ 📛 🚫 💯 💢 ♨️ 🚷 🚯 🚳 🚱 🔞 📵 🚭 ❗ ❕ ❓ ❔ ‼️ ⁉️ 🔅 🔆 〽️ ⚠️ 🚸 🔱 ⚜️ 🔰 ♻️ ✅ " +
            "🈯 💹 ❇️ ✳️ ❎ 🌐 💠 Ⓜ️ 🌀 💤 🏧 🚾 ♿ 🅿️ 🛗 🈳 🈂️ 🛂 🛃 🛄 🛅 🚹 🚺 🚼 ⚧️ 🚻 🚮 🎦 📶 🈁 🔣 ℹ️ 🔤 🔡 🔠 " +
            "🆖 🆗 🆙 🆒 🆕 🆓 0️⃣ 1️⃣ 2️⃣ 3️⃣ 4️⃣ 5️⃣ 6️⃣ 7️⃣ 8️⃣ 9️⃣ 🔟 🔢 #️⃣ *️⃣ ⏏️ ▶️ ⏸️ ⏯️ ⏹️ ⏺️ ⏭️ ⏮️ ⏩ ⏪ ⏫ ⏬ " +
            "◀️ 🔼 🔽 ➡️ ⬅️ ⬆️ ⬇️ ↗️ ↘️ ↙️ ↖️ ↕️ ↔️ ↪️ ↩️ ⤴️ ⤵️ 🔀 🔁 🔂 🔄 🔃 🎵 🎶 ➕ ➖ ➗ ✖️ 🟰 ♾️ 💲 💱 ™️ ©️ ®️ 〰️ " +
            "➰ ➿ 🔚 🔙 🔛 🔝 🔜 ✔️ ☑️ 🔘 🔴 🟠 🟡 🟢 🔵 🟣 ⚫ ⚪ 🟤 🔺 🔻 🔸 🔹 🔶 🔷 🔳 🔲 ▪️ ▫️ ◾ ◽ ◼️ ◻️ 🟥 🟧 🟨 " +
            "🟩 🟦 🟪 ⬛ ⬜ 🟫 🔈 🔇 🔉 🔊 🔔 🔕 📣 📢 💬 💭 🗯️ ♠️ ♣️ ♥️ ♦️ 🃏 🎴 🀄 🕐 🕑 🕒 🕓 🕔 🕕 🕖 🕗 🕘 🕙 🕚 🕛").split(' '),
        ("🏳️ 🏴 🏁 🚩 🏳️‍🌈 🇻🇳 🇺🇸 🇬🇧 🇯🇵 🇰🇷 🇨🇳 🇹🇼 🇭🇰 🇸🇬 🇹🇭 🇱🇦 🇰🇭 🇲🇾 🇮🇩 🇵🇭 🇮🇳 🇦🇺 🇳🇿 🇨🇦 🇫🇷 🇩🇪 🇮🇹 🇪🇸 🇵🇹 " +
            "🇳🇱 🇧🇪 🇨🇭 🇦🇹 🇸🇪 🇳🇴 🇩🇰 🇫🇮 🇮🇸 🇮🇪 🇵🇱 🇨🇿 🇭🇺 🇷🇴 🇺🇦 🇷🇺 🇹🇷 🇬🇷 🇮🇱 🇸🇦 🇦🇪 🇪🇬 🇿🇦 🇳🇬 🇰🇪 🇲🇦 🇧🇷 🇦🇷 " +
            "🇨🇱 🇨🇴 🇵🇪 🇲🇽 🇨🇺 🇯🇲 🇺🇳 🇪🇺").split(' '),
    )
}

@SuppressLint("ViewConstructor")
class EmojiView(context: Context, private val prefs: Prefs) : LinearLayout(context) {

    interface Listener {
        fun onEmojiPicked(e: String)
        fun onEmojiDelete()
        fun onEmojiBack()
        fun onKeyFeedback(type: KeyType)
    }

    var listener: Listener? = null
    private val d = resources.displayMetrics.density
    private fun dp(v: Float) = (v * d).toInt()

    private val title = TextView(context)
    private val grid = GridView(context)
    private val tabBar = LinearLayout(context)
    private val tabs = ArrayList<TextView>()
    private var palette = Palette(false)
    private var current: List<String> = emptyList()
    private var currentIndex = -1
    private val handler = Handler(Looper.getMainLooper())
    private val glyphPaint = android.graphics.Paint()
    private val supported = HashMap<Int, List<String>>()

    private val adapter = object : BaseAdapter() {
        override fun getCount() = current.size
        override fun getItem(p: Int) = current[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, convertView: View?, parent: ViewGroup?): View {
            val tv = (convertView as? TextView) ?: TextView(context).apply {
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_DIP, 28f)
                layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46f))
            }
            tv.text = current[p]
            return tv
        }
    }

    init {
        orientation = VERTICAL
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f)
        title.setPadding(dp(14f), dp(8f), dp(14f), dp(2f))
        title.isAllCaps = true
        addView(title, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        grid.numColumns = 8
        grid.stretchMode = GridView.STRETCH_COLUMN_WIDTH
        grid.adapter = adapter
        grid.isVerticalScrollBarEnabled = false
        grid.selector = GradientDrawable().apply { setColor(Color.argb(40, 128, 128, 128)); cornerRadius = 8 * d }
        grid.setOnItemClickListener { _, _, pos, _ ->
            val e = current[pos]
            listener?.onKeyFeedback(KeyType.CHAR)
            listener?.onEmojiPicked(e)
            val r = ArrayList(prefs.recentEmoji); r.remove(e); r.add(0, e)
            prefs.recentEmoji = r.take(32)
        }
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        tabBar.orientation = HORIZONTAL
        tabBar.gravity = Gravity.CENTER_VERTICAL
        tabBar.setPadding(dp(4f), 0, dp(4f), dp(2f))
        addView(tabBar, LayoutParams(LayoutParams.MATCH_PARENT, dp(44f)))

        val abc = makeTab("ABC", 15f).apply { setOnClickListener { listener?.onKeyFeedback(KeyType.LETTERS); listener?.onEmojiBack() } }
        tabBar.addView(abc, LayoutParams(dp(52f), LayoutParams.MATCH_PARENT))
        val recentTab = makeTab("🕘", 19f)
        tabs.add(recentTab)
        tabBar.addView(recentTab, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        recentTab.setOnClickListener { select(-1) }
        for ((i, c) in EmojiData.categories.withIndex()) {
            val t = makeTab(c.first, 19f)
            t.setOnClickListener { select(i) }
            tabs.add(t)
            tabBar.addView(t, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
        val del = makeTab("⌫", 20f)
        del.setOnTouchListener(DeleteRepeater())
        tabBar.addView(del, LayoutParams(dp(52f), LayoutParams.MATCH_PARENT))
    }

    private inner class DeleteRepeater : OnTouchListener {
        private val r = object : Runnable {
            override fun run() { listener?.onEmojiDelete(); handler.postDelayed(this, 90) }
        }
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    listener?.onKeyFeedback(KeyType.DELETE); listener?.onEmojiDelete()
                    handler.postDelayed(r, 420)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(r)
            }
            return true
        }
    }

    private fun makeTab(text: String, size: Float) = TextView(context).apply {
        this.text = text
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, size)
    }

    fun show(p: Palette, height: Int) {
        palette = p
        setBackgroundColor(p.bg)
        title.setTextColor(p.dim)
        for (v in 0 until tabBar.childCount) (tabBar.getChildAt(v) as TextView).setTextColor(p.text)
        layoutParams = layoutParams?.apply { this.height = height } ?: LayoutParams(LayoutParams.MATCH_PARENT, height)
        select(if (prefs.recentEmoji.isEmpty()) 0 else -1)
    }

    private fun select(i: Int) {
        currentIndex = i
        current = if (i < 0) prefs.recentEmoji else supported.getOrPut(i) { EmojiData.emojis[i].filter { glyphPaint.hasGlyph(it) } }
        title.text = if (i < 0) "Thường dùng" else EmojiData.categories[i].second
        adapter.notifyDataSetChanged()
        grid.setSelection(0)
        for ((ti, t) in tabs.withIndex()) {
            val sel = ti - 1 == i
            t.background = if (sel) GradientDrawable().apply {
                setColor(palette.highlight); cornerRadius = 8 * d
            } else null
        }
    }
}
