package com.aetherlearn.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 寒暄/元问题识别测试：决定是否跳过知识库检索。
 */
class QaServiceImplSmallTalkTest {

    @Test
    void detectsGreetingsMetaQuestionsAndThanks() {
        assertTrue(QaServiceImpl.isSmallTalk("你好呀"));
        assertTrue(QaServiceImpl.isSmallTalk("您好！"));
        assertTrue(QaServiceImpl.isSmallTalk("hello"));
        assertTrue(QaServiceImpl.isSmallTalk("Hi~"));
        assertTrue(QaServiceImpl.isSmallTalk("在吗？"));
        assertTrue(QaServiceImpl.isSmallTalk("谢谢啦"));
        assertTrue(QaServiceImpl.isSmallTalk("你是谁"));
        assertTrue(QaServiceImpl.isSmallTalk("你能做什么？"));
        assertTrue(QaServiceImpl.isSmallTalk("你好，你是谁呀？"));
    }

    @Test
    void keepsRealQuestionsOnRetrievalPath() {
        assertFalse(QaServiceImpl.isSmallTalk("你好，Java 多态是什么？"));
        assertFalse(QaServiceImpl.isSmallTalk("Java 集合框架有哪些主要接口"));
        assertFalse(QaServiceImpl.isSmallTalk("什么是 JVM"));
        assertFalse(QaServiceImpl.isSmallTalk("谢谢，那 HashMap 扩容机制呢"));
        assertFalse(QaServiceImpl.isSmallTalk(" "));
        assertFalse(QaServiceImpl.isSmallTalk(null));
    }
}
