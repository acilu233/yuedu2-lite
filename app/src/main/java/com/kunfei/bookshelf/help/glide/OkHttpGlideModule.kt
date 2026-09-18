package com.kunfei.bookshelf.help.glide

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.bitmap_recycle.LruBitmapPool
import com.bumptech.glide.load.engine.cache.LruResourceCache
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.AppGlideModule
import com.bumptech.glide.request.RequestOptions
import java.io.InputStream

@GlideModule
class OkHttpGlideModule : AppGlideModule() {

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        // 4.4 老设备 Dalvik 堆只有 ~18MB：Glide 默认按 memoryClass 推算的缓存会把堆吃光，
        // 导致全程 GC 抖动。这里显式压到 3MB/2MB，并统一用 RGB_565
        // （墨水屏是 16 级灰度，32 位色纯浪费一半内存）。
        builder.setMemoryCache(LruResourceCache(3L * 1024 * 1024))
        builder.setBitmapPool(LruBitmapPool(2L * 1024 * 1024))
        builder.setDefaultRequestOptions(
            RequestOptions()
                .format(DecodeFormat.PREFER_RGB_565)
                .disallowHardwareConfig()
        )
    }

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        registry.replace(
            GlideUrl::class.java,
            InputStream::class.java,
            OkHttpModeLoaderFactory
        )
    }
}
