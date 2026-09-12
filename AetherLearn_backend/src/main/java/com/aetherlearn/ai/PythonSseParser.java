package com.aetherlearn.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * SSE 行解析器（纯逻辑，便于单测）。
 * <p>与 Python 侧 {@code app/llm/sse.py} 的编码规则对偶：</p>
 * <ul>
 *   <li>一个事件 = 一行 {@code event:} + 一到多行 {@code data:} + 空行；</li>
 *   <li>多行 data 用 {@code \n} 重新拼接，保证模型输出里的代码块换行不丢失；</li>
 *   <li>{@code :} 开头是心跳注释，直接忽略；{@code id:} / {@code retry:} 也忽略。</li>
 * </ul>
 */
public class PythonSseParser {

    private String eventName;
    private final List<String> dataLines = new ArrayList<>();

    /**
     * 喂入一行，若凑成一个完整事件则返回它，否则返回 null。
     *
     * @param line 从流里读到的一行（不含换行符）
     * @return 完整事件，或 null
     */
    public SseEvent feed(String line) {
        if (line == null) {
            return null;
        }
        if (line.isEmpty()) {
            return dispatch();
        }
        if (line.startsWith(":")) {
            // 心跳/注释行
            return null;
        }
        if (line.startsWith("event:")) {
            eventName = line.substring(6).trim();
            return null;
        }
        if (line.startsWith("data:")) {
            String value = line.substring(5);
            // SSE 规范：冒号后最多去掉一个空格，其余空格属于数据本身
            if (value.startsWith(" ")) {
                value = value.substring(1);
            }
            dataLines.add(value);
            return null;
        }
        // 其它字段（id/retry/未知字段）忽略
        return null;
    }

    /** 流结束时派发残留事件（部分服务端不发最后一个空行）。 */
    public SseEvent flush() {
        return dispatch();
    }

    private SseEvent dispatch() {
        if (eventName == null && dataLines.isEmpty()) {
            return null;
        }
        String name = eventName == null ? "message" : eventName;
        String data = String.join("\n", dataLines);
        eventName = null;
        dataLines.clear();
        return new SseEvent(name, data);
    }

    /** 一个 SSE 事件。 */
    public record SseEvent(String event, String data) {
    }
}
