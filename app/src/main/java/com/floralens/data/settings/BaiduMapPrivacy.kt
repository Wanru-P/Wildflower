package com.floralens.data.settings

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import javax.inject.Inject
import javax.inject.Singleton

enum class MapPrivacyChoice { PENDING, DECLINED, GRANTED }

@Singleton
class BaiduMapPrivacy @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("baidu_map_privacy_v1", Context.MODE_PRIVATE)
    fun current() = runCatching { MapPrivacyChoice.valueOf(prefs.getString("choice", "PENDING")!!) }
        .getOrDefault(MapPrivacyChoice.PENDING)
    val choice = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(current()) }
        prefs.registerOnSharedPreferenceChangeListener(listener); trySend(current())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate()
    fun choose(value: MapPrivacyChoice) { check(prefs.edit().putString("choice", value.name).commit()) }
}
