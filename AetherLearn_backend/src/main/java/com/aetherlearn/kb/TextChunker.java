package com.aetherlearn.kb;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 文本切片器（F-KB-02 文档解析与切片）
 * <p>将 {@link DocumentParser} 解析出的纯文本按段落聚合为约 {@code chunkSize} 字符的切片，
 * 相邻切片保留少量重叠（overlap），兼顾召回完整性与上下文连贯性。</p>
 */
@Component
public class TextChunker {

    /** 默认切片字符上限 */
    private static final int DEFAULT_CHUNK_SIZE = 600;
    /** 默认重叠字符数（约 20%） */
    private static final int DEFAULT_OVERLAP = 120;

    /**
     * 默认切片（600 字符 / 重叠 120）
     */
    public List<String> split(String text) {
        return split(text, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    /**
     * 按指定大小切片
     *
     * @param text      待切片文本（可为 null）
     * @param chunkSize 切片字符上限
     * @param overlap   相邻切片重叠字符数
     * @return 切片列表（非空文本下至少返回 1 条）
     */
    public List<String> split(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        // 规范化：按换行拆段落并去除空段落
        List<String> paragraphs = Arrays.stream(text.split("\\n+"))
                .map(String::trim)
                .filter(p -> !p.isEmpty())
                .toList();

        StringBuilder sb = new StringBuilder();
        for (String p : paragraphs) {
            // 单段落本身超长：先把缓冲落盘，再对该段落做字符窗口硬切
            if (p.length() > chunkSize) {
                if (sb.length() > 0) {
                    chunks.add(sb.toString().trim());
                    sb.setLength(0);
                }
                flushHardSplit(chunks, p, chunkSize, overlap);
                continue;
            }
            // 当前缓冲 + 新段落超过上限：先落一个切片，并保留尾部重叠内容
            if (sb.length() > 0 && sb.length() + 1 + p.length() > chunkSize) {
                chunks.add(sb.toString().trim());
                String tail = sb.length() > overlap
                        ? sb.substring(sb.length() - overlap)
                        : sb.toString();
                sb.setLength(0);
                sb.append(tail);
            }
            if (sb.length() > 0) sb.append("\n");
            sb.append(p);
        }
        if (sb.length() > 0) {
            chunks.add(sb.toString().trim());
        }
        return chunks;
    }

    /** 超长单段落的硬切分（按字符窗口，带重叠） */
    private void flushHardSplit(List<String> chunks, String p, int chunkSize, int overlap) {
        int start = 0;
        while (start < p.length()) {
            int end = Math.min(start + chunkSize, p.length());
            chunks.add(p.substring(start, end).trim());
            if (end >= p.length()) break;
            start = end - overlap;
            if (start < 0) start = 0;
        }
    }
}
