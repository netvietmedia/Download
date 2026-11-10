package com.ushareit.core.net

import okhttp3.Interceptor
import okhttp3.Response
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class SecurityInterceptor(private val signingSecret: String) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalReq = chain.request()
        val timestamp = System.currentTimeMillis().toString()
        val nonce = "non_${System.currentTimeMillis()}_${(1000..9999).random()}"

        // Tính HMAC-SHA256 Signature
        val method = originalReq.method
        val path = originalReq.url.encodedPath
        val payload = "$method:$path:$timestamp:$nonce"
        val signature = computeHmacSha256(payload, signingSecret)

        val newRequest = originalReq.newBuilder()
            .addHeader("X-App-Timestamp", timestamp)
            .addHeader("X-App-Nonce", nonce)
            .addHeader("X-App-Signature", signature)
            .addHeader("User-Agent", "ShareNow-Android/1.0.0 (Kotlin-Core)")
            .build()

        return chain.proceed(newRequest)
    }

    private fun computeHmacSha256(data: String, key: String): String {
        val secretKey = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(secretKey)
        val hmacBytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return hmacBytes.joinToString("") { "%02x".format(it) }
    }
}

