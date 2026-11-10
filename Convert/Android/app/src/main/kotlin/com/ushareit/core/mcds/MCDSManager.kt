package com.ushareit.core.mcds

import android.content.Context
import android.util.Log
import com.tencent.mmkv.MMKV
import com.ushareit.core.net.MCDSRequest
import com.ushareit.core.net.MCDSSpaceData
import com.ushareit.core.net.NetworkClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object MCDSManager {

    private val mmkv by lazy { MMKV.defaultMMKV() }

    fun init(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            fetchRemoteConfig()
        }
    }

    suspend fun fetchRemoteConfig() {
        try {
            val req = MCDSRequest(
                spaceIds = listOf("home_top_banner", "trans_complete_interstitial"),
                context = mapOf("country" to "VN", "version_code" to 4565338)
            )
            val res = NetworkClient.apiService.fetchMCDS(req)
            if (res.isSuccessful && res.body()?.code == 0) {
                Log.i("MCDSManager", "Dynamic config synchronized successfully")
            }
        } catch (e: Exception) {
            Log.w("MCDSManager", "MCDS fetch fallback: ${e.message}")
        }
    }
}
