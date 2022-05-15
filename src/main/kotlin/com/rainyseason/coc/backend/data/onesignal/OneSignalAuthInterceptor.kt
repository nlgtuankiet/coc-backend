package com.rainyseason.coc.backend.data.onesignal

import com.rainyseason.coc.backend.core.ConfigKeys
import com.rainyseason.coc.backend.core.getValue
import okhttp3.Interceptor
import okhttp3.Response
import org.apache.commons.configuration2.ImmutableConfiguration
import javax.inject.Inject

class OneSignalAuthInterceptor @Inject constructor(
    configuration: ImmutableConfiguration
) : Interceptor {
    val oneSignalApiKey = configuration.getValue(ConfigKeys.oneSignalApiKey)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
            .newBuilder()
            .addHeader("Authorization", "Basic $oneSignalApiKey")
            .build()
        return chain.proceed(request)
    }
}
