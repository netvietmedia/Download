package com.ushareit.core.coin

import com.tencent.mmkv.MMKV
import com.ushareit.core.net.CheckinRequest
import com.ushareit.core.net.NetworkClient

object CoinRewardManager {

    private const val KEY_COIN_BALANCE = "pref_key_coin_balance"

    fun getBalance(): Int {
        return MMKV.defaultMMKV().decodeInt(KEY_COIN_BALANCE, 100)
    }

    suspend fun performDailyCheckin(deviceUuid: String): Result<Int> {
        return try {
            val res = NetworkClient.apiService.dailyCheckin(CheckinRequest(deviceUuid))
            if (res.isSuccessful && res.body()?.code == 0) {
                val newBal = res.body()!!.data!!.totalBalance
                MMKV.defaultMMKV().encode(KEY_COIN_BALANCE, newBal)
                Result.success(newBal)
            } else {
                Result.failure(Exception("Checkin failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
