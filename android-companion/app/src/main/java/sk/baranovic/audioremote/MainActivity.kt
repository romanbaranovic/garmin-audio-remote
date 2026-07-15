package sk.baranovic.audioremote

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Minimal UI: explains the two things the user must grant once
 * (Notification access, battery-optimization exemption), and a button
 * to (re)start the foreground service. There is nothing else to configure —
 * pairing with the watch happens automatically via the Connect IQ SDK once
 * Garmin Connect Mobile has paired the Edge 830.
 */
class MainActivity : Activity() {

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }

        statusText = TextView(this)
        root.addView(statusText)

        val grantNotifButton = Button(this).apply {
            text = "Povoliť prístup k notifikáciám"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }
        root.addView(grantNotifButton)

        val batteryButton = Button(this).apply {
            text = "Vypnúť optimalizáciu batérie pre appku"
            setOnClickListener {
                @Suppress("BatteryLife")
                startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        android.net.Uri.parse("package:$packageName")
                    )
                )
            }
        }
        root.addView(batteryButton)

        val startButton = Button(this).apply {
            text = "Spustiť službu"
            setOnClickListener { startService() }
        }
        root.addView(startButton)

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        startService()
    }

    private fun startService() {
        val intent = Intent(this, AudioRemoteService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun updateStatus() {
        val notifGranted = isNotificationAccessGranted()
        statusText.text = if (notifGranted) {
            "Prístup k notifikáciám: povolený\n\nSlužba beží na pozadí a prepája Edge 830 s prehrávačom audiokníh."
        } else {
            "Prístup k notifikáciám: CHÝBA\n\nBez neho appka nevie zistiť, čo práve hrá. Povoľ ho nižšie."
        }
    }

    private fun isNotificationAccessGranted(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return enabled?.contains(packageName) == true
    }
}
