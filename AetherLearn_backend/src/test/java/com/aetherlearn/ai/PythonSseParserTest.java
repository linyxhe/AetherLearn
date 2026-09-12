package com.aetherlearn.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PythonSseParser 单元测试。
 * <p>重点覆盖与 Python 侧 {@code app/llm/sse.py} 的编码对偶关系：多行 data 的重新拼接、
 * 心跳注释忽略、冒号后一个空格的规范处理，以及流结束时的残留派发。</p>
 */
class PythonSseParserTest {

    /** 一次性喂入所有行，收集派发出的事件。 */
    private List<PythonSseParser.SseEvent> feedAll(String... lines) {
        PythonSseParser parser = new PythonSseParser();
        List<PythonSseParser.SseEvent> events = new ArrayList<>();
        for (String line : lines) {
            PythonSseParser.SseEvent event = parser.feed(line);
            if (event != null) {
                events.add(event);
            }
        }
        PythonSseParser.SseEvent tail = parser.flush();
        if (tail != null) {
            events.add(tail);
        }
        return events;
    }

    @Test
    @DisplayName("单行 data 组成一个完整事件")
    void parsesSingleDataLine() {
        List<PythonSseParser.SseEvent> events = feedAll("event: chunk", "data: 你好", "");

        assertEquals(1, events.size());
        assertEquals("chunk", events.get(0).event());
        assertEquals("你好", events.get(0).data());
    }

    @Test
    @DisplayName("多行 data 用换行重新拼接，代码块换行不丢失")
    void joinsMultiLineData() {
        // Python 侧把含换行的增量文本拆成多条 data:，这里必须拼回原样
        List<PythonSseParser.SseEvent> events = feedAll(
                "event: chunk",
                "data: ```java",
                "data: int a = 1;",
                "data: ```",
                "");

        assertEquals(1, events.size());
        assertEquals("```java\nint a = 1;\n```", events.get(0).data());
    }

    @Test
    @DisplayName("data 为空字符串时是合法事件（空增量），不是心跳")
    void keepsEmptyDataEvent() {
        List<PythonSseParser.SseEvent> events = feedAll("event: chunk", "data:", "");

        assertEquals(1, events.size());
        assertEquals("chunk", events.get(0).event());
        assertEquals("", events.get(0).data());
    }

    @Test
    @DisplayName("冒号后只去掉一个空格，其余空格属于数据本身")
    void stripsOnlyOneLeadingSpace() {
        List<PythonSseParser.SseEvent> events = feedAll("event: chunk", "data:   缩进保留", "");

        assertEquals(1, events.size());
        assertEquals("  缩进保留", events.get(0).data());
    }

    @Test
    @DisplayName("心跳注释与 id/retry 字段被忽略")
    void ignoresHeartbeatAndUnknownFields() {
        List<PythonSseParser.SseEvent> events = feedAll(
                ": ping",
                "id: 7",
                "retry: 3000",
                "event: chunk",
                "data: x",
                "");

        assertEquals(1, events.size());
        assertEquals("chunk", events.get(0).event());
        assertEquals("x", events.get(0).data());
    }

    @Test
    @DisplayName("心跳的空行不会误发事件")
    void heartbeatBlankLineEmitsNothing() {
        PythonSseParser parser = new PythonSseParser();
        assertNull(parser.feed(": ping"));
        // 心跳自带空行；此时没有任何待派发内容，必须返回 null
        assertNull(parser.feed(""));
    }

    @Test
    @DisplayName("连续多个事件之间互不串味")
    void parsesMultipleEventsInOrder() {
        List<PythonSseParser.SseEvent> events = feedAll(
                "event: sources",
                "data: [{\"docId\":1}]",
                "",
                "event: chunk",
                "data: A",
                "",
                "event: done",
                "data: {\"useLlm\":true}",
                "");

        assertEquals(3, events.size());
        assertEquals("sources", events.get(0).event());
        assertEquals("[{\"docId\":1}]", events.get(0).data());
        assertEquals("chunk", events.get(1).event());
        assertEquals("A", events.get(1).data());
        assertEquals("done", events.get(2).event());
        assertEquals("{\"useLlm\":true}", events.get(2).data());
    }

    @Test
    @DisplayName("流结束时残留事件由 flush 派发（服务端可能不发最后一个空行）")
    void flushDispatchesTrailingEvent() {
        List<PythonSseParser.SseEvent> events = feedAll("event: done", "data: {\"ok\":true}");

        assertEquals(1, events.size());
        assertEquals("done", events.get(0).event());
        assertEquals("{\"ok\":true}", events.get(0).data());
    }

    @Test
    @DisplayName("只有 data 没有 event 时按 SSE 规范取默认事件名 message")
    void defaultsEventNameToMessage() {
        List<PythonSseParser.SseEvent> events = feedAll("data: hello", "");

        assertEquals(1, events.size());
        assertEquals("message", events.get(0).event());
    }

    @Test
    @DisplayName("空行后重复空行不会产生幽灵事件")
    void repeatedBlankLinesEmitNothing() {
        List<PythonSseParser.SseEvent> events = feedAll("", "", "");

        assertTrue(events.isEmpty());
    }

    @Test
    @DisplayName("CRLF 行会被上层剥掉，解析器不依赖行尾符")
    void worksWithAlreadyStrippedLines() {
        // BufferedReader.readLine() 已经去掉 \r\n；这里确认不含 \r 的输入解析正常
        List<PythonSseParser.SseEvent> events = feedAll("event: chunk", "data: 行一", "data: 行二", "");

        assertEquals(1, events.size());
        assertNotNull(events.get(0).data());
        assertEquals("行一\n行二", events.get(0).data());
    }

    @Test
    @DisplayName("null 行被安全忽略")
    void ignoresNullLine() {
        PythonSseParser parser = new PythonSseParser();

        assertNull(parser.feed(null));
    }
}
