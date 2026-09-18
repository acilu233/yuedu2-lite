package com.kunfei.bookshelf.fanqie

/**
 * 番茄书号解析：支持纯书号、页面链接、以及整段分享文案。
 *
 * 设计取向：尽量宽松——用户把"分享文案 + 链接 + 口令"整段粘进来也能识别；
 * 识别不到就返回 null，由上层给出提示，不做任何猜测。
 */
object FanqieInput {

    private val PAGE_URL = Regex("""fanqienovel\.com/(?:page|reader)/(\d{8,})""")
    private val ANY_ID = Regex("""\d{8,}""")

    /** 从任意文本里提取书号；失败返回 null。 */
    fun extractBookId(text: String?): String? {
        val raw = text?.trim().orEmpty()
        if (raw.isEmpty()) return null

        PAGE_URL.find(raw)?.let { return it.groupValues[1] }

        val digitsOnly = raw.filter { !it.isWhitespace() }
        if (digitsOnly.length >= 8 && digitsOnly.all { it.isDigit() }) return digitsOnly

        ANY_ID.find(raw)?.let { return it.value }
        return null
    }

    fun pageUrl(bookId: String): String = "https://fanqienovel.com/page/$bookId"

    fun readerUrl(chapterId: String): String = "https://fanqienovel.com/reader/$chapterId"
}
