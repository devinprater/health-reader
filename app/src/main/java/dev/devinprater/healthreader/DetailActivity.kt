package dev.devinprater.healthreader

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant

class DetailActivity : AppCompatActivity() {

    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 56, 56, 56)
        }
        scroll.addView(content)
        setContentView(scroll)

        val key = intent.getStringExtra("key").orEmpty()
        val section = Sections.byKey(key)
        if (section == null) {
            content.addView(TextView(this).apply { text = "That section is not known." })
            return
        }

        content.addView(TextView(this).apply {
            text = section.title
            textSize = 26f
            setPadding(0, 0, 0, 12)
        })
        content.addView(TextView(this).apply {
            text = section.blurb
            textSize = 15f
            setPadding(0, 0, 0, 20)
        })

        val status = TextView(this).apply {
            text = "Reading Health Connect."
            textSize = 17f
        }
        content.addView(status)

        val client = runCatching { HealthConnectClient.getOrCreate(this) }.getOrNull()
        if (client == null) {
            status.text = "Health Connect is not available on this device."
            return
        }

        val end = Instant.now()
        val start = end.minus(Duration.ofDays(30))
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { section.load(client, start, end) }
            }
            result.exceptionOrNull()?.let { error ->
                android.util.Log.e("HealthReader", "reading ${section.key} failed", error)
            }
            status.text = result.fold(
                onSuccess = { report -> render(report); "" },
                onFailure = { error -> describe(error, section.title) }
            )
        }
    }

    private fun describe(error: Throwable, title: String): String = when (error) {
        is SecurityException ->
            "Health Reader is not allowed to read $title. Go back, choose Allow health data " +
                "access, and turn that kind of data on for Health Reader."
        else -> "Could not read $title: ${error.javaClass.simpleName}. ${error.message.orEmpty()}"
    }

    private fun render(report: Report) {
        content.addView(TextView(this).apply {
            text = report.summary
            textSize = 18f
            setPadding(0, 12, 0, 16)
        })
        if (report.rows.isEmpty()) return
        content.addView(TextView(this).apply {
            text = "Records"
            textSize = 20f
            setPadding(0, 8, 0, 8)
        })
        report.rows.forEach { line ->
            content.addView(TextView(this).apply {
                text = line
                textSize = 16f
                setPadding(0, 8, 0, 8)
            })
        }
    }
}
