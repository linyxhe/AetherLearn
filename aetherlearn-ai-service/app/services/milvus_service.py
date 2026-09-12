from typing import Any, Dict, List, Optional, Set

import logging

from app.config import get_settings
from app.utils.native_runtime import preload_native_runtime


logger = logging.getLogger(__name__)
settings = get_settings()


class MilvusService:
    """Milvus 向量数据库封装：集合管理、切片写入、删除与检索。"""

    def __init__(self, collection_name: Optional[str] = None):
        """延迟创建客户端，避免服务启动时因 Milvus 未就绪而失败。

        :param collection_name: 可选集合名；backfill 等脚本用它写入非默认集合。
        """
        self.client = None
        self.collection_name = collection_name or settings.COLLECTION_NAME
        self.legacy_collection_name = settings.LEGACY_COLLECTION_NAME
        # 新集合保存 MySQL 切片 ID；旧集合继续通过 course_id + doc_id + seq 回查。
        self._supports_chunk_id = False
        self._collection_ready = False

    @property
    def supports_chunk_id(self) -> bool:
        """当前集合是否支持显式 MySQL 切片 ID。"""
        return self._supports_chunk_id

    @staticmethod
    def _dict(value: Any) -> dict:
        """将 PyMilvus 的字典/对象描述统一转换为字典。"""
        if isinstance(value, dict):
            return value
        if hasattr(value, "to_dict"):
            return value.to_dict()
        if hasattr(value, "__dict__"):
            return vars(value)
        return {}

    def _ensure_database(self, uri: str) -> None:
        """确保配置的 Milvus 数据库存在，避免首次连接失败。"""
        db_name = settings.MILVUS_DB
        if not db_name or db_name == "default":
            return

        from pymilvus import MilvusClient

        kwargs = {
            "uri": uri,
            "timeout": settings.MILVUS_CONNECT_TIMEOUT,
        }
        if settings.MILVUS_USER:
            kwargs["user"] = settings.MILVUS_USER
        if settings.MILVUS_PASSWORD:
            kwargs["password"] = settings.MILVUS_PASSWORD

        admin = MilvusClient(**kwargs)
        try:
            if db_name not in admin.list_databases():
                admin.create_database(db_name)
                logger.info("Created Milvus database: %s", db_name)
        finally:
            admin.close()

    def _get_client(self):
        """获取 Milvus 客户端，首次使用时创建集合和索引。"""
        if self.client is None:
            # 必须在导入 pymilvus 之前预加载 torch/sentencepiece，
            # 否则 Windows 上会出现原生 DLL 加载顺序导致的 access violation。
            preload_native_runtime()
            from pymilvus import MilvusClient

            uri = f"http://{settings.MILVUS_HOST}:{settings.MILVUS_PORT}"
            logger.info("Connecting to Milvus at %s", uri)
            self._ensure_database(uri)
            kwargs = {
                "uri": uri,
                "db_name": settings.MILVUS_DB,
                "timeout": settings.MILVUS_CONNECT_TIMEOUT,
            }
            if settings.MILVUS_USER:
                kwargs["user"] = settings.MILVUS_USER
            if settings.MILVUS_PASSWORD:
                kwargs["password"] = settings.MILVUS_PASSWORD
            self.client = MilvusClient(**kwargs)
            self.ensure_collection()
        return self.client

    def ensure_collection(self):
        """创建缺失的新集合并检测已有集合能力。

        Milvus 2.4 之前不支持直接给已有集合添加字段，因此这里不对已有集合
        做破坏性 schema 修改。旧集合继续保留，Java 可通过 doc_id + seq 回查。
        """
        # 必须先把 torch/transformers 加载好，再导入 pymilvus(grpcio)；
        # 否则 Windows 上后加载的原生模块会 access violation。
        preload_native_runtime()
        from pymilvus import DataType, Function, FunctionType

        # 允许脚本直接调用 ensure_collection()，内部按需创建客户端。
        client = self._get_client()
        if client.has_collection(collection_name=self.collection_name):
            description = self._describe_collection()
            self._supports_chunk_id = "chunk_id" in description["field_names"]
            self._collection_ready = self._is_collection_ready(description)
            logger.info(
                "Collection %s 已存在，chunk_id=%s, ready=%s",
                self.collection_name,
                self._supports_chunk_id,
                self._collection_ready,
            )
            return

        schema = client.create_schema(
            auto_id=True,
            enable_dynamic_field=True,
            description="AetherLearn 知识库切片集合（稠密向量 + BM25 稀疏向量）",
        )
        schema.add_field("id", DataType.INT64, is_primary=True)
        schema.add_field("course_id", DataType.INT64, is_required=True)
        schema.add_field("doc_id", DataType.INT64, is_required=True)
        schema.add_field("seq", DataType.INT32)
        schema.add_field(
            "content",
            DataType.VARCHAR,
            max_length=65535,
            enable_analyzer=True,
        )
        # 新集合保存 MySQL 切片 ID，便于 Java 侧精确映射检索结果。
        schema.add_field("chunk_id", DataType.INT64, is_required=True)
        schema.add_field("dense_vector", DataType.FLOAT_VECTOR, dim=settings.EMBEDDING_DIM)
        # BM25 函数会从 content 自动生成 sparse_vector。
        schema.add_field("sparse_vector", DataType.SPARSE_FLOAT_VECTOR)

        bm25_fn = Function(
            name="bm25_fn",
            input_field_names=["content"],
            output_field_names=["sparse_vector"],
            function_type=FunctionType.BM25,
        )
        schema.add_function(bm25_fn)

        index_params = client.prepare_index_params()
        index_params.add_index(
            field_name="dense_vector",
            index_type="HNSW",
            metric_type="COSINE",
            params={"M": 16, "efConstruction": 200},
        )
        index_params.add_index(
            field_name="sparse_vector",
            index_type="SPARSE_INVERTED_INDEX",
            metric_type="BM25",
            params={"drop_ratio_build": 0.2},
        )
        client.create_collection(
            collection_name=self.collection_name,
            schema=schema,
            index_params=index_params,
        )
        self._supports_chunk_id = True
        self._collection_ready = True
        logger.info("Milvus collection %s 创建成功", self.collection_name)

    def _describe_collection(self) -> dict:
        """返回集合字段、函数和索引的描述，供兼容检测与运维统计使用。"""
        client = self._get_client()
        description = self._dict(client.describe_collection(collection_name=self.collection_name))
        schema = self._dict(description.get("schema", description))
        fields = schema.get("fields", [])
        functions = schema.get("functions", description.get("functions", []))
        # 部分 PyMilvus 版本的 describe_collection 不返回索引，需要单独查询。
        indexes = description.get("indexes", [])
        if not indexes:
            try:
                indexes = [
                    {"field_name": field_name}
                    for field_name in client.list_indexes(collection_name=self.collection_name)
                ]
            except Exception as exc:
                logger.warning("查询 Milvus 索引列表失败：%s", exc)
                indexes = []

        field_names = set()
        for field in fields:
            item = self._dict(field)
            name = item.get("name") or getattr(field, "name", None)
            if name:
                field_names.add(name)

        function_names = set()
        for function in functions:
            item = self._dict(function)
            name = item.get("name") or getattr(function, "name", None)
            if name:
                function_names.add(str(name).lower())

        index_fields = set()
        for index in indexes:
            item = self._dict(index)
            field = (
                item.get("field_name")
                or item.get("index_field_name")
                or getattr(index, "field_name", None)
            )
            if field:
                index_fields.add(str(field))

        return {
            "collection_name": self.collection_name,
            "field_names": sorted(field_names),
            "function_names": sorted(function_names),
            "index_fields": sorted(index_fields),
            "raw": description,
        }

    def _is_collection_ready(self, description: dict) -> bool:
        """检查新集合是否具备稠密、稀疏和 BM25 所需能力。"""
        fields = set(description.get("field_names", []))
        functions = set(description.get("function_names", []))
        indexes = set(description.get("index_fields", []))
        return {
            "course_id",
            "doc_id",
            "seq",
            "content",
            "chunk_id",
            "dense_vector",
            "sparse_vector",
        }.issubset(fields) and {"dense_vector", "sparse_vector"}.issubset(indexes) and any(
            "bm25" in name for name in functions
        )

    def get_existing_chunk_ids(self, course_id: int, doc_id: int) -> Set[int]:
        """查询指定文档已存在的 MySQL 切片 ID，供安全替换使用。"""
        if not self._supports_chunk_id:
            return set()
        client = self._get_client()
        try:
            results = client.query(
                collection_name=self.collection_name,
                filter=f"course_id == {int(course_id)} and doc_id == {int(doc_id)}",
                output_fields=["chunk_id"],
                limit=10000,
            )
            return {
                int(item["chunk_id"])
                for item in results
                if item.get("chunk_id") is not None
            }
        except Exception as exc:
            logger.warning("查询 Milvus 旧切片 ID 失败：%s", exc)
            return set()

    def get_all_chunk_ids(self) -> Set[int]:
        """查询集合中全部 MySQL 切片 ID，供 backfill 幂等跳过使用。"""
        if not self._supports_chunk_id:
            return set()
        client = self._get_client()
        try:
            results = client.query(
                collection_name=self.collection_name,
                filter="chunk_id > 0",
                output_fields=["chunk_id"],
                limit=16384,
            )
            return {
                int(item["chunk_id"])
                for item in results
                if item.get("chunk_id") is not None
            }
        except Exception as exc:
            logger.warning("查询 Milvus 全部切片 ID 失败：%s", exc)
            return set()

    def delete_chunks_by_ids(self, chunk_ids: List[int]) -> int:
        """按 MySQL 切片 ID 删除 Milvus 向量，避免误删新写入的数据。"""
        if not chunk_ids or not self._supports_chunk_id:
            return 0
        ids = sorted({int(value) for value in chunk_ids})
        expression = "chunk_id in [" + ",".join(str(value) for value in ids) + "]"
        client = self._get_client()
        # PyMilvus 2.5 的 MilvusClient.delete 使用 filter 参数，不是 expr。
        result = client.delete(collection_name=self.collection_name, filter=expression)
        client.flush(collection_name=self.collection_name)
        return int(result.get("delete_count", 0))

    def insert_chunks(self, chunks: List[dict]) -> List[int]:
        """批量写入切片；Milvus 的 BM25 函数会自动生成稀疏向量。"""
        if not chunks:
            return []

        data = []
        for chunk in chunks:
            # 新集合要求显式 chunk_id；旧集合不发送该字段以保持兼容。
            for field_name in ("course_id", "doc_id", "seq"):
                if chunk.get(field_name) is None:
                    raise ValueError(f"{field_name} 不能为空")
            if self._supports_chunk_id and chunk.get("chunk_id") is None:
                raise ValueError("新集合的 chunk_id 不能为空")
            embedding = chunk.get("embedding")
            if embedding is None or len(embedding) != settings.EMBEDDING_DIM:
                raise ValueError("dense_vector 维度必须为 %d" % settings.EMBEDDING_DIM)
            content = str(chunk.get("content", ""))
            if not content:
                raise ValueError("content 不能为空")
            if len(content) > 65535:
                raise ValueError("content 长度不能超过 65535")
            item = {
                "course_id": int(chunk["course_id"]),
                "doc_id": int(chunk["doc_id"]),
                "seq": int(chunk["seq"]),
                "content": content,
                "dense_vector": [float(value) for value in embedding],
            }
            # 旧集合没有 chunk_id 时不写入该字段，避免 schema 不兼容导致整批入库失败。
            if self._supports_chunk_id and chunk.get("chunk_id") is not None:
                item["chunk_id"] = int(chunk["chunk_id"])
            data.append(item)

        client = self._get_client()
        result = client.insert(collection_name=self.collection_name, data=data)
        client.flush(collection_name=self.collection_name)
        logger.info("已写入 Milvus %d 条切片", len(data))
        return list(result.get("ids", []))

    def _output_fields(self) -> List[str]:
        """按集合字段能力选择检索输出字段，避免旧集合查询不存在的字段。"""
        fields = ["id", "course_id", "doc_id", "seq", "content"]
        if self._supports_chunk_id:
            fields.append("chunk_id")
        return fields

    def delete_chunks_by_doc(self, course_id: int, doc_id: int) -> int:
        """按课程和文档删除 Milvus 中的切片。"""
        client = self._get_client()
        expr = f"course_id == {int(course_id)} and doc_id == {int(doc_id)}"
        result = client.delete(collection_name=self.collection_name, filter=expr)
        client.flush(collection_name=self.collection_name)
        return int(result.get("delete_count", 0))

    def vector_search(
        self,
        course_id: int,
        query_text: str,
        dense_vector: List[float],
        top_k: int = 30,
    ) -> List[dict]:
        """纯稠密向量检索。"""
        client = self._get_client()
        results = client.search(
            collection_name=self.collection_name,
            data=[dense_vector],
            anns_field="dense_vector",
            filter=f"course_id == {int(course_id)}",
            output_fields=self._output_fields(),
            limit=top_k,
        )
        return self._format_hits(results, course_id)

    def sparse_search(
        self,
        course_id: int,
        query_text: str,
        top_k: int = 30,
    ) -> List[dict]:
        """纯 BM25 稀疏检索，原始文本由 Milvus BM25 函数编码。"""
        client = self._get_client()
        results = client.search(
            collection_name=self.collection_name,
            data=[query_text],
            anns_field="sparse_vector",
            filter=f"course_id == {int(course_id)}",
            output_fields=self._output_fields(),
            limit=top_k,
        )
        return self._format_hits(results, course_id)

    def hybrid_search(
        self,
        course_id: int,
        query_text: str,
        dense_vector: Optional[List[float]],
        top_k: int = 30,
    ) -> List[dict]:
        """稠密向量 + BM25 稀疏向量检索，使用 Milvus 原生 RRF 融合。"""
        from pymilvus import AnnSearchRequest, RRFRanker

        if not query_text or not query_text.strip():
            raise ValueError("query_text 不能为空")
        client = self._get_client()
        expr = f"course_id == {int(course_id)}"
        reqs = []
        if dense_vector:
            reqs.append(
                AnnSearchRequest(
                    data=[dense_vector],
                    anns_field="dense_vector",
                    param={"metric_type": "COSINE", "params": {"ef": 64}},
                    limit=top_k,
                    expr=expr,
                )
            )
        # 即使稠密向量暂时不可用，也保留 BM25 召回路径。
        reqs.append(
            AnnSearchRequest(
                data=[query_text],
                anns_field="sparse_vector",
                param={"metric_type": "BM25", "params": {}},
                limit=top_k,
                expr=expr,
            )
        )

        results = client.hybrid_search(
            collection_name=self.collection_name,
            reqs=reqs,
            ranker=RRFRanker(k=settings.RRF_K),
            limit=top_k,
            output_fields=self._output_fields(),
        )
        return self._format_hits(results, course_id)

    def _format_hits(self, results, course_id: int) -> List[dict]:
        """统一转换 Milvus hit 为服务内部结构。"""
        hits = []
        for hit_list in results:
            for hit in hit_list:
                # MilvusClient 返回 dict；ORM 风格接口返回对象，两种都要兼容。
                if isinstance(hit, dict):
                    entity = hit.get("entity", hit)
                    score = hit.get("distance", hit.get("score", 0.0))
                else:
                    entity = hit.entity
                    score = hit.score
                get_entity = getattr(entity, "get", None)
                content = get_entity("content", "") if get_entity else getattr(entity, "content", "")
                doc_id = get_entity("doc_id", 0) if get_entity else getattr(entity, "doc_id", 0)
                seq = get_entity("seq", 0) if get_entity else getattr(entity, "seq", 0)
                chunk_id_value = (
                    get_entity("chunk_id") if get_entity else getattr(entity, "chunk_id", None)
                )
                hits.append(
                    {
                        # 新集合返回 MySQL chunk_id；旧集合没有该字段时保留为空。
                        "chunk_id": (
                            int(chunk_id_value)
                            if self._supports_chunk_id and chunk_id_value is not None
                            else None
                        ),
                        "course_id": int(get_entity("course_id", course_id) if get_entity else course_id),
                        "doc_id": int(doc_id),
                        "seq": int(seq),
                        "content": content,
                        "score": float(score),
                    }
                )
        return hits

    def get_collection_stats(self) -> dict:
        """获取集合统计、索引状态和错误信息。"""
        try:
            client = self._get_client()
            stats = client.get_collection_stats(self.collection_name)
            description = self._describe_collection()
            ready = self._is_collection_ready(description)
            return {
                "collection_name": self.collection_name,
                # PyMilvus 2.5 的 get_collection_stats 返回 {'row_count': N}。
                "num_entities": int(
                    stats.get("row_count", stats.get("count", stats.get("num_entities", 0)))
                ),
                "indexed": ready,
                "status": "ok" if ready else "degraded",
                "fields": description["field_names"],
                "functions": description["function_names"],
                "indexes": description["index_fields"],
            }
        except Exception as exc:
            logger.warning("获取 Milvus 集合统计失败: %s", exc)
            return {
                "collection_name": self.collection_name,
                "num_entities": 0,
                "indexed": False,
                "status": "degraded",
                "error": str(exc),
            }

    def close(self):
        """释放 Milvus 客户端资源。"""
        if self.client is not None:
            try:
                self.client.close()
            finally:
                self.client = None
