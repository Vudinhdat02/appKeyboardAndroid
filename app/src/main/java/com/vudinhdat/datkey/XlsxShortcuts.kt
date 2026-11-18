package com.vudinhdat.datkey

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Đọc/ghi danh sách cụm từ thay thế dưới dạng file Excel (.xlsx), cột A = viết tắt, cột B = cụm từ. */
object XlsxShortcuts {
    private const val HEADER_KEY = "Viết tắt"
    private const val HEADER_VALUE = "Cụm từ"

    private fun esc(s: String) = buildString {
        for (c in s) when (c) {
            '&' -> append("&amp;"); '<' -> append("&lt;"); '>' -> append("&gt;"); '"' -> append("&quot;")
            else -> if (c.code >= 0x20 || c == '\n' || c == '\t') append(c)
        }
    }

    fun write(map: Map<String, String>, out: OutputStream) {
        val rows = StringBuilder()
        val all = listOf(HEADER_KEY to HEADER_VALUE) + map.toSortedMap().toList()
        for ((i, p) in all.withIndex()) {
            val r = i + 1
            rows.append("<row r=\"$r\">")
            rows.append("<c r=\"A$r\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${esc(p.first)}</t></is></c>")
            rows.append("<c r=\"B$r\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${esc(p.second)}</t></is></c>")
            rows.append("</row>")
        }
        val files = linkedMapOf(
            "[Content_Types].xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""",
            "_rels/.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""",
            "xl/workbook.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Cum tu" sheetId="1" r:id="rId1"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""",
            "xl/worksheets/sheet1.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><cols><col min="1" max="1" width="16" customWidth="1"/><col min="2" max="2" width="48" customWidth="1"/></cols><sheetData>$rows</sheetData></worksheet>""",
        )
        ZipOutputStream(out).use { z ->
            for ((name, content) in files) {
                z.putNextEntry(ZipEntry(name)); z.write(content.toByteArray(Charsets.UTF_8)); z.closeEntry()
            }
        }
    }

    fun read(input: InputStream): Map<String, String> {
        val entries = HashMap<String, ByteArray>()
        ZipInputStream(input).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (!e.isDirectory) entries[e.name] = z.readBytes()
            }
        }
        val shared = entries["xl/sharedStrings.xml"]?.let { parseShared(it) } ?: emptyList()
        val sheetName = entries.keys.filter { it.startsWith("xl/worksheets/") && it.endsWith(".xml") }.minOrNull()
            ?: throw IllegalArgumentException("Không tìm thấy trang tính")
        val result = LinkedHashMap<String, String>()
        for ((i, row) in parseSheet(entries[sheetName]!!, shared).withIndex()) {
            val k = row.getOrNull(0)?.trim().orEmpty()
            val v = row.getOrNull(1).orEmpty()
            if (i == 0 && k.equals(HEADER_KEY, true)) continue
            if (k.isNotEmpty() && v.isNotEmpty() && !k.contains(' ')) result[k.lowercase()] = v
        }
        return result
    }

    private fun parser(bytes: ByteArray): XmlPullParser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        setInput(ByteArrayInputStream(bytes), "UTF-8")
    }

    private fun parseShared(bytes: ByteArray): List<String> {
        val p = parser(bytes)
        val list = ArrayList<String>()
        var cur: StringBuilder? = null
        var inT = false
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) { "si" -> cur = StringBuilder(); "t" -> inT = true }
                XmlPullParser.TEXT -> if (inT) cur?.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) { "t" -> inT = false; "si" -> { list.add(cur.toString()); cur = null } }
            }
        }
        return list
    }

    private fun colIndex(ref: String?): Int {
        if (ref == null) return -1
        var n = 0
        for (c in ref) { if (c in 'A'..'Z') n = n * 26 + (c - 'A' + 1) else break }
        return n - 1
    }

    private fun parseSheet(bytes: ByteArray, shared: List<String>): List<List<String>> {
        val p = parser(bytes)
        val rows = ArrayList<List<String>>()
        var row: MutableList<String>? = null
        var col = -1; var type: String? = null
        var text = StringBuilder(); var inValue = false
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "row" -> row = ArrayList()
                    "c" -> {
                        val r = row ?: continue
                        col = colIndex(p.getAttributeValue(null, "r")).let { if (it < 0) r.size else it }
                        type = p.getAttributeValue(null, "t"); text = StringBuilder()
                    }
                    "v", "t" -> inValue = true
                }
                XmlPullParser.TEXT -> if (inValue) text.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "v", "t" -> inValue = false
                    "c" -> {
                        val r = row ?: continue
                        val value = if (type == "s") shared.getOrNull(text.toString().trim().toIntOrNull() ?: -1).orEmpty() else text.toString()
                        while (r.size <= col) r.add("")
                        r[col] = value
                    }
                    "row" -> { row?.let { rows.add(it) }; row = null }
                }
            }
        }
        return rows
    }
}
