package com.csm.paas.core.infrastructure.adapter.port;

import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnixDomainSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * 最小 Docker Engine HTTP 客户端（替代厂商的 docker-java `DockerClient`）。
 *
 * <p><b>类 C：本环境到不了 docker daemon，代码齐全但不做在线验证。</b>
 *
 * <p>厂商的 docker-java 通过 `docker.host` 连接：
 * k8s profile 默认 `unix:///var/run/docker.sock`（`application-k8s.yml:28`），
 * base profile 默认 `tcp://localhost:2375`（`application.yml:77`）。
 * JDK 的 `java.net.http.HttpClient` **不支持 unix socket**，所以这里对 `unix://` 自己写 HTTP/1.1：
 * `SocketChannel.open(StandardProtocolFamily.UNIX)` + `Channels.newInputStream/newOutputStream`，
 * 支持 `Content-Length` 与 `Transfer-Encoding: chunked` 两种 body（大镜像 tar 是 chunked）。
 * `tcp://` / `http(s)://` 走 `HttpClient`。
 */
@Slf4j
public class DockerEngineHttpClient implements Closeable {

    /** 一次性读完的响应（状态码、头、body）。 */
    public record DockerResponse(int status, Map<String, List<String>> headers, byte[] body) {
    }

    /** 流式响应：body 读完必须 {@link #close()}（会顺带关掉底层连接/通道）。 */
    public record DockerStreamResponse(int status, InputStream body, Closeable connection) implements Closeable {

        @Override
        public void close() throws IOException {
            try {
                body.close();
            } finally {
                connection.close();
            }
        }
    }

    private final boolean unix;
    private final String unixPath;
    private final String scheme;
    private final String host;
    private final int port;
    private final HttpClient httpClient;

    public DockerEngineHttpClient(String dockerHost) {
        String value = dockerHost == null ? "" : dockerHost.trim();
        if (value.startsWith("unix://")) {
            this.unix = true;
            this.unixPath = value.substring("unix://".length());
            this.scheme = "http";
            this.host = "localhost";
            this.port = 80;
        } else {
            this.unix = false;
            this.unixPath = null;
            String rest = value;
            if (rest.startsWith("tcp://")) {
                rest = rest.substring("tcp://".length());
            }
            String resolvedScheme = "http";
            if (rest.startsWith("https://")) {
                resolvedScheme = "https";
                rest = rest.substring("https://".length());
            } else if (rest.startsWith("http://")) {
                rest = rest.substring("http://".length());
            }
            this.scheme = resolvedScheme;
            int colon = rest.lastIndexOf(':');
            if (colon > 0) {
                this.host = rest.substring(0, colon);
                int parsedPort;
                try {
                    parsedPort = Integer.parseInt(rest.substring(colon + 1));
                } catch (NumberFormatException e) {
                    parsedPort = "https".equals(resolvedScheme) ? 443 : 2375;
                }
                this.port = parsedPort;
            } else {
                this.host = rest;
                this.port = "https".equals(resolvedScheme) ? 443 : 2375;
            }
        }
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /** 一次性响应（小 body，如 `/images/load` 的响应、tag、inspect）。 */
    public DockerResponse request(String method, String path, Map<String, String> headers, byte[] body) {
        if (unix) {
            return rawRequest(method, path, headers, body, null);
        }
        HttpRequest.Builder builder = baseBuilder(method, path, headers);
        builder.method(method, (body == null || body.length == 0)
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(body));
        try {
            HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            return new DockerResponse(response.statusCode(), response.headers().map(), response.body());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("请求 docker daemon 被中断", e);
        }
    }

    /** 请求体是流（大 tar，`/images/load` 用 chunked 上传），响应一次读完。 */
    public DockerResponse requestStreamingBody(String method, String path, Map<String, String> headers,
                                               InputStream bodyIn) {
        if (unix) {
            return rawRequest(method, path, headers, null, bodyIn);
        }
        HttpRequest.Builder builder = baseBuilder(method, path, headers);
        builder.method(method, HttpRequest.BodyPublishers.ofInputStream(() -> bodyIn));
        try {
            HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            return new DockerResponse(response.statusCode(), response.headers().map(), response.body());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("请求 docker daemon 被中断", e);
        }
    }

    /** 流式响应体（`/images/{name}/get` 的 tar）。 */
    public DockerStreamResponse stream(String method, String path, Map<String, String> headers) {
        if (unix) {
            return rawStream(method, path, headers);
        }
        HttpRequest.Builder builder = baseBuilder(method, path, headers);
        builder.method(method, HttpRequest.BodyPublishers.noBody());
        try {
            HttpResponse<InputStream> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            return new DockerStreamResponse(response.statusCode(), response.body(), response.body());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("请求 docker daemon 被中断", e);
        }
    }

    @Override
    public void close() {
        // HttpClient 无需显式关闭；unix 通道随每个请求关闭。
    }

    private HttpRequest.Builder baseBuilder(String method, String path, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(scheme + "://" + host + ":" + port + path))
                .timeout(Duration.ofMinutes(30));
        // docker-java 的 unix socket 也是这条 Host 语义
        builder.header("Host", host + ":" + port);
        if (headers != null) {
            headers.forEach(builder::header);
        }
        return builder;
    }

    // ---------------- unix socket 的原生 HTTP/1.1 ----------------

    private SocketChannel openChannel() throws IOException {
        if (unix) {
            SocketChannel channel = SocketChannel.open(java.net.StandardProtocolFamily.UNIX);
            channel.connect(UnixDomainSocketAddress.of(Path.of(unixPath)));
            return channel;
        }
        return SocketChannel.open(new InetSocketAddress(host, port));
    }

    private String hostHeader() {
        return unix ? "localhost" : host + ":" + port;
    }

    private DockerResponse rawRequest(String method, String path, Map<String, String> headers,
                                      byte[] body, InputStream bodyIn) {
        SocketChannel channel = null;
        try {
            channel = openChannel();
            OutputStream out = Channels.newOutputStream(channel);
            InputStream in = Channels.newInputStream(channel);
            if (bodyIn != null) {
                writeRequestHead(out, method, path, headers, null, true);
                writeChunked(out, bodyIn);
            } else {
                long length = body == null ? 0L : body.length;
                writeRequestHead(out, method, path, headers, length, false);
                if (body != null && body.length > 0) {
                    out.write(body);
                    out.flush();
                }
            }
            ResponseHead head = readHead(in);
            ByteArrayOutputStream sink = new ByteArrayOutputStream();
            try (InputStream bodyStream = bodyStream(head, in)) {
                bodyStream.transferTo(sink);
            }
            return new DockerResponse(head.status, head.headers, sink.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            closeQuietly(channel);
        }
    }

    private DockerStreamResponse rawStream(String method, String path, Map<String, String> headers) {
        try {
            SocketChannel channel = openChannel();
            OutputStream out = Channels.newOutputStream(channel);
            InputStream in = Channels.newInputStream(channel);
            writeRequestHead(out, method, path, headers, 0L, false);
            ResponseHead head = readHead(in);
            return new DockerStreamResponse(head.status, bodyStream(head, in), channel);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeRequestHead(OutputStream out, String method, String path, Map<String, String> headers,
                                 Long contentLength, boolean chunked) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(method).append(' ').append(path).append(" HTTP/1.1\r\n");
        sb.append("Host: ").append(hostHeader()).append("\r\n");
        sb.append("Connection: close\r\n");
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
            }
        }
        if (chunked) {
            sb.append("Transfer-Encoding: chunked\r\n");
        } else if (contentLength != null) {
            sb.append("Content-Length: ").append(contentLength).append("\r\n");
        }
        sb.append("\r\n");
        out.write(sb.toString().getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
    }

    private static void writeChunked(OutputStream out, InputStream bodyIn) throws IOException {
        byte[] buffer = new byte[40 * 1024];
        int n;
        while ((n = bodyIn.read(buffer)) != -1) {
            out.write((Integer.toHexString(n) + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
            out.write(buffer, 0, n);
            out.write("\r\n".getBytes(StandardCharsets.ISO_8859_1));
        }
        out.write("0\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
    }

    private static final class ResponseHead {
        private int status;
        private final Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    }

    private static ResponseHead readHead(InputStream in) throws IOException {
        String statusLine = readLine(in);
        if (statusLine == null) {
            throw new IOException("docker daemon 无响应");
        }
        String[] parts = statusLine.split(" ", 3);
        if (parts.length < 2) {
            throw new IOException("非法状态行: " + statusLine);
        }
        ResponseHead head = new ResponseHead();
        head.status = Integer.parseInt(parts[1].trim());
        String line;
        while ((line = readLine(in)) != null && !line.isEmpty()) {
            int idx = line.indexOf(':');
            if (idx <= 0) {
                continue;
            }
            String key = line.substring(0, idx).trim();
            String value = line.substring(idx + 1).trim();
            head.headers.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }
        return head;
    }

    private static InputStream bodyStream(ResponseHead head, InputStream in) {
        if (head.status == 204 || head.status == 304) {
            return InputStream.nullInputStream();
        }
        String transferEncoding = first(head.headers, "Transfer-Encoding");
        if (transferEncoding != null && transferEncoding.toLowerCase(Locale.ROOT).contains("chunked")) {
            return new ChunkedInputStream(in);
        }
        String contentLength = first(head.headers, "Content-Length");
        if (contentLength != null) {
            try {
                return new LimitedInputStream(in, Long.parseLong(contentLength.trim()));
            } catch (NumberFormatException ignored) {
                // 落到 EOF 读取
            }
        }
        return in;
    }

    private static String first(Map<String, List<String>> headers, String name) {
        List<String> values = headers.get(name);
        return (values == null || values.isEmpty()) ? null : values.get(0);
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') {
                break;
            }
            if (c != '\r') {
                buffer.write(c);
            }
        }
        if (c == -1 && buffer.size() == 0) {
            return null;
        }
        return buffer.toString(StandardCharsets.ISO_8859_1);
    }

    private static void closeQuietly(SocketChannel channel) {
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException e) {
                log.debug("关闭 docker 通道失败", e);
            }
        }
    }

    /** chunked 解码（docker 的 `save`/`load` 响应都是 chunked）。 */
    private static final class ChunkedInputStream extends InputStream {

        private final InputStream in;
        private long remaining;
        private boolean finished;

        private ChunkedInputStream(InputStream in) {
            this.in = in;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int n = read(one, 0, 1);
            return n == -1 ? -1 : (one[0] & 0xFF);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (finished) {
                return -1;
            }
            if (remaining == 0 && !nextChunk()) {
                return -1;
            }
            int toRead = (int) Math.min(len, remaining);
            int n = in.read(b, off, toRead);
            if (n == -1) {
                finished = true;
                return -1;
            }
            remaining -= n;
            if (remaining == 0) {
                readLine(in);   // 吃掉 chunk 数据后的 CRLF
            }
            return n;
        }

        private boolean nextChunk() throws IOException {
            String line = readLine(in);
            if (line == null) {
                finished = true;
                return false;
            }
            String sizeHex = line;
            int semicolon = sizeHex.indexOf(';');
            if (semicolon >= 0) {
                sizeHex = sizeHex.substring(0, semicolon);
            }
            sizeHex = sizeHex.trim();
            if (sizeHex.isEmpty()) {
                line = readLine(in);
                if (line == null) {
                    finished = true;
                    return false;
                }
                sizeHex = line;
                semicolon = sizeHex.indexOf(';');
                if (semicolon >= 0) {
                    sizeHex = sizeHex.substring(0, semicolon);
                }
                sizeHex = sizeHex.trim();
            }
            long size;
            try {
                size = Long.parseLong(sizeHex, 16);
            } catch (NumberFormatException e) {
                throw new IOException("非法 chunk 长度: " + line, e);
            }
            if (size == 0L) {
                String trailer;
                while ((trailer = readLine(in)) != null && !trailer.isEmpty()) {
                    // 丢弃 trailer
                }
                finished = true;
                return false;
            }
            remaining = size;
            return true;
        }
    }

    /** 定长 body。 */
    private static final class LimitedInputStream extends InputStream {

        private final InputStream in;
        private long remaining;

        private LimitedInputStream(InputStream in, long remaining) {
            this.in = in;
            this.remaining = remaining;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int c = in.read();
            if (c != -1) {
                remaining--;
            }
            return c;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int n = in.read(b, off, (int) Math.min(len, remaining));
            if (n > 0) {
                remaining -= n;
            }
            return n;
        }
    }
}
