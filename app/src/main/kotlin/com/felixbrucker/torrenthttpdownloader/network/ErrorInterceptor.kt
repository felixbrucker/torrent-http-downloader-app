package com.felixbrucker.torrenthttpdownloader.network

import okhttp3.Interceptor
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

class ErrorInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (!response.isSuccessful) {
            val errorBody = response.body.string()
            var jsonObj: JSONObject? = null
            try {
                jsonObj = JSONObject(errorBody)
            } catch (_: Exception) {
                // Ignore if it's not a JSON object
            }
            if (jsonObj != null && jsonObj.has("error_code") && jsonObj.getInt("error_code") == 7) {
                // Throw a custom exception for resource not found
                throw ResourceNotFoundException()
            }
        }
        return response
    }
}

class ResourceNotFoundException : IOException()
