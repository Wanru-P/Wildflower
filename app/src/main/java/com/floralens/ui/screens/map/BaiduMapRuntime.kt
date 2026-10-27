package com.floralens.ui.screens.map

import android.content.Context
import android.util.Log
import com.baidu.mapapi.CoordType
import com.baidu.mapapi.RequestAuthResultListener
import com.baidu.mapapi.SDKInitializer
import kotlinx.coroutines.flow.MutableStateFlow

/** Process-wide SDK state contains application context only. Never called to grant consent automatically. */
object BaiduMapRuntime {
    val authCode = MutableStateFlow<Int?>(null)
    val ready = MutableStateFlow(false)
    private var started = false
    /*
     * The SDK owns a native run-loop for the life of the process.  Calling
     * SDKInitializer.setAgreePrivacy(false) after a MapView has been created
     * races that run-loop on API 35 x86_64 (the SDK dereferences a released
     * native object).  Withdrawal still takes effect immediately in our app:
     * the persisted choice removes every MapView and blocks all later SDK
     * calls.  A process started with withdrawal recorded never initializes
     * the SDK, so its privacy flag is false before any native component runs.
     */
    private val listener = RequestAuthResultListener { code, _ ->
        authCode.value = code
        Log.i("WildflowerMap", "Baidu authentication result code=$code")
    }
    @Synchronized fun initialize(context: Context, agreed: Boolean): Boolean {
        if (!agreed) return false
        val app = context.applicationContext
        if (SDKInitializer.getAgreePrivacy() != agreed) SDKInitializer.setAgreePrivacy(app, agreed)
        if (!started) {
            SDKInitializer.setCoordType(CoordType.BD09LL)
            SDKInitializer.setHttpsEnable(true)
            SDKInitializer.addAuthResultListener(listener)
            SDKInitializer.initialize(app, com.baidu.mapapi.ISDKInitializerListener { ready.value = true })
            started = true
        }
        if (SDKInitializer.isInitialized()) ready.value = true
        return SDKInitializer.isInitialized()
    }
    @Synchronized fun revokeIfStarted(context: Context) {
        ready.value = false
        authCode.value = null
        Log.i("WildflowerMap", "Map privacy withdrawn: SDK use is blocked until an explicit new grant")
    }
}
