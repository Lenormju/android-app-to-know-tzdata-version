package dev.tzv

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageInfo
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import java.io.File

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val icu = android.icu.util.TimeZone.getTZDataVersion()
        val tzVersion = runCatching {
            File("/apex/com.android.tzdata/etc/tz/tz_version").readText().trim()
        }.getOrElse { "n/a: $it" }
        val javaTz = java.util.TimeZone.getDefault().id

        val tzApex = runCatching { apexVersion("tzdata", listOf("com.google.android.tzdata3", "com.android.tzdata", "com.google.android.tzdata")) }
            .getOrElse { "n/a: $it" }
        val i18nApex = runCatching { apexVersion("i18n", listOf("com.google.android.i18n", "com.android.i18n")) }
            .getOrElse { "n/a: $it" }

        setContentView(TextView(this).apply {
            textSize = 20f
            setPadding(48, 96, 48, 48)
            setTextIsSelectable(true)
            text = "ICU tzdata: $icu\ntz_version: $tzVersion\njava.util.TimeZone: $javaTz\ntzdata APEX: $tzApex\ni18n APEX: $i18nApex"
        })
    }

    @SuppressLint("QueryPermissionsNeeded")
    @Suppress("DEPRECATION")
    private fun apexVersion(keyword: String, candidates: List<String>): String {
        if (Build.VERSION.SDK_INT < 29) return "n/a (API < 29)"
        val flags = 0x40000000 // PackageManager.MATCH_APEX
        val found = LinkedHashMap<String, PackageInfo>()
        candidates.forEach { name ->
            runCatching { packageManager.getPackageInfo(name, flags) }.getOrNull()?.let { found[name] = it }
        }
        runCatching { packageManager.getInstalledPackages(flags) }.getOrNull()
            ?.filter { it.packageName.contains(keyword) }
            ?.forEach { found.putIfAbsent(it.packageName, it) }
        if (found.isEmpty()) return "n/a: not visible"
        return found.values.joinToString("; ") {
            "${it.packageName} versionCode=${it.longVersionCode} versionName=${it.versionName}"
        }
    }
}
