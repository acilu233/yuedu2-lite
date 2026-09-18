# 番茄直达（阅读2.0 / Kindle 4.4）移植说明

> 2026-09-18 完成并在 Kindle（02722011511203ML，Android 4.4.2 / API19）上实测通过。
> 来源：`legado-ET-litelite` 的 `io.legado.app.fanqie` 模块（Kotlin 代码源方案）。

## 一、做了什么

不写书源规则、不依赖 JS、不需要外部服务器，全部逻辑在 Kotlin 内，
复用阅读2.0 既有的 **阅读 / 缓存 / 导出 / 换源** 通道，书源执行引擎（AnalyzeUrl/AnalyzeRule）一行没改。

新增代码（`app/src/main/java/com/kunfei/bookshelf/fanqie/`）：

| 文件 | 作用 |
|---|---|
| `FanqiePuaTable.kt` | 362 条私有区码点还原表（从 legado 侧原样搬过来） |
| `FanqieInput.kt` | 书号解析：纯书号 / 链接 / 整段分享文案 |
| `FanqieApi.kt` | 目录接口 + 详情页元信息 + 正文，内部调用解码表 |
| `FanqieInterceptor.kt` | 代码后端：`fanqie.local` 的 `/toc`、`/read`、`/search` 就地应答 |
| `FanqieBookSource.kt` | 内置 2.0 书源定义（JSONPath 规则）与幂等注册 |
| `FanqieTlsSocketFactory.kt` | **4.4 专用 TLS 工厂**（见第三节） |

接入点（每处都很小）：

| 位置 | 改动 |
|---|---|
| `base/BaseModelImpl.java` | `getClient()` 拦截器链最前面加 `FanqieInterceptor()` |
| `MApplication.java` | `onCreate()` 末尾 `FanqieBookSource.ensureInserted()`（幂等） |
| `res/menu/menu_book_search_activity.xml` + `values/strings.xml` | 搜索页菜单加「番茄直达」开关与文案 |
| `view/activity/SearchBookActivity.java` | ①菜单开关（持久化 `fanqieDirect`）②提交时识别番茄链接/文案/书号 → 直接开详情页 |

识别策略：**链接/分享文案总是识别**；纯书号需要先在搜索页菜单里勾选「番茄直达」，
避免把普通数字关键词误判成书号。认不出就退回原版书源搜索。

## 二、数据契约

```
GET https://fanqie.local/toc/<bookId>      -> {name, author, intro, cover, chapters:[{title,url}]}
GET https://fanqie.local/read/<chapterId>  -> {title, text}   // text 已还原私有区码点
GET https://fanqie.local/search            -> {books:[]}      // 不参与关键字搜索
```

内置源规则：`ruleBookName=$.name`、`ruleChapterList=$.chapters`、`ruleChapterName=$.title`、
`ruleContentUrl=$.url`、`ruleBookContent=$.text`；`ruleChapterUrl` 留空（目录页=详情页）。

## 三、关键坑：Android 4.4 的 TLS（移植时最容易卡住的地方）

现象：番茄请求 `fanqienovel.com` 报 `SSL handshake aborted ... tlsv1 alert protocol version`。

原因与结论（实测数据，别再用猜的）：

1. 4.4 默认只开 TLS1.0 → 站点回 `protocol_version` alert；
2. 打开 TLS1.2 后仍失败（`sslv3 alert handshake failure`）→ 因为 **4.4 的 53 个内置套件里没有 AES-GCM**，
   只有 ECDHE-CBC / DHE-CBC / RSA-CBC；
3. 实测 `fanqienovel.com` **接受** TLS1.2 + `ECDHE-RSA-AES128-SHA`（CBC）——所以 4.4 有救；
4. 但 **OkHttp 会在建连后重设 enabledProtocols/enabledCipherSuites**，把 socket 工厂里的设置覆盖掉，
   所以走 OkHttp 的方案无效（`help/Tls12SocketFactory` 就是这个原因没起作用）；
5. 有效做法：番茄请求改走 `HttpURLConnection` + 自定义工厂（`FanqieTlsSocketFactory`），
   在 `createSocket` 时只启用 `TLSv1.2` + 现代套件；**不要**把 SSLv3/TLS1.0 留在列表里（部分 WAF 会直接拒）。

诊断日志（`FanqieTls` tag）会打印设备支持的协议/套件与最终启用项，换设备时先看它。

## 四、真机验证（Kindle 4.4.2）

- 内置源注册：`BOOK_SOURCE_BEAN` 里出现 `https://fanqie.local`（番茄小说 / 分组「番茄」）
- 番茄直达：搜索框粘 `fanqienovel.com/page/7143038691944959011` → 直接进详情页，
  显示 书名 **十日终焉**、作者 杀虫队队员、简介、最新章节，目录 **1496 章**
- 加入书架：`BOOK_SHELF_BEAN` 里出现 `https://fanqie.local/toc/7143038691944959011`
- 阅读/缓存：缓存目录出现 `十日终焉-httpsfanqielocal/00000-第1章 空屋.nb` 等 5 个文件；
  抽查第 1 章 2550 字、67 段，**未还原私有区码点 0 个**（解码正常）

## 五、注意事项

- 老 Kinde 内存只有 210MB：番茄接口异常时会重试（目录最多 3 次），
  期间设备可能很慢，别同时开别的应用；测试时建议先用 `logcat -c` 再复现，看 `FanqieApi`/`FanqieInterceptor` 的日志。
- `am start` 拉不起 `MainActivity`（未导出），要用 `WelcomeActivity`（启动页，已导出）或 root。
- 设备自带的拼音输入法会把 `adb shell input text` 的字符组词，要打 ASCII 时优先用
  `am start --es searchKey <文本>` 这种带 extra 的方式，别硬打字。
