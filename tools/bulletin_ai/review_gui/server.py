#!/usr/bin/env python3
import argparse
import json
from http import HTTPStatus
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any, Dict, Optional
from urllib.parse import parse_qs, urlparse
import sys

SCRIPT_DIR = Path(__file__).resolve().parent

def find_repo_root() -> Path:
    markers = ["backend", "course_planner_frontend", "tools"]
    for parent in SCRIPT_DIR.parents:
        if all((parent / marker).exists() for marker in markers):
            return parent
    return SCRIPT_DIR.parents[2]


REPO_ROOT = find_repo_root()
BULLETIN_ROOT = REPO_ROOT / "tools" / "bulletin_ai"
DEFAULT_QUEUE = BULLETIN_ROOT / "review" / "queue.json"

sys.path.append(str(BULLETIN_ROOT))
from lib import load_dotenv  # noqa: E402


def read_json(path: Path) -> Any:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def write_json(path: Path, data: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as handle:
        json.dump(data, handle, indent=2, ensure_ascii=True)


def resolve_safe(path_value: Optional[str]) -> Optional[Path]:
    if not path_value:
        return None
    path = Path(path_value)
    if not path.is_absolute():
        path = (REPO_ROOT / path).resolve()
    else:
        path = path.resolve()
    if REPO_ROOT not in path.parents and path != REPO_ROOT:
        return None
    return path


class ReviewHandler(SimpleHTTPRequestHandler):
    queue_path: Path = DEFAULT_QUEUE
    gold_root: Path = BULLETIN_ROOT / "golden" / "expected"

    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(SCRIPT_DIR / "static"), **kwargs)

    def log_message(self, format: str, *args) -> None:  # noqa: A003
        return

    def send_json(self, payload: Any, status: int = HTTPStatus.OK) -> None:
        data = json.dumps(payload, ensure_ascii=True).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def load_queue(self) -> Dict[str, Any]:
        if not self.queue_path.exists():
            return {"items": []}
        return read_json(self.queue_path)

    def find_item(self, slug: str, catalog_label: str) -> Optional[Dict[str, Any]]:
        queue = self.load_queue()
        for item in queue.get("items", []) or []:
            if item.get("slug") == slug and item.get("catalogLabel") == catalog_label:
                return item
        return None

    def do_GET(self) -> None:  # noqa: N802
        parsed = urlparse(self.path)
        if parsed.path == "/api/queue":
            queue = self.load_queue()
            self.send_json(queue)
            return

        if parsed.path == "/api/item":
            params = parse_qs(parsed.query)
            slug = params.get("slug", [None])[0]
            catalog_label = params.get("catalogLabel", [None])[0]
            if not slug or not catalog_label:
                self.send_json({"error": "Missing slug or catalogLabel"}, status=HTTPStatus.BAD_REQUEST)
                return
            item = self.find_item(slug, catalog_label)
            if not item:
                self.send_json({"error": "Item not found"}, status=HTTPStatus.NOT_FOUND)
                return

            def read_text(path_key: str) -> Optional[str]:
                path_value = item.get("paths", {}).get(path_key)
                safe_path = resolve_safe(path_value)
                if not safe_path or not safe_path.exists():
                    return None
                return safe_path.read_text(encoding="utf-8")

            def read_json_path(path_key: str) -> Optional[Any]:
                path_value = item.get("paths", {}).get(path_key)
                safe_path = resolve_safe(path_value)
                if not safe_path or not safe_path.exists():
                    return None
                return read_json(safe_path)

            repaired_path = BULLETIN_ROOT / "repaired" / catalog_label / f"{slug}.json"

            payload = {
                "item": item,
                "snippetHtml": read_text("snippet"),
                "parsed": read_json_path("parsed"),
                "repaired": read_json(repaired_path) if repaired_path.exists() else None,
                "lint": read_json_path("lint"),
                "llm": read_json_path("llm"),
                "goldDiff": read_json_path("goldDiff"),
                "goldExpected": read_json_path("goldExpected"),
            }
            self.send_json(payload)
            return

        super().do_GET()

    def do_POST(self) -> None:  # noqa: N802
        parsed = urlparse(self.path)
        if parsed.path == "/api/queue/status":
            length = int(self.headers.get("Content-Length", "0"))
            raw = self.rfile.read(length).decode("utf-8") if length else ""
            try:
                data = json.loads(raw)
            except json.JSONDecodeError:
                self.send_json({"error": "Invalid JSON body"}, status=HTTPStatus.BAD_REQUEST)
                return

            slug = data.get("slug")
            catalog_label = data.get("catalogLabel")
            status_value = data.get("status")
            review_note = data.get("reviewNote")
            if not slug or not catalog_label or not status_value:
                self.send_json({"error": "Missing slug, catalogLabel, or status"}, status=HTTPStatus.BAD_REQUEST)
                return

            queue = self.load_queue()
            updated = False
            for item in queue.get("items", []) or []:
                if item.get("slug") == slug and item.get("catalogLabel") == catalog_label:
                    item["status"] = status_value
                    if review_note is not None:
                        item["reviewNote"] = review_note
                    item["updatedAt"] = data.get("updatedAt")
                    updated = True
                    break

            if not updated:
                self.send_json({"error": "Item not found"}, status=HTTPStatus.NOT_FOUND)
                return

            queue["updatedAt"] = data.get("updatedAt")
            write_json(self.queue_path, queue)
            self.send_json({"ok": True})
            return

        if parsed.path == "/api/gold/accept":
            length = int(self.headers.get("Content-Length", "0"))
            raw = self.rfile.read(length).decode("utf-8") if length else ""
            try:
                data = json.loads(raw)
            except json.JSONDecodeError:
                self.send_json({"error": "Invalid JSON body"}, status=HTTPStatus.BAD_REQUEST)
                return

            slug = data.get("slug")
            catalog_label = data.get("catalogLabel")
            source = data.get("source") or "repaired"
            if not slug or not catalog_label:
                self.send_json({"error": "Missing slug or catalogLabel"}, status=HTTPStatus.BAD_REQUEST)
                return

            if source == "parsed":
                source_path = BULLETIN_ROOT / "parsed" / catalog_label / f"{slug}.json"
            else:
                source_path = BULLETIN_ROOT / "repaired" / catalog_label / f"{slug}.json"

            if not source_path.exists():
                self.send_json({"error": f"Source file not found: {source_path}"}, status=HTTPStatus.NOT_FOUND)
                return

            payload = read_json(source_path)
            root = payload.get("root") if isinstance(payload, dict) else None
            if not root:
                self.send_json({"error": "Source file missing root"}, status=HTTPStatus.BAD_REQUEST)
                return

            self.gold_root.mkdir(parents=True, exist_ok=True)
            gold_path = self.gold_root / f"{slug}.expected.json"
            write_json(gold_path, root)
            self.send_json({"ok": True, "path": str(gold_path)})
            return

        self.send_json({"error": "Unknown endpoint"}, status=HTTPStatus.NOT_FOUND)
        return


def main() -> None:
    load_dotenv()
    parser = argparse.ArgumentParser(description="Review GUI for bulletin requirements")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=5175)
    parser.add_argument("--queue", default=str(DEFAULT_QUEUE))
    parser.add_argument("--gold-root", default=str(BULLETIN_ROOT / "golden" / "expected"))
    args = parser.parse_args()

    ReviewHandler.queue_path = resolve_safe(args.queue) or DEFAULT_QUEUE
    ReviewHandler.gold_root = resolve_safe(args.gold_root) or (BULLETIN_ROOT / "golden" / "expected")

    server = ThreadingHTTPServer((args.host, args.port), ReviewHandler)
    print(f"Review GUI running at http://{args.host}:{args.port}")
    print(f"Queue: {ReviewHandler.queue_path}")
    server.serve_forever()


if __name__ == "__main__":
    main()
