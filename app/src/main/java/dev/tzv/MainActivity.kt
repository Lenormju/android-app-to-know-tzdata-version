package dev.tzv

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val lines = mutableListOf<String>()
        lines += "ICU tzdata: ${android.icu.util.TimeZone.getTZDataVersion()}"
        lines += "tz_version: " + probe { File("/apex/com.android.tzdata/etc/tz/tz_version").readText().trim() }
        lines += "java.util.TimeZone: ${java.util.TimeZone.getDefault().id}"
        lines += "tzdata APEX manifest: " + probe { manifestSummary("/apex/com.android.tzdata/apex_manifest.pb") }
        lines += "i18n APEX manifest: " + probe { manifestSummary("/apex/com.android.i18n/apex_manifest.pb") }
        lines += "tzdata file header: " + probe { tzdataHeader("/apex/com.android.tzdata/etc/tz/tzdata") }
        lines += "/data/misc/zoneinfo/current/tzdata: " + probe {
            val f = File("/data/misc/zoneinfo/current/tzdata")
            "exists=${f.exists()} canRead=${f.canRead()}"
        }
        lines += "icu_tzdata.dat: " + probe {
            val f = File("/apex/com.android.tzdata/etc/icu/icu_tzdata.dat")
            "exists=${f.exists()} size=${f.length()} bytes"
        }
        lines += "/apex/com.android.i18n/etc/icu: " + probe {
            (File("/apex/com.android.i18n/etc/icu").list() ?: throw java.io.IOException("cannot list directory"))
                .sorted().joinToString(", ")
        }
        for (id in listOf("Africa/Cairo", "America/Asuncion")) lines += offsets(id)

        setContentView(TextView(this).apply {
            textSize = 16f
            setPadding(48, 96, 48, 48)
            setTextIsSelectable(true)
            text = lines.joinToString("\n\n")
        })
    }

    /** Runs [block]; on failure returns the exception text so the user can see what is blocked. */
    private fun probe(block: () -> String): String = runCatching(block).getOrElse { "n/a: $it" }

    private fun manifestSummary(path: String): String {
        val (name, version) = parseApexManifest(File(path).readBytes())
        return "name=$name version=$version"
    }

    private fun tzdataHeader(path: String): String {
        val buf = ByteArray(12)
        val n = File(path).inputStream().use { it.read(buf) }
        return String(buf, 0, maxOf(n, 0), Charsets.US_ASCII).substringBefore('\u0000')
    }

    /**
     * Minimal ApexManifest decoder: field 1 = name (string), field 2 = version (int64 varint).
     * Other fields are skipped according to their wire type.
     */
    private fun parseApexManifest(data: ByteArray): Pair<String?, Long?> {
        var pos = 0
        fun varint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                if (pos >= data.size || shift > 63) throw java.io.IOException("bad varint at $pos")
                val b = data[pos++].toInt() and 0xFF
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
            }
        }
        fun skip(count: Long) {
            if (count < 0 || count > data.size - pos) throw java.io.IOException("truncated field at $pos")
            pos += count.toInt()
        }
        var name: String? = null
        var version: Long? = null
        while (pos < data.size) {
            val tag = varint()
            val field = (tag ushr 3).toInt()
            when (val wireType = (tag and 7).toInt()) {
                0 -> { val v = varint(); if (field == 2) version = v }
                1 -> skip(8)
                2 -> {
                    val len = varint()
                    val start = pos
                    skip(len)
                    if (field == 1) name = String(data, start, len.toInt(), Charsets.UTF_8)
                }
                5 -> skip(4)
                else -> throw java.io.IOException("unsupported wire type $wireType at $pos")
            }
        }
        return name to version
    }

    /**
     * Raw offsets on 2025-07-15T12:00:00Z, as seen by three APIs, to show which data each one uses.
     * Egypt DST was reinstated in 2023: Cairo in July is +03:00 with tzdata >= 2023c, +02:00 with 2022f.
     * Paraguay moved to permanent -03:00 in 2024: Asuncion in July is -03:00 with tzdata >= 2024b, -04:00 with 2022f.
     */
    private fun offsets(id: String): String {
        val millis = 1752580800000L // 2025-07-15T12:00:00Z
        val javaUtil = probe { formatOffset(java.util.TimeZone.getTimeZone(id).getOffset(millis)) }
        val icu = probe { formatOffset(android.icu.util.TimeZone.getTimeZone(id).getOffset(millis)) }
        val javaTime = if (Build.VERSION.SDK_INT >= 26) {
            probe { formatOffset(ZoneId.of(id).rules.getOffset(Instant.ofEpochMilli(millis)).totalSeconds * 1000) }
        } else {
            "n/a (API < 26)"
        }
        return "$id 2025-07-15T12:00Z\njava.util.TimeZone: $javaUtil\nandroid.icu.util.TimeZone: $icu\njava.time.ZoneId: $javaTime"
    }

    private fun formatOffset(ms: Int): String {
        val minutes = abs(ms) / 60000
        return "%s%02d:%02d".format(if (ms < 0) "-" else "+", minutes / 60, minutes % 60)
    }
}
