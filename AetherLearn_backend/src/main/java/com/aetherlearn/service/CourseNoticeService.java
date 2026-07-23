package com.aetherlearn.service;

import com.aetherlearn.dto.CourseNoticeSaveRequest;
import com.aetherlearn.entity.CourseNotice;

import java.util.List;

/**
 * 课程公告服务（F-NOTIFY）
 */
public interface CourseNoticeService {

    /** 按角色查询公告列表 */
    List<CourseNotice> listByRole(Long userId, Integer role, Long courseId);

    /** 保存公告 */
    CourseNotice save(CourseNoticeSaveRequest request, Long operatorId);

    /** 删除公告 */
    void delete(Long id);
}
