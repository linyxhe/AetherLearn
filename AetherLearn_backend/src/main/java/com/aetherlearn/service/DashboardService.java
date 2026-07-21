package com.aetherlearn.service;

import com.aetherlearn.dto.AnalyticsOverviewVO;
import com.aetherlearn.dto.DashboardStatVO;

/**
 * 看板与学情统计服务（F-DASH / F-LEARN）
 */
public interface DashboardService {

    /**
     * 教师/管理员数据看板统计（F-DASH-01~06）
     *
     * @param userId 当前用户ID（教师/管理员）
     * @param role   当前角色编码
     * @return 看板聚合数据
     */
    DashboardStatVO getDashboardStat(Long userId, Integer role);

    /**
     * 学生学情总览（F-LEARN-01/02/04）
     *
     * @param studentId 学生ID
     * @return 学情聚合数据
     */
    AnalyticsOverviewVO getAnalyticsOverview(Long studentId);
}
