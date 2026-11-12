"""Embedding 768 chiều, tất định, không gọi mạng.

Cùng một hàm cho lúc ghi vào pgvector và lúc nhúng câu hỏi. Hai câu gần nhau
(cùng từ) nằm gần nhau theo cosine. Không dùng LLM.
"""

import math
import re
import zlib

DIM = 768
_TOKEN = re.compile(r"[0-9a-zà-ỹđ]+", re.IGNORECASE)


def tokenize(text: str) -> list[str]:
    return [token.lower() for token in _TOKEN.findall(text)]


def embed(text: str) -> tuple[float, ...]:
    vector = [0.0] * DIM
    tokens = tokenize(text)
    if not tokens:
        vector[0] = 1.0
        return tuple(vector)
    for token in tokens:
        digest = zlib.crc32(token.encode("utf-8"))
        vector[digest % DIM] += 1.0 if (digest // DIM) % 2 == 0 else -1.0
    norm = math.sqrt(sum(value * value for value in vector)) or 1.0
    return tuple(value / norm for value in vector)


def cosine(left: tuple[float, ...], right: tuple[float, ...]) -> float:
    return sum(a * b for a, b in zip(left, right, strict=True))


def to_literal(vector: tuple[float, ...]) -> str:
    """Chuỗi pgvector: [0.1,0.2,...]."""
    return "[" + ",".join(f"{value:.6f}" for value in vector) + "]"
