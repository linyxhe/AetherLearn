import request from '../utils/request'

// 看板与学情接口（F-DASH / F-LEARN）

// 教师/管理员数据看板统计（F-DASH-01~06）
export function getDashboardStat() {
  return request.get('/dashboard/stat')
}

// 学生学情总览（F-LEARN-01/02/04）
export function getAnalyticsOverview() {
  return request.get('/dashboard/analytics/overview')
}
