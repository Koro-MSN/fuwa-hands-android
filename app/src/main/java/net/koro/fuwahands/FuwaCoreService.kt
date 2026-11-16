package net.koro.fuwahands

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder

/**
 * Always-on core: hosts the HTTP API (health / tree / tap / keys / type).
 * Auto-started on boot by BootReceiver at the user's request and from
 * MainActivity. Does NOT capture the screen — MediaProjection needs the
 * user's consent every session, so capture lives in FuwaService and is
 * started on demand; this core merely serves its frames when active.
 */
class FuwaCoreService : Service() {

    companion object {
        const val ACTION_START = "net.koro.fuwahands.CORE_START"
        const val ACTION_STOP = "net.koro.fuwahands.CORE_STOP"
        const val PORT = 8766

        @Volatile var running: Boolean = false
            private set
    }

    private var http: HttpServer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopAll()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                if (!running) {
                    createChannel()
                    startForeground(
                        2, buildNotification(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                    http = HttpServer(PORT, Prefs.token(this)).also { it.start() }
                    running = true
                }
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        stopAll()
        super.onDestroy()
    }

    private fun stopAll() {
        running = false
        http?.stop(); http = null
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel("fuwa") == null) {
            nm.createNotificationChannel(
                NotificationChannel("fuwa", "fuwa-hands", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, FuwaCoreService::class.java).setAction(ACTION_STOP)
        val stopPi = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, "fuwa")
            .setContentTitle("fuwa-hands 待受中")
            .setContentText("ポート $PORT で API が動作しています")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "停止", stopPi).build())
            .build()
    }
}
