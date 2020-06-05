package com.github.wumo.http

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.Headers.Companion.toHeaders
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import java.io.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object OkHttpUtils {
  data class ContentRange(val start: Long, val end: Long, val size: Long) {
    companion object {
      fun ContentRange(start: Long, size: Long) =
        ContentRange(start, size + start - 1, size)
      
      val rangePattern = Regex("""(?<unit>\w+)?\s*(?<start>\d+)-(?<end>\d*)?(?:/(?<size>\d+))?""")
      fun parse(range: String): ContentRange? {
        rangePattern.matchEntire(range)?.also { result->
          result.groups["unit"]?.also { unit->
            assert(unit.value == "bytes")
          }
          val start = result.groups["start"]!!.value.toLong()
          val end = result.groups["end"]?.value?.toLong() ?: Long.MAX_VALUE
          val size = result.groups["size"]?.value?.toLong() ?: -1L
          return ContentRange(start, end, size)
        }
        return null
      }
    }
  }
  
  fun url(
    scheme: String, host: String, path: String,
    vararg queryParams: String
  ) = HttpUrl.Builder()
    .scheme(scheme)
    .host(host)
    .addPathSegments(path)
    .apply {
      assert(queryParams.size % 2 == 0)
      repeat(queryParams.size / 2) {
        addQueryParameter(queryParams[it * 2], queryParams[it * 2 + 1])
      }
    }.build()
  
  fun HttpUrl.url(
    path: String,
    vararg queryParams: Any
  ) =
    newBuilder().addPathSegments(path).apply {
      assert(queryParams.size % 2 == 0)
      repeat(queryParams.size / 2) {
        addQueryParameter(queryParams[it * 2].toString(), queryParams[it * 2 + 1].toString())
      }
    }.build()
  
  fun headers(vararg headers: String) = Headers.headersOf(*headers)
  
  val emptyHeaders = Headers.Builder().build()
  
  fun OkHttpClient.close() {
    dispatcher.executorService.shutdown()
    connectionPool.evictAll()
    cache?.close()
  }
  
  suspend fun OkHttpClient.readBytes(url: String): ByteArray {
    getResp(url).use {
      it.body?.byteStream()?.also { inStream->
        return inStream.readAllBytes()
      }
    }
    return ByteArray(0)
  }
  
  suspend fun OkHttpClient.getCode(url: HttpUrl, headers: Headers = emptyHeaders): Pair<Int, String> {
    val request = Request.Builder().url(url).headers(headers).build()
    val response = newCall(request).await()
    response.use {
      return response.code to (it.body?.string() ?: "")
    }
  }
  
  suspend fun OkHttpClient.postCode(
    url: HttpUrl, headers: Headers = emptyHeaders,
    body: String, mediaType: String? = null
  ): Pair<Int, String> {
    val request = Request.Builder().url(url)
      .headers(headers)
      .post(body.toRequestBody(mediaType?.toMediaTypeOrNull())).build()
    val response = newCall(request).await()
    response.use {
      return response.code to (it.body?.string() ?: "")
    }
  }
  
  suspend fun OkHttpClient.postCode(
    url: HttpUrl, headers: Headers = emptyHeaders, body: InputStream, mediaType: String? = null
  ): Pair<Int, String> {
    
    val request = Request.Builder().url(url)
      .headers(headers)
      .post(
        object: RequestBody() {
          override fun contentType() = mediaType?.toMediaTypeOrNull()
          override fun contentLength() = body.available().toLong()
          override fun writeTo(sink: BufferedSink) {
            sink.writeAll(body.source())
          }
        }
      ).build()
    val response = newCall(request).await()
    response.use {
      return response.code to (it.body?.string() ?: "")
    }
  }
  
  suspend fun OkHttpClient.deleteCode(url: HttpUrl, headers: Headers = emptyHeaders): Pair<Int, String> {
    val request = Request.Builder().url(url)
      .headers(headers)
      .delete().build()
    val response = newCall(request).await()
    response.use {
      return response.code to (it.body?.string() ?: "")
    }
  }
  
  suspend fun OkHttpClient.get(url: String, headers: Map<String, Any> = emptyMap()): String {
    getResp(url, headers).use {
      return it.body?.string() ?: ""
    }
  }
  
  suspend fun OkHttpClient.get(url: HttpUrl, headers: Headers = emptyHeaders): String {
    getResp(url, headers).use {
      return it.body?.string() ?: ""
    }
  }
  
  suspend fun OkHttpClient.getHeaders(
    url: String, headers: Map<String, String> = emptyMap()
  ): Headers {
    headResp(url, headers).use {
      return it.headers
    }
  }
  
  suspend fun OkHttpClient.getResp(url: HttpUrl, headers: Headers = emptyHeaders): Response {
    val request = Request.Builder().url(url).headers(headers).build()
    val response = newCall(request).await()
    if(!response.isSuccessful) throw IOException()
    return response
  }
  
  suspend fun OkHttpClient.getResp(url: String, headers: Map<String, Any> = emptyMap()): Response {
    val headerBuilder = headers.entries.associate { (k, v)-> k to v.toString() }.toHeaders()
    val request = Request.Builder().url(url).headers(headerBuilder).build()
    val response = newCall(request).await()
    if(!response.isSuccessful) {
      response.close()
      throw IOException()
    }
    return response
  }
  
  suspend fun OkHttpClient.headResp(url: String, headers: Map<String, Any> = emptyMap()): Response {
    val headerBuilder = headers.entries.associate { (k, v)-> k to v.toString() }.toHeaders()
    val request = Request.Builder().head().url(url).headers(headerBuilder).build()
    val response = newCall(request).await()
    if(!response.isSuccessful) {
      response.close()
      throw IOException()
    }
    return response
  }
  
  suspend fun OkHttpClient.post(
    url: String,
    headers: Map<String, Any> = emptyMap(),
    form: Map<String, Any> = emptyMap()
  ): String {
    val header = headers.entries.associate { (k, v)-> k to v.toString() }.toHeaders()
    val data = FormBody.Builder().apply {
      form.forEach { k, v->
        add(k, v.toString())
      }
    }.build()
    val request = Request.Builder().url(url).headers(header).post(data).build()
    val response = newCall(request).await()
    response.use {
      if(!response.isSuccessful) throw IOException(it.body?.string() ?: "")
      return it.body?.string() ?: ""
    }
  }
  
  suspend fun OkHttpClient.post(
    url: HttpUrl, headers: Headers = emptyHeaders,
    form: Map<String, Any> = emptyMap()
  ): String {
    val data = FormBody.Builder().apply {
      form.forEach { (k, v)->
        add(k, v.toString())
      }
    }.build()
    val request = Request.Builder().url(url).headers(headers).post(data).build()
    val response = newCall(request).await()
    response.use {
      if(!response.isSuccessful) throw IOException(it.body?.string() ?: "")
      return it.body?.string() ?: ""
    }
  }
  
  suspend fun OkHttpClient.post(
    url: HttpUrl, headers: Headers = emptyHeaders,
    json: String,
    mediaType: String? = null
  ): String {
    val request = Request.Builder().url(url)
      .headers(headers)
      .post(json.toRequestBody(mediaType?.toMediaTypeOrNull())).build()
    val response = newCall(request).await()
    response.use {
      if(!response.isSuccessful) throw IOException(it.body?.string() ?: "")
      return it.body?.string() ?: ""
    }
  }
  
  suspend fun OkHttpClient.download(
    out: OutputStream, url: String,
    headers: Map<String, Any> = emptyMap(),
    block: (Long, Long)->Unit = { _, _-> }
  ) {
    getResp(url, headers).use {
      it.body?.apply {
        byteStream().also { input->
          val total = contentLength()
          val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
          var bytes = input.read(buffer)
          while(bytes >= 0) {
            out.write(buffer, 0, bytes)
            block(bytes.toLong(), total)
            bytes = input.read(buffer)
          }
          block(0, total)
        }
      }
    }
  }
  
  suspend fun OkHttpClient.download(
    dest: File, url: String,
    headers: Map<String, Any> = emptyMap(),
    block: (Long, Long)->Unit = { _, _-> }
  ) {
    FileOutputStream(dest).use { out->
      download(out, url, headers, block)
    }
  }
  
  /**
   * Suspend extension that allows suspend [Call] inside coroutine.
   *
   * [recordStack] enables track recording, so in case of exception stacktrace will contain call stacktrace, may be useful for debugging
   *      Not free! Creates exception on each request so disabled by default, but may be enabled using system properties:
   *
   *      ```
   *      System.setProperty(OKHTTP_STACK_RECORDER_PROPERTY, OKHTTP_STACK_RECORDER_ON)
   *      ```
   *      see [README.md](https://github.com/gildor/kotlin-coroutines-okhttp/blob/master/README.md#Debugging) with details about debugging using this feature
   *
   * @return Result of request or throw exception
   */
  suspend fun Call.await(recordStack: Boolean = isRecordStack): Response {
    val callStack = if(recordStack) {
      IOException().apply {
        // Remove unnecessary lines from stacktrace
        // This doesn't remove await$default, but better than nothing
        stackTrace = stackTrace.copyOfRange(1, stackTrace.size)
      }
    } else {
      null
    }
    return suspendCancellableCoroutine { continuation->
      enqueue(object: Callback {
        override fun onResponse(call: Call, response: Response) {
          continuation.resume(response)
        }
        
        override fun onFailure(call: Call, e: IOException) {
          // Don't bother with resuming the continuation if it is already cancelled.
          if(continuation.isCancelled) return
          callStack?.initCause(e)
          continuation.resumeWithException(callStack ?: e)
        }
      })
      
      continuation.invokeOnCancellation {
        try {
          cancel()
        } catch(ex: Throwable) {
          //Ignore cancel exception
        }
      }
    }
  }
  
  const val OKHTTP_STACK_RECORDER_PROPERTY = "ru.gildor.coroutines.okhttp.stackrecorder"
  
  /**
   * Debug turned on value for [DEBUG_PROPERTY_NAME]. See [newCoroutineContext][CoroutineScope.newCoroutineContext].
   */
  const val OKHTTP_STACK_RECORDER_ON = "on"
  
  /**
   * Debug turned on value for [DEBUG_PROPERTY_NAME]. See [newCoroutineContext][CoroutineScope.newCoroutineContext].
   */
  const val OKHTTP_STACK_RECORDER_OFF = "off"
  
  @JvmField
  val isRecordStack = when(System.getProperty(OKHTTP_STACK_RECORDER_PROPERTY)) {
    OKHTTP_STACK_RECORDER_ON -> true
    OKHTTP_STACK_RECORDER_OFF, null, "" -> false
    else -> error(
      "System property '$OKHTTP_STACK_RECORDER_PROPERTY' has unrecognized value '${System.getProperty(
        OKHTTP_STACK_RECORDER_PROPERTY
      )}'"
    )
  }
}