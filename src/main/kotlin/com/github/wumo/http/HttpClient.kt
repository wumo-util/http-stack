package com.github.wumo.http

import com.github.wumo.http.OkHttpUtils.close
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import java.net.*

object HttpClient {
  const val UserAgent =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.149 Safari/537.36"
  
  fun makeClient(
    cookieStore: CookieStore? = null,
    userAgent: String = UserAgent, proxy: Proxy? = null
  ): OkHttpClient {
    return OkHttpClient.Builder().let {
      if(cookieStore != null) {
        val cookieMgr = CookieManager(cookieStore, CookiePolicy.ACCEPT_ALL)
        CookieHandler.setDefault(cookieMgr)
        it.cookieJar(JavaNetCookieJar(cookieMgr))
      }
      it.addNetworkInterceptor { chain->
        val req = chain.request()
        chain.proceed(
          req.newBuilder()
            .header("User-Agent", userAgent)
            .build()
        )
      }
      it.proxy(proxy)
      it.build()
    }
  }
  
  lateinit var client: OkHttpClient
  
  fun initClient(
    cookieStore: CookieStore? = null,
    userAgent: String = UserAgent, proxy: Proxy? = null
  
  ) {
    client =
      makeClient(cookieStore, userAgent, proxy)
  }
  
  fun closeClient() {
    if(::client.isInitialized)
      client.close()
  }
}