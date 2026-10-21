package edu.dlsu.esca_3_2526_c3

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** A separate launch entry. No navigation from MainActivity leads here. */
class SupervisorActivity : Activity() {
    private data class Driver(
        val name: String, val risk: String, val event: String,
        val trip: String, val speed: String, val position: String, val color: Int
    )

    private val drivers = listOf(
        Driver("Driver 1", "High risk", "Using phone", "48 minutes", "61 km/h", "18.35, 121.04", Ui.red),
        Driver("Driver 2", "Medium risk", "Yawning", "32 minutes", "45 km/h", "18.31, 121.07", Ui.amber),
        Driver("Driver 3", "Low risk", "Gaze deviation", "15 minutes", "27 km/h", "18.28, 121.09", Ui.green)
    )
    private var selected = 0
    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Ui.navy
        window.navigationBarColor = Ui.navy
        val scroll = ScrollView(this).apply { setBackgroundColor(Ui.background) }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@SupervisorActivity, 16), 0, Ui.dp(this@SupervisorActivity, 16), Ui.dp(this@SupervisorActivity, 24))
        }
        scroll.addView(content)
        setContentView(scroll)
        showSupervisor()
    }

    private fun showSupervisor() {
        content.removeAllViews()
        content.addView(Ui.header(this, "Supervisor mode", "Remote monitoring  ·  DEMO"), Ui.blockParams(this))
        val monitor = Ui.card(this)
        monitor.addView(Ui.title(this, "Live Monitoring"))
        monitor.addView(Ui.note(this, "Select a driver to update the details below"))
        drivers.forEachIndexed { index, driver ->
            val row = Ui.monitorRow(this, driver.name, driver.risk, driver.event, driver.color, index == selected)
            row.setOnClickListener { selected = index; showSupervisor() }
            monitor.addView(row)
        }
        content.addView(monitor, Ui.blockParams(this))

        val driver = drivers[selected]
        val details = Ui.card(this)
        details.addView(Ui.title(this, "Selected Driver"))
        details.addView(Ui.pair(this, "Driver", driver.name))
        details.addView(Ui.pair(this, "Risk Level", driver.risk))
        details.addView(Ui.pair(this, "Current status", driver.event))
        details.addView(Ui.pair(this, "Trip duration", driver.trip))
        details.addView(Ui.pair(this, "Speed", driver.speed))
        details.addView(Ui.pair(this, "Location", driver.position))
        details.addView(Ui.space(this, 10))
        details.addView(Ui.placeholder(this, "MAP PLACEHOLDER", "GPS map for ${driver.name} goes here", 150))
        content.addView(details, Ui.blockParams(this))

        val alerts = Ui.card(this)
        alerts.addView(Ui.title(this, "Recent Alerts"))
        alerts.addView(Ui.alert(this, "Driver 3 · Phone usage", "10:49 AM", "HIGH", Ui.red))
        alerts.addView(Ui.alert(this, "Driver 1 · Yawning", "10:30 AM", "WARNING", Ui.amber))
        alerts.addView(Ui.alert(this, "Driver 2 · Gaze deviation", "10:12 AM", "ⓘ INFO", Ui.blue))
        content.addView(alerts, Ui.blockParams(this))
    }
}
