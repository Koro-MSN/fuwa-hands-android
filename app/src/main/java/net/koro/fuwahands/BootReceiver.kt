package net.koro.fuwahands

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Starts the API core after boot so fuwa-hands is reachable without
 *  the user opening the app first. Capture is never auto-started:
 *  Android requires fresh consent for every screen-sharing session. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ctx.startForegroundService(
                Intent(ctx, FuwaCoreService::class.java).setAction(FuwaCoreService.ACTION_START)
            )
        }
    }
}
