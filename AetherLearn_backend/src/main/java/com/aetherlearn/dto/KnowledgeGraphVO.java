package com.aetherlearn.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 知识点图谱视图对象
 */
@Data
public class KnowledgeGraphVO implements Serializable {

    /** 图谱节点 */
    private List<Node> nodes;

    /** 图谱边 */
    private List<Edge> edges;

    /** 节点 */
    @Data
    public static class Node implements Serializable {
        private String id;
        private String name;
        private String category;
        private int value;
    }

    /** 边 */
    @Data
    public static class Edge implements Serializable {
        private String source;
        private String target;
        private String label;
    }
}
