package net.koro.fuwahands

import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Tiny HTTP API shaped like the fuwa-hands PC agent:
 *   GET  /health        -> status json
 *   GET  /screenshot    -> image/jpeg (X-Width / X-Height headers)
 *   GET  /screen/tree   -> accessibility UI tree json
 *   POST /tap           {x, y}
 *   POST /swipe         {x1, y1, x2, y2, duration_ms?}
 *   POST /long_press    {x, y, duration_ms?}
 *   POST /key           {key: back|home|recents|notifications|quick_settings|power_dialog|lock}
 *   POST /input/type    {text}
 * Every endpoint requires "Authorization: Bearer <token>".
 */
class HttpServer(private val port: Int, private val token: String) {
    @Volatile var running: Boolean = false
        private set
    private var socket: ServerSocket? = null

    fun start() {
        if (running) return
        val ss = ServerSocket(port)
        socket = ss
        running = true
        thread(name = "fuwa-http", isDaemon = true) {
            while (running) {
                try {
                    val client = ss.accept()
                    thread(isDaemon = true) { handle(client) }
                } catch (_: Exception) {
                    if (running) Thread.sleep(100)
                }
            }
        }
    }

    fun stop() {
        running = false
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }

    private fun handle(client: Socket) {
        client.soTimeout = 15000
        try {
            val input = BufferedInputStream(client.getInputStream())
            val out = client.getOutputStream()

            // --- request line + headers ---
            val headerBytes = ByteArrayOutputStream()
            var matched = 0
            val terminator = intArrayOf('\r'.code, '\n'.code, '\r'.code, '\n'.code)
            while (headerBytes.size() < 16384) {
                val b = input.read()
                if (b == -1) break
                headerBytes.write(b)
                matched = if (b == terminator[matched]) matched + 1 else if (b == terminator[0]) 1 else 0
                if (matched == 4) break
            }
            val headerText = headerBytes.toString(Charsets.ISO_8859_1.name())
            val lines = headerText.split("\r\n")
            val requestLine = lines.firstOrNull()?.split(" ") ?: emptyList()
            if (requestLine.size < 2) { respond(out, 400, err("bad request")); return }
            val method = requestLine[0]
            val path = requestLine[1].substringBefore("?")
            var contentLength = 0
            var auth: String? = null
            for (line in lines.drop(1)) {
                val idx = line.indexOf(':')
                if (idx <= 0) continue
                val name = line.substring(0, idx).trim().lowercase()
                val value = line.substring(idx + 1).trim()
                when (name) {
                    "content-length" -> contentLength = value.toIntOrNull() ?: 0
                    "authorization" -> auth = value
                }
            }
            var body = ""
            if (contentLength > 0) {
                val buf = ByteArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val n = input.read(buf, read, contentLength - read)
                    if (n == -1) break
                    read += n
                }
                body = String(buf, 0, read, Charsets.UTF_8)
            }

            if (auth != "Bearer $token") { respond(out, 401, err("unauthorized")); return }

            route(method, path, body, out)
        } catch (_: Exception) {
            // client went away or timed out; nothing useful to do
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun route(method: String, path: String, body: String, out: OutputStream) {
        val a11y = FuwaAccessibilityService.instance
        when {
            method == "GET" && path == "/health" -> {
                val o = JSONObject()
                o.put("ok", true)
                o.put("version", "0.2.0")
                o.put("accessibility", a11y != null)
                o.put("capture", ScreenCapture.active)
                if (ScreenCapture.width > 0) {
                    o.put("width", ScreenCapture.width)
                    o.put("height", ScreenCapture.height)
                }
                respond(out, 200, o)
            }
            method == "GET" && path == "/screenshot" -> {
                val jpg = ScreenCapture.jpeg()
                if (jpg == null) {
                    respond(out, 503, err("no frame yet (capture not started or first frame pending)"))
                } else {
                    val head = "HTTP/1.1 200 OK\r\nContent-Type: image/jpeg\r\n" +
                        "X-Width: ${ScreenCapture.width}\r\nX-Height: ${ScreenCapture.height}\r\n" +
                        "Content-Length: ${jpg.size}\r\nConnection: close\r\n\r\n"
                    out.write(head.toByteArray(Charsets.ISO_8859_1))
                    out.write(jpg)
                    out.flush()
                }
            }
            method == "GET" && path == "/screen/tree" -> {
                if (a11y == null) { respond(out, 503, err("accessibility service not enabled")); return }
                try {
                    val tree = a11y.treeJson()
                    val o = JSONObject()
                    o.put("ok", true)
                    o.put("tree", tree)
                    respond(out, 200, o)
                } catch (e: Exception) {
                    respond(out, 500, err("tree failed: ${e.message}"))
                }
            }
            method == "POST" && path == "/tap" -> {
                if (a11y == null) { respond(out, 503, err("accessibility service not enabled")); return }
                val j = JSONObject(body)
                okOr(out, a11y.tap(j.getInt("x"), j.getInt("y")))
            }
            method == "POST" && path == "/long_press" -> {
                if (a11y == null) { respond(out, 503, err("accessibility service not enabled")); return }
                val j = JSONObject(body)
                okOr(out, a11y.longPress(j.getInt("x"), j.getInt("y"), j.optLong("duration_ms", 600)))
            }
            method == "POST" && path == "/swipe" -> {
                if (a11y == null) { respond(out, 503, err("accessibility service not enabled")); return }
                val j = JSONObject(body)
                okOr(out, a11y.swipe(j.getInt("x1"), j.getInt("y1"), j.getInt("x2"), j.getInt("y2"), j.optLong("duration_ms", 300)))
            }
            method == "POST" && path == "/key" -> {
                if (a11y == null) { respond(out, 503, err("accessibility service not enabled")); return }
                val j = JSONObject(body)
                okOr(out, a11y.global(j.getString("key")))
            }
            method == "POST" && path == "/input/type" -> {
                if (a11y == null) { respond(out, 503, err("accessibility service not enabled")); return }
                val j = JSONObject(body)
                okOr(out, a11y.setText(j.getString("text")))
            }
            else -> respond(out, 404, err("not found: $method $path"))
        }
    }

    private fun okOr(out: OutputStream, ok: Boolean) {
        if (ok) respond(out, 200, JSONObject().put("ok", true))
        else respond(out, 500, err("action failed"))
    }

    private fun err(msg: String): JSONObject = JSONObject().put("ok", false).put("error", msg)

    private fun respond(out: OutputStream, code: Int, json: JSONObject) {
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        val reason = when (code) {
            200 -> "OK"; 400 -> "Bad Request"; 401 -> "Unauthorized"
            404 -> "Not Found"; 500 -> "Internal Server Error"; 503 -> "Service Unavailable"
            else -> "Status"
        }
        val head = "HTTP/1.1 $code $reason\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        out.write(head.toByteArray(Charsets.ISO_8859_1))
        out.write(bytes)
        out.flush()
    }
}
