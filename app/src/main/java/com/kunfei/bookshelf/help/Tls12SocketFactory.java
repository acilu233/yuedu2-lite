package com.kunfei.bookshelf.help;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Android 4.4(API19) 默认不启用 TLS 1.2，OkHttp 走 MODERN_TLS 时会在现代站点上握手失败。
 * 这里包一层 socket 工厂，把 TLSv1.2 显式加进 enabledProtocols。
 *
 * 4.4 上 OkHttp 的 sslSocketFactory() 需要显式工厂 + X509TrustManager 成对传入，
 * 所以这个类只负责协议协商，证书校验仍由调用方（SSLSocketClient 的 trust-all manager）决定。
 */
public class Tls12SocketFactory extends SSLSocketFactory {

    private static final String TLS_V12 = "TLSv1.2";

    private final SSLSocketFactory delegate;

    public Tls12SocketFactory(SSLSocketFactory delegate) {
        this.delegate = delegate;
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return delegate.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return delegate.getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket() throws IOException {
        return enableTls12(delegate.createSocket());
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
        return enableTls12(delegate.createSocket(s, host, port, autoClose));
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return enableTls12(delegate.createSocket(host, port));
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
        return enableTls12(delegate.createSocket(host, port, localHost, localPort));
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return enableTls12(delegate.createSocket(host, port));
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        return enableTls12(delegate.createSocket(address, port, localAddress, localPort));
    }

    private Socket enableTls12(Socket socket) {
        if (socket instanceof SSLSocket) {
            SSLSocket sslSocket = (SSLSocket) socket;
            List<String> protocols = new ArrayList<>(Arrays.asList(sslSocket.getEnabledProtocols()));
            if (!protocols.contains(TLS_V12)) {
                protocols.add(TLS_V12);
            }
            try {
                sslSocket.setEnabledProtocols(protocols.toArray(new String[0]));
            } catch (Exception ignored) {
                // 该设备不支持 TLSv1.2 时保持原样，让 OkHttp 自己回落
            }
        }
        return socket;
    }
}
