# AetherLearn 前端（Vue 3 + Vite + Element Plus）

MVP 第一波：登录页（分栏）+ 路由权限守卫 + Pinia 状态 + 课程管理 + 文件上传组件。

## 运行步骤
1. 安装依赖：
   ```bash
   npm install
   ```
2. 启动开发服务器（默认 5173，已配置 `/api` 代理到后端 8080）：
   ```bash
   npm run dev
   ```
3. 浏览器打开 http://localhost:5173 ，使用 admin / teacher01 / student01（密码 123456）登录。
   - 管理员/教师 → 数据看板 + 课程管理
   - 学生 → 学习中心

## 目录
- `src/router` 路由与权限守卫（按 token + 角色动态加载菜单）
- `src/store/user.js` Pinia 用户状态（token / 角色 / 昵称）
- `src/utils/request.js` Axios 封装（注入 Bearer、401 跳登录）
- `src/views/Login.vue` 左右分栏登录页
- `src/layout/MainLayout.vue` 可折叠菜单 + 顶栏（按角色动态菜单）
- `src/views/Course.vue` 课程管理（表格 + 新建/编辑对话框 + 封面上传）
- `src/components/UploadFile.vue` 拖拽上传组件（bizType 分桶）

## 视觉规范
科技蓝紫主色 `#5b6ef5 / #7c4dff`、浅灰背景、卡片化、圆角、柔和阴影（见 `src/styles/main.css`）。
