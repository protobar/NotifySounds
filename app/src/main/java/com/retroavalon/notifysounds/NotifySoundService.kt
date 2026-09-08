package com.retroavalon.notifysounds

import android.app.Notification
import android.media.MediaPlayer
import android.net.Uri
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Listens to every notification posted on the device (system requirement — you can't
 * filter at the OS level), but only reacts to ones from the package the user picked
 * in the app. Keeps a "streak" counter that maps to a sound slot, the same way a game
 * plays a different kill-streak sound at 1, 2, 3... kills. Counter resets after an
 * idle timeout with no new notification from that app.
 */
class NotifySoundService : NotificationListenerService() {

    companion object {
        private const val TAG = "NotifySoundService"
        private var streakCount = 0
        private var lastNotificationAtMs = 0L
        private var activePlayer: MediaPlayer? = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        val targetPackage = Prefs.getTargetPackage(applicationContext) ?: return
        if (sbn.packageName != targetPackage) return

        // Messaging apps (WhatsApp included) post a real per-message notification AND
        // an invisible "group summary" notification for the same conversation — both
        // land in onNotificationPosted. Without this check every message counts twice.
        val isGroupSummary = (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        if (isGroupSummary) return

        val now = System.currentTimeMillis()
        val timeoutMs = Prefs.getTimeoutSeconds(applicationContext) * 1000L

        streakCount = if (now - lastNotificationAtMs > timeoutMs) 1 else streakCount + 1
        lastNotificationAtMs = now

        // Cap at MAX_SLOTS: anything beyond the last configured slot keeps reusing it,
        // same idea as an "ace" sound covering 5+ kills.
        val slot = streakCount.coerceAtMost(Prefs.MAX_SLOTS)
        playSlot(slot)
    }

    private fun playSlot(slot: Int) {
        val uriString = Prefs.getSlotSound(applicationContext, slot) ?: return
        try {
            activePlayer?.release()
            val player = MediaPlayer()
            activePlayer = player
            player.setDataSource(applicationContext, Uri.parse(uriString))
            player.setOnCompletionListener {
                it.release()
                if (activePlayer === it) activePlayer = null
            }
            player.prepare()
            player.start()
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't play sound for slot $slot", e)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification listener connected")
    }
}
