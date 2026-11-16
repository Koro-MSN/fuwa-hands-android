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
 * Screen-capture service (MediaProjection). Android requires the user's
 * consent for every session, so this is always started on demand from
 * MainActivity — never automatically. The HTTP API itself lives in
 * FuwaCoreService; this service only feeds it frames via ScreenCapture.
 */
class FuwaService : Service() {

    companion object {
        const val ACTION_START = "net.koro.fuwahands.START"
        const val ACTION_STOP = "net.koro.fuwahands.STOP"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"

        @Volatile var running: Boolean = false
            private set
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopAll()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                if (running) return START_STICKY
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                @Suppress("DEPRECATION")
                val data: Intent? = intent.getParcelableExtra(EXTRA_DATA) as? Intent
                if (data == null) { stopSelf(); return START_NOT_STICKY }

                createChannel()
                startForeground(
                    1, buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
                ScreenCapture.start(this, resultCode, data)
                running = true
                return START_STICKY
            }
            else -> return START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        stopAll()
        super.onDestroy()
    }

    private fun stopAll() {
        running = false
        ScreenCapture.stop()
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
        val stopIntent = Intent(this, FuwaService::class.java).setAction(ACTION_STOP)
        val stopPi = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, "fuwa")
            .setContentTitle("fuwa-hands 画面共有中")
            .setContentText("画面キャプチャが動作しています")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "停止", stopPi).build())
            .build()
    }
}
