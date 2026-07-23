// AetherLearn 图表主题 v3 — "温润校园"
// 统一蓝绿柔和调色板 + 扁平 2D 风格。
// 与 main.css v3 token 系统对齐，低饱和、无渐变立体效果。

import * as echarts from 'echarts'

// 蓝绿主色与图表辅助色（与 main.css token 对齐）
export const BRAND = {
  primary: '#42B5BB',
  accent: '#87D8C9',
  deep: '#38a4a9',
  soft: '#a8e6cf',
  amber: '#FFB86C',
  green: '#67C23A',
  red: '#F56C6C',
  text2: '#666666',
  text3: '#999999',
  grid: '#d9edeb',
  surface: '#f2fbfa'
}

// 多序列调色板（6 色，低饱和柔和）
export const PALETTE = [
  BRAND.primary,
  BRAND.accent,
  BRAND.deep,
  BRAND.green,
  BRAND.amber,
  BRAND.red
]

// 竖向蓝绿渐变（面积图/折线填充）
export function areaGradient() {
  return new echarts.graphic.LinearGradient(0, 0, 0, 1, [
    { offset: 0, color: 'rgba(66, 181, 187, 0.30)' },
    { offset: 0.7, color: 'rgba(135, 216, 201, 0.06)' },
    { offset: 1, color: 'rgba(135, 216, 201, 0.01)' }
  ])
}

// 横向蓝绿渐变（柱状）
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
    textStyle: {
      color: BRAND.text2,
      fontFamily: '"PingFang SC", "Microsoft YaHei", sans-serif'
    },
    title: {
      textStyle: {
        color: '#333333',
        fontWeight: 500,
        fontFamily: '"PingFang SC", "Microsoft YaHei", sans-serif'
      }
    },
    legend: {
      textStyle: {
        color: BRAND.text2,
        fontSize: 12
      },
      itemGap: 16,
      icon: 'roundRect',
      itemWidth: 14,
      itemHeight: 8
    },
    tooltip: {
      backgroundColor: 'rgba(255, 255, 255, 0.96)',
      borderColor: BRAND.grid,
      textStyle: {
        color: '#333333',
        fontSize: 13
      },
      extraCssText: 'box-shadow: 0 4px 16px rgba(66, 181, 187, 0.12); border-radius: 12px; padding: 10px 14px;'
    },
    categoryAxis: {
      axisLine: { lineStyle: { color: BRAND.grid } },
      axisTick: { show: false },
      axisLabel: {
        color: BRAND.text2,
        fontSize: 12,
        margin: 12
      },
      splitLine: { show: false }
    },
    valueAxis: {
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: {
        color: BRAND.text3,
        fontSize: 12
      },
      splitLine: {
        lineStyle: {
          color: '#eeeeee',
          type: 'dashed'
        }
      }
    },
    radar: {
      axisLine: { lineStyle: { color: BRAND.grid } },
      splitLine: { lineStyle: { color: BRAND.grid } },
      splitArea: {
        areaStyle: {
          color: ['rgba(66, 181, 187, 0.02)', 'rgba(135, 216, 201, 0.04)']
        }
      },
      axisName: {
        color: BRAND.text2,
        fontSize: 12
      }
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
        style: {
          text,
          fill: BRAND.text3,
          fontSize: 14,
          fontFamily: '"PingFang SC", "Microsoft YaHei", sans-serif'
        }
      }
    ]
  }
}

export default echarts
