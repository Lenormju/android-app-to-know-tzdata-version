package dev.tzv

import android.app.Activity
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

        setContentView(TextView(this).apply {
            textSize = 20f
            setPadding(48, 96, 48, 48)
            setTextIsSelectable(true)
            text = "ICU tzdata: $icu\ntz_version: $tzVersion\njava.util.TimeZone: $javaTz"
        })
    }
}
