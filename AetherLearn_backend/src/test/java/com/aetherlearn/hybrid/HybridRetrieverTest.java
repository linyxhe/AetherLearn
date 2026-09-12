package com.aetherlearn.hybrid;

import com.aetherlearn.ai.PythonAiClient;
import com.aetherlearn.entity.Course;
import com.aetherlearn.entity.KnowledgeChunk;
import com.aetherlearn.entity.KnowledgeDoc;
import com.aetherlearn.kb.Bm25Retriever;
import com.aetherlearn.mapper.CourseMapper;
import com.aetherlearn.mapper.KnowledgeChunkMapper;
import com.aetherlearn.mapper.KnowledgeDocMapper;
import com.aetherlearn.service.impl.KnowledgeServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 混合检索编排器测试。
 */
class HybridRetrieverTest {

    @Test
    void mapsLegacyMilvusHitThroughDocumentAndSequence() {
        PythonAiClient pythonAiClient = mock(PythonAiClient.class);
        Bm25Retriever bm25Retriever = mock(Bm25Retriever.class);
        KnowledgeChunkMapper chunkMapper = mock(KnowledgeChunkMapper.class);
        HybridRetriever retriever = new HybridRetriever(
                pythonAiClient, bm25Retriever, chunkMapper, true, 30, 10
        );

        PythonAiClient.RetrievalHit hit = new PythonAiClient.RetrievalHit();
        hit.setChunkId(null);
        hit.setCourseId(1L);
        hit.setDocId(2L);
        hit.setSeq(3);
        hit.setContent("旧集合检索内容");

        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setId(99L);
        chunk.setCourseId(1L);
        chunk.setDocId(2L);
        chunk.setSeq(3);
        chunk.setContent("旧集合检索内容");
        when(chunkMapper.selectById(99L)).thenReturn(null);
        when(chunkMapper.selectByDocIdAndSeq(1L, 2L, 3)).thenReturn(chunk);

        PythonAiClient.RetrievalResult result = new PythonAiClient.RetrievalResult(
                Collections.singletonList(hit), 5, 2, true
        );
        when(pythonAiClient.hybridSearch(1L, "问题", 30, 10, true)).thenReturn(result);

        HybridRetriever.RetrievalResult retrieved = retriever.retrieveWithMetrics(1L, "问题", 5);

        assertSame(chunk, retrieved.getChunks().get(0));
        assertTrue(retrieved.isUsedPython());
    }

    @Test
    void fallsBackToBm25WhenPythonReturnsNoHits() {
        PythonAiClient pythonAiClient = mock(PythonAiClient.class);
        Bm25Retriever bm25Retriever = mock(Bm25Retriever.class);
        KnowledgeChunkMapper chunkMapper = mock(KnowledgeChunkMapper.class);
        HybridRetriever retriever = new HybridRetriever(
                pythonAiClient, bm25Retriever, chunkMapper, true, 30, 10
        );

        KnowledgeChunk fallback = new KnowledgeChunk();
        fallback.setId(7L);
        fallback.setCourseId(1L);
        fallback.setContent("本地 BM25 结果");
        when(pythonAiClient.hybridSearch(1L, "问题", 30, 10, true))
                .thenReturn(new PythonAiClient.RetrievalResult(
                        Collections.emptyList(), 5, 0, true
                ));
        when(bm25Retriever.retrieve(1L, "问题", 5)).thenReturn(Collections.singletonList(fallback));

        HybridRetriever.RetrievalResult retrieved = retriever.retrieveWithMetrics(1L, "问题", 5);

        assertSame(fallback, retrieved.getChunks().get(0));
        assertTrue(retrieved.getRetrievalMs() >= 0);
        assertTrue(retrieved.getRerankMs() == 0);
        assertTrue(!retrieved.isUsedPython());
    }

    @Test
    void queuesMilvusDeletionAfterDocumentSoftDelete() {
        KnowledgeDocMapper docMapper = mock(KnowledgeDocMapper.class);
        KnowledgeChunkMapper chunkMapper = mock(KnowledgeChunkMapper.class);
        CourseMapper courseMapper = mock(CourseMapper.class);
        Course course = mock(Course.class);
        PythonAiClient pythonAiClient = mock(PythonAiClient.class);
        TaskExecutor taskExecutor = mock(TaskExecutor.class);
        when(courseMapper.selectById(1L)).thenReturn(course);
        when(course.getTeacherId()).thenReturn(2L);
        KnowledgeServiceImpl service = new KnowledgeServiceImpl(
                docMapper, chunkMapper, null, null, pythonAiClient, null,
                courseMapper, null, taskExecutor
        );
        ReflectionTestUtils.setField(service, "uploadDir", "/tmp/uploads");
        KnowledgeDoc doc = mock(KnowledgeDoc.class);
        when(docMapper.selectById(10L)).thenReturn(doc);
        when(doc.getCourseId()).thenReturn(1L);
        when(doc.getFilePath()).thenReturn("/uploads/knowledge/test.txt");
        doReturn(true).when(pythonAiClient).isEnabled();

        service.delete(10L, 2L, 2);

        verify(docMapper).deleteById(10L);
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(taskExecutor).execute(captor.capture());
        captor.getValue().run();
        verify(pythonAiClient).deleteChunks(1L, 10L);
    }

    @Test
    void skipsMilvusDeletionWhenPythonServiceIsDisabled() {
        KnowledgeDocMapper docMapper = mock(KnowledgeDocMapper.class);
        KnowledgeChunkMapper chunkMapper = mock(KnowledgeChunkMapper.class);
        CourseMapper courseMapper = mock(CourseMapper.class);
        Course course = mock(Course.class);
        PythonAiClient pythonAiClient = mock(PythonAiClient.class);
        TaskExecutor taskExecutor = mock(TaskExecutor.class);
        when(courseMapper.selectById(1L)).thenReturn(course);
        when(course.getTeacherId()).thenReturn(2L);
        KnowledgeServiceImpl service = new KnowledgeServiceImpl(
                docMapper, chunkMapper, null, null, pythonAiClient, null,
                courseMapper, null, taskExecutor
        );
        ReflectionTestUtils.setField(service, "uploadDir", "/tmp/uploads");
        KnowledgeDoc doc = mock(KnowledgeDoc.class);
        when(docMapper.selectById(10L)).thenReturn(doc);
        when(doc.getCourseId()).thenReturn(1L);
        when(doc.getFilePath()).thenReturn("/uploads/knowledge/test.txt");
        doReturn(false).when(pythonAiClient).isEnabled();

        service.delete(10L, 2L, 2);

        verify(docMapper).deleteById(10L);
        verify(pythonAiClient).isEnabled();
        verifyNoInteractions(taskExecutor);
    }

    @Test
    void fallsBackWithReasonWhenPythonThrows() {
        PythonAiClient pythonAiClient = mock(PythonAiClient.class);
        Bm25Retriever bm25Retriever = mock(Bm25Retriever.class);
        KnowledgeChunkMapper chunkMapper = mock(KnowledgeChunkMapper.class);
        HybridRetriever retriever = new HybridRetriever(
                pythonAiClient, bm25Retriever, chunkMapper, true, 30, 10
        );
        KnowledgeChunk fallback = new KnowledgeChunk();
        fallback.setId(7L);
        when(pythonAiClient.hybridSearch(1L, "问题", 30, 10, true))
                .thenThrow(new RuntimeException("connection refused"));
        when(bm25Retriever.retrieve(1L, "问题", 5)).thenReturn(Collections.singletonList(fallback));

        HybridRetriever.RetrievalResult retrieved = retriever.retrieveWithMetrics(1L, "问题", 5);

        assertEquals("bm25", retrieved.getStrategy());
        assertEquals("python_error", retrieved.getFallbackReason());
        assertTrue(!retrieved.isUsedPython());
        assertSame(fallback, retrieved.getChunks().get(0));
    }

    @Test
    void fallsBackWithUnavailableReasonWhenPythonReturnsNull() {
        PythonAiClient pythonAiClient = mock(PythonAiClient.class);
        Bm25Retriever bm25Retriever = mock(Bm25Retriever.class);
        KnowledgeChunkMapper chunkMapper = mock(KnowledgeChunkMapper.class);
        HybridRetriever retriever = new HybridRetriever(
                pythonAiClient, bm25Retriever, chunkMapper, true, 30, 10
        );
        when(pythonAiClient.hybridSearch(1L, "问题", 30, 10, true)).thenReturn(null);
        when(bm25Retriever.retrieve(1L, "问题", 5)).thenReturn(Collections.emptyList());

        HybridRetriever.RetrievalResult retrieved = retriever.retrieveWithMetrics(1L, "问题", 5);

        assertEquals("bm25", retrieved.getStrategy());
        assertEquals("python_unavailable", retrieved.getFallbackReason());
        assertTrue(!retrieved.isUsedPython());
    }

    @Test
    void fallsBackWithClientFailureReasonWhenPythonReturnsNull() {
        PythonAiClient pythonAiClient = mock(PythonAiClient.class);
        Bm25Retriever bm25Retriever = mock(Bm25Retriever.class);
        KnowledgeChunkMapper chunkMapper = mock(KnowledgeChunkMapper.class);
        HybridRetriever retriever = new HybridRetriever(
                pythonAiClient, bm25Retriever, chunkMapper, true, 30, 10
        );
        when(pythonAiClient.hybridSearch(1L, "问题", 30, 10, true)).thenReturn(null);
        when(pythonAiClient.getLastFailureReason()).thenReturn("python_unauthorized");
        when(bm25Retriever.retrieve(1L, "问题", 5)).thenReturn(Collections.emptyList());

        HybridRetriever.RetrievalResult retrieved = retriever.retrieveWithMetrics(1L, "问题", 5);

        assertEquals("python_unauthorized", retrieved.getFallbackReason());
        assertTrue(!retrieved.isUsedPython());
    }

    @Test
    void fallsBackWithDisabledReasonWhenPythonDisabled() {
        PythonAiClient pythonAiClient = mock(PythonAiClient.class);
        Bm25Retriever bm25Retriever = mock(Bm25Retriever.class);
        KnowledgeChunkMapper chunkMapper = mock(KnowledgeChunkMapper.class);
        HybridRetriever retriever = new HybridRetriever(
                pythonAiClient, bm25Retriever, chunkMapper, false, 30, 10
        );
        when(bm25Retriever.retrieve(1L, "问题", 5)).thenReturn(Collections.emptyList());

        HybridRetriever.RetrievalResult retrieved = retriever.retrieveWithMetrics(1L, "问题", 5);

        assertEquals("bm25", retrieved.getStrategy());
        assertEquals("python_disabled", retrieved.getFallbackReason());
        verifyNoInteractions(pythonAiClient);
    }

    @Test
    void rejectsInvalidParametersWithoutCallingRetrievers() {
        PythonAiClient pythonAiClient = mock(PythonAiClient.class);
        Bm25Retriever bm25Retriever = mock(Bm25Retriever.class);
        KnowledgeChunkMapper chunkMapper = mock(KnowledgeChunkMapper.class);
        HybridRetriever retriever = new HybridRetriever(
                pythonAiClient, bm25Retriever, chunkMapper, true, 30, 10
        );

        HybridRetriever.RetrievalResult retrieved = retriever.retrieveWithMetrics(1L, "   ", 0);

        assertTrue(retrieved.getChunks().isEmpty());
        assertEquals("invalid_parameter", retrieved.getFallbackReason());
        verifyNoInteractions(pythonAiClient);
        verifyNoInteractions(bm25Retriever);
    }

    @Test
    void propagatesPythonStageMetrics() {
        PythonAiClient pythonAiClient = mock(PythonAiClient.class);
        Bm25Retriever bm25Retriever = mock(Bm25Retriever.class);
        KnowledgeChunkMapper chunkMapper = mock(KnowledgeChunkMapper.class);
        HybridRetriever retriever = new HybridRetriever(
                pythonAiClient, bm25Retriever, chunkMapper, true, 30, 10
        );

        PythonAiClient.RetrievalHit hit = new PythonAiClient.RetrievalHit();
        hit.setChunkId(99L);
        hit.setCourseId(1L);
        hit.setDocId(2L);
        hit.setSeq(0);
        hit.setContent("新集合命中");
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setId(99L);
        when(chunkMapper.selectById(99L)).thenReturn(chunk);

        PythonAiClient.RetrievalResult result = new PythonAiClient.RetrievalResult(
                Collections.singletonList(hit), 40, 15, true,
                12, 28, "hybrid", null, "ok", "ok", "ok"
        );
        when(pythonAiClient.hybridSearch(1L, "问题", 30, 10, true)).thenReturn(result);

        HybridRetriever.RetrievalResult retrieved = retriever.retrieveWithMetrics(1L, "问题", 5);

        assertTrue(retrieved.isUsedPython());
        assertEquals("hybrid", retrieved.getStrategy());
        assertEquals(12, retrieved.getEmbeddingMs());
        assertEquals(28, retrieved.getMilvusMs());
        assertEquals(15, retrieved.getRerankMs());
        // 检索总耗时 = Embedding + Milvus + Rerank（12+28+15），
        // 不能只透传 Python 的 retrieval_time_ms（它刻意不含精排），否则前端会少算十几秒。
        assertEquals(55, retrieved.getRetrievalMs());
        assertEquals("ok", retrieved.getRerankStatus());
    }
}
