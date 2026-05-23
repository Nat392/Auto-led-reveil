package com.example.alarmwatcher

import android.Manifest
import android.app.AlarmManager
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.text.DateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingStartupPrompts = 0
    private var startupFlowStarted = false
    private var finishRunnable: Runnable? = null

    private lateinit var statusTextView: TextView

    private val requestBluetoothConnect = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        onStartupPromptResolved()
    }

    private val requestPostNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        onStartupPromptResolved()
    }

    private val requestScheduleExact = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        onStartupPromptResolved()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        statusTextView = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.CENTER_HORIZONTAL
            setLineSpacing(0f, 1.15f)
            setPadding(48, 48, 48, 48)
            text = "Analyse du réveil système…"
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 96, 48, 96)
            addView(
                statusTextView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
        setContentView(root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingStartupPrompts++
            requestBluetoothConnect.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingStartupPrompts++
            requestPostNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val am = getSystemService(AlarmManager::class.java)
        if (am != null && !am.canScheduleExactAlarms()) {
            pendingStartupPrompts++
            // open system UI to grant SCHEDULE_EXACT_ALARM to this app
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            requestScheduleExact.launch(intent)
        }

        if (pendingStartupPrompts == 0) {
            startStartupFlow()
        } else {
            statusTextView.text = buildPendingPermissionText()
        }
    }

    override fun onDestroy() {
        finishRunnable?.let { mainHandler.removeCallbacks(it) }
        super.onDestroy()
    }

    private fun onStartupPromptResolved() {
        if (pendingStartupPrompts > 0) {
            pendingStartupPrompts--
        }

        if (pendingStartupPrompts == 0) {
            startStartupFlow()
        }
    }

    private fun startStartupFlow() {
        if (startupFlowStarted) return
        startupFlowStarted = true

        // Rescan and schedule the next pre-warn directly.
        AlarmMonitor.scanNextAlarmAndSchedule(this)
        SunsetAutomationScheduler.requestRefreshAndSchedule(this)

        // If launched with diagnostic action, run BLE diagnostic routine.
        if (intent?.action == "com.example.alarmwatcher.DIAGNOSTIC") {
            Thread {
                try {
                    kotlinx.coroutines.runBlocking {
                        SunriseZoneConfig.configuredZones().forEach { zone ->
                            ZenggeBulbController.diagnosticApplyScene(
                                applicationContext,
                                zone.macAddress,
                                zone.sunriseR,
                                zone.sunriseG,
                                zone.sunriseB,
                                0
                            )
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Diagnostic failed", e)
                }
            }.start()
        }

        statusTextView.text = buildStartupStatusText()
        scheduleAutoClose()
    }

    private fun scheduleAutoClose() {
        finishRunnable?.let { mainHandler.removeCallbacks(it) }
        finishRunnable = Runnable {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                finishAndRemoveTask()
            } else {
                finish()
            }
        }
        mainHandler.postDelayed(finishRunnable!!, STATUS_VISIBLE_MS)
    }

    private fun buildPendingPermissionText(): String {
        return """
            Configuration en cours…

            L’application demande les autorisations nécessaires, puis affichera le statut de la prochaine alarme et du bulbe BLE.
        """.trimIndent()
    }

    private fun buildStartupStatusText(): String {
        val lines = mutableListOf(
            "Alarm Watcher prêt"
        )

        val alarmManager = getSystemService(AlarmManager::class.java)
        val nextAlarmClock = alarmManager?.nextAlarmClock
        if (nextAlarmClock == null) {
            lines += "Prochaine alarme : aucune alarme système détectée"
            lines += "Rampe : aucune rampe programmée"
        } else {
            val triggerTime = nextAlarmClock.triggerTime
            val now = System.currentTimeMillis()
            val window = AlarmTimingSupport.computePreWarnWindow(triggerTime, now)
            if (window == null) {
                lines += "Prochaine alarme : déjà expirée"
                lines += "Rampe : annulée"
            } else {
                lines += "Prochaine alarme : ${formatDateTime(triggerTime)}"
                lines += "Rampe : programmée pour ${formatDateTime(window.scheduleAt)} (${formatDuration(window.durationMs)})"
            }
        }

        lines += exactAlarmStatusLine(alarmManager)
        lines += bleStatusLines()
        lines += "Fermeture automatique dans 2 secondes."

        return lines.joinToString("\n\n")
    }

    private fun exactAlarmStatusLine(alarmManager: AlarmManager?): String {
        if (alarmManager == null) {
            return "Alarme exacte : AlarmManager indisponible"
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            return "Alarme exacte : autorisation accordée"
        }
        return "Alarme exacte : autorisation à accorder"
    }

    private fun bleStatusLines(): List<String> {
        val bluetoothManager = getSystemService(BluetoothManager::class.java)
            ?: return listOf("BLE : Bluetooth indisponible")
        val adapter = bluetoothManager.adapter ?: return listOf("BLE : Bluetooth indisponible")
        if (!adapter.isEnabled) {
            return listOf("BLE : Bluetooth désactivé")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            return listOf("BLE : permission BLUETOOTH_CONNECT manquante")
        }

        val lines = SunriseZoneConfig.all().map { zone ->
            val status = if (zone.macAddress.isBlank()) {
                "MAC manquante"
            } else {
                "MAC configurée"
            }
            "BLE ${zone.label} : $status matin(${zone.sunriseR}, ${zone.sunriseG}, ${zone.sunriseB}) soir(${zone.sunsetR}, ${zone.sunsetG}, ${zone.sunsetB})"
        }.toMutableList()

        val configuredZone = SunriseZoneConfig.primaryZone()
        lines += "Zone sunrise par défaut : ${configuredZone.label}"
        return lines
    }

    private fun formatDateTime(timeMs: Long): String {
        val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        return formatter.format(Date(timeMs))
    }

    private fun formatDuration(durationMs: Long): String {
        val minutes = (durationMs / 60_000L).coerceAtLeast(1L)
        return if (minutes == 1L) "1 min" else "$minutes min"
    }

    private companion object {
        const val TAG = "MainActivity"
        const val STATUS_VISIBLE_MS = 2_000L
    }
}
