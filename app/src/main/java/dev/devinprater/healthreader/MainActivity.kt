package dev.devinprater.healthreader

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var content: LinearLayout
    private var requestedOnce = false
    private var statusView: TextView? = null
    private var grantButton: Button? = null

    private val permissionContract = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        android.util.Log.i("HealthReader", "permission result: ${granted.size} granted")
        showStatus(granted)
        buildSections()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 56, 56, 56)
        }
        scroll.addView(content)
        setContentView(scroll)

        val heading = TextView(this).apply {
            text = "Health Reader"
            textSize = 26f
            isAccessibilityHeading = true
            setPadding(0, 0, 0, 16)
        }
        content.addView(heading)

        val intro = TextView(this).apply {
            text = "Every Health Connect record type, written out as words instead of charts, " +
                "for the last 30 days. The numbers come straight from Health Connect, which " +
                "Samsung Health and other apps write to."
            textSize = 16f
        }
        content.addView(intro)

        val client = clientOrNull()
        if (client == null) {
            val problem = TextView(this).apply {
                text = "Health Connect is not available on this device, so there is nothing to read."
                textSize = 18f
                setPadding(0, 32, 0, 0)
            }
            content.addView(problem)
            return
        }

        lifecycleScope.launch {
            val granted = runCatching { client.permissionController.getGrantedPermissions() }
                .getOrDefault(emptySet())
            showStatus(granted)
            buildSections()
            if (!granted.containsAll(Sections.permissions) && !requestedOnce) {
                requestedOnce = true
                permissionContract.launch(Sections.permissions)
            }
        }
    }

    private fun clientOrNull(): HealthConnectClient? {
        val status = HealthConnectClient.getSdkStatus(this)
        return if (status == HealthConnectClient.SDK_AVAILABLE) {
            runCatching { HealthConnectClient.getOrCreate(this) }.getOrNull()
        } else {
            null
        }
    }

    private fun showStatus(granted: Set<String>) {
        statusView?.let { content.removeView(it) }
        grantButton?.let { content.removeView(it) }
        val grantedCount = Sections.permissions.count { granted.contains(it) }
        val status = TextView(this).apply {
            textSize = 16f
            setPadding(0, 24, 0, 8)
            text = "Access to $grantedCount of ${Sections.permissions.size} kinds of health data. " +
                "Sections you have not allowed say so when you open them."
        }
        statusView = status
        content.addView(status, 2)

        if (grantedCount < Sections.permissions.size) {
            val grant = Button(this).apply {
                text = "Allow health data access"
                contentDescription = "Allow health data access, opens the Health Connect permission screen"
                setOnClickListener {
                    android.util.Log.i("HealthReader", "asking for ${Sections.permissions.size} permissions")
                    runCatching { permissionContract.launch(Sections.permissions) }
                        .onFailure { android.util.Log.e("HealthReader", "launch failed", it) }
                }
            }
            grantButton = grant
            content.addView(grant, 3)
        }
    }

    private fun buildSections() {
        content.findViewWithTag<LinearLayout>("sections")?.let { content.removeView(it) }
        val list = LinearLayout(this).apply {
            tag = "sections"
            orientation = LinearLayout.VERTICAL
            setPadding(0, 24, 0, 0)
        }
        Sections.all.forEach { section ->
            val button = Button(this).apply {
                text = section.title
                contentDescription = section.title + ". " + section.blurb
                isAllCaps = false
                setOnClickListener {
                    startActivity(
                        Intent(this@MainActivity, DetailActivity::class.java)
                            .putExtra("key", section.key)
                    )
                }
            }
            list.addView(button, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        val note = TextView(this).apply {
            text = "Data is read on the phone only. Nothing is uploaded."
            textSize = 14f
            setPadding(0, 32, 0, 0)
        }
        list.addView(note)
        content.addView(list)
    }
}
