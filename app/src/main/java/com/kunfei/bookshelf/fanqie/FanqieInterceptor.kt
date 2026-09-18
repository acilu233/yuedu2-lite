package com.kunfei.bookshelf.fanqie

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody

/**
 * 内置番茄源的"代码后端"（移植自 legado-ET 的同名类）。
 *
 * 思路（最小侵入）：
 * - 注册一个书源，其 bookSourceUrl 用保留域名 [HOST]（`https://fanqie.local`）
 * - 在应用统一的 OkHttp 客户端上加本拦截器（见 BaseModelImpl.getClient()）
 * - 发往该域名的请求由本拦截器直接应答，数据来自 [FanqieApi]
 *
 * 这样书源执行引擎（AnalyzeUrl/AnalyzeRule）一行不动，
 * 阅读、缓存、导出等既有能力全部照常工作。
 *
 * 应答形状：
 * ```
 * GET /toc/<bookId>      -> { name, author, intro, cover, chapters:[{title, url}] }
 * GET /read/<chapterId>  -> { title, text }
 * GET /search            -> { books:[] }
 * ```
 * 内部出错返回 500 + `{error}`，让上层按正常"获取失败"处理。
 */
class FanqieInterceptor : Interceptor {

    companion object {
        const val HOST = "fanqie.local"
        private val JSON: MediaType = MediaType.parse("application/json; charset=utf-8")!!
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url().host()
        if (!host.equals(HOST, ignoreCase = true)) {
            return chain.proceed(request)
        }

        val segments = request.url().pathSegments()
        val payload = runCatching {
            when {
                segments.size >= 2 && segments[0] == "toc" -> bookJson(segments[1])
                segments.size >= 2 && segments[0] == "read" -> contentJson(segments[1])
                segments.size >= 1 && segments[0] == "search" -> emptySearchJson()
                else -> throw IllegalArgumentException("未知路径：${request.url().encodedPath()}")
            }
        }

        val body = payload.getOrElse { errorJson(it.message ?: "请求失败") }
        if (payload.isFailure) {
            Log.w("FanqieInterceptor", "处理 ${request.url().encodedPath()} 失败：${payload.exceptionOrNull()}")
        }
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(if (payload.isSuccess) 200 else 500)
            .message(if (payload.isSuccess) "OK" else "Internal Error")
            .body(ResponseBody.create(JSON, body))
            .build()
    }

    /** 目录（含书名/作者/简介/封面）。 */
    private fun bookJson(bookId: String): String {
        val book = FanqieApi.fetchBook(bookId)
        val chapters = JsonArray()
        book.chapters.forEach { chapter ->
            chapters.add(
                JsonObject().apply {
                    addProperty("title", chapter.title)
                    addProperty("url", "${FanqieBookSource.URL}/read/${chapter.id}")
                }
            )
        }
        return JsonObject().apply {
            addProperty("name", book.name)
            addProperty("author", book.author)
            addProperty("intro", book.intro)
            addProperty("cover", book.coverUrl)
            add("chapters", chapters)
        }.toString()
    }

    /** 正文（已解码的纯文本）。 */
    private fun contentJson(chapterId: String): String {
        val text = FanqieApi.fetchContent(chapterId)
        return JsonObject().apply {
            addProperty("title", "")
            addProperty("text", text)
        }.toString()
    }

    private fun errorJson(message: String): String = JsonObject().apply {
        addProperty("error", message)
    }.toString()

    /** 固定空结果：内置源不参与关键字搜索，书号搜索走搜索页的"番茄直达"。 */
    private fun emptySearchJson(): String = JsonObject().apply {
        add("books", JsonArray())
    }.toString()
}
