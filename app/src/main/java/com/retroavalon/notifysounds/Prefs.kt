package com.retroavalon.notifysounds

import android.content.Context
import android.content.SharedPreferences

/**
 * Thin wrapper around SharedPreferences. Holds:
 *  - which app package we're counting notifications from
 *  - a sound URI (as string) per "slot" (1..MAX_SLOTS)
 *  - the idle timeout (seconds) after which the streak counter resets
 */
object Prefs {

    const val MAX_SLOTS = 6
    private const val FILE = "notify_sounds_prefs"
    private const val KEY_TARGET_PACKAGE = "target_package"
    private const val KEY_TIMEOUT_SECONDS = "timeout_seconds"
    private const val KEY_SLOT_PREFIX = "slot_"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getTargetPackage(context: Context): String? =
        prefs(context).getString(KEY_TARGET_PACKAGE, null)

    fun setTargetPackage(context: Context, packageName: String) {
        prefs(context).edit().putString(KEY_TARGET_PACKAGE, packageName).apply()
    }

    fun getTimeoutSeconds(context: Context): Int =
        prefs(context).getInt(KEY_TIMEOUT_SECONDS, 30)

    fun setTimeoutSeconds(context: Context, seconds: Int) {
        prefs(context).edit().putInt(KEY_TIMEOUT_SECONDS, seconds).apply()
    }

    fun getSlotSound(context: Context, slot: Int): String? =
        prefs(context).getString(KEY_SLOT_PREFIX + slot, null)

    fun setSlotSound(context: Context, slot: Int, uri: String) {
        prefs(context).edit().putString(KEY_SLOT_PREFIX + slot, uri).apply()
    }

    fun clearSlotSound(context: Context, slot: Int) {
        prefs(context).edit().remove(KEY_SLOT_PREFIX + slot).apply()
    }
}
