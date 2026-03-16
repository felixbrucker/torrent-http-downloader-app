package com.felixbrucker.torrenthttpdownloader.network

import okhttp3.Interceptor
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

class ErrorInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (!response.isSuccessful) {
            if (response.code == 429) {
                throw RateLimitExceededException()
            }
            if (response.code == 509) {
                throw BandwidthLimitExceededException()
            }

            val errorBody = response.body.string()
            var jsonObj: JSONObject? = null
            try {
                jsonObj = JSONObject(errorBody)
            } catch (_: Exception) {
                // Ignore if it's not a JSON object
            }
            if (jsonObj != null) {
                val apiException = ApiException(jsonObj.getInt("error_code"), jsonObj.getString("error"))

                if (apiException.code == 7) {
                    throw ResourceNotFoundException(apiException.message.toString())
                }

                throw apiException
            }
        }
        return response
    }
}

// We need to subclass from IOException or okhttp does not pass them up the stream
class ResourceNotFoundException(message: String) : ApiException(7, message)
class RateLimitExceededException: IOException()
class BandwidthLimitExceededException: IOException()
open class ApiException(val code: Int, message: String): IOException(message)
