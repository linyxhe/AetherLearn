package com.aetherlearn.controller;

import com.aetherlearn.common.Result;
import com.aetherlearn.common.RoleConstant;
import com.aetherlearn.common.SecurityUtils;
import com.aetherlearn.dto.AnalyticsOverviewVO;
import com.aetherlearn.dto.DashboardStatVO;
import com.aetherlearn.service.DashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 看板与学情控制器（F-DASH / F-LEARN）
 * <p>路径：/api/dashboard/** 。数据看板限定 教师/管理员；学情总览限定 学生。</p>
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * 教师/管理员数据看板统计（F-DASH-01~06）
     */
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @GetMapping("/stat")
    public Result<DashboardStatVO> stat() {
        Long userId = SecurityUtils.getCurrentUserId();
        Integer role = SecurityUtils.getCurrentRole();
        return Result.success(dashboardService.getDashboardStat(userId, role));
    }

    /**
     * 学生学情总览（F-LEARN-01/02/04）
     */
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/analytics/overview")
    public Result<AnalyticsOverviewVO> analyticsOverview() {
        Long studentId = SecurityUtils.getCurrentUserId();
        return Result.success(dashboardService.getAnalyticsOverview(studentId));
    }
}
