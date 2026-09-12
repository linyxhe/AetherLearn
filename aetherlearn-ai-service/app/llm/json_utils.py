"""从模型输出里稳健地提取 JSON。

Java 侧原来用正则 `\\{[^}]*\\}` 提取批改结果，不支持嵌套对象、也不支持 feedback 里带 `}`。
这里改用 `json.JSONDecoder().raw_decode` 逐位置扫描，能正确处理嵌套与转义，属于纯升级。
"""

from __future__ import annotations

import json
import re
from typing import Any, Optional


_FENCE_PATTERN = re.compile(r"^\s*```[a-zA-Z]*\s*\n?(?P<body>.*?)\n?\s*```\s*$", re.DOTALL)


def strip_code_fence(text: str) -> str:
    """剥掉 ```json / ```JSON / ``` 围栏。"""
    if not text:
        return ""
    match = _FENCE_PATTERN.match(text)
    if match:
        return match.group("body").strip()
    # 模型偶尔只给半个围栏
    return text.replace("```json", "").replace("```JSON", "").replace("```", "").strip()


def _scan(text: str, opener: str, closer: str) -> Optional[Any]:
    """从每个 opener 位置尝试 raw_decode，返回第一个成功的解析结果。"""
    decoder = json.JSONDecoder()
    index = text.find(opener)
    while index >= 0:
        try:
            value, _ = decoder.raw_decode(text[index:])
            if isinstance(value, dict) and opener == "{":
                return value
            if isinstance(value, list) and opener == "[":
                return value
        except json.JSONDecodeError:
            pass
        index = text.find(opener, index + 1)

    # 兜底：截取首尾成对括号再试一次（处理模型在 JSON 后继续写解释的情况）
    start = text.find(opener)
    end = text.rfind(closer)
    if start >= 0 and end > start:
        try:
            value = json.loads(text[start : end + 1])
            if (opener == "{" and isinstance(value, dict)) or (
                opener == "[" and isinstance(value, list)
            ):
                return value
        except json.JSONDecodeError:
            return None
    return None


def extract_json_object(text: str) -> Optional[dict]:
    """提取第一个 JSON 对象，失败返回 None。"""
    if not text:
        return None
    return _scan(strip_code_fence(text), "{", "}")


def extract_json_array(text: str) -> Optional[list]:
    """提取第一个 JSON 数组，失败返回 None。"""
    if not text:
        return None
    return _scan(strip_code_fence(text), "[", "]")
