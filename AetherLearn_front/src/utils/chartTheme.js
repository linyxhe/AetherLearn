// AetherLearn 图表主题与初始化助手（第四波看板 / frontend-design 签名元素）
// 统一蓝紫渐变调色板 + 柔和网格 + 圆角容器风格，所有 ECharts 图表共用，
// 形成可辨识但不古板的"科技教学数据"质感；并统一处理自适应与 reduced-motion。

import * as echarts from 'echarts'

// 蓝紫主色与图表辅助色（与 main.css token 对齐）
export const BRAND = {
  primary: '#5b6ef5',
  accent: '#7c4dff',
  cyan: '#38bdf8',
  green: '#22c55e',
  amber: '#f59e0b',
  red: '#ef4444',
  text2: '#6b7280',
  grid: '#eef0f6'
}

// 多序列调色板
export const PALETTE = [BRAND.primary, BRAND.accent, BRAND.cyan, BRAND.green, BRAND.amber, BRAND.red]

// 竖向蓝→紫渐变（面积图/折线填充）
export function areaGradient() {
  return new echarts.graphic.LinearGradient(0, 0, 0, 1, [
    { offset: 0, color: 'rgba(91,110,245,0.45)' },
    { offset: 1, color: 'rgba(124,77,255,0.04)' }
  ])
}

// 横向蓝→紫渐变（柱状）
export function barGradient() {
  return new echarts.graphic.LinearGradient(0, 0, 1, 0, [
    { offset: 0, color: BRAND.primary },
    { offset: 1, color: BRAND.accent }
  ])
}

let registered = false

// 注册统一的 aeth 主题（仅一次）
function ensureTheme() {
  if (registered) return
  echarts.registerTheme('aeth', {
    color: PALETTE,
    backgroundColor: 'transparent',
    textStyle: { color: BRAND.text2, fontFamily: 'inherit' },
    title: { textStyle: { color: '#1f2330', fontWeight: 700 } },
    legend: { textStyle: { color: BRAND.text2 } },
    tooltip: {
      backgroundColor: 'rgba(255,255,255,0.96)',
      borderColor: '#eef0f6',
      textStyle: { color: '#1f2330' },
      extraCssText: 'box-shadow:0 8px 24px rgba(91,110,245,0.12);border-radius:10px;'
    },
    categoryAxis: {
      axisLine: { lineStyle: { color: BRAND.grid } },
      axisTick: { show: false },
      axisLabel: { color: BRAND.text2 },
      splitLine: { show: false }
    },
    valueAxis: {
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: BRAND.text2 },
      splitLine: { lineStyle: { color: BRAND.grid, type: 'dashed' } }
    },
    radar: {
      axisLine: { lineStyle: { color: BRAND.grid } },
      splitLine: { lineStyle: { color: BRAND.grid } },
      splitArea: { areaStyle: { color: ['rgba(91,110,245,0.03)', 'rgba(124,77,255,0.05)'] } }
    }
  })
  registered = true
}

// 是否启用减弱动画（无障碍）
export function prefersReducedMotion() {
  return window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

// 初始化图表：注册主题 + ResizeObserver 自适应
export function initChart(dom) {
  ensureTheme()
  const chart = echarts.init(dom, 'aeth', { renderer: 'canvas' })
  chart.__reduce = prefersReducedMotion()
  const ro = new ResizeObserver(() => chart.resize())
  ro.observe(dom)
  chart.__ro = ro
  return chart
}

// 应用配置（统一 reduced-motion 处理）
export function applyOption(chart, option) {
  if (!chart) return
  chart.setOption({ ...option, animation: !chart.__reduce })
}

// 销毁图表并断开观察器
export function disposeChart(chart) {
  if (chart && chart.__ro) chart.__ro.disconnect()
  if (chart) chart.dispose()
}

// 空状态图文（数据为空时在画布中央提示）
export function emptyGraphic(text = '暂无数据') {
  return {
    graphic: [
      {
        type: 'text',
        left: 'center',
        top: 'middle',
        style: { text, fill: '#9aa0b4', fontSize: 14 }
      }
    ]
  }
}

export default echarts
