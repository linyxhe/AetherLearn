package com.aetherlearn.kb;

import com.aetherlearn.entity.KnowledgeChunk;
import com.aetherlearn.mapper.KnowledgeChunkMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BM25 检索器（F-KB / RAG 本地检索）
 * <p>按 {@code course_id} 隔离，加载课程全部切片后在内存中计算 BM25 相关性得分，返回 Top-K 切片。
 * 中文采用“单字 + 二元”混合切分，英文/数字按词切分，适配中英混合的课程资料。</p>
 */
@Slf4j
@Component
public class Bm25Retriever {

    /** BM25 参数：词频饱和度 */
    private static final double K1 = 1.5;
    /** BM25 参数：文档长度归一化 */
    private static final double B = 0.75;

    /** 英文/数字词 */
    private static final Pattern EN_PATTERN = Pattern.compile("[a-z0-9]+");
    /** 连续中文字符串 */
    private static final Pattern CN_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]+");

    private final KnowledgeChunkMapper chunkMapper;

    public Bm25Retriever(KnowledgeChunkMapper chunkMapper) {
        this.chunkMapper = chunkMapper;
    }

    /**
     * 检索相关切片
     *
     * @param courseId 课程ID（隔离维度）
     * @param query    用户提问
     * @param topK     返回数量上限
     * @return 按相关性降序排列的切片（最多 topK 条，仅返回得分>0 的）
     */
    public List<KnowledgeChunk> retrieve(Long courseId, String query, int topK) {
        List<KnowledgeChunk> chunks = chunkMapper.selectByCourseId(courseId);
        if (chunks.isEmpty()) {
            return new ArrayList<>();
        }
        // 1) 预处理：每条切片分词 + 统计词频
        List<List<String>> docTokens = new ArrayList<>();
        for (KnowledgeChunk c : chunks) {
            docTokens.add(tokenize(c.getContent()));
        }
        int n = chunks.size();
        // 平均文档长度（按 token 数）
        double avgdl = docTokens.stream().mapToInt(List::size).average().orElse(1.0);

        // 2) 文档频率 df（term -> 含该 term 的切片数）
        Map<String, Integer> df = new HashMap<>();
        for (List<String> toks : docTokens) {
            for (String t : new HashSet<>(toks)) {
                df.merge(t, 1, Integer::sum);
            }
        }

        // 3) 查询词
        List<String> qTokens = tokenize(query);
        if (qTokens.isEmpty()) {
            return new ArrayList<>();
        }

        // 4) 计算每条切片得分
        Map<KnowledgeChunk, Double> scores = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            List<String> toks = docTokens.get(i);
            Map<String, Integer> tf = new HashMap<>();
            for (String t : toks) tf.merge(t, 1, Integer::sum);
            double dl = toks.size();
            double score = 0.0;
            for (String qt : qTokens) {
                Integer n_t = df.get(qt);
                if (n_t == null) continue;
                // BM25 逆文档频率
                double idf = Math.log(1.0 + (n - n_t + 0.5) / (n_t + 0.5));
                int f = tf.getOrDefault(qt, 0);
                double numerator = f * (K1 + 1);
                double denominator = f + K1 * (1 - B + B * dl / avgdl);
                score += idf * numerator / denominator;
            }
            if (score > 0) {
                scores.put(chunks.get(i), score);
            }
        }

        // 5) 降序排序并取 Top-K
        return scores.entrySet().stream()
                .sorted(Map.Entry.<KnowledgeChunk, Double>comparingByValue().reversed())
                .limit(topK)
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * 分词：英文/数字按词；中文按“单字 + 二元”
     */
    private List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return tokens;
        }
        String lower = text.toLowerCase();
        Matcher en = EN_PATTERN.matcher(lower);
        while (en.find()) {
            tokens.add(en.group());
        }
        Matcher cn = CN_PATTERN.matcher(lower);
        while (cn.find()) {
            String seg = cn.group();
            for (int i = 0; i < seg.length(); i++) {
                tokens.add(String.valueOf(seg.charAt(i)));
                if (i + 1 < seg.length()) {
                    tokens.add(seg.substring(i, i + 2));
                }
            }
        }
        return tokens;
    }
}
