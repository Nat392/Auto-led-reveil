package com.example.alarmwatcher

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*

/**
 * AccessibilityService that performs the UI automation on the target app.
 * Requires the user to enable this service in Accessibility settings.
 */
class AutomationService : AccessibilityService() {
    private val TAG = "AutomationService"
    private val mainHandler = Handler(Looper.getMainLooper())
    private var automationJob: Job? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.alarmwatcher.ACTION_RUN_AUTOMATION") {
                val duration = intent.getLongExtra("duration_ms", AlarmMonitorService.PREWARN_MS)
                val r = intent.getIntExtra("target_r", 255)
                val g = intent.getIntExtra("target_g", 230)
                val b = intent.getIntExtra("target_b", 210)
                val room = intent.getStringExtra("room_name_click") ?: "Chambre"
                val fallback = intent.getStringExtra("fallback_room") ?: "Bureau"
                Log.i(TAG, "Automation requested duration=$duration, rgb=($r,$g,$b), room=$room")
                startAutomation(duration, r, g, b, room, fallback)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.DEFAULT
        }
        serviceInfo = info

        registerReceiver(receiver, IntentFilter("com.example.alarmwatcher.ACTION_RUN_AUTOMATION"))
        Log.i(TAG, "Service connected and receiver registered")
    }

    override fun onInterrupt() {
        automationJob?.cancel()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No-op: this service is driven by broadcasts and direct UI inspection.
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(receiver) } catch (e: Exception) {}
    }

    private fun startAutomation(durationMs: Long, r: Int, g: Int, b: Int, room: String, fallbackRoom: String) {
        automationJob?.cancel()
        automationJob = CoroutineScope(Dispatchers.Main).launch {
            // 1) Press Back to ensure we're at room list
            performGlobalAction(GLOBAL_ACTION_BACK)
            delay(1000)

            // 2) Click fallback room (e.g., "Bureau") to ensure list visible
            if (!clickByText(fallbackRoom)) {
                Log.w(TAG, "Could not find fallback room: $fallbackRoom")
            }
            delay(1200)

            // 3) Click target room ("Chambre")
            if (!clickByText(room)) {
                Log.w(TAG, "Could not find room: $room")
            }
            // Take a screenshot after opening the target app / navigating to room
            try {
                performScreenshotAttempt()
            } catch (e: Exception) {
                Log.w(TAG, "Screenshot attempt failed: ${e.message}")
            }
            delay(1500)

            // 4) Select color on the color wheel (approximate tap)
            // We attempt to find a view that looks like a color wheel; if not, we tap center area
            selectColorOnWheel(r, g, b)
            delay(800)

            // 5) Gradually increase brightness over durationMs until reaching 100%
            performBrightnessRamp(durationMs)
        }
    }

    private fun clickByText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(text)
        for (n in nodes) {
            if (n.isClickable) {
                n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                n.recycle()
                return true
            } else {
                var parent = n.parent
                while (parent != null) {
                    if (parent.isClickable) {
                        parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        parent.recycle()
                        n.recycle()
                        return true
                    }
                    parent = parent.parent
                }
                n.recycle()
            }
        }
        return false
    }

    private fun selectColorOnWheel(r: Int, g: Int, b: Int) {
        // Convert target color to an angle on wheel (simple heuristic)
        // For simplicity map color temperature to angle; this is highly device/app dependent
        val angle = 0.0 // placeholder angle; tuning required per app
        val root = rootInActiveWindow ?: return
        // Attempt to find a node likely representing color wheel
        val candidates = ArrayList<AccessibilityNodeInfo>()
        collectNodesWithTextOrDesc(root, candidates, listOf("color", "wheel", "couleur"))
        var tapped = false
        if (candidates.isNotEmpty()) {
            val n = candidates[0]
            val bounds = android.graphics.Rect()
            n.getBoundsInScreen(bounds)
            val cx = bounds.centerX()
            val cy = bounds.centerY()
            // approximate offset on circle
            val radius = Math.min(bounds.width(), bounds.height()) / 4
            val x = (cx + radius * Math.cos(angle)).toFloat()
            val y = (cy + radius * Math.sin(angle)).toFloat()
            tapped = dispatchTap(x, y)
            n.recycle()
        }

        if (!tapped) {
            // fallback tap near center of screen
            val displayMetrics = resources.displayMetrics
            val x = displayMetrics.widthPixels * 0.5f
            val y = displayMetrics.heightPixels * 0.45f
            dispatchTap(x, y)
        }
    }

    private fun collectNodesWithTextOrDesc(root: AccessibilityNodeInfo, out: ArrayList<AccessibilityNodeInfo>, keywords: List<String>) {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val text = buildString {
                append(n.text ?: "")
                append(' ')
                append(n.contentDescription ?: "")
            }
            val low = text.toString().lowercase()
            for (k in keywords) if (low.contains(k)) {
                out.add(n)
                break
            }
            for (i in 0 until n.childCount) {
                val c = n.getChild(i)
                if (c != null) queue.add(c)
            }
        }
    }

    private fun dispatchTap(x: Float, y: Float): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path().apply { moveTo(x, y) }
            val desc = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 50)).build()
            return dispatchGesture(desc, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) { super.onCompleted(gestureDescription); Log.i(TAG, "Tap completed at $x,$y") }
                override fun onCancelled(gestureDescription: GestureDescription?) { super.onCancelled(gestureDescription); Log.w(TAG, "Tap cancelled") }
            }, null)
        }
        return false
    }

    private suspend fun performBrightnessRamp(durationMs: Long) {
        if (durationMs <= 0) return
        val steps = 60 // update every 30 seconds for 30 minutes -> 60 steps
        val delayPerStep = durationMs / steps
        for (i in 0..steps) {
            val percent = (i * 100) / steps
            setBrightnessPercent(percent)
            delay(delayPerStep)
        }
    }

    private fun setBrightnessPercent(percent: Int) {
        // Find a SeekBar-like node and set progress if supported
        val root = rootInActiveWindow ?: return
        val nodes = ArrayList<AccessibilityNodeInfo>()
        collectNodesWithTextOrDesc(root, nodes, listOf("brightness", "luminosité", "slider"))
        for (n in nodes) {
            if (n.actionList.any { it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id }) {
                val args = android.os.Bundle()
                args.putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, percent / 100f)
                n.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id, args)
                n.recycle()
                return
            }
            // fallback: try tapping at proportional x inside node bounds
            val bounds = android.graphics.Rect()
            n.getBoundsInScreen(bounds)
            val x = bounds.left + (bounds.width() * percent / 100f)
            val y = bounds.centerY().toFloat()
            dispatchTap(x, y)
            n.recycle()
            return
        }
    }

    private fun performScreenshotAttempt() {
        try {
            val ok = performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            Log.i(TAG, "Requested system screenshot, result=$ok")
        } catch (e: Exception) {
            Log.w(TAG, "performScreenshotAttempt failed: ${e.message}")
        }
    }
}
