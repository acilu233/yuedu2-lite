package com.kunfei.bookshelf.help.http

import okhttp3.MediaType
import okhttp3.RequestBody
import java.io.File

/**
 * 4.4(API19) 兼容：本工程必须用 OkHttp 3.12.x。
 *
 * OkHttp 4.x/5.x 的 AndroidPlatform 在类初始化时写死了 "Expected Android API level 21+"，
 * 在 4.4 上第一次构建 OkHttpClient（也就是第一次搜索/发现）就会抛
 * ExceptionInInitializerError 闪退。3.12 是最后一个支持 API19 的版本。
 *
 * 3.12 是 Java 实现，没有 4.x 的 Kotlin 扩展函数，这里补上同名等价实现，
 * 调用点只需换 import。
 */
fun String.toMediaType(): MediaType =
    MediaType.parse(this) ?: throw IllegalArgumentException("Invalid media type: $this")

fun String.toRequestBody(contentType: MediaType? = null): RequestBody =
    RequestBody.create(contentType, this)

fun ByteArray.toRequestBody(contentType: MediaType? = null): RequestBody =
    RequestBody.create(contentType, this)

fun File.asRequestBody(contentType: MediaType? = null): RequestBody =
    RequestBody.create(contentType, this)
