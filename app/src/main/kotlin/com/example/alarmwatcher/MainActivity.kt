package com.example.alarmwatcher

import android.Manifest
import android.app.AlarmManager
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.text.DateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {
    private var pendingStartupPrompts = 0
    private var startupFlowStarted = false

    // Composants de l'interface utilisateur
    private lateinit var tvAlarmStatus: TextView
    private lateinit var tvBleStatus: TextView
    private lateinit var btnTriggerNightMode: Button

    private val requestBluetoothConnect =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) {
            onStartupPromptResolved()
        }

    private val requestPostNotifications =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) {
            onStartupPromptResolved()
        }

    private val requestScheduleExact =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) {
            onStartupPromptResolved()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // On attache la vue XML qu'on vient de créer
        setContentView(R.layout.activity_main)

        tvAlarmStatus = findViewById(R.id.tvAlarmStatus)
        tvBleStatus = findViewById(R.id.tvBleStatus)
        btnTriggerNightMode = findViewById(R.id.btnTriggerNightMode)

        // Action du bouton
        btnTriggerNightMode.setOnClickListener {
            triggerNightMode()
        }

        // --- Gestion des permissions (inchangée) ---
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
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            requestScheduleExact.launch(intent)
        }

        if (pendingStartupPrompts == 0) {
            startStartupFlow()
        } else {
            tvAlarmStatus.text = "Configuration et demande des permissions en cours..."
            tvBleStatus.text = "En attente des permissions..."
        }
    }

    private fun triggerNightMode() {
        Toast.makeText(this, "Envoi des commandes de soirée (séquentiel)...", Toast.LENGTH_SHORT).show()

        // On utilise un Thread et runBlocking pour appliquer les scènes l'une après l'autre
        // sans que la deuxième n'annule la première dans le cycle de vie du Service.
        Thread {
            try {
                kotlinx.coroutines.runBlocking {
                    // 1. On applique d'abord le Bureau directement via la méthode suspendue du compagnon
                    android.util.Log.i(TAG, "Application manuelle soirée : Bureau")
                    SunsetSceneService.applySunsetScene(applicationContext, SunsetAutomationScheduler.ZONE_BUREAU)

                    // Petite pause de sécurité pour laisser le contrôleur BLE respirer
                    kotlinx.coroutines.delay(MANUAL_SEQUENCE_DELAY_MS)

                    // 2. On applique ensuite la Chambre
                    android.util.Log.i(TAG, "Application manuelle soirée : Chambre")
                    SunsetSceneService.applySunsetScene(applicationContext, SunsetAutomationScheduler.ZONE_CHAMBRE)
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Échec de l'application manuelle du mode soirée", e)
            }
        }.start()
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

        // Diagnostic routine
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
                                0,
                            )
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Diagnostic failed", e)
                }
            }.start()
        }

        updateUIWithStatus()
    }

    private fun updateUIWithStatus() {
        val alarmManager = getSystemService(AlarmManager::class.java)
        val nextAlarmClock = alarmManager?.nextAlarmClock

        val alarmText = java.lang.StringBuilder()
        if (nextAlarmClock == null) {
            alarmText.append("⏰ Prochaine alarme : Aucune détectée\n")
            alarmText.append("🌅 Rampe lumineuse : Aucune programmée\n\n")
        } else {
            val triggerTime = nextAlarmClock.triggerTime
            val now = System.currentTimeMillis()
            val window = AlarmTimingSupport.computePreWarnWindow(triggerTime, now)
            if (window == null) {
                alarmText.append("⏰ Prochaine alarme : Déjà expirée\n")
                alarmText.append("🌅 Rampe lumineuse : Annulée\n\n")
            } else {
                alarmText.append("⏰ Prochaine alarme : ${formatDateTime(triggerTime)}\n")
                alarmText.append(
                    "🌅 Rampe (Simulateur d'Aube) : Programmée pour ${formatDateTime(
                        window.scheduleAt,
                    )} (${formatDuration(window.durationMs)})\n\n",
                )
            }
        }
        alarmText.append(exactAlarmStatusLine(alarmManager))
        tvAlarmStatus.text = alarmText.toString()

        val bleText = java.lang.StringBuilder()
        bleStatusLines().forEach { line ->
            bleText.append("• ").append(line).append("\n\n")
        }
        tvBleStatus.text = bleText.toString().trim()
    }

    private fun exactAlarmStatusLine(alarmManager: AlarmManager?): String {
        return when {
            alarmManager == null -> "⚙️ Autorisation 'Alarme Exacte' : Indisponible"
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms() -> {
                "⚙️ Autorisation 'Alarme Exacte' : Accordée ✅"
            }
            else -> "⚙️ Autorisation 'Alarme Exacte' : À accorder ⚠️"
        }
    }

    private fun bleStatusLines(): List<String> {
        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        val adapter = bluetoothManager?.adapter
        val staticMessage =
            when {
                bluetoothManager == null -> "Bluetooth indisponible sur cet appareil"
                adapter == null -> "Bluetooth indisponible"
                !adapter.isEnabled -> "Le Bluetooth est désactivé"
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED -> {
                    "Permission BLUETOOTH_CONNECT manquante"
                }
                else -> null
            }
        if (staticMessage != null) {
            return listOf(staticMessage)
        }

        val lines =
            SunriseZoneConfig.all().map { zone ->
                val status =
                    if (zone.macAddress.isBlank()) {
                        "MAC manquante ❌"
                    } else {
                        "Prêt ✅"
                    }
                "Zone [${zone.label}]\n" +
                    "Statut : $status\n" +
                    "Cible Aube : RGB(${zone.sunriseR}, ${zone.sunriseG}, ${zone.sunriseB})\n" +
                    "Cible Soirée : RGB(${zone.sunsetR}, ${zone.sunsetG}, ${zone.sunsetB})"
            }.toMutableList()

        val configuredZone = SunriseZoneConfig.primaryZone()
        lines += "Zone prioritaire : ${configuredZone.label}"
        return lines
    }

    private fun formatDateTime(timeMs: Long): String {
        val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        return formatter.format(Date(timeMs))
    }

    private fun formatDuration(durationMs: Long): String {
        val minutes = (durationMs / ONE_MINUTE_MS).coerceAtLeast(1L)
        return if (minutes == 1L) "1 min" else "$minutes min"
    }

    private companion object {
        const val TAG = "MainActivity"
        const val MANUAL_SEQUENCE_DELAY_MS = 1_000L
        const val ONE_MINUTE_MS = 60_000L
    }
}
