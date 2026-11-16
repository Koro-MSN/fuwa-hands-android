package net.koro.fuwahands

import android.content.Context
import java.util.UUID

object Prefs {
    private const val FILE = "fuwa"
    private const val KEY_TOKEN = "token"

    /** API bearer token: generated once, then stable. Shown in MainActivity. */
    fun token(ctx: Context): String {
        val sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val existing = sp.getString(KEY_TOKEN, null)
        if (existing != null) return existing
        val t = UUID.randomUUID().toString()
        sp.edit().putString(KEY_TOKEN, t).apply()
        return t
    }
}
