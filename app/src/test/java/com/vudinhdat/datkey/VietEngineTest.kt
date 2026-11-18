package com.vudinhdat.datkey

import org.junit.Assert.assertEquals
import org.junit.Test

class VietEngineTest {
    private fun type(keys: String, vni: Boolean = false, modern: Boolean = true): String {
        val e = VietEngine()
        e.method = if (vni) VietEngine.Method.VNI else VietEngine.Method.TELEX
        e.modernTone = modern
        val out = StringBuilder()
        for (c in keys) {
            if (c == '<') { e.backspace(); continue }
            if (!e.process(c)) { out.append(e.composed()).append(c); e.reset() }
        }
        return out.append(e.composed()).toString()
    }

    @Test fun telexBasics() {
        assertEquals("Việt Nam", type("Vieejt Nam"))
        assertEquals("tiếng", type("tieesng"))
        assertEquals("tiếng", type("tiengse"))
        assertEquals("người", type("nguowif"))
        assertEquals("người", type("nguoiwf"))
        assertEquals("đường", type("dduwowngf"))
        assertEquals("đường", type("dduongwf"))
        assertEquals("được", type("dduwowcj"))
        assertEquals("hoà", type("hoaf"))
        assertEquals("hòa", type("hoaf", modern = false))
        assertEquals("thuý", type("thuys"))
        assertEquals("quả", type("quar"))
        assertEquals("giữ", type("giuwx"))
        assertEquals("gì", type("gif"))
        assertEquals("mưa", type("muaw"))
        assertEquals("ư", type("w"))
        assertEquals("tư", type("tw"))
        assertEquals("khuya", type("khuya"))
        assertEquals("nguyễn", type("nguyeenx"))
        assertEquals("Ơ", type("Ow"))
        assertEquals("ĐƯỜNG", type("DDUWOWNGF"))
        assertEquals("hoặc", type("hoawcj"))
        assertEquals("ngoài", type("ngoaif"))
        assertEquals("cửa", type("cuwar"))
        assertEquals("bỏ", type("bor"))
    }

    @Test fun telexUndoAndEnglish() {
        assertEquals("as", type("ass"))
        assertEquals("aa", type("aaa"))
        assertEquals("dd", type("ddd"))
        assertEquals("w", type("ww"))
        assertEquals("text", type("text"))
        assertEquals("we", type("we"))
        assertEquals("hello", type("hello"))
        assertEquals("first", type("first"))
        assertEquals("windows", type("windows"))
        assertEquals("baz", type("baz"))
        assertEquals("ba", type("basz"))
    }

    @Test fun abbreviations() {
        assertEquals("đg", type("ddg"))
        assertEquals("đc", type("ddc"))
        assertEquals("đt", type("ddt"))
        assertEquals("Đk", type("DDk"))
        assertEquals("đk đg đc", type("ddk ddg ddc"))
        assertEquals("được", type("dduwowcj"))
    }

    @Test fun backspace() {
        assertEquals("tiế", type("tieesng<<"))
        assertEquals("việ", type("vieejt<"))
    }

    @Test fun reloadWord() {
        val e = VietEngine()
        e.load("tuyên"); e.process('f'); assertEquals("tuyền", e.composed())
        e.load("quang"); e.process('r'); assertEquals("quảng", e.composed())
        e.load("Viet"); e.process('e'); e.process('j'); assertEquals("Việt", e.composed())
    }

    @Test fun vni() {
        assertEquals("Việt Nam", type("Vie65t Nam", vni = true))
        assertEquals("đường", type("d9uong72", vni = true))
        assertEquals("người", type("nguoi72", vni = true))
        assertEquals("a1", type("a11", vni = true))
    }
}
