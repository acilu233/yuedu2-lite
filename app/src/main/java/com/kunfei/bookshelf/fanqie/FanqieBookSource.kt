package com.kunfei.bookshelf.fanqie

import android.util.Log
import com.kunfei.bookshelf.bean.BookSourceBean
import com.kunfei.bookshelf.model.BookSourceManager

/**
 * 内置"番茄小说"书源（阅读2.0 版）。
 *
 * 它不是真实网络书源：bookSourceUrl 用保留域名 [URL]，
 * 所有发往该域名的请求由 [FanqieInterceptor] 就地应答（数据来自 [FanqieApi]）。
 * 规则只用 JSONPath 取值，因此书源执行引擎一行不用改，
 * 阅读、缓存、导出等既有流程全部照常复用。
 *
 * 应答形状：
 * ```
 * GET https://fanqie.local/toc/<bookId>      -> {name, author, intro, cover, chapters:[{title,url}]}
 * GET https://fanqie.local/read/<chapterId>  -> {title, text}
 * GET https://fanqie.local/search            -> {books:[]}
 * ```
 */
object FanqieBookSource {

    /** 保留域名，见 [FanqieInterceptor.HOST]。 */
    const val URL = "https://${FanqieInterceptor.HOST}"

    const val NAME = "番茄小说"
    const val GROUP = "番茄"

    /** 详情页（同时作为目录页）地址。 */
    fun tocUrl(bookId: String): String = "$URL/toc/$bookId"

    fun build(): BookSourceBean {
        val bean = BookSourceBean()
        bean.setBookSourceUrl(URL)
        bean.setBookSourceName(NAME)
        bean.setBookSourceGroup(GROUP)
        bean.setBookSourceType("0")
        bean.setEnable(true)
        bean.setSerialNumber(-1)          // addBookSource 会补号
        bean.setWeight(0)
        bean.setRuleBookUrlPattern("""fanqie\.local/toc/\d+""")
        // 搜索固定返回空列表：内置源不参与关键字搜索，书号搜索走搜索页的"番茄直达"
        bean.setRuleSearchUrl("$URL/search")
        bean.setRuleSearchList("$.books")
        bean.setRuleSearchName("$.name")
        bean.setRuleSearchAuthor("$.author")
        bean.setRuleSearchIntroduce("$.intro")
        bean.setRuleSearchNoteUrl("$.url")
        bean.setRuleSearchCoverUrl("$.cover")
        bean.setRuleSearchLastChapter("$.lastChapter")
        bean.setRuleBookName("$.name")
        bean.setRuleBookAuthor("$.author")
        bean.setRuleIntroduce("$.intro")
        bean.setRuleCoverUrl("$.cover")
        bean.setRuleChapterUrl("")         // 目录页 = 详情页，留空即复用同一份响应
        bean.setRuleChapterList("$.chapters")
        bean.setRuleChapterName("$.title")
        bean.setRuleContentUrl("$.url")
        bean.setRuleBookContent("$.text")
        return bean
    }

    /**
     * 幂等注册：已存在（含用户改过的）就完全不动。
     * 启动时调用一次即可，见 MApplication.onCreate。
     */
    fun ensureInserted() {
        try {
            if (BookSourceManager.getBookSourceByUrl(URL) == null) {
                BookSourceManager.addBookSource(build())
            }
        } catch (t: Throwable) {
            Log.w("FanqieBookSource", "内置番茄源注册失败：" + t.message)
        }
    }
}
