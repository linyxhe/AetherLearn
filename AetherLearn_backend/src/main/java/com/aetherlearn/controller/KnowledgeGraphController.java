package com.aetherlearn.controller;

import com.aetherlearn.common.Result;
import com.aetherlearn.common.SecurityUtils;
import com.aetherlearn.dto.KnowledgeGraphVO;
import com.aetherlearn.service.KnowledgeGraphService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识点图谱控制器（F-GRAPH）
 */
@RestController
@RequestMapping("/api/knowledge-graph")
public class KnowledgeGraphController {

    private final KnowledgeGraphService knowledgeGraphService;

    public KnowledgeGraphController(KnowledgeGraphService knowledgeGraphService) {
        this.knowledgeGraphService = knowledgeGraphService;
    }

    /** 查询当前用户可见课程的知识点图谱 */
    @GetMapping
    public Result<KnowledgeGraphVO> graph(@RequestParam(required = false) Long courseId) {
        return Result.success(knowledgeGraphService.buildGraph(
                SecurityUtils.getCurrentUserId(),
                SecurityUtils.getCurrentRole(),
                courseId
        ));
    }
}
