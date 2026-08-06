package com.oneclickcopy.sync

import android.content.Context
import androidx.core.content.edit

/** Small persisted store for sync bookkeeping. */
class SyncPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var lastSyncedAt: Long
        get() = prefs.getLong(KEY_LAST_SYNCED_AT, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_SYNCED_AT, value) }

    private companion object {
        const val NAME = "sync_prefs"
        const val KEY_LAST_SYNCED_AT = "last_synced_at"
    }
}
