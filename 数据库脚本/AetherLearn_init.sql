-- =============================================================
-- AetherLearn 智能教学辅助与学习分析平台 数据库初始化脚本
-- 数据库：MySQL 8.0   端口：3306   字符集：utf8mb4
-- 说明：本脚本包含建库、建表（含字段注释）、索引与测试数据。
--       初始口令统一为 123456，MD5 = e10adc3949ba59abbe56e057f20f883e
--       执行方式：mysql -u root -p < AetherLearn_init.sql
-- v2.0 Schema 变更：course/knowledge_doc/assignment 增加 is_deleted（软删除）；
--       新增 course.invite_code、knowledge_chunk.embedding（预留向量）、
--       student_answer.review_status（复核状态）；并补充 qa_record / student_answer 联合索引。
-- =============================================================

-- 1. 创建数据库
DROP DATABASE IF EXISTS aetherlearn;
CREATE DATABASE aetherlearn
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;
USE aetherlearn;

-- =============================================================
-- 2. 系统用户表
-- =============================================================
CREATE TABLE sys_user (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  username     VARCHAR(50)  NOT NULL COMMENT '登录账号',
  password     VARCHAR(100) NOT NULL COMMENT '密码（MD5加密存储）',
  real_name    VARCHAR(50)  DEFAULT NULL COMMENT '真实姓名',
  role         TINYINT      NOT NULL COMMENT '角色：1-管理员 2-教师 3-学生',
  avatar       VARCHAR(255) DEFAULT NULL COMMENT '头像路径（存于 uploads/avatar）',
  email        VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
  phone        VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
  status       TINYINT      DEFAULT 1 COMMENT '状态：1-正常 0-禁用',
  create_time  DATETIME     DEFAULT NULL COMMENT '创建时间',
  update_time  DATETIME     DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- =============================================================
-- 3. 课程表
-- =============================================================
CREATE TABLE course (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '课程ID',
  course_name  VARCHAR(100) NOT NULL COMMENT '课程名称',
  course_code  VARCHAR(50)  DEFAULT NULL COMMENT '课程编号',
  teacher_id   BIGINT       DEFAULT NULL COMMENT '授课教师ID（关联 sys_user.id）',
  description  VARCHAR(500) DEFAULT NULL COMMENT '课程简介',
  cover        VARCHAR(255) DEFAULT NULL COMMENT '封面图路径（uploads/course）',
  status       TINYINT      DEFAULT 1 COMMENT '状态：1-开课 0-下架',
  is_deleted   TINYINT      DEFAULT 0 COMMENT '软删除标志：0-未删 1-已删（v2.0 新增）',
  invite_code  VARCHAR(20)  DEFAULT NULL COMMENT '加入邀请码（6位随机串，v2.0 新增）',
  create_time  DATETIME     DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程表';

-- =============================================================
-- 4. 选课关系表
-- =============================================================
CREATE TABLE course_student (
  id          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '关系ID',
  course_id   BIGINT   NOT NULL COMMENT '课程ID',
  student_id  BIGINT   NOT NULL COMMENT '学生ID',
  create_time DATETIME DEFAULT NULL COMMENT '选课时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_course_student (course_id, student_id),
  KEY idx_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程-学生选课关系表';

-- =============================================================
-- 5. 课程在线学习章节表
-- =============================================================
CREATE TABLE course_chapter (
  id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '章节ID',
  course_id        BIGINT       NOT NULL COMMENT '所属课程ID',
  title            VARCHAR(200) NOT NULL COMMENT '章节标题',
  content          TEXT         NOT NULL COMMENT '章节学习内容',
  resource_type    VARCHAR(20)  DEFAULT 'TEXT' COMMENT '资源类型：TEXT/VIDEO/PDF/WORD/FILE',
  resource_url     VARCHAR(500) DEFAULT NULL COMMENT '章节资源文件路径（uploads/course）',
  duration_minutes INT          DEFAULT 15 COMMENT '预计学习时长（分钟）',
  sort_no          INT          DEFAULT 1 COMMENT '排序序号',
  status           TINYINT      DEFAULT 1 COMMENT '状态：1-发布 0-草稿',
  create_time      DATETIME     DEFAULT NULL COMMENT '创建时间',
  update_time      DATETIME     DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_course (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程在线学习章节表';

-- =============================================================
-- 6. 知识库文档表
-- =============================================================
CREATE TABLE knowledge_doc (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '文档ID',
  course_id   BIGINT       NOT NULL COMMENT '所属课程ID',
  title       VARCHAR(200) NOT NULL COMMENT '文档标题',
  file_path   VARCHAR(500) NOT NULL COMMENT '原文件存储路径（uploads/knowledge）',
  file_type   VARCHAR(20)  DEFAULT NULL COMMENT '文件类型：pdf/docx/md/txt/img',
  file_size   BIGINT       DEFAULT 0 COMMENT '文件大小（字节）',
  chunk_count INT          DEFAULT 0 COMMENT '切片数量',
  status      TINYINT      DEFAULT 1 COMMENT '状态：1-已解析 0-解析中 2-失败',
  is_deleted  TINYINT      DEFAULT 0 COMMENT '软删除标志：0-未删 1-已删（v2.0 新增）',
  upload_by   BIGINT       DEFAULT NULL COMMENT '上传人ID',
  create_time DATETIME     DEFAULT NULL COMMENT '上传时间',
  PRIMARY KEY (id),
  KEY idx_course (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程知识库文档表';

-- =============================================================
-- 7. 知识切片表（RAG 检索语料）
-- =============================================================
CREATE TABLE knowledge_chunk (
  id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '切片ID',
  doc_id     BIGINT      NOT NULL COMMENT '所属文档ID',
  course_id  BIGINT      NOT NULL COMMENT '所属课程ID',
  seq        INT         DEFAULT 0 COMMENT '切片序号',
  content    TEXT        NOT NULL COMMENT '切片文本内容',
  char_len   INT         DEFAULT 0 COMMENT '文本长度',
  embedding  TEXT        DEFAULT NULL COMMENT '向量值（预留，当前可 NULL；v2.0 新增）',
  create_time DATETIME   DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_doc (doc_id),
  KEY idx_course (course_id),
  FULLTEXT KEY ft_content (content) COMMENT '全文索引，用于关键词检索'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库切片表（RAG检索语料）';

-- =============================================================
-- 8. 问答记录表
-- =============================================================
CREATE TABLE qa_record (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  user_id       BIGINT       NOT NULL COMMENT '提问用户ID',
  course_id     BIGINT       NOT NULL COMMENT '所属课程ID',
  question      VARCHAR(500) NOT NULL COMMENT '用户提问',
  answer        TEXT         DEFAULT NULL COMMENT '系统回答',
  source_chunks VARCHAR(200) DEFAULT NULL COMMENT '引用的切片ID列表',
  sources_json  TEXT         DEFAULT NULL COMMENT '来源切片详情JSON（M4）',
  use_llm       TINYINT      DEFAULT 0 COMMENT '是否使用大模型：1-是 0-否',
  cost_ms       INT          DEFAULT 0 COMMENT '回答耗时（毫秒）',
  create_time   DATETIME     DEFAULT NULL COMMENT '提问时间',
  PRIMARY KEY (id),
  KEY idx_user (user_id),
  KEY idx_course (course_id),
  KEY idx_user_course_time (user_id, course_id, create_time) COMMENT 'v2.0 联合索引：按用户/课程/时间查询问答记录'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能问答记录表';

-- =============================================================
-- 9. 作业/测验表
-- =============================================================
CREATE TABLE assignment (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '作业ID',
  course_id    BIGINT       NOT NULL COMMENT '所属课程ID',
  title        VARCHAR(200) NOT NULL COMMENT '作业/测验标题',
  type         TINYINT      DEFAULT 1 COMMENT '类型：1-作业 2-测验',
  description  VARCHAR(500) DEFAULT NULL COMMENT '说明',
  start_time   DATETIME     DEFAULT NULL COMMENT '开始时间',
  end_time     DATETIME     DEFAULT NULL COMMENT '截止时间',
  total_score  INT          DEFAULT 100 COMMENT '总分',
  status       TINYINT      DEFAULT 1 COMMENT '状态：1-进行中 0-已结束',
  is_deleted   TINYINT      DEFAULT 0 COMMENT '软删除标志：0-未删 1-已删（v2.0 新增）',
  create_by    BIGINT       DEFAULT NULL COMMENT '创建人ID',
  create_time  DATETIME     DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_course (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作业/测验表';

-- =============================================================
-- 10. 题目表
-- =============================================================
CREATE TABLE question (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '题目ID',
  assignment_id BIGINT       NOT NULL COMMENT '所属作业ID',
  type          TINYINT      NOT NULL COMMENT '题型：1-单选 2-多选 3-判断 4-填空 5-简答',
  content       TEXT         NOT NULL COMMENT '题干',
  options       TEXT         DEFAULT NULL COMMENT '选项（JSON数组，如["A","B","C","D"]）',
  answer        VARCHAR(500) DEFAULT NULL COMMENT '标准答案',
  analysis      VARCHAR(500) DEFAULT NULL COMMENT '解析/参考答案',
  score         INT          DEFAULT 0 COMMENT '分值',
  knowledge_point VARCHAR(100) DEFAULT NULL COMMENT '关联知识点',
  seq           INT          DEFAULT 0 COMMENT '题目序号',
  PRIMARY KEY (id),
  KEY idx_assignment (assignment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目表';

-- =============================================================
-- 11. 学生作答表
-- =============================================================
CREATE TABLE student_answer (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '作答ID',
  assignment_id BIGINT      NOT NULL COMMENT '作业ID',
  question_id  BIGINT       NOT NULL COMMENT '题目ID',
  student_id   BIGINT       NOT NULL COMMENT '学生ID',
  answer       TEXT         DEFAULT NULL COMMENT '学生作答内容',
  score        INT          DEFAULT 0 COMMENT '本题得分',
  is_correct   TINYINT      DEFAULT NULL COMMENT '是否正确：1-对 0-错（客观题）',
  feedback     VARCHAR(500) DEFAULT NULL COMMENT '批改反馈',
  image_url    VARCHAR(255) DEFAULT NULL COMMENT '作答图片路径（uploads/answer/，v2.0 新增）',
  grade_type   TINYINT      DEFAULT 1 COMMENT '批改方式：1-自动 2-人工复核',
  review_status TINYINT     DEFAULT 0 COMMENT '复核状态：0-待复核 1-已复核无异议 2-已修改分数（v2.0 新增）',
  create_time  DATETIME     DEFAULT NULL COMMENT '提交时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_stu_q (assignment_id, question_id, student_id),
  KEY idx_student (student_id),
  KEY idx_stu_assignment (student_id, assignment_id) COMMENT 'v2.0 联合索引：按学生/作业查询作答'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生作答表';

-- =============================================================
-- 12. 学习行为记录表（学情统计源）
-- =============================================================
CREATE TABLE learning_record (
  id           BIGINT   NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  student_id   BIGINT   NOT NULL COMMENT '学生ID',
  course_id    BIGINT   NOT NULL COMMENT '课程ID',
  action_type  VARCHAR(20) NOT NULL COMMENT '行为类型：登录/问答/作业/浏览/章节学习',
  target_id    BIGINT   DEFAULT NULL COMMENT '关联对象ID（作业ID/问答ID等）',
  duration     INT      DEFAULT 0 COMMENT '时长（秒）',
  score        INT      DEFAULT NULL COMMENT '关联成绩（作业得分等）',
  create_time  DATETIME DEFAULT NULL COMMENT '发生时间',
  PRIMARY KEY (id),
  KEY idx_student (student_id),
  KEY idx_course (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习行为记录表';

-- =============================================================
-- 13. 学习建议表
-- =============================================================
CREATE TABLE learning_suggestion (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '建议ID',
  student_id  BIGINT       NOT NULL COMMENT '学生ID',
  course_id   BIGINT       NOT NULL COMMENT '课程ID',
  content     VARCHAR(500) NOT NULL COMMENT '建议内容',
  type        VARCHAR(50)  DEFAULT NULL COMMENT '建议类型：知识点巩固/学习路径/预警',
  ref_doc_id  BIGINT       DEFAULT NULL COMMENT '关联知识库文档ID',
  status      TINYINT      DEFAULT 1 COMMENT '状态：1-未读 0-已读',
  create_time DATETIME     DEFAULT NULL COMMENT '生成时间',
  PRIMARY KEY (id),
  KEY idx_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='个性化学习建议表';

-- =============================================================
-- 14. 课程公告表
-- =============================================================
CREATE TABLE course_notice (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '公告ID',
  course_id    BIGINT       NOT NULL COMMENT '所属课程ID',
  title        VARCHAR(200) NOT NULL COMMENT '公告标题',
  content      TEXT         NOT NULL COMMENT '公告内容',
  notice_type  VARCHAR(20)  DEFAULT 'NOTICE' COMMENT '公告类型：NOTICE/HOMEWORK/RESOURCE',
  status       TINYINT      DEFAULT 1 COMMENT '状态：1-已发布 0-草稿',
  create_by    BIGINT       DEFAULT NULL COMMENT '发布人ID',
  create_time  DATETIME     DEFAULT NULL COMMENT '创建时间',
  update_time  DATETIME     DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_course (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程公告表';

-- =============================================================
-- 15. 章节笔记表
-- =============================================================
CREATE TABLE chapter_note (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '笔记ID',
  course_id    BIGINT       NOT NULL COMMENT '课程ID',
  chapter_id   BIGINT       NOT NULL COMMENT '章节ID',
  student_id   BIGINT       NOT NULL COMMENT '学生ID',
  title        VARCHAR(200) DEFAULT NULL COMMENT '笔记标题',
  content      TEXT         NOT NULL COMMENT '笔记内容',
  favorite     TINYINT      DEFAULT 0 COMMENT '是否收藏：1-收藏 0-普通',
  create_time  DATETIME     DEFAULT NULL COMMENT '创建时间',
  update_time  DATETIME     DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_student_chapter (student_id, chapter_id),
  KEY idx_course_student (course_id, student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='章节学习笔记表';

-- =============================================================
-- 16. 章节小测题目表
-- =============================================================
CREATE TABLE chapter_quiz (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '题目ID',
  course_id   BIGINT       NOT NULL COMMENT '课程ID',
  chapter_id  BIGINT       NOT NULL COMMENT '章节ID',
  type        TINYINT      NOT NULL COMMENT '题型：1单选 2多选 3判断 4填空',
  content     VARCHAR(800) NOT NULL COMMENT '题干',
  options     TEXT         DEFAULT NULL COMMENT '选项JSON',
  answer      VARCHAR(500) NOT NULL COMMENT '标准答案',
  analysis    VARCHAR(1000) DEFAULT NULL COMMENT '解析',
  score       INT          DEFAULT 5 COMMENT '分值',
  seq         INT          DEFAULT 1 COMMENT '序号',
  create_time DATETIME     DEFAULT NULL COMMENT '创建时间',
  update_time DATETIME     DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_chapter (chapter_id),
  KEY idx_course (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='章节小测题目表';

-- =============================================================
-- 17. 章节小测作答记录表（错题沉淀）
-- =============================================================
CREATE TABLE chapter_quiz_record (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  course_id   BIGINT       NOT NULL COMMENT '课程ID',
  chapter_id  BIGINT       NOT NULL COMMENT '章节ID',
  quiz_id     BIGINT       NOT NULL COMMENT '题目ID',
  student_id  BIGINT       NOT NULL COMMENT '学生ID',
  answer      VARCHAR(1000) DEFAULT NULL COMMENT '学生作答',
  score       INT          DEFAULT 0 COMMENT '得分',
  is_correct  TINYINT      DEFAULT 0 COMMENT '是否正确：1对 0错',
  feedback    VARCHAR(1000) DEFAULT NULL COMMENT '反馈',
  create_time DATETIME     DEFAULT NULL COMMENT '提交时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_stu_quiz (student_id, quiz_id),
  KEY idx_student_chapter (student_id, chapter_id),
  KEY idx_wrong (student_id, is_correct)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='章节小测作答记录表';

-- =============================================================
-- 18. 学习计划/待办表
-- =============================================================
CREATE TABLE learning_todo (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '待办ID',
  user_id     BIGINT       NOT NULL COMMENT '学生ID',
  course_id   BIGINT       DEFAULT NULL COMMENT '课程ID',
  title       VARCHAR(200) NOT NULL COMMENT '待办标题',
  content     VARCHAR(1000) DEFAULT NULL COMMENT '待办内容',
  todo_type   VARCHAR(20)  DEFAULT 'PLAN' COMMENT '类型：PLAN/REVIEW/HOMEWORK/QUIZ',
  priority    TINYINT      DEFAULT 2 COMMENT '优先级：1低 2中 3高',
  due_time    DATETIME     DEFAULT NULL COMMENT '截止时间',
  status      TINYINT      DEFAULT 0 COMMENT '状态：0未完成 1已完成',
  create_time DATETIME     DEFAULT NULL COMMENT '创建时间',
  update_time DATETIME     DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_user_status (user_id, status),
  KEY idx_user_course (user_id, course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习计划/待办表';

-- =============================================================
-- 19. 系统配置表
-- =============================================================
CREATE TABLE sys_config (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  config_key   VARCHAR(50)  NOT NULL COMMENT '配置键',
  config_value VARCHAR(500) DEFAULT NULL COMMENT '配置值',
  remark       VARCHAR(200) DEFAULT NULL COMMENT '说明',
  PRIMARY KEY (id),
  UNIQUE KEY uk_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

-- =============================================================
-- 20. 测试数据
-- 密码统一为 123456 → MD5 = e10adc3949ba59abbe56e057f20f883e
-- =============================================================

-- 14.1 用户（管理员 / 教师 / 学生）
INSERT INTO sys_user (id, username, password, real_name, role, email, phone, status, create_time, update_time) VALUES
(1, 'admin',     'e10adc3949ba59abbe56e057f20f883e', '系统管理员', 1, 'admin@aether.edu',  '13800000001', 1, '2026-07-01 09:00:00', '2026-07-01 09:00:00'),
(2, 'teacher01', 'e10adc3949ba59abbe56e057f20f883e', '张老师',     2, 'zhang@aether.edu',  '13800000002', 1, '2026-07-01 09:00:00', '2026-07-01 09:00:00'),
(3, 'student01', 'e10adc3949ba59abbe56e057f20f883e', '李同学',     3, 'li@aether.edu',     '13800000003', 1, '2026-07-01 09:00:00', '2026-07-01 09:00:00');

-- 14.2 课程
INSERT INTO course (id, course_name, course_code, teacher_id, description, status, create_time) VALUES
(1, 'Java 程序设计', 'CS201', 2, '面向零基础学生的 Java 语言入门与面向对象编程课程。', 1, '2026-07-01 10:00:00'),
(2, '数据结构',     'CS202', 2, '讲解线性表、树、图等常用数据结构及其算法。',         1, '2026-07-01 10:05:00');

-- 14.3 选课关系（李同学 选修两门课）
INSERT INTO course_student (id, course_id, student_id, create_time) VALUES
(1, 1, 3, '2026-07-01 10:10:00'),
(2, 2, 3, '2026-07-01 10:10:00');

-- 14.4 在线学习章节
INSERT INTO course_chapter (id, course_id, title, content, resource_type, resource_url, duration_minutes, sort_no, status, create_time, update_time) VALUES
(1, 1, 'Java 与 JVM 入门', '理解 Java 的跨平台运行机制，掌握 JDK、JRE、JVM 的关系，并完成一次 Hello World 程序运行。', 'TEXT', NULL, 20, 1, 1, '2026-07-01 10:20:00', '2026-07-01 10:20:00'),
(2, 1, '基本数据类型与变量', '学习 8 种基本数据类型、变量声明、类型转换和常量定义，为后续表达式与流程控制打基础。', 'PDF', 'course/java-basic.pdf', 25, 2, 1, '2026-07-01 10:25:00', '2026-07-01 10:25:00'),
(3, 2, '线性表概念', '认识顺序表与链表的存储方式，比较插入、删除、查找操作的时间复杂度。', 'TEXT', NULL, 30, 1, 1, '2026-07-01 10:30:00', '2026-07-01 10:30:00');

-- 14.5 课程公告
INSERT INTO course_notice (id, course_id, title, content, notice_type, status, create_by, create_time, update_time) VALUES
(1, 1, '课程开课说明', '本周课程正式开课，请同学先完成章节一学习，再进入作业模块。', 'NOTICE', 1, 2, '2026-07-01 10:35:00', '2026-07-01 10:35:00'),
(2, 1, '章节资料已更新', '第二章已上传 PDF 资料，建议先阅读资料再完成对应章节小测。', 'RESOURCE', 1, 2, '2026-07-01 10:40:00', '2026-07-01 10:40:00'),
(3, 2, '作业提醒', '数据结构第一次作业将在本周五截止，请按时提交。', 'HOMEWORK', 1, 2, '2026-07-01 10:45:00', '2026-07-01 10:45:00');

-- 14.6 章节笔记
INSERT INTO chapter_note (id, course_id, chapter_id, student_id, title, content, favorite, create_time, update_time) VALUES
(1, 1, 1, 3, 'JVM 入门笔记', 'JVM 是 Java 跨平台的关键，JDK 包含 JRE，JRE 包含 JVM。后续需要重点复习类加载机制。', 1, '2026-07-01 11:10:00', '2026-07-01 11:10:00');

-- 14.7 章节小测
INSERT INTO chapter_quiz (id, course_id, chapter_id, type, content, options, answer, analysis, score, seq, create_time, update_time) VALUES
(1, 1, 1, 1, 'Java 实现跨平台运行主要依赖哪个组件？', '["JVM","MySQL","Vue","Nginx"]', 'A', 'JVM 屏蔽了不同操作系统差异，是 Java 跨平台运行的核心。', 5, 1, '2026-07-01 11:15:00', '2026-07-01 11:15:00'),
(2, 1, 1, 3, 'JDK 包含 JRE，JRE 又包含 JVM。', '[]', '正确', 'JDK 面向开发，JRE 面向运行，JVM 是运行 Java 字节码的虚拟机。', 5, 2, '2026-07-01 11:16:00', '2026-07-01 11:16:00'),
(3, 1, 2, 4, 'Java 中表示整数的常用基本类型是 ______。', '[]', 'int', 'int 是 Java 中常用的 32 位整数类型。', 5, 1, '2026-07-01 11:17:00', '2026-07-01 11:17:00');

INSERT INTO chapter_quiz_record (id, course_id, chapter_id, quiz_id, student_id, answer, score, is_correct, feedback, create_time) VALUES
(1, 1, 1, 1, 3, 'A', 5, 1, '回答正确。', '2026-07-03 11:05:00'),
(2, 1, 1, 2, 3, '错误', 0, 0, '回答错误。JDK 面向开发，JRE 面向运行，JVM 是运行 Java 字节码的虚拟机。', '2026-07-03 11:06:00');

-- 14.8 学习待办
INSERT INTO learning_todo (id, user_id, course_id, title, content, todo_type, priority, due_time, status, create_time, update_time) VALUES
(1, 3, 1, '复习 JVM 与类加载机制', '结合章节一与错题本，整理一页复习卡片。', 'REVIEW', 3, '2026-07-08 22:00:00', 0, '2026-07-03 12:00:00', '2026-07-03 12:00:00'),
(2, 3, 1, '完成 Java 第一次作业', '在截止前提交作业并检查错题。', 'HOMEWORK', 2, '2026-07-09 20:00:00', 0, '2026-07-03 12:10:00', '2026-07-03 12:10:00');

-- 14.9 知识库文档
INSERT INTO knowledge_doc (id, course_id, title, file_path, file_type, file_size, chunk_count, status, upload_by, create_time) VALUES
(1, 1, 'Java 基础讲义',   'knowledge/java_basic.pdf', 'pdf', 204800, 2, 1, 2, '2026-07-01 11:00:00'),
(2, 2, '数据结构概述',   'knowledge/ds_intro.pdf',   'pdf', 153600, 1, 1, 2, '2026-07-01 11:05:00');

-- 14.10 知识切片（RAG 检索语料）
INSERT INTO knowledge_chunk (id, doc_id, course_id, seq, content, char_len, create_time) VALUES
(1, 1, 1, 1, 'Java 是一种面向对象的编程语言，具有跨平台特性，通过 JVM 实现“一次编写，到处运行”。', 45, '2026-07-01 11:00:10'),
(2, 1, 1, 2, 'Java 的基本数据类型包括：byte、short、int、long、float、double、char、boolean。', 50, '2026-07-01 11:00:20'),
(3, 2, 2, 1, '数据结构研究数据的逻辑结构（集合、线性、树、图）与存储结构（顺序、链式）。', 40, '2026-07-01 11:05:10');

-- 14.11 问答记录
INSERT INTO qa_record (id, user_id, course_id, question, answer, source_chunks, use_llm, cost_ms, create_time) VALUES
(1, 3, 1, 'Java 有哪些基本数据类型？', 'Java 的基本数据类型包括：byte、short、int、long、float、double、char、boolean。', '2', 0, 15, '2026-07-02 14:30:00');

-- 14.12 作业
INSERT INTO assignment (id, course_id, title, type, description, start_time, end_time, total_score, status, create_by, create_time) VALUES
(1, 1, 'Java 第一次作业', 1, '巩固 Java 基础与面向对象概念。', '2026-07-02 00:00:00', '2026-07-09 23:59:59', 100, 1, 2, '2026-07-02 09:00:00');

-- 14.13 题目（覆盖五种题型）
INSERT INTO question (id, assignment_id, type, content, options, answer, analysis, score, knowledge_point, seq) VALUES
(1, 1, 1, '下列哪个是 Java 的基本数据类型？', '["int","String","Array","List"]', 'A', 'int 是基本数据类型，其余为引用类型。', 20, 'Java基础', 1),
(2, 1, 2, '以下哪些属于面向对象的三大特性？', '["封装","继承","多态","编译"]', 'ABC', '面向对象三大特性为封装、继承、多态。', 20, '面向对象', 2),
(3, 1, 3, 'Java 是一种纯面向过程语言。', NULL, '错误', 'Java 是面向对象语言。', 20, 'Java基础', 3),
(4, 1, 4, 'Java 通过 ______ 实现跨平台运行。', NULL, 'JVM', 'JVM（Java 虚拟机）屏蔽了底层操作系统差异。', 20, 'Java基础', 4),
(5, 1, 5, '请简述面向对象的三大特性。', NULL, '封装、继承、多态', '封装隐藏实现细节；继承实现复用；多态提升扩展性。', 20, '面向对象', 5);

-- 14.14 学生作答（李同学 基本答对，简答题教师复核给 18 分）
-- review_status：0-待复核（客观题由系统初判，待教师确认）/ 2-已修改分数（简答题教师手评分值）
INSERT INTO student_answer (id, assignment_id, question_id, student_id, answer, score, is_correct, feedback, grade_type, review_status, create_time) VALUES
(1, 1, 1, 3, 'A',     20, 1, '回答正确。',           1, 0, '2026-07-03 10:00:00'),
(2, 1, 2, 3, 'ABC',   20, 1, '回答正确。',           1, 0, '2026-07-03 10:01:00'),
(3, 1, 3, 3, '错误',  20, 1, '回答正确。',           1, 0, '2026-07-03 10:02:00'),
(4, 1, 4, 3, 'JVM',   20, 1, '回答正确。',           1, 0, '2026-07-03 10:03:00'),
(5, 1, 5, 3, '封装、继承、多态。', 18, 0, '要点基本正确，建议补充各自含义。', 2, 2, '2026-07-03 10:05:00');

-- 14.15 学习行为记录
INSERT INTO learning_record (id, student_id, course_id, action_type, target_id, duration, score, create_time) VALUES
(1, 3, 1, '登录', NULL, 60,   NULL, '2026-07-03 09:50:00'),
(2, 3, 1, '作业', 1,    300,  98,   '2026-07-03 10:05:00'),
(3, 3, 1, '问答', 1,    30,   NULL, '2026-07-02 14:30:00'),
(4, 3, 2, '登录', NULL, 45,   NULL, '2026-07-03 09:55:00'),
(5, 3, 1, '章节学习', 1, 1200, NULL, '2026-07-03 11:00:00');

-- 14.16 学习建议
INSERT INTO learning_suggestion (id, student_id, course_id, content, type, ref_doc_id, status, create_time) VALUES
(1, 3, 1, '你在“面向对象”知识点掌握较好，建议进一步练习“多态”相关的编程题以加深理解。', '知识点巩固', 1, 1, '2026-07-03 10:10:00');

-- 14.17 系统配置（AI 模块默认关闭，无外部 Key 也可运行）
INSERT INTO sys_config (id, config_key, config_value, remark) VALUES
(1, 'ai.enabled', 'false', '是否启用大模型（true/false），关闭时走本地检索降级'),
(2, 'ai.api.url', '',      '大模型接口地址（OpenAI 兼容 / Ollama）'),
(3, 'ai.api.key', '',      '大模型 API Key');

-- =============================================================
-- 初始化完成
-- =============================================================
SELECT 'AetherLearn 数据库初始化完成：库名 aetherlearn，13 张表，测试数据已写入。' AS result;
