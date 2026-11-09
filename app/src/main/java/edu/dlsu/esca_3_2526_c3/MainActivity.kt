package edu.dlsu.esca_3_2526_c3

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.Manifest
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import androidx.core.content.ContextCompat
import android.view.Gravity
import java.text.DateFormat
import java.util.Date
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    // Firebase test connection and shared monitoring-service state.
    private lateinit var cloud: DemoCloud
    private val statuses get() = DriverMonitoringService.statuses
    private val recent get() = DriverMonitoringService.recent
    private val sessionId get() = DriverMonitoringService.sessionId
    private val sessionStarted get() = DriverMonitoringService.sessionStarted

    // Track the current page and the saved display preference.
    private var onHome = true
    private var onDetails = false
    private var nightMode = false

    // The HIGH-risk animation must be cancelled before rebuilding the page.
    private var borderPulse: ValueAnimator? = null

    // References to the reusable screen containers and navigation tabs.
    private lateinit var root: LinearLayout
    private lateinit var tabs: LinearLayout
    private lateinit var page: LinearLayout
    private lateinit var scroller: ScrollView
    private lateinit var homeTab: TextView
    private lateinit var historyTab: TextView

    /** Set up UI and reconnect to an existing monitoring session. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DriverMonitoringService.restore(this)
        nightMode = getSharedPreferences("driver_display", MODE_PRIVATE).getBoolean("night", false)
        Ui.nightMode = nightMode
        window.statusBarColor = Ui.navy
        window.navigationBarColor = Ui.navy

        // Fixed header, scrollable page content, and bottom navigation.
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.canvas)
        }
        root.addView(Ui.header(this, "Driver Dashboard"))
        scroller = ScrollView(this).apply {
            isFillViewport = true
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(
                    Ui.dp(this@MainActivity, 16),
                    Ui.dp(this@MainActivity, 16),
                    Ui.dp(this@MainActivity, 16),
                    Ui.dp(this@MainActivity, 24)
                )
                page = this
            })
        }
        root.addView(scroller, LinearLayout.LayoutParams(-1, 0, 1f))

        // Keep navigation visible while the page content scrolls.
        tabs = LinearLayout(this).apply {
            setBackgroundColor(Ui.surface)
            setPadding(
                Ui.dp(this@MainActivity, 12),
                Ui.dp(this@MainActivity, 8),
                Ui.dp(this@MainActivity, 12),
                Ui.dp(this@MainActivity, 8)
            )
        }
        homeTab = Ui.tab(this, "⌂  Home") { showHome() }
        historyTab = Ui.tab(this, "◷  History") { showHistory() }
        tabs.addView(homeTab, LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1f))
        tabs.addView(historyTab, LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1f))
        tabs.addView(Ui.tab(this, "⚙  Settings") {
            startActivity(Intent(this, SettingsActivity::class.java))
        }, LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1f))
        root.addView(tabs)
        setContentView(root)

        // Refresh the current page when Firebase connection data changes.
        cloud = DemoCloud(this)
        cloud.onChange = { if (!isFinishing) updateVisiblePage() }
        cloud.start()

        if (DriverMonitoringService.active) {
            ContextCompat.startForegroundService(this, Intent(this, DriverMonitoringService::class.java).setAction(DriverMonitoringService.START))
        }

        showHome()
    }

    /** Refresh the page currently selected by the driver. */
    private fun updateVisiblePage() {
        if (!onHome) showHistory() else if (onDetails) showDetails() else showHome()
    }

    /** Resume receiving service updates when the app becomes visible. */
    override fun onResume() {
        super.onResume()
        Ui.nightMode = nightMode
        DriverMonitoringService.onChange = { if (!isFinishing && !isDestroyed) updateVisiblePage() }
        if (::page.isInitialized) updateVisiblePage()
    }

    /** Stop UI callbacks while allowing the foreground service to continue. */
    override fun onPause() {
        DriverMonitoringService.onChange = null
        Ui.nightMode = false
        super.onPause()
    }

    /** Clean up the border animation and Firebase listeners. */
    override fun onDestroy() {
        borderPulse?.cancel()
        cloud.stop()
        super.onDestroy()
    }

    /** Cycle a manually simulated alert for testing */
    private fun trigger(name: String) {
        if (!DriverMonitoringService.active) {
            cloud.toast("Start driver monitoring service first")
            return
        }

        val current = statuses[name] ?: "Normal"
        val next = when (current) {
            "Normal" -> "Warning"; "Warning" -> "High"; else -> "Normal"
        }
        DriverMonitoringService.updateSimulatedRisk(name, next)

        if (next != "Normal") {
            val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())
            recent.add(0, Triple(name, time, next))
            cloud.record(name, next, sessionId)
        }

        if (next == "High") {
            startService(Intent(this, DriverMonitoringService::class.java).setAction(DriverMonitoringService.HIGH_ALERT))
        }
        cloud.publish(statuses, sessionId, (System.currentTimeMillis() - sessionStarted) / 1000)
        updateVisiblePage()
    }

    /** Start or stop the foreground monitoring service */
    private fun controlSession() {
        val running = DriverMonitoringService.active
        if (!running && Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 79)
        }
        val intent = Intent(this, DriverMonitoringService::class.java)
            .setAction(if (running) DriverMonitoringService.STOP else DriverMonitoringService.START)
        if (running) startService(intent) else ContextCompat.startForegroundService(this, intent)
    }

    /** Save the night-mode preference and repaint the current page. */
    private fun switchNightMode() {
        nightMode = !nightMode
        Ui.nightMode = nightMode
        getSharedPreferences("driver_display", MODE_PRIVATE).edit().putBoolean("night", nightMode).apply()
        root.setBackgroundColor(Ui.canvas)
        tabs.setBackgroundColor(Ui.surface)
        updateVisiblePage()
    }

    /** controls for Firebase syncing. */
    private fun accountArea(target: LinearLayout) {
        target.addView(Ui.note(this, cloud.connectionLabel))
        val button = Button(this).apply {
            text =
                if (cloud.signedIn) "Sign out  ·  ${cloud.email}" else "Sign in (Driver researcher account)"
            isAllCaps = false
            setOnClickListener {
                if (cloud.signedIn) cloud.signOut() else cloud.signInDialog("driver")
            }
        }
        target.addView(button)
        if (cloud.signedIn && cloud.role != "driver") {
            target.addView(
                Ui.note(
                    this,
                    "Driver Firebase writes require a researcher account assigned the driver role."
                )
            )
        }
    }

    /** Reset page, flashing border */
    private fun selectTab(home: Boolean) {
        Ui.activeTab(homeTab, home)
        Ui.activeTab(historyTab, !home)
        borderPulse?.cancel()
        borderPulse = null
        root.foreground = null

        // Flash around both Driving Mode and Details while HIGH risk is active.
        if (home && DriverMonitoringService.active && statuses.values.any { it == "High" }) {
            val border = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
            root.foreground = border
            borderPulse = ValueAnimator.ofArgb(Color.TRANSPARENT, Ui.red).apply {
                duration = 550L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                addUpdateListener {
                    border.setStroke(Ui.dp(this@MainActivity, 12), it.animatedValue as Int)
                }
                start()
            }
        }

        page.removeAllViews()
        scroller.scrollTo(0, 0)
    }

    /** Driving Mode */
    private fun showHome() {
        onHome = true
        onDetails = false
        selectTab(true)
        val overview = Ui.card(this)
        overview.addView(Ui.eyebrow(this, "DRIVING MODE"))

        // Show the highest simulated risk, or STOPPED when inactive.
        val risk = when {
            !DriverMonitoringService.active -> "STOPPED"
            statuses.values.any { it == "High" } -> "HIGH"
            statuses.values.any { it == "Warning" } -> "MEDIUM"
            else -> "LOW"
        }
        val color = when (risk) {
            "HIGH" -> Ui.red
            "MEDIUM" -> Color.rgb(250, 204, 21)
            "LOW" -> Ui.green
            else -> Ui.muted
        }

        // The solid-color panel makes the risk visible at a glance.
        val riskBackground = GradientDrawable().apply {
            setColor(color)
            cornerRadius = Ui.dp(this@MainActivity, 16).toFloat()
        }

        val riskDisplay = TextView(this).apply {
            text = if (risk == "STOPPED") "DRIVER MONITORING SERVICE IS STOPPED" else "$risk RISK"
            textSize = 32f
            gravity = Gravity.CENTER
            setTextColor(if (risk == "MEDIUM") Color.BLACK else Color.WHITE)
            setPadding(Ui.dp(this@MainActivity, 12), Ui.dp(this@MainActivity, 45),
                Ui.dp(this@MainActivity, 12), Ui.dp(this@MainActivity, 45))
            background = riskBackground
        }
        overview.addView(riskDisplay, LinearLayout.LayoutParams(-1, -2))
        overview.addView(Ui.space(this, 12))

        overview.addView(Ui.title(this, when (risk) {
            "HIGH" -> "Please take a break"
            "MEDIUM" -> "Stay alert"
            "LOW" -> "Keep your eyes on the road"
            else -> "Driver monitoring service is stopped"
        }))
        overview.addView(Ui.title(this, "Camera not connected"))

        overview.addView(Button(this).apply {
            text = if (DriverMonitoringService.active) "Stop driver monitoring service" else "Start driver monitoring service"
            isAllCaps = false
            setTextColor(Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(if (DriverMonitoringService.active) Ui.red else Ui.green)
            setOnClickListener { controlSession() }
        })
        overview.addView(Ui.space(this, 12))

        overview.addView(Button(this).apply {
            text = if (nightMode) "Night mode ON · Turn off" else "Night mode OFF · Turn on"
            isAllCaps = false
            if (nightMode) { backgroundTintList = ColorStateList.valueOf(Ui.navy); setTextColor(Color.WHITE) }
            setOnClickListener { switchNightMode() }
        })

        overview.addView(Button(this).apply {
            text = "View details"
            isAllCaps = false
            if (nightMode) { backgroundTintList = ColorStateList.valueOf(Ui.navy); setTextColor(Color.WHITE) }
            setOnClickListener { showDetails() }
        })
        page.addView(overview, Ui.blockParams(this))
    }

    /** Display simulated detection controls and placeholder measurements. */
    private fun showDetails() {
        onHome = true
        onDetails = true
        selectTab(true)
        val metrics = Ui.card(this)
        metrics.addView(Ui.title(this, "Detection Details"))
        metrics.addView(Ui.pair(this, "Trip duration", if (DriverMonitoringService.active)
            "${(System.currentTimeMillis() - sessionStarted).coerceAtLeast(0L) / 60000} min" else "Not monitoring"))

        // Make rows tappable to simulate detection status.
        for (name in listOf("Drowsiness", "Yawning", "Gaze deviation", "Phone usage")) {
            val row = Ui.pair(this, name, statuses[name] ?: "Normal")
            row.setOnClickListener { trigger(name) }
            metrics.addView(row)
        }
        metrics.addView(Ui.space(this, 10))

        // Replace these sample measurements eventually.
        metrics.addView(Ui.eyebrow(this, "MEASUREMENTS"))
        metrics.addView(Ui.pair(this, "EAR", "0.286"))
        metrics.addView(Ui.pair(this, "MAR", "0.124"))
        metrics.addView(Ui.pair(this, "Yaw", "4.5°"))
        metrics.addView(Ui.pair(this, "Pitch", "-2.0°"))
        metrics.addView(Ui.pair(this, "FPS", "Camera not connected"))
        metrics.addView(Button(this).apply {
            text = "Back to Driving mode"
            isAllCaps = false
            if (nightMode) { backgroundTintList = ColorStateList.valueOf(Ui.navy); setTextColor(Color.WHITE) }
            setOnClickListener { showHome() }
        })
        page.addView(metrics, Ui.blockParams(this))

        // Recent alerts list that contains manually simulated test events
        val alerts = Ui.card(this)
        alerts.addView(Ui.title(this, "Recent Alerts"))
        if (recent.isEmpty()) alerts.addView(Ui.note(this, "No events yet"))
        recent.take(5).forEach { (behavior, time, severity) ->
            alerts.addView(Ui.alert(this, "$behavior · TEST", time, severity.uppercase(),
                if (severity == "High") Ui.red else Ui.amber))
        }
        page.addView(alerts, Ui.blockParams(this))
    }

    /** Show example sessions and manual Firebase synchronization controls. */
    private fun showHistory() {
        onHome = false
        selectTab(false)
        // These history rows are static examples currently
        val history = Ui.card(this)
        history.addView(Ui.title(this, "Session History"))
        history.addView(Ui.session(this, "Apr 26", "48 minutes", "5 alerts"))
        history.addView(Ui.session(this, "Apr 25", "1 hour 12 minutes", "2 alerts"))
        history.addView(Ui.session(this, "Apr 24", "35 minutes", "0 alerts"))
        page.addView(history, Ui.blockParams(this))

        // Retry pending Firebase events when the driver role is authorized
        val sync = Ui.card(this)
        sync.addView(Ui.title(this, "Supervisor Sync"))
        val button = Button(this).apply {
            text = "Manually Sync Logs to Supervisor"
            isAllCaps = false
            setOnClickListener {
                if (cloud.role != "driver") cloud.toast("Sign in as a driver researcher to sync.")
                else {
                    cloud.flush(); cloud.toast("Sync requested. Check pending count and Firebase connection.")
                }
            }
        }
        sync.addView(button)
        sync.addView(Ui.note(this, "Last successful event sync: ${cloud.lastSyncText()}"))
        sync.addView(Ui.note(this, "Pending simulated events: ${cloud.pendingCount}"))
        sync.addView(Ui.note(this, cloud.connectionLabel))
        accountArea(sync)
        page.addView(sync, Ui.blockParams(this))
    }
}
