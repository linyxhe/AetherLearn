package com.aetherlearn.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * Python AI 服务的 SSE 流式客户端。
 * <p>必须独立于 {@link PythonAiClient} 的 RestTemplate：那里的读超时是"整请求超时"
 * （Spring 把它映射到 JDK HttpClient 的 request timeout），用于长回答会在 60s 被掐断。</p>
 * <p>这里用 JDK HttpClient 直连：HTTP/1.1（uvicorn/h11 不支持 h2c 升级）、连接超时 10s、
 * 不设整请求超时；另外用看门狗兜住"服务端卡住不吐数据"的情况。</p>
 */
@Slf4j
@Component
public class PythonAiStreamClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final ScheduledExecutorService watchdog =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "ai-stream-watchdog");
                thread.setDaemon(true);
                return thread;
            });

    @Value("${ai.python-service.url:http://localhost:8000}")
    private String baseUrl;

    @Value("${ai.python-service.api-key:}")
    private String apiKey;

    /** 首块超时：连接建立后多久没收到任何事件就放弃（Python 侧检索阶段可能十几秒）。 */
    @Value("${ai.python-service.stream-first-event-timeout:45s}")
    private Duration firstEventTimeout;

    /** 空闲超时：多久没有任何行到达就放弃（Python 侧每 15s 发一次心跳）。 */
    @Value("${ai.python-service.stream-idle-timeout:60s}")
    private Duration idleTimeout;

    public PythonAiStreamClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** SSE 事件处理器。 */
    @FunctionalInterface
    public interface SseHandler {
        void onEvent(String event, String data);
    }

    /**
     * 发起流式请求并逐事件回调。
     *
     * @param path    形如 {@code /v1/chat/qa/stream}
     * @param body    请求体
     * @param handler 事件处理器
     * @return 流结果（是否收到 done、失败原因等）
     */
    public StreamOutcome stream(String path, Map<String, Object> body, SseHandler handler) {
        AtomicBoolean completedEvent = new AtomicBoolean(false);
        AtomicLong lastActivity = new AtomicLong(System.currentTimeMillis());
        AtomicReference<Stream<String>> streamRef = new AtomicReference<>();
        AtomicBoolean watchdogFired = new AtomicBoolean(false);
        AtomicBoolean gotFirstEvent = new AtomicBoolean(false);

        ScheduledFuture<?> firstGuard = watchdog.schedule(() -> {
            if (!gotFirstEvent.get()) {
                watchdogFired.set(true);
                closeQuietly(streamRef.get());
            }
        }, firstEventTimeout.toMillis(), TimeUnit.MILLISECONDS);

        ScheduledFuture<?> idleGuard = watchdog.scheduleWithFixedDelay(() -> {
            long idleMs = System.currentTimeMillis() - lastActivity.get();
            if (idleMs > idleTimeout.toMillis()) {
                watchdogFired.set(true);
                closeQuietly(streamRef.get());
            }
        }, idleTimeout.toMillis(), idleTimeout.toMillis(), TimeUnit.MILLISECONDS);

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(resolveUrl(path)))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Accept", "text/event-stream")
                    .header("X-API-Key", apiKey == null ? "" : apiKey)
                    // 不调用 .timeout(...)：流式响应不能有整请求超时
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<Stream<String>> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofLines());

            if (response.statusCode() >= 300) {
                String detail = response.body().findFirst().orElse("");
                return StreamOutcome.failure("http_" + response.statusCode(), detail);
            }

            PythonSseParser parser = new PythonSseParser();
            try (Stream<String> lines = response.body()) {
                streamRef.set(lines);
                var iterator = lines.iterator();
                while (iterator.hasNext()) {
                    String line = iterator.next();
                    lastActivity.set(System.currentTimeMillis());
                    PythonSseParser.SseEvent event = parser.feed(line);
                    if (event == null) {
                        continue;
                    }
                    gotFirstEvent.set(true);
                    if ("done".equals(event.event())) {
                        completedEvent.set(true);
                    }
                    handler.onEvent(event.event(), event.data());
                }
                PythonSseParser.SseEvent tail = parser.flush();
                if (tail != null) {
                    if ("done".equals(tail.event())) {
                        completedEvent.set(true);
                    }
                    handler.onEvent(tail.event(), tail.data());
                }
            }

            if (watchdogFired.get()) {
                return StreamOutcome.failure("stream_timeout", "等待模型输出超时");
            }
            return completedEvent.get()
                    ? StreamOutcome.success()
                    : StreamOutcome.failure("stream_incomplete", "流在 done 之前结束");
        } catch (IOException e) {
            if (watchdogFired.get()) {
                return StreamOutcome.failure("stream_timeout", "等待模型输出超时");
            }
            return StreamOutcome.failure("io_error", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return StreamOutcome.failure("interrupted", "流式读取被中断");
        } catch (Exception e) {
            return StreamOutcome.failure("error", e.getMessage());
        } finally {
            firstGuard.cancel(false);
            idleGuard.cancel(false);
        }
    }

    private void closeQuietly(Stream<String> stream) {
        if (stream == null) {
            return;
        }
        try {
            stream.close();
        } catch (Exception e) {
            log.debug("关闭流式响应失败：{}", e.getMessage());
        }
    }

    private String resolveUrl(String path) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        if (base.endsWith("/")) {
            return base + path.substring(1);
        }
        return base + path;
    }

    /** 流式调用结果。 */
    public record StreamOutcome(boolean completed, String errorCode, String detail) {

        public static StreamOutcome success() {
            return new StreamOutcome(true, null, null);
        }

        public static StreamOutcome failure(String errorCode, String detail) {
            return new StreamOutcome(false, errorCode, detail);
        }
    }
}
