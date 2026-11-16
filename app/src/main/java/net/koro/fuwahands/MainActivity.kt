package net.koro.fuwahands

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.net.NetworkInterface

/**
 * Setup screen: shows status, the API token and this phone's LAN
 * addresses, and walks the user through the two things only they can do:
 *  1. enable the accessibility service in Android settings
 *  2. grant the screen-capture consent (system dialog) and start
 */
class MainActivity : Activity() {

    private lateinit var statusView: TextView
    private val reqCapture = 42

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Opening the app ensures the API core is up (it also auto-starts on boot).
        if (!FuwaCoreService.running) {
            startForegroundService(
                Intent(this, FuwaCoreService::class.java).setAction(FuwaCoreService.ACTION_START)
            )
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 48)
        }

        root.addView(TextView(this).apply {
            text = "fuwa-hands"
            textSize = 26f
        })
        root.addView(TextView(this).apply {
            text = "ふわがこのスマホの画面を見て、タップ操作するためのアプリです。\n" +
                "動かすのはあなたの操作だけ。止めたいときは通知からいつでも止められます。"
            textSize = 14f
            setPadding(0, 16, 0, 24)
        })

        statusView = TextView(this).apply { textSize = 15f }
        root.addView(statusView)

        root.addView(Button(this).apply {
            text = "1. アクセシビリティ設定を開く"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        })
        root.addView(Button(this).apply {
            text = "2. 画面共有を許可して開始"
            setOnClickListener {
                if (FuwaService.running) {
                    Toast.makeText(this@MainActivity, "画面共有はすでに動作中です", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val mpm = getSystemService(MediaProjectionManager::class.java)
                @Suppress("DEPRECATION")
                startActivityForResult(mpm.createScreenCaptureIntent(), reqCapture)
            }
        })
        root.addView(Button(this).apply {
            text = "すべて停止（API・画面共有）"
            setOnClickListener {
                startService(Intent(this@MainActivity, FuwaService::class.java).setAction(FuwaService.ACTION_STOP))
                startService(Intent(this@MainActivity, FuwaCoreService::class.java).setAction(FuwaCoreService.ACTION_STOP))
                refresh()
            }
        })
        root.addView(Button(this).apply {
            text = "状態を更新"
            setOnClickListener { refresh() }
        })

        root.addView(TextView(this).apply {
            text = "API トークン（ふわに一度だけ教えてください）"
            textSize = 13f
            setPadding(0, 32, 0, 4)
        })
        root.addView(TextView(this).apply {
            text = Prefs.token(this@MainActivity)
            textSize = 13f
            setTextIsSelectable(true)
        })
        root.addView(TextView(this).apply {
            text = "接続先: http://<このスマホのIP>:${FuwaCoreService.PORT}"
            textSize = 13f
            setPadding(0, 16, 0, 0)
        })

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    @Deprecated("activity result for the capture consent")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == reqCapture) {
            if (resultCode == RESULT_OK && data != null) {
                val svc = Intent(this, FuwaService::class.java).apply {
                    action = FuwaService.ACTION_START
                    putExtra(FuwaService.EXTRA_RESULT_CODE, resultCode)
                    putExtra(FuwaService.EXTRA_DATA, data)
                }
                startForegroundService(svc)
                Toast.makeText(this, "開始しました", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "画面共有が許可されませんでした", Toast.LENGTH_SHORT).show()
            }
            refresh()
        }
    }

    private fun accessibilityEnabled(): Boolean {
        if (FuwaAccessibilityService.instance != null) return true
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains(packageName)
    }

    private fun lanAddresses(): List<String> {
        val out = mutableListOf<String>()
        try {
            val ifaces = NetworkInterface.getNetworkInterfaces()
            while (ifaces.hasMoreElements()) {
                val addrs = ifaces.nextElement().inetAddresses
                while (addrs.hasMoreElements()) {
                    val a = addrs.nextElement()
                    if (!a.isLoopbackAddress && a.hostAddress?.contains(':') == false) {
                        out.add(a.hostAddress!!)
                    }
                }
            }
        } catch (_: Exception) { }
        return out
    }

    private fun refresh() {
        if (!::statusView.isInitialized) return
        val sb = StringBuilder()
        sb.append("アクセシビリティ: ").append(if (accessibilityEnabled()) "有効" else "未設定").append('\n')
        sb.append("APIコア: ").append(if (FuwaCoreService.running) "動作中" else "停止中").append('\n')
        sb.append("画面キャプチャ: ").append(if (ScreenCapture.active) "有効" else "未開始").append('\n')
        val ips = lanAddresses()
        sb.append("このスマホのアドレス: ").append(if (ips.isEmpty()) "不明" else ips.joinToString(", "))
        statusView.text = sb.toString()
        statusView.gravity = Gravity.START
    }
}
