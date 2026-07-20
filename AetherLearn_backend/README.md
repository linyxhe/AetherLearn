# AetherLearn 后端（Spring Boot 3.4.x）

MVP 第一波：登录鉴权 + 课程 CRUD（软删除）+ 统一文件上传。

## 运行步骤
1. 先执行数据库脚本 `../数据库脚本/AetherLearn_init.sql`（位于 `AetherLearn/数据库脚本/`）建库建表并写入测试数据（MySQL 8）。
2. 修改 `src/main/resources/application.yml` 中的 `spring.datasource` 账号密码（默认 root/root）。
3. 编译运行：
   ```bash
   mvn clean spring-boot:run
   ```
   或打包：`mvn clean package` 后 `java -jar target/AetherLearn_backend-1.0.0.jar`
4. 服务默认端口 8080；接口文档：http://localhost:8080/swagger-ui.html

## 默认账号（密码均为 123456，MD5=e10adc3949ba59abbe56e057f20f883e）
- admin / 123456（管理员，role=1）
- teacher01 / 123456（教师，role=2）
- student01 / 123456（学生，role=3）

## 主要接口
- POST `/api/auth/login` 登录（返回 token + 用户角色）
- GET  `/api/user/info` 、PUT `/api/user/update`
- POST `/api/course` 新建/编辑、DELETE `/api/course/{id}` 软删除、GET `/api/course/list`
- POST `/api/course/{id}/invite` 生成邀请码、POST `/api/course/join?code=xxx` 学生加入
- POST `/api/file/upload?bizType=course` 统一上传（avatar/course/knowledge/answer/export）

## 说明
- JWT 鉴权：除登录外所有请求需在 Header 携带 `Authorization: Bearer <token>`。
- 角色拦截：`@PreAuthorize` 区分 ADMIN(1)/TEACHER(2)/STUDENT(3)。
- 软删除：`course/knowledge_doc/assignment` 的 `is_deleted` 由 MyBatis-Plus 全局逻辑删除自动处理。
