package com.brian.dialbuddy.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.brian.dialbuddy.domain.ReleaseOffer
import com.brian.dialbuddy.domain.formatAreaCodes
import com.brian.dialbuddy.domain.parseAreaCodes
import kotlinx.coroutines.flow.first

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

// The screening service reads these on the incoming-call thread.
private const val AREA_PREFS = "area_codes"
private const val AREA_KEY = "codes"

// The update check reads these on a background thread.
private const val UPDATE_PREFS = "app_update"
private const val UPDATE_CHECKED_AT = "checked_at"
private const val UPDATE_VERSION = "version_name"
private const val UPDATE_URL = "apk_url"
private const val UPDATE_DISMISSED = "dismissed_version"

class AppStorage(private val context: Context) {

    object Keys {
        val THRESHOLD = intPreferencesKey("threshold_seconds")
        val DELETION_STAMPS = stringPreferencesKey("deletion_stamps")
        val DELETED_TOTAL = longPreferencesKey("deleted_total")
        val DELETED_KNOWN = longPreferencesKey("deleted_known")
        val DELETED_UNKNOWN = longPreferencesKey("deleted_unknown")
        val DELETED_BULK = longPreferencesKey("deleted_bulk")
        val SPEAKER = booleanPreferencesKey("speakerphone")
    }

    suspend fun getStringOnce(key: Preferences.Key<String>, default: String = ""): String =
        context.dataStore.data.first()[key] ?: default

    suspend fun setString(key: Preferences.Key<String>, value: String) {
        context.dataStore.edit { it[key] = value }
    }

    suspend fun getIntOnce(key: Preferences.Key<Int>, default: Int): Int =
        context.dataStore.data.first()[key] ?: default

    suspend fun setInt(key: Preferences.Key<Int>, value: Int) {
        context.dataStore.edit { it[key] = value }
    }

    suspend fun getLongOnce(key: Preferences.Key<Long>, default: Long = 0L): Long =
        context.dataStore.data.first()[key] ?: default

    suspend fun setLong(key: Preferences.Key<Long>, value: Long) {
        context.dataStore.edit { it[key] = value }
    }

    suspend fun getBooleanOnce(key: Preferences.Key<Boolean>, default: Boolean = false): Boolean =
        context.dataStore.data.first()[key] ?: default

    suspend fun setBoolean(key: Preferences.Key<Boolean>, value: Boolean) {
        context.dataStore.edit { it[key] = value }
    }

    fun readAreaCodes(): List<String> =
        parseAreaCodes(
            context.getSharedPreferences(AREA_PREFS, Context.MODE_PRIVATE)
                .getString(AREA_KEY, "")
                .orEmpty(),
        )

    fun writeAreaCodes(codes: Collection<String>): Boolean =
        context.getSharedPreferences(AREA_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(AREA_KEY, formatAreaCodes(codes))
            .commit()

    fun readUpdateCheckedAt(): Long = updatePrefs().getLong(UPDATE_CHECKED_AT, 0L)

    fun readUpdateOffer(): ReleaseOffer? {
        val version = updatePrefs().getString(UPDATE_VERSION, null)?.trim().orEmpty()
        val url = updatePrefs().getString(UPDATE_URL, null)?.trim().orEmpty()
        if (version.isEmpty() || !url.startsWith("https://")) return null
        return ReleaseOffer(version, url)
    }

    fun readDismissedUpdate(): String? = updatePrefs().getString(UPDATE_DISMISSED, null)

    fun writeUpdateCheck(checkedAtMillis: Long, offer: ReleaseOffer?) {
        val editor = updatePrefs().edit().putLong(UPDATE_CHECKED_AT, checkedAtMillis)
        if (offer == null) {
            editor.remove(UPDATE_VERSION).remove(UPDATE_URL)
        } else {
            editor.putString(UPDATE_VERSION, offer.versionName).putString(UPDATE_URL, offer.apkUrl)
        }
        editor.apply()
    }

    fun writeUpdateCheckedAt(checkedAtMillis: Long) {
        updatePrefs().edit().putLong(UPDATE_CHECKED_AT, checkedAtMillis).apply()
    }

    fun writeDismissedUpdate(versionName: String) {
        updatePrefs().edit().putString(UPDATE_DISMISSED, versionName).apply()
    }

    private fun updatePrefs() =
        context.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE)
}
