/**
 * 为后端返回的站内资源路径补齐当前应用前缀。
 *
 * @param {string} url 后端返回的资源地址
 * @returns {string} 浏览器可直接访问的地址
 */
export function resolveApplicationUrl(url) {
  if (!url || /^(https?:|data:|blob:)/i.test(url)) return url || ''

  const basePath = import.meta.env.BASE_URL.replace(/\/$/, '')
  return url.startsWith('/') ? `${basePath}${url}` : `${basePath}/${url}`
}
