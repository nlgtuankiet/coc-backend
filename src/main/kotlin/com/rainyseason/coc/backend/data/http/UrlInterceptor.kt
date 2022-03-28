package com.rainyseason.coc.backend.data.http

import com.rainyseason.coc.backend.util.getLogger
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class UrlInterceptor @Inject constructor() : Interceptor {
    private val logger = getLogger<UrlInterceptor>()
    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url
        logger.debug(url.toString())
        return chain.proceed(chain.request())
    }
}
