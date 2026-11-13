package dev.tzv

import android.app.Activity
import android.os.Bundle
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import android.widget.TextView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val icu = android.icu.util.TimeZone.getTZDataVersion()
        val tzVersion = runCatching {
            File("/apex/com.android.tzdata/etc/tz/tz_version").readText().trim()
        }.getOrElse { "n/a: $it" }
        val javaTz = java.util.TimeZone.getDefault().id

        val tzApex = apexVersion("tzdata")
        val i18nApex = apexVersion("i18n")

        setContentView(TextView(this).apply {
            textSize = 20f
            setPadding(48, 96, 48, 48)
            setTextIsSelectable(true)
            text = "ICU tzdata: $icu\ntz_version: $tzVersion\njava.util.TimeZone: $javaTz\ntzdata APEX: $tzApex\ni18n APEX: $i18nApex"
        })
    }

    /** Version of the APEX module com.android.<name>, read from the filesystem (no permission needed). */
    private fun apexVersion(name: String): String {
        val module = "com.android.$name"
        val infoList = runCatching { fromInfoList(module, name) }
        infoList.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return "[apex-info-list.xml] " + it.joinToString("; ") }
        val dir = runCatching { fromApexDir(module, name) }
        dir.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return "[/apex dir] " + it.joinToString("; ") }
        fun reason(r: Result<List<String>>) = r.exceptionOrNull()?.toString() ?: "no matching entry"
        return "n/a: apex-info-list.xml: ${reason(infoList)}; /apex dir: ${reason(dir)}"
    }

    private fun fromInfoList(module: String, keyword: String): List<String> {
        val result = ArrayList<String>()
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        File("/apex/apex-info-list.xml").inputStream().use { input ->
            val parser = Xml.newPullParser()
            parser.setInput(input, null)
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "apex-info") {
                    fun attr(n: String) = parser.getAttributeValue(null, n)
                    val moduleName = attr("moduleName") ?: ""
                    if (moduleName == module || moduleName.contains(keyword)) {
                        val updated = attr("lastUpdateMillis")?.toLongOrNull()?.let { " updated=" + format.format(Date(it)) } ?: ""
                        result.add("$moduleName versionCode=${attr("versionCode")} isFactory=${attr("isFactory")} isActive=${attr("isActive")}$updated")
                    }
                }
                event = parser.next()
            }
        }
        return result
    }

    private fun fromApexDir(module: String, keyword: String): List<String> {
        val names = File("/apex").list() ?: throw java.io.IOException("cannot list /apex")
        return names.filter { it.startsWith(module) || it.contains(keyword) }.sorted()
            .map { if ("@" in it) "$it (versionCode=${it.substringAfter('@')})" else it }
    }
}
