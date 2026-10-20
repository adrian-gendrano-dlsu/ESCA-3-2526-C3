package edu.dlsu.esca-3-2526-c3

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Static driver dashboard. All values are examples; no camera, AI, or sync is running. */
class MainActivity : Activity() {
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
        root.addView(Ui.header(this, "Driver mode", "Your driving dashboard  ·  DEMO"))
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
        showHome()
    }

    private fun selectTab(home: Boolean) {
        Ui.activeTab(homeTab, home)
        Ui.activeTab(historyTab, !home)
        page.removeAllViews()
        scroller.scrollTo(0, 0)
    }

    private fun showHome() {
        selectTab(true)
        val overview = Ui.card(this)
        overview.addView(Ui.eyebrow(this, "CURRENT SESSION"))
        overview.addView(Ui.title(this, "Risk Level"))
        overview.addView(Ui.chip(this, "LOW RISK  ·  DEMO", Ui.green))
        overview.addView(Ui.space(this, 12))
        overview.addView(Ui.placeholder(this, "CAMERA / AI LIVE VIEW", "Driver's face preview will appear here", 182))
        overview.addView(Ui.space(this, 12))
        overview.addView(Ui.pair(this, "Trip duration", "00:48:00"))
        page.addView(overview, Ui.blockParams(this))

        val metrics = Ui.card(this)
        metrics.addView(Ui.title(this, "Your Metrics"))
        metrics.addView(Ui.eyebrow(this, "DETECTION STATUS"))
        for (name in listOf("Drowsiness", "Yawning", "Gaze deviation", "Phone usage")) {
            metrics.addView(Ui.pair(this, name, "Normal"))
        }
        metrics.addView(Ui.space(this, 10))
        metrics.addView(Ui.eyebrow(this, "RAW MEASUREMENTS  ·  SAMPLE VALUES"))
        metrics.addView(Ui.pair(this, "EAR", "0.286"))
        metrics.addView(Ui.pair(this, "MAR", "0.124"))
        metrics.addView(Ui.pair(this, "Yaw", "4.5°"))
        metrics.addView(Ui.pair(this, "Pitch", "-2.0°"))
        page.addView(metrics, Ui.blockParams(this))

        val alerts = Ui.card(this)
        alerts.addView(Ui.title(this, "Recent Alerts"))
        alerts.addView(Ui.alert(this, "Phone usage", "10:49 AM", "HIGH", Ui.red))
        alerts.addView(Ui.alert(this, "Yawning", "10:30 AM", "WARNING", Ui.amber))
        alerts.addView(Ui.alert(this, "Gaze deviation", "10:12 AM", "ⓘ INFO", Ui.blue))
        page.addView(alerts, Ui.blockParams(this))
    }

    private fun showHistory() {
        selectTab(false)
        val history = Ui.card(this)
        history.addView(Ui.title(this, "Session History"))
        history.addView(Ui.eyebrow(this, "SAMPLE RECORDS"))
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
                Toast.makeText(this@MainActivity, "UI demonstration only. No logs were synchronized.", Toast.LENGTH_LONG).show()
            }
        }
        sync.addView(button)
        sync.addView(Ui.note(this, "Last synced: Apr 26, 5:00 PM  (sample only)"))
        page.addView(sync, Ui.blockParams(this))
    }
}
