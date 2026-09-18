package com.kunfei.bookshelf.fanqie

/**
 * 番茄小说阅读页的私有区码点还原表。
 *
 * **本文件由脚本自动生成，请勿手工修改。**
 * 生成脚本：Tomato-Novel-Downloader/scripts/make_kotlin_pua_table.py
 * 推导原理：阅读页把常用字替换成私有区码点，再用自带字体渲染回真实字形；
 * 该字体是「思源黑体 SC Normal v2.002」(SIL OFL 1.1) 的子集，
 * 因此可用字形轮廓比对把码点对应回汉字（本项目自行推导，不依赖第三方数据表）。
 *
 * 站点更换字体后需要重新推导并重新生成本文件。
 */
object FanqiePuaTable {

    /** 私有区码点范围（含两端）。 */
    const val START = 0xE3E8
    const val END = 0xE55B

    /** 条目数（= END - START + 1），{362} 表示未收录，解码时原样保留。 */
    private const val TABLE = "D在主特家军然表场4要只v和?6别还g现儿岁??此象月3出战工相o男直失世F都平文什VO将真T那当?会立些u是十张学气大爱两命全后东性通被1它乐接而感车山公了常以何可话先pi叫轻M士w着变尔快l个说少色里安花远7难师放t报认面道S?克地度I好机U民写把万同水新没书电吃像斯5为y白几日教看但第加候作上拉住有法r事应位利你声身国问马女他Y比父xAHNsX边美对所金活回意到z从j知又内因点Q三定8Rb正或夫向德听更?得告并本q过记L让打f人就者去原满体做经K走如孩cG给使物?最笑部?员等受k行一条果动光门头见往自解成处天能于名其发总母的死手入路进心来h时力多开已许d至由很界n小与Z想代么分生口再妈望次西风种带J?实情才这?E我神格长觉间年眼无不亲关结0友信下却重己老2音字m呢明之前高PB目太e9起稜她也W用方子英每理便四数期中C外样a海们任"

    /** 映射表版本：字体更换后此值应随之变化，便于排查"解码失效"。 */
    const val VERSION = "v2002-362"

    fun isPua(code: Int): Boolean = code in START..END

    /**
     * 把正文里的私有区码点还原成真实汉字。
     * 未收录的码点原样保留，绝不丢字；不含码点时直接返回原串（零开销）。
     */
    fun decode(text: String): String {
        var hit = false
        for (ch in text) {
            if (ch.code in START..END) {
                hit = true
                break
            }
        }
        if (!hit) return text

        val sb = StringBuilder(text.length)
        for (ch in text) {
            val code = ch.code
            if (code in START..END) {
                val mapped = TABLE[code - START]
                sb.append(if (mapped == '?') ch else mapped)
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }
}
