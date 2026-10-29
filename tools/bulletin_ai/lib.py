#!/usr/bin/env python3
import hashlib
import json
import os
import re
from pathlib import Path
from typing import Any, Dict, Iterable, List
from urllib.parse import urlparse

SAFE_SLUG_PATTERN = re.compile(r"[^a-zA-Z0-9]+")
DOTENV_PATH = Path(__file__).resolve().parent / ".env"


def ensure_dir(path: Path) -> None:
    path.mkdir(parents=True, exist_ok=True)


def read_json(path: Path) -> Any:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def write_json(path: Path, data: Any) -> None:
    ensure_dir(path.parent)
    with path.open("w", encoding="utf-8") as handle:
        json.dump(data, handle, indent=2, ensure_ascii=True)


def read_text(path: Path) -> str:
    with path.open("r", encoding="utf-8") as handle:
        return handle.read()


def write_text(path: Path, text: str) -> None:
    ensure_dir(path.parent)
    with path.open("w", encoding="utf-8") as handle:
        handle.write(text)


def load_dotenv(path: Path | None = None) -> None:
    env_path = path or DOTENV_PATH
    if not env_path.exists():
        return
    for line in env_path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip().strip("\"").strip("'")
        if key and key not in os.environ:
            os.environ[key] = value


def slug_from_url(url: str) -> str:
    parsed = urlparse(url)
    path = parsed.path.rstrip("/")
    if not path:
        return "index"
    slug = path.split("/")[-1]
    slug = SAFE_SLUG_PATTERN.sub("-", slug).strip("-")
    return slug or "index"


def normalize_text(value: str) -> str:
    return re.sub(r"\s+", " ", value or "").strip().lower()


def sha256_text(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def clamp_text(text: str, max_chars: int) -> str:
    if max_chars <= 0 or len(text) <= max_chars:
        return text
    if max_chars <= 3:
        return text[:max_chars]
    return text[: max_chars - 3] + "..."


def iter_manifest_items(manifest: List[Dict[str, Any]]) -> Iterable[Dict[str, Any]]:
    for item in manifest:
        if not isinstance(item, dict):
            continue
        if not item.get("sourceUrl"):
            continue
        yield item
