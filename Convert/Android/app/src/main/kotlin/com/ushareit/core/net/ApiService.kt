package com.ushareit.core.net

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.*

// 1. Data Models
data class ApiResponse<T>(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String?,
    @SerializedName("data") val data: T?
)

data class DeviceRegisterRequest(
    @SerializedName("device_fingerprint") val deviceFingerprint: Map<String, Any>
)

data class DeviceRegisterResponse(
    @SerializedName("device_uuid") val deviceUuid: String,
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("is_new_device") val isNewDevice: Boolean
)

data class MCDSRequest(
    @SerializedName("space_ids") val spaceIds: List<String>,
    @SerializedName("context") val context: Map<String, Any>
)

data class MCDSSpaceData(
    @SerializedName("space_id") val spaceId: String,
    @SerializedName("prom_units") val promUnits: List<PromUnit>
)

data class PromUnit(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("content_type") val contentType: String,
    @SerializedName("media_url") val mediaUrl: String,
    @SerializedName("action_type") val actionType: String,
    @SerializedName("action_value") val actionValue: String
)

data class AdStrategyRequest(
    @SerializedName("placement_ids") val placementIds: List<String>,
    @SerializedName("country_code") val countryCode: String
)

data class CheckinRequest(
    @SerializedName("device_uuid") val deviceUuid: String
)

data class CheckinResponse(
    @SerializedName("coins_awarded") val coinsAwarded: Int,
    @SerializedName("total_balance") val totalBalance: Int,
    @SerializedName("day_streak") val dayStreak: Int
)

// 2. Retrofit API Interface
interface ApiService {

    @POST("auth/device/register")
    suspend fun registerDevice(@Body req: DeviceRegisterRequest): Response<ApiResponse<DeviceRegisterResponse>>

    @POST("mcds/fetch")
    suspend fun fetchMCDS(@Body req: MCDSRequest): Response<ApiResponse<Map<String, List<MCDSSpaceData>>>>

    @POST("ads/strategy/get")
    suspend fun getAdStrategy(@Body req: AdStrategyRequest): Response<ApiResponse<Map<String, Any>>>

    @POST("coin/checkin")
    suspend fun dailyCheckin(@Body req: CheckinRequest): Response<ApiResponse<CheckinResponse>>

    @POST("telemetry/events/batch")
    suspend fun sendTelemetryBatch(@Body req: Map<String, Any>): Response<ApiResponse<Map<String, Any>>>
}

