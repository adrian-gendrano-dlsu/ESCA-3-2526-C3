package edu.dlsu.esca_3_2526_c3

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class DemoMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data["demo"] != "true") return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = "esca_3_2526_c3_demo_high"
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(
                channel, "Simulated high-risk tests", NotificationManager.IMPORTANCE_HIGH
            ))
        }
        val notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("SIMULATED high-risk alert")
            .setContentText(message.data["behavior"] ?: "Driver 1 test event")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }
}
