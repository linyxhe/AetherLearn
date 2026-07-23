package com.aetherlearn.service.impl;

import com.aetherlearn.common.RoleConstant;
import com.aetherlearn.dto.KnowledgeGraphVO;
import com.aetherlearn.entity.Assignment;
import com.aetherlearn.entity.Course;
import com.aetherlearn.entity.Question;
import com.aetherlearn.mapper.AssignmentMapper;
import com.aetherlearn.mapper.CourseMapper;
import com.aetherlearn.mapper.QuestionMapper;
import com.aetherlearn.service.KnowledgeGraphService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识点图谱服务实现
 * <p>基于作业/测验题目中的 {@code knowledge_point} 字段生成课程-知识点关系图。</p>
 */
@Service
public class KnowledgeGraphServiceImpl implements KnowledgeGraphService {

    private final AssignmentMapper assignmentMapper;
    private final QuestionMapper questionMapper;
    private final CourseMapper courseMapper;

    public KnowledgeGraphServiceImpl(AssignmentMapper assignmentMapper, QuestionMapper questionMapper, CourseMapper courseMapper) {
        this.assignmentMapper = assignmentMapper;
        this.questionMapper = questionMapper;
        this.courseMapper = courseMapper;
    }

    @Override
    public KnowledgeGraphVO buildGraph(Long userId, Integer role, Long courseId) {
        List<Assignment> assignments = role != null && role == RoleConstant.STUDENT
                ? assignmentMapper.selectByStudent(userId, courseId)
                : assignmentMapper.selectByTeacherOrAdmin(userId, role, courseId);
        Map<Long, Map<String, Integer>> courseKnowledgeCount = new LinkedHashMap<>();
        for (Assignment assignment : assignments) {
            Map<String, Integer> map = courseKnowledgeCount.computeIfAbsent(assignment.getCourseId(), key -> new LinkedHashMap<>());
            List<Question> questions = questionMapper.selectByAssignmentId(assignment.getId());
            for (Question question : questions) {
                String point = question.getKnowledgePoint();
                if (point == null || point.isBlank()) {
                    continue;
                }
                map.put(point, map.getOrDefault(point, 0) + 1);
            }
        }
        return toGraph(courseKnowledgeCount);
    }

    private KnowledgeGraphVO toGraph(Map<Long, Map<String, Integer>> courseKnowledgeCount) {
        List<KnowledgeGraphVO.Node> nodes = new ArrayList<>();
        List<KnowledgeGraphVO.Edge> edges = new ArrayList<>();
        for (Map.Entry<Long, Map<String, Integer>> entry : courseKnowledgeCount.entrySet()) {
            Course course = courseMapper.selectById(entry.getKey());
            String courseId = "course-" + entry.getKey();
            nodes.add(node(courseId, course == null ? "课程#" + entry.getKey() : course.getCourseName(), "course", entry.getValue().size()));
            for (Map.Entry<String, Integer> kp : entry.getValue().entrySet()) {
                String pointId = courseId + "-kp-" + Math.abs(kp.getKey().hashCode());
                nodes.add(node(pointId, kp.getKey(), "knowledge", kp.getValue()));
                KnowledgeGraphVO.Edge edge = new KnowledgeGraphVO.Edge();
                edge.setSource(courseId);
                edge.setTarget(pointId);
                edge.setLabel("包含");
                edges.add(edge);
            }
        }
        KnowledgeGraphVO vo = new KnowledgeGraphVO();
        vo.setNodes(nodes);
        vo.setEdges(edges);
        return vo;
    }

    private KnowledgeGraphVO.Node node(String id, String name, String category, int value) {
        KnowledgeGraphVO.Node node = new KnowledgeGraphVO.Node();
        node.setId(id);
        node.setName(name);
        node.setCategory(category);
        node.setValue(value);
        return node;
    }
}
