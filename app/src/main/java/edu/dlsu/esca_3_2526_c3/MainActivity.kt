package edu.dlsu.esca_3_2526_c3

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.util.UUID
import java.text.DateFormat
import java.util.Date
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Driver interface with manually simulated alerts for testing Firebase synchronization. */
class MainActivity : Activity() {
    private lateinit var cloud: DemoCloud
    private val resetHandler = Handler(Looper.getMainLooper())
    private val statuses = linkedMapOf(
        "Drowsiness" to "Normal", "Yawning" to "Normal",
        "Gaze deviation" to "Normal", "Phone usage" to "Normal"
    )
    private val sessionId = "DEMO-" + UUID.randomUUID().toString()
    private val sessionStarted = System.currentTimeMillis()
    private val recent = mutableListOf<Triple<String, String, String>>()
    private var onHome = true
    private lateinit var page: LinearLayout
    private lateinit var scroller: ScrollView
    private lateinit var homeTab: TextView
    private lateinit var historyTab: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Ui.navy
        window.navigationBarColor = Ui.navy

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.background)
        }
        root.addView(Ui.header(this, "Driver Dashboard"))
        scroller = ScrollView(this).apply {
            isFillViewport = true
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 24))
                page = this
            })
        }
        root.addView(scroller, LinearLayout.LayoutParams(-1, 0, 1f))

        val tabs = LinearLayout(this).apply {
            setBackgroundColor(Ui.white)
            setPadding(Ui.dp(this@MainActivity, 12), Ui.dp(this@MainActivity, 8), Ui.dp(this@MainActivity, 12), Ui.dp(this@MainActivity, 8))
        }
        homeTab = Ui.tab(this, "⌂  Home") { showHome() }
        historyTab = Ui.tab(this, "◷  History") { showHistory() }
        tabs.addView(homeTab, LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1f))
        tabs.addView(historyTab, LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1f))
        root.addView(tabs)
        setContentView(root)
        cloud = DemoCloud(this)
        cloud.onChange = { if (!isFinishing) updateVisiblePage() }
        cloud.start()
        showHome()
    }

    private fun updateVisiblePage() { if (onHome) showHome() else showHistory() }

    override fun onDestroy() {
        resetHandler.removeCallbacksAndMessages(null)
        cloud.stop()
        super.onDestroy()
    }

    private fun trigger(name: String) {
        val current = statuses[name] ?: "Normal"
        val next = when (current) { "Normal" -> "Warning"; "Warning" -> "High"; else -> "Normal" }
        statuses[name] = next
        if (next != "Normal") {
            val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())
            recent.add(0, Triple(name, time, next))
            cloud.record(name, next, sessionId)
        }
        cloud.publish(statuses, sessionId, (System.currentTimeMillis()-sessionStarted)/1000)
        updateVisiblePage()
        if (next != "Normal") {
            resetHandler.postDelayed({
                if (statuses[name] == next) {
                    statuses[name] = "Normal"
                    cloud.publish(statuses, sessionId, (System.currentTimeMillis()-sessionStarted)/1000)
                    updateVisiblePage()
                }
            }, 12000L)
        }
    }

    private fun accountArea(target: LinearLayout) {
        target.addView(Ui.note(this, cloud.connectionLabel))
        val button = Button(this).apply {
            text = if (cloud.signedIn) "Sign out  ·  ${cloud.email}" else "Sign in (Driver researcher account)"
            isAllCaps = false
            setOnClickListener {
                if (cloud.signedIn) cloud.signOut() else cloud.signInDialog("driver")
            }
        }
        target.addView(button)
        if (cloud.signedIn && cloud.role != "driver") {
            target.addView(Ui.note(this, "Driver Firebase writes require a researcher account assigned the driver role."))
        }
    }

    private fun selectTab(home: Boolean) {
        Ui.activeTab(homeTab, home)
        Ui.activeTab(historyTab, !home)
        page.removeAllViews()
        scroller.scrollTo(0, 0)
    }

    private fun showHome() {
        onHome = true
        selectTab(true)
        val overview = Ui.card(this)
        overview.addView(Ui.eyebrow(this, "CURRENT SESSION"))
        overview.addView(Ui.title(this, "Risk Level"))
        val risk = if (statuses.values.any { it == "High" }) "HIGH" else if (statuses.values.any { it == "Warning" }) "MEDIUM" else "LOW"
        overview.addView(Ui.bigChip(this, "$risk RISK", if (risk == "HIGH") Ui.red else if (risk == "MEDIUM") Ui.amber else Ui.green))
        overview.addView(Ui.space(this, 12))
        overview.addView(Ui.placeholder(this, "LIVE VIEW", "Driver's face preview will appear here", 150))
        overview.addView(Ui.space(this, 12))
        overview.addView(Ui.pair(this, "Trip duration", "${(System.currentTimeMillis()-sessionStarted)/60000} min"))
        accountArea(overview)
        page.addView(overview, Ui.blockParams(this))

        val metrics = Ui.card(this)
        metrics.addView(Ui.title(this, "Your Metrics"))
        metrics.addView(Ui.eyebrow(this, "DETECTION STATUS"))
        for (name in listOf("Drowsiness", "Yawning", "Gaze deviation", "Phone usage")) {
            val row = Ui.pair(this, name, statuses[name] ?: "Normal")
            row.setOnClickListener { trigger(name) }
            row.contentDescription = "$name. Tap to cycle Normal, Warning, High, and simulate a Firebase event."
            metrics.addView(row)
        }
        metrics.addView(Ui.space(this, 10))
        metrics.addView(Ui.eyebrow(this, "RAW MEASUREMENTS"))
        metrics.addView(Ui.pair(this, "EAR", "0.286"))
        metrics.addView(Ui.pair(this, "MAR", "0.124"))
        metrics.addView(Ui.pair(this, "Yaw", "4.5°"))
        metrics.addView(Ui.pair(this, "Pitch", "-2.0°"))
        page.addView(metrics, Ui.blockParams(this))

        val alerts = Ui.card(this)
        alerts.addView(Ui.title(this, "Recent Alerts"))
        if (recent.isEmpty()) alerts.addView(Ui.note(this, "No events yet"))
        recent.take(5).forEach { (behavior, time, severity) ->
            alerts.addView(Ui.alert(this, "$behavior · TEST", time, severity.uppercase(), if (severity == "High") Ui.red else Ui.amber))
        }
        page.addView(alerts, Ui.blockParams(this))
    }

    private fun showHistory() {
        onHome = false
        selectTab(false)
        val history = Ui.card(this)
        history.addView(Ui.title(this, "Session History"))
        history.addView(Ui.session(this, "Apr 26", "48 minutes", "5 alerts"))
        history.addView(Ui.session(this, "Apr 25", "1 hour 12 minutes", "2 alerts"))
        history.addView(Ui.session(this, "Apr 24", "35 minutes", "0 alerts"))
        page.addView(history, Ui.blockParams(this))

        val sync = Ui.card(this)
        sync.addView(Ui.title(this, "Supervisor Sync"))
        val button = Button(this).apply {
            text = "Manually Sync Logs to Supervisor"
            isAllCaps = false
            setOnClickListener {
                if (cloud.role != "driver") cloud.toast("Sign in as a driver researcher to sync.")
                else { cloud.flush(); cloud.toast("Sync requested. Check pending count and Firebase connection.") }
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
