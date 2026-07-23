-- =============================================================
-- AetherLearn 增量升级脚本：课程在线学习章节
-- 适用场景：已有 aetherlearn 数据库，不希望重新执行 AetherLearn_init.sql 清空数据。
-- 执行方式：mysql -u root -p aetherlearn < AetherLearn_upgrade_course_chapter.sql
-- =============================================================

CREATE TABLE IF NOT EXISTS course_chapter (
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

SET @has_resource_type := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course_chapter' AND COLUMN_NAME = 'resource_type'
);
SET @sql := IF(@has_resource_type = 0,
  'ALTER TABLE course_chapter ADD COLUMN resource_type VARCHAR(20) DEFAULT ''TEXT'' COMMENT ''资源类型：TEXT/VIDEO/PDF/WORD/FILE''',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_resource_url := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course_chapter' AND COLUMN_NAME = 'resource_url'
);
SET @sql := IF(@has_resource_url = 0,
  'ALTER TABLE course_chapter ADD COLUMN resource_url VARCHAR(500) DEFAULT NULL COMMENT ''章节资源文件路径（uploads/course）''',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO course_chapter (course_id, title, content, resource_type, resource_url, duration_minutes, sort_no, status, create_time, update_time)
SELECT 1, 'Java 与 JVM 入门', '理解 Java 的跨平台运行机制，掌握 JDK、JRE、JVM 的关系，并完成一次 Hello World 程序运行。', 'TEXT', NULL, 20, 1, 1, NOW(), NOW()
WHERE EXISTS (SELECT 1 FROM course WHERE id = 1)
  AND NOT EXISTS (SELECT 1 FROM course_chapter WHERE course_id = 1 AND sort_no = 1);

INSERT INTO course_chapter (course_id, title, content, resource_type, resource_url, duration_minutes, sort_no, status, create_time, update_time)
SELECT 1, '基本数据类型与变量', '学习 8 种基本数据类型、变量声明、类型转换和常量定义，为后续表达式与流程控制打基础。', 'PDF', 'course/java-basic.pdf', 25, 2, 1, NOW(), NOW()
WHERE EXISTS (SELECT 1 FROM course WHERE id = 1)
  AND NOT EXISTS (SELECT 1 FROM course_chapter WHERE course_id = 1 AND sort_no = 2);

CREATE TABLE IF NOT EXISTS course_notice (
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

INSERT INTO course_notice (course_id, title, content, notice_type, status, create_by, create_time, update_time)
SELECT 1, '课程开课说明', '本周课程正式开课，请同学先完成章节一学习，再进入作业模块。', 'NOTICE', 1, 2, NOW(), NOW()
WHERE EXISTS (SELECT 1 FROM course WHERE id = 1)
  AND NOT EXISTS (SELECT 1 FROM course_notice WHERE course_id = 1 AND title = '课程开课说明');

INSERT INTO course_notice (course_id, title, content, notice_type, status, create_by, create_time, update_time)
SELECT 1, '章节资料已更新', '第二章已上传 PDF 资料，建议先阅读资料再完成对应章节小测。', 'RESOURCE', 1, 2, NOW(), NOW()
WHERE EXISTS (SELECT 1 FROM course WHERE id = 1)
  AND NOT EXISTS (SELECT 1 FROM course_notice WHERE course_id = 1 AND title = '章节资料已更新');

CREATE TABLE IF NOT EXISTS chapter_note (
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

INSERT INTO chapter_note (course_id, chapter_id, student_id, title, content, favorite, create_time, update_time)
SELECT 1, 1, 3, 'JVM 入门笔记', 'JVM 是 Java 跨平台的关键，JDK 包含 JRE，JRE 包含 JVM。后续需要重点复习类加载机制。', 1, NOW(), NOW()
WHERE EXISTS (SELECT 1 FROM course_chapter WHERE id = 1)
  AND EXISTS (SELECT 1 FROM sys_user WHERE id = 3)
  AND NOT EXISTS (SELECT 1 FROM chapter_note WHERE student_id = 3 AND chapter_id = 1);

CREATE TABLE IF NOT EXISTS chapter_quiz (
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

CREATE TABLE IF NOT EXISTS chapter_quiz_record (
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

INSERT INTO chapter_quiz (course_id, chapter_id, type, content, options, answer, analysis, score, seq, create_time, update_time)
SELECT 1, 1, 1, 'Java 实现跨平台运行主要依赖哪个组件？', '["JVM","MySQL","Vue","Nginx"]', 'A', 'JVM 屏蔽了不同操作系统差异，是 Java 跨平台运行的核心。', 5, 1, NOW(), NOW()
WHERE EXISTS (SELECT 1 FROM course_chapter WHERE id = 1)
  AND NOT EXISTS (SELECT 1 FROM chapter_quiz WHERE chapter_id = 1 AND seq = 1);

INSERT INTO chapter_quiz (course_id, chapter_id, type, content, options, answer, analysis, score, seq, create_time, update_time)
SELECT 1, 1, 3, 'JDK 包含 JRE，JRE 又包含 JVM。', '[]', '正确', 'JDK 面向开发，JRE 面向运行，JVM 是运行 Java 字节码的虚拟机。', 5, 2, NOW(), NOW()
WHERE EXISTS (SELECT 1 FROM course_chapter WHERE id = 1)
  AND NOT EXISTS (SELECT 1 FROM chapter_quiz WHERE chapter_id = 1 AND seq = 2);

INSERT INTO chapter_quiz (course_id, chapter_id, type, content, options, answer, analysis, score, seq, create_time, update_time)
SELECT 1, 2, 4, 'Java 中表示整数的常用基本类型是 ______。', '[]', 'int', 'int 是 Java 中常用的 32 位整数类型。', 5, 1, NOW(), NOW()
WHERE EXISTS (SELECT 1 FROM course_chapter WHERE id = 2)
  AND NOT EXISTS (SELECT 1 FROM chapter_quiz WHERE chapter_id = 2 AND seq = 1);

CREATE TABLE IF NOT EXISTS learning_todo (
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

INSERT INTO learning_todo (user_id, course_id, title, content, todo_type, priority, due_time, status, create_time, update_time)
SELECT 3, 1, '复习 JVM 与类加载机制', '结合章节一与错题本，整理一页复习卡片。', 'REVIEW', 3, '2026-07-08 22:00:00', 0, NOW(), NOW()
WHERE EXISTS (SELECT 1 FROM sys_user WHERE id = 3)
  AND NOT EXISTS (SELECT 1 FROM learning_todo WHERE user_id = 3 AND title = '复习 JVM 与类加载机制');

INSERT INTO learning_todo (user_id, course_id, title, content, todo_type, priority, due_time, status, create_time, update_time)
SELECT 3, 1, '完成 Java 第一次作业', '在截止前提交作业并检查错题。', 'HOMEWORK', 2, '2026-07-09 20:00:00', 0, NOW(), NOW()
WHERE EXISTS (SELECT 1 FROM sys_user WHERE id = 3)
  AND NOT EXISTS (SELECT 1 FROM learning_todo WHERE user_id = 3 AND title = '完成 Java 第一次作业');
