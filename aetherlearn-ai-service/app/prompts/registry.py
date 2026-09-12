"""提示词注册表。

提示词以「key → 模板」的方式集中管理，图节点只认 key，不内嵌大段文本。
`placeholders()` 用于单测校验"调用方少传一个变量"这类线上炸点。
"""

from __future__ import annotations

import string
from dataclasses import dataclass, field
from typing import Any, Dict, Optional


@dataclass(frozen=True)
class PromptSpec:
    """一条提示词模板。"""

    key: str
    system: str
    user_template: str
    default_output_format: str = "text"  # text | json_object | json_array
    description: str = ""
    # 声明式契约，供结构化输出的自动校验使用（如 required_keys / item_required_keys）。
    # 用 Any 而不是 str：契约是列表，字符串化会让调用方被迫拼接/切分。
    extra: Dict[str, Any] = field(default_factory=dict)

    def placeholders(self) -> set:
        """从 user_template 提取 `{name}` 占位符名（忽略 `{{` 转义）。"""
        names = set()
        for _, field_name, _, _ in string.Formatter().parse(self.user_template):
            if field_name:
                names.add(field_name)
        return names

    def render(self, variables: Optional[dict] = None) -> tuple:
        """渲染出 (system, user)；缺失变量直接暴露为 KeyError 便于尽早发现。"""
        values = dict(variables or {})
        missing = self.placeholders() - set(values.keys())
        if missing:
            raise KeyError(f"提示词 {self.key} 缺少变量：{sorted(missing)}")
        return self.system, self.user_template.format(**values)


_REGISTRY: Dict[str, PromptSpec] = {}


def register(spec: PromptSpec) -> PromptSpec:
    """注册一条提示词（重复 key 直接报错，避免静默覆盖）。"""
    if spec.key in _REGISTRY:
        raise ValueError(f"提示词 key 重复注册：{spec.key}")
    _REGISTRY[spec.key] = spec
    return spec


def get_prompt(key: str) -> PromptSpec:
    """按 key 取提示词。"""
    spec = _REGISTRY.get(key)
    if spec is None:
        raise KeyError(f"未注册的提示词 key：{key}")
    return spec


def all_prompts() -> Dict[str, PromptSpec]:
    """返回全部提示词（测试与自检用）。"""
    return dict(_REGISTRY)
