package com.kunfei.bookshelf.fanqie

import android.util.Log
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.kunfei.bookshelf.help.SSLSocketClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import javax.net.ssl.HttpsURLConnection

/** 目录里的一章。 */
data class FanqieChapter(val id: String, val title: String)

/** 一本书的基本信息 + 目录。 */
data class FanqieBook(
    val id: String,
    val name: String,
    val author: String,
    val intro: String,
    val coverUrl: String,
    val chapters: List<FanqieChapter>,
)

/**
 * 番茄内容的 Kotlin 实现（移植自 legado-ET 的 io.legado.app.fanqie.FanqieApi）。
 *
 * - 目录：公开的目录接口（JSON）
 * - 正文：公开阅读页 → jsoup 取段落 → [FanqiePuaTable] 还原私有区码点
 *
 * 并发与超时都刻意保守：低配设备上不抢资源，也对站点友好。
 */
object FanqieApi {

    private const val TAG = "FanqieApi"

    private const val UA_DESKTOP =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /** 兜底用的移动端 UA：桌面 UA 配老安卓的 TLS 指纹时容易被站点区别对待。 */
    private const val UA_MOBILE =
        "Mozilla/5.0 (Linux; Android 13; SM-S901B) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    /** 站点用于返回正文的匿名 Cookie，固定值即可（官方网页也是这么用的）。 */
    private const val COOKIE = "novel_web_id=7312345678901234567"

    private val cookieStore = ConcurrentHashMap<String, String>()

    private const val BASE_URL = "https://fanqienovel.com"

    /** 目录接口（JSON）。 */
    private const val DIRECTORY_URL = "$BASE_URL/api/reader/directory/detail?bookId="

    /** 封面 CDN：接口只给相对路径时要拼上它。 */
    private const val COVER_HOST = "https://p3-reading-sign.fqnovelpic.com/"

    private const val MAX_ATTEMPTS = 3

    /** 专供番茄请求使用的 TLS 工厂（4.4 上补 TLS1.2 与 ECDHE/GCM 套件）。 */
    private val tlsFactory by lazy { FanqieTlsSocketFactory.create() }

    /**
     * 统一的 HTTP GET。带齐浏览器导航头并回带 Set-Cookie，
     * 让请求形态尽量与真实浏览器一致（正文接口常依赖页面访问时下发的票据）。
     *
     * 这里刻意不用 OkHttp：Android 4.4(API19) 下 OkHttp 会在建连后重设 enabledProtocols，
     * 把 TLS1.2 覆盖掉（表现为 tlsv1 alert protocol version）；
     * HttpsURLConnection + 显式启用 TLS1.2 的 socket 工厂才是 4.4 上可靠的做法。
     */
    private fun get(
        url: String,
        ua: String = UA_DESKTOP,
        referer: String = "$BASE_URL/",
        json: Boolean = false
    ): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10000
            conn.readTimeout = 20000
            conn.instanceFollowRedirects = true
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", ua)
            conn.setRequestProperty("Cookie", cookieHeader())
            conn.setRequestProperty("Referer", referer)
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
            conn.setRequestProperty("Accept-Encoding", "gzip")
            if (json) {
                conn.setRequestProperty("Accept", "application/json, text/plain, */*")
                conn.setRequestProperty("sec-fetch-dest", "empty")
                conn.setRequestProperty("sec-fetch-mode", "cors")
                conn.setRequestProperty("sec-fetch-site", "same-origin")
            } else {
                conn.setRequestProperty(
                    "Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9," +
                        "image/avif,image/webp,*/*;q=0.8"
                )
                conn.setRequestProperty("Upgrade-Insecure-Requests", "1")
                conn.setRequestProperty("sec-fetch-dest", "document")
                conn.setRequestProperty("sec-fetch-mode", "navigate")
                conn.setRequestProperty("sec-fetch-site", "same-origin")
                conn.setRequestProperty("sec-fetch-user", "?1")
            }
            if (conn is HttpsURLConnection) {
                // 关键：4.4 上显式启用 TLS1.2 + ECDHE/GCM 套件，否则现代站点握手失败
                conn.sslSocketFactory = tlsFactory
                conn.hostnameVerifier = SSLSocketClient.getHostnameVerifier()
            }
            conn.connect()
            conn.headerFields?.forEach { (name, values) ->
                if (name != null && name.equals("Set-Cookie", ignoreCase = true)) {
                    values?.forEach { line -> parseCookie(line)?.let { (n, v) -> cookieStore[n] = v } }
                }
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code ($url)")
            }
            var raw = conn.inputStream.use { it.readBytes() }
            if ((conn.contentEncoding ?: "").contains("gzip", ignoreCase = true) ||
                (raw.size > 2 && raw[0] == 0x1f.toByte() && raw[1] == 0x8b.toByte())
            ) {
                raw = GZIPInputStream(ByteArrayInputStream(raw)).use { it.readBytes() }
            }
            return String(raw, charsetOf(conn.contentType))
        } finally {
            conn.disconnect()
        }
    }

    private fun charsetOf(contentType: String?): java.nio.charset.Charset {
        val m = Regex("charset=([\\w\\-]+)", RegexOption.IGNORE_CASE).find(contentType ?: "")
        return try {
            if (m != null) java.nio.charset.Charset.forName(m.groupValues[1])
            else Charsets.UTF_8
        } catch (e: Exception) {
            Charsets.UTF_8
        }
    }

    private fun parseCookie(line: String): Pair<String, String>? {
        val first = line.substringBefore(';').trim()
        val idx = first.indexOf('=')
        if (idx <= 0) return null
        return first.substring(0, idx).trim() to first.substring(idx + 1).trim()
    }

    private fun cookieHeader(): String {
        val sb = StringBuilder(COOKIE)
        cookieStore.forEach { (name, value) ->
            if (name.isNotBlank() && name != "novel_web_id") {
                sb.append("; ").append(name).append('=').append(value)
            }
        }
        return sb.toString()
    }

    /** 拉目录：返回书名/作者/简介/封面 + 全部章节（平铺，按卷顺序）。 */
    fun fetchBook(bookId: String): FanqieBook {
        val html = runCatching { get(FanqieInput.pageUrl(bookId)) }.getOrNull()
        val meta = html?.let { parsePage(it) }

        val chapters = fetchChapters(bookId)
        Log.i(TAG, "fetchBook $bookId 完成：书名=${meta?.name} 章节数=${chapters.size}")
        return FanqieBook(
            id = bookId,
            name = meta?.name?.takeIf { it.isNotBlank() } ?: bookId,
            author = meta?.author.orEmpty(),
            intro = meta?.intro.orEmpty(),
            coverUrl = meta?.cover.orEmpty(),
            chapters = chapters,
        )
    }

    /** 拉一章正文并完成码点还原。 */
    fun fetchContent(chapterId: String): String {
        val url = FanqieInput.readerUrl(chapterId)
        var lastError: Throwable? = null
        for (attempt in 1..2) {
            try {
                val ua = if (attempt == 1) UA_DESKTOP else UA_MOBILE
                val html = get(url, ua = ua, referer = url)
                val text = extractContent(html)
                if (text.isNotBlank()) {
                    return FanqiePuaTable.decode(text)
                }
                lastError = IllegalStateException(diagnose(html))
            } catch (e: Throwable) {
                lastError = e
            }
            if (attempt == 1) {
                runCatching { get("$BASE_URL/") }
                Thread.sleep(500)
            }
        }
        val detail = lastError?.message ?: "未知错误"
        Log.w(TAG, "番茄取正文失败（章节 $chapterId）：$detail")
        throw IllegalStateException("获取正文失败：$detail")
    }

    /** 正文段落抽取：多个选择器依次尝试，避免站点小改版就整章失败。 */
    private fun extractContent(html: String): String {
        val doc = Jsoup.parse(html)
        val selectors = listOf(
            ".muye-reader-content p",
            "div.muye-reader-content p",
            ".muye-reader-content",
            "div[class*=reader-content] p",
            "article p"
        )
        for (selector in selectors) {
            val nodes = doc.select(selector)
            if (nodes.isEmpty()) continue
            val sb = StringBuilder(4096)
            for (node in nodes) {
                val line = node.text().trim()
                if (line.isNotEmpty()) {
                    if (sb.isNotEmpty()) sb.append('\n')
                    sb.append(line)
                }
            }
            if (sb.isNotBlank()) return sb.toString()
        }
        return ""
    }

    private fun diagnose(html: String): String {
        val doc = Jsoup.parse(html)
        val title = doc.title().take(60)
        val body = (doc.body()?.text() ?: "").take(160).replace('\n', ' ')
        val marker = html.contains("muye-reader-content")
        return "页面里没有正文（长度=${html.length}，标题=$title，含正文容器=$marker，正文片段=$body）"
    }

    /** 目录接口：被限频/风控时先访问详情页预热 Cookie，再退避重试。 */
    private fun fetchChapters(bookId: String): List<FanqieChapter> {
        var lastError: Throwable? = null
        for (attempt in 1..MAX_ATTEMPTS) {
            try {
                val json = get(
                    DIRECTORY_URL + bookId,
                    referer = FanqieInput.pageUrl(bookId),
                    json = true
                )
                val data = JsonParser.parseString(json)
                    .takeIf { it.isJsonObject }
                    ?.asJsonObject
                    ?.getAsJsonObject("data")
                    ?: throw IllegalStateException("目录返回异常")
                val chapters = parseChapters(data)
                if (chapters.isEmpty()) throw IllegalStateException("目录为空（书号可能有误）")
                return chapters
            } catch (e: Throwable) {
                lastError = e
                if (attempt < MAX_ATTEMPTS) {
                    runCatching { get(FanqieInput.pageUrl(bookId)) }
                    Thread.sleep(400L * attempt)
                }
            }
        }
        throw IllegalStateException("获取目录失败：${lastError?.message}")
    }

    /** 目录结构：`data.chapterListWithVolume` 是按卷分组的数组的数组，展平即可。 */
    private fun parseChapters(data: JsonObject): List<FanqieChapter> {
        val chapters = ArrayList<FanqieChapter>(1024)
        val volumes = data.getAsJsonArray("chapterListWithVolume") ?: return chapters
        for (volume in volumes) {
            if (!volume.isJsonArray) continue
            for (item in volume.asJsonArray) {
                if (!item.isJsonObject) continue
                val obj = item.asJsonObject
                val id = obj.stringOrNull("itemId") ?: obj.stringOrNull("item_id") ?: continue
                val title = obj.stringOrNull("title") ?: id
                chapters.add(FanqieChapter(id, title))
            }
        }
        return chapters
    }

    // ── 详情页：书名 / 作者 / 简介 / 封面 ────────────────────────────────

    private class PageMeta {
        var name: String = ""
        var author: String = ""
        var intro: String = ""
        var cover: String = ""
    }

    private fun parsePage(html: String): PageMeta {
        val meta = PageMeta()
        val doc = Jsoup.parse(html)

        val root = nextDataJson(doc) ?: initialStateJson(doc)
        if (root != null) {
            meta.name = findString(root, NAME_KEYS).orEmpty()
            meta.author = findString(root, AUTHOR_KEYS).orEmpty()
            meta.intro = findString(root, INTRO_KEYS).orEmpty()
            meta.cover = findString(root, COVER_KEYS)?.let(::normalizeCoverUrl).orEmpty()
        }

        if (meta.name.isBlank()) meta.name = jsonField(html, NAME_KEYS_TEXT).orEmpty()
        if (meta.author.isBlank()) meta.author = jsonField(html, AUTHOR_KEYS_TEXT).orEmpty()
        if (meta.intro.isBlank()) meta.intro = jsonField(html, INTRO_KEYS_TEXT).orEmpty()
        if (meta.cover.isBlank()) {
            meta.cover = jsonField(html, COVER_KEYS)?.let(::normalizeCoverUrl).orEmpty()
        }
        if (meta.cover.isBlank()) meta.cover = coverFromLdJson(doc).orEmpty()

        if (meta.name.isBlank()) {
            meta.name = doc.metaContent("og:novel:book_name").ifEmpty { doc.title() }
        }
        if (meta.author.isBlank()) meta.author = doc.metaContent("og:novel:author")
        if (meta.intro.isBlank()) meta.intro = doc.metaContent("og:description")
        if (meta.cover.isBlank()) meta.cover = doc.metaContent("og:image")

        return meta
    }

    private fun nextDataJson(doc: Document): JsonElement? {
        val script = doc.selectFirst("script#__NEXT_DATA__") ?: return null
        return runCatching { JsonParser.parseString(script.data()) }.getOrNull()
    }

    private fun initialStateJson(doc: Document): JsonElement? {
        val script = doc.select("script").firstOrNull {
            it.data().contains("__INITIAL_STATE__")
        } ?: return null
        val text = script.data()
        val keyIndex = text.indexOf("__INITIAL_STATE__")
        val from = text.indexOf('{', keyIndex)
        val to = text.lastIndexOf('}')
        if (keyIndex < 0 || from < 0 || to <= from) return null
        return runCatching { JsonParser.parseString(text.substring(from, to + 1)) }.getOrNull()
    }

    private fun findString(element: JsonElement?, keys: List<String>): String? {
        if (element == null || element.isJsonNull) return null
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            for (key in keys) {
                val value = obj.stringOrNull(key)
                if (!value.isNullOrBlank()) return value
            }
            for ((_, value) in obj.entrySet()) {
                findString(value, keys)?.let { return it }
            }
        } else if (element.isJsonArray) {
            for (value in element.asJsonArray) {
                findString(value, keys)?.let { return it }
            }
        }
        return null
    }

    private fun jsonField(html: String, keys: List<String>): String? {
        for (key in keys) {
            val match = Regex("\"$key\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(html)
                ?: continue
            val value = unescapeJsonString(match.groupValues[1])
            if (value.isNotBlank()) return value
        }
        return null
    }

    private fun unescapeJsonString(raw: String): String =
        runCatching { JsonParser.parseString("\"$raw\"").asString }.getOrDefault(raw)

    private fun coverFromLdJson(doc: Document): String? {
        for (script in doc.select("script[type=application/ld+json]")) {
            val root = runCatching { JsonParser.parseString(script.data()) }.getOrNull() ?: continue
            val url = findString(root, listOf("image", "images", "thumbnailUrl"))
            if (!url.isNullOrBlank()) return normalizeCoverUrl(url)
        }
        return null
    }

    private fun normalizeCoverUrl(url: String): String {
        val trimmed = url.trim()
        return when {
            trimmed.isEmpty() -> ""
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            else -> COVER_HOST + trimmed.trimStart('/')
        }
    }

    private fun Document.metaContent(property: String): String =
        selectFirst("meta[property=$property]")?.attr("content").orEmpty()

    private fun JsonObject.stringOrNull(key: String): String? {
        val value = get(key) ?: return null
        if (!value.isJsonPrimitive) return null
        return value.asString
    }

    private val NAME_KEYS = listOf("bookName", "book_name", "originalBookName", "title", "name")
    private val AUTHOR_KEYS = listOf("author", "authorName", "author_name")
    private val INTRO_KEYS = listOf("abstract", "description", "intro", "introduce")
    private val NAME_KEYS_TEXT = listOf("bookName", "book_name", "originalBookName")
    private val AUTHOR_KEYS_TEXT = listOf("authorName", "author_name", "author")
    private val INTRO_KEYS_TEXT = listOf("abstract", "description", "intro", "introduce")
    private val COVER_KEYS = listOf(
        "thumbUrl", "thumb_url", "thumbUri", "thumb_uri",
        "expandThumbUrl", "expand_thumb_url",
        "coverUrl", "cover_url",
        "detailPageThumbUrl", "detail_page_thumb_url",
        "detailThumbUrl", "detail_thumb_url",
        "horizThumbUrl", "horiz_thumb_url",
        "audioThumbUrlHd", "audio_thumb_url_hd"
    )
}
