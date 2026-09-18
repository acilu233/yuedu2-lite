package com.kunfei.bookshelf.fanqie

import android.util.Log
import com.kunfei.bookshelf.help.SSLSocketClient
import java.net.Socket
import java.security.SecureRandom
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager

/**
 * Android 4.4(API19) 专用 TLS 工厂。
 *
 * 4.4 的两个坑：
 * 1) 默认不启用 TLS1.2（ClientHello 用 TLS1.0，现代站点直接回 protocol_version alert）；
 * 2) 即使打开 TLS1.2，默认加密套件里也没有 ECDHE+GCM，站点会回 handshake failure。
 *
 * 这里在 socket 建好时同时补齐协议与套件（只用设备真正支持的），
 * 再交给 HttpsURLConnection 使用——HttpsURLConnection 不会像 OkHttp 那样
 * 在建连后重设 enabledProtocols/enabledCipherSuites，所以设置能生效。
 */
class FanqieTlsSocketFactory private constructor(private val delegate: SSLSocketFactory) : SSLSocketFactory() {

    companion object {
        /** 优先级从高到低；实际只会启用设备 supportedCipherSuites 里存在的那些。 */
        private val MODERN_CIPHERS = listOf(
            "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
            "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA",
            "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",
            "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA",
            "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_DHE_RSA_WITH_AES_128_CBC_SHA",
            "TLS_DHE_RSA_WITH_AES_256_CBC_SHA",
            "TLS_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_RSA_WITH_AES_128_CBC_SHA",
            "TLS_RSA_WITH_AES_256_CBC_SHA"
        )

        // 只开 TLS1.2：ClientHello 里带 SSLv3/TLS1.0 会被部分站点的 WAF 直接拒绝
        private val PREFERRED_PROTOCOLS = listOf("TLSv1.2")

        /** 信任所有证书的 TLS 工厂（与 SSLSocketClient 同策略）。 */
        fun create(): SSLSocketFactory {
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf<TrustManager>(SSLSocketClient.createTrustAllManager()), SecureRandom())
            return FanqieTlsSocketFactory(ctx.socketFactory)
        }
    }

    override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites

    override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites

    override fun createSocket(s: Socket?, host: String?, port: Int, autoClose: Boolean): Socket =
        tune(delegate.createSocket(s, host, port, autoClose))

    override fun createSocket(host: String?, port: Int): Socket = tune(delegate.createSocket(host, port))

    override fun createSocket(host: String?, port: Int, localHost: java.net.InetAddress?, localPort: Int): Socket =
        tune(delegate.createSocket(host, port, localHost, localPort))

    override fun createSocket(host: java.net.InetAddress?, port: Int): Socket = tune(delegate.createSocket(host, port))

    override fun createSocket(
        address: java.net.InetAddress?, port: Int,
        localAddress: java.net.InetAddress?, localPort: Int
    ): Socket = tune(delegate.createSocket(address, port, localAddress, localPort))

    private fun tune(socket: Socket): Socket {
        if (socket !is SSLSocket) return socket
        try {
            val supportedProtocols = socket.supportedProtocols.toSet()
            val supportedCiphers = socket.supportedCipherSuites.toSet()
            val protocols = PREFERRED_PROTOCOLS.filter { it in supportedProtocols }
            if (protocols.isNotEmpty()) {
                socket.enabledProtocols = protocols.toTypedArray()
            }
            // 只留现代套件（不再回落到设备默认那批 RC4/3DES）
            val ciphers = MODERN_CIPHERS.filter { it in supportedCiphers }
            if (ciphers.isNotEmpty()) {
                socket.enabledCipherSuites = ciphers.toTypedArray()
            }
            Log.i("FanqieTls", "supportedProtocols=${socket.supportedProtocols.joinToString()}")
            Log.i("FanqieTls", "支持套件数=${supportedCiphers.size} 现代套件命中=${MODERN_CIPHERS.count { it in supportedCiphers }}")
            Log.i("FanqieTls", "命中=${MODERN_CIPHERS.filter { it in supportedCiphers }.joinToString()}")
            Log.i("FanqieTls", "启用协议=${socket.enabledProtocols.joinToString()} 启用套件=${socket.enabledCipherSuites.joinToString()}")
        } catch (ignored: Throwable) {
            Log.w("FanqieTls", "tune 失败: ${ignored}")
            // 设置失败就保持默认，让调用方看到原始握手错误
        }
        return socket
    }
}
