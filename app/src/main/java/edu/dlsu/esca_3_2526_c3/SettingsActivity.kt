package edu.dlsu.esca_3_2526_c3

import android.app.Activity
import android.content.Context
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class SettingsActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("device_setup_v1", Context.MODE_PRIVATE) }
    private lateinit var ssid: EditText
    private lateinit var password: EditText
    private lateinit var cameraUrl: EditText
    private lateinit var sensorUrl: EditText
    private lateinit var pollMs: EditText
    private lateinit var status: TextView
    private lateinit var snapshot: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Ui.navy
        window.navigationBarColor = Ui.navy
        val scroller = ScrollView(this).apply { setBackgroundColor(Ui.background) }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@SettingsActivity, 16), Ui.dp(this@SettingsActivity, 16),
                Ui.dp(this@SettingsActivity, 16), Ui.dp(this@SettingsActivity, 28))
        }
        scroller.addView(body)
        setContentView(scroller)

        body.addView(Ui.title(this, "Device settings"))

        val pairing = Ui.card(this)

        pairing.addView(Ui.note(this, "Camera Wi-Fi name"))
        ssid = field("Camera Wi-Fi name ",
            prefs.getString("camera_ssid", "DMS-CAM") ?: "DMS-CAM")
        pairing.addView(ssid)

        pairing.addView(Ui.note(this, "New Wi-Fi password (8–63 characters)"))
        password = field("New Wi-Fi password (8–63 characters)", "").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        pairing.addView(password)

        pairing.addView(action("Send Wi-Fi credentials") { provisionCamera() })
        body.addView(pairing, Ui.blockParams(this))

        val endpoints = Ui.card(this)
        endpoints.addView(Ui.title(this, "Device addresses"))

        endpoints.addView(Ui.note(this, "Camera base URL"))
        cameraUrl = field("Camera base URL", prefs.getString("camera_url", "http://192.168.4.1") ?: "http://192.168.4.1")
        endpoints.addView(cameraUrl)

        endpoints.addView(Ui.note(this, "Sensor base URL"))
        sensorUrl = field("Sensor base URL", prefs.getString("sensor_url", "") ?: "")
        endpoints.addView(sensorUrl)

        endpoints.addView(Ui.note(this, "Future sensor polling interval (ms)"))
        pollMs = field("Future sensor polling interval (ms)", prefs.getInt("poll_ms", 1000).toString()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        endpoints.addView(pollMs)

        endpoints.addView(action("Save settings") { saveLocal() })
        endpoints.addView(action("Check camera status") { checkStatus() })
        endpoints.addView(action("Test camera photo") { testSnapshot() })
        endpoints.addView(action("Test MPU6050") { testSensors() })

        snapshot = ImageView(this).apply {
            adjustViewBounds = true
            minimumHeight = Ui.dp(this@SettingsActivity, 90)
            contentDescription = "Last test image"
        }
        endpoints.addView(snapshot, LinearLayout.LayoutParams(-1, -2))
        body.addView(endpoints, Ui.blockParams(this))
    }

    private fun field(hintText: String, initial: String) = EditText(this).apply {
        hint = hintText
        setSingleLine(true)
        setText(initial)
        textSize = 15f
        setSelectAllOnFocus(true)
        setPadding(Ui.dp(this@SettingsActivity, 8), Ui.dp(this@SettingsActivity, 10),
            Ui.dp(this@SettingsActivity, 8), Ui.dp(this@SettingsActivity, 10))
    }

    private fun action(label: String, callback: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { callback() }
    }

    private fun normalizedBase(text: String, optional: Boolean = false): String {
        val value = text.trim().trimEnd('/')
        if (optional && value.isEmpty()) return ""
        val url = URL(value)
        require(url.protocol == "http" || url.protocol == "https") { "Address must start with http:// or https://" }
        require(url.host.isNotBlank() && url.userInfo == null) { "Enter a valid URL without credentials" }
        require(url.path.isEmpty() || url.path == "/") { "Enter only the base URL, for example http://192.168.4.1" }
        require(url.query == null && url.ref == null) { "Remove URL queries and fragments" }
        return value
    }

    private fun saveLocal() {
        try {
            val camera = normalizedBase(cameraUrl.text.toString())
            val sensor = normalizedBase(sensorUrl.text.toString(), optional = true)
            val interval = pollMs.text.toString().toInt()
            require(interval in 200..60000) { "Polling interval must be 200–60000 ms" }
            prefs.edit().putString("camera_url", camera).putString("sensor_url", sensor)
                .putString("camera_ssid", ssid.text.toString().trim()).putInt("poll_ms", interval).apply()
            show("Saved locally.")
        } catch (e: Exception) { show("Could not save settings. ${e.message}") }
    }

    private fun show(message: String) { status.text = message }

    private fun onWorker(label: String, operation: () -> Unit) {
        show("$label …")
        Thread {
            try { operation() }
            catch (e: Exception) { runOnUiThread { show("$label failed. ${e.message}") } }
        }.start()
    }

    /** Use actual Wi-Fi Network */
    private fun connect(url: String, method: String = "GET", form: String? = null): HttpURLConnection {
        val target = URL(url)
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifi = manager.allNetworks.firstOrNull { network ->
            manager.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        val connection = (if (wifi != null) wifi.openConnection(target) else target.openConnection()) as HttpURLConnection
        connection.connectTimeout = 4000
        connection.readTimeout = 4000
        connection.requestMethod = method
        connection.useCaches = false
        if (form != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            connection.outputStream.use { it.write(form.toByteArray(Charsets.UTF_8)) }
        }
        return connection
    }

    private fun requestText(url: String, method: String = "GET", form: String? = null): String {
        val connection = connect(url, method, form)
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val message = stream?.bufferedReader()?.use { it.readText().take(4000) } ?: "No response body"
            require(code in 200..299) { "HTTP $code, $message" }
            return message
        } finally { connection.disconnect() }
    }

    private fun provisionCamera() {
        val newSsid = ssid.text.toString().trim()
        val newPassword = password.text.toString()
        if (!Regex("[A-Za-z0-9_-]{3,32}").matches(newSsid)) {
            show("Wi-Fi name must contain 3–32 letters, numbers, hyphens or underscores."); return
        }
        if (newPassword.length !in 8..63 || newPassword.any { it.code !in 32..126 }) {
            show("Wi-Fi password must have 8–63 characters."); return
        }
        onWorker("Provisioning camera") {
            val form = "ssid=" + URLEncoder.encode(newSsid, "UTF-8") +
                "&password=" + URLEncoder.encode(newPassword, "UTF-8")
            val response = JSONObject(requestText("http://192.168.4.1/api/config", "POST", form))
            require(response.optBoolean("ok", false)) { "ESP32 did not confirm the configuration" }
            runOnUiThread {
                password.setText("")
                prefs.edit().putString("camera_ssid", newSsid).apply()
                show("Saved on ESP32. It will reboot. Open Wi-Fi settings and join '$newSsid' using the password you just chose.")
            }
        }
    }

    private fun checkStatus() {
        val base = try { normalizedBase(cameraUrl.text.toString()) }
        catch (e: Exception) { show("Invalid camera URL. ${e.message}"); return }
        onWorker("Checking camera") {
        val response = JSONObject(requestText("$base/api/status"))
        runOnUiThread {
            show("Camera mode ${response.optString("mode", "?")}; Wi-Fi ${response.optString("ssid", "?")}; IP ${response.optString("ip", "?")}; camera initialized ${response.optBoolean("cameraReady", false)}")
        }
        }
    }

    private fun testSnapshot() {
        val base = try { normalizedBase(cameraUrl.text.toString()) }
        catch (e: Exception) { show("Invalid camera URL. ${e.message}"); return }
        onWorker("Testing camera photo") {
        val connection = connect("$base/jpg")
        try {
            require(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
            require(connection.contentType?.startsWith("image/jpeg") == true) { "Not a JPEG response" }
            val bitmap = connection.inputStream.use { BitmapFactory.decodeStream(it) }
            require(bitmap != null) { "Could not decode camera JPEG" }
            runOnUiThread {
                snapshot.setImageBitmap(bitmap)
                show("Camera photo received: ${bitmap.width} × ${bitmap.height}.")
            }
        } finally { connection.disconnect() }
        }
    }

    private fun testSensors() {
        val base = try { normalizedBase(sensorUrl.text.toString(), optional = true) }
        catch (e: Exception) { show("Invalid sensor URL. ${e.message}"); return }
        if (base.isEmpty()) { show("Enter a sensor endpoint first."); return }
        onWorker("Testing sensors") {
            val json = JSONObject(requestText("$base/sensors"))
            runOnUiThread {
                show("Sensor response. GPS fix ${json.optBoolean("fix", false)}, speed ${json.optDouble("kmh", 0.0)} km/h, lat ${json.optDouble("lat", 0.0)}, lng ${json.optDouble("lng", 0.0)}.")
            }
        }
    }
}
