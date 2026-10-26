package dev.devinprater.healthreader

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Shown by Health Connect when the user asks why this app wants their data. */
class RationaleActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = TextView(this).apply {
            text = "Health Reader reads your health records from Health Connect, which is the " +
                "store where Samsung Health and other apps put them. It turns sessions, totals " +
                "and readings into plain sentences, because some of those charts cannot be read " +
                "with a screen reader. Everything is read on this phone and nothing is sent " +
                "anywhere."
            textSize = 18f
            setPadding(56, 56, 56, 56)
        }
        setContentView(ScrollView(this).apply { addView(text) })
    }
}
