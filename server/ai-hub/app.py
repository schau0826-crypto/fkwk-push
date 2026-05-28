#!/usr/bin/env python3
"""fkwk-push AI Hub.

A small dependency-free HTTP service for notification memory, OpenAI-compatible
summaries, todo drafts, action confirmation, Feishu webhook notifications, and
Bark reminders.
"""

from __future__ import annotations

import argparse
import json
import os
import sqlite3
import time
import uuid
import urllib.error
import urllib.request
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any
from urllib.parse import parse_qs, urlparse

DB_PATH = os.getenv("AI_HUB_DB", "/data/ai-hub.db")
HUB_TOKEN = os.getenv("AI_HUB_TOKEN", "")
OPENAI_BASE_URL = os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1").rstrip("/")
OPENAI_API_KEY = os.getenv("OPENAI_API_KEY", "")
OPENAI_MODEL = os.getenv("OPENAI_MODEL", "gpt-4o-mini")
FEISHU_WEBHOOK_URL = os.getenv("FEISHU_WEBHOOK_URL", "")
PUBLIC_BASE_URL = os.getenv("PUBLIC_BASE_URL", "").rstrip("/")
BARK_SERVER_URL = os.getenv("BARK_SERVER_URL", "https://api.day.app").rstrip("/")
BARK_DEVICE_KEY = os.getenv("BARK_DEVICE_KEY", "")

SCHEMA = """
CREATE TABLE IF NOT EXISTS notification_events (
  id TEXT PRIMARY KEY,
  received_at INTEGER NOT NULL,
  post_time INTEGER NOT NULL,
  package_name TEXT NOT NULL,
  app_name TEXT NOT NULL,
  title TEXT NOT NULL,
  text TEXT NOT NULL,
  priority TEXT NOT NULL,
  matched_rule_name TEXT,
  forwarded INTEGER NOT NULL,
  http_code INTEGER,
  error TEXT,
  status TEXT NOT NULL,
  raw_json TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_events_post_time ON notification_events(post_time);
CREATE INDEX IF NOT EXISTS idx_events_package ON notification_events(package_name);

CREATE TABLE IF NOT EXISTS memories (
  id TEXT PRIMARY KEY,
  created_at INTEGER NOT NULL,
  event_id TEXT,
  kind TEXT NOT NULL,
  summary TEXT NOT NULL,
  tags TEXT NOT NULL,
  deleted_at INTEGER
);

CREATE TABLE IF NOT EXISTS todo_drafts (
  id TEXT PRIMARY KEY,
  created_at INTEGER NOT NULL,
  source_event_ids TEXT NOT NULL,
  title TEXT NOT NULL,
  detail TEXT NOT NULL,
  suggested_due_at TEXT,
  priority TEXT NOT NULL,
  recommended_action TEXT NOT NULL,
  status TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS action_requests (
  id TEXT PRIMARY KEY,
  created_at INTEGER NOT NULL,
  action_type TEXT NOT NULL,
  payload_json TEXT NOT NULL,
  status TEXT NOT NULL,
  decided_at INTEGER
);
"""


def now_ms() -> int:
    return int(time.time() * 1000)


def new_id(prefix: str) -> str:
    return f"{prefix}_{uuid.uuid4().hex}"


def db() -> sqlite3.Connection:
    os.makedirs(os.path.dirname(DB_PATH), exist_ok=True)
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    conn.executescript(SCHEMA)
    return conn


def json_response(handler: BaseHTTPRequestHandler, status: int, payload: dict[str, Any]) -> None:
    body = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    handler.send_response(status)
    handler.send_header("Content-Type", "application/json; charset=utf-8")
    handler.send_header("Content-Length", str(len(body)))
    handler.end_headers()
    handler.wfile.write(body)


def read_json(handler: BaseHTTPRequestHandler) -> dict[str, Any]:
    length = int(handler.headers.get("Content-Length", "0"))
    if length <= 0:
        return {}
    return json.loads(handler.rfile.read(length).decode("utf-8"))


def authorize(handler: BaseHTTPRequestHandler, qs: dict[str, list[str]] | None = None) -> bool:
    if not HUB_TOKEN:
        return True
    auth = handler.headers.get("Authorization", "")
    token = auth.removeprefix("Bearer ").strip()
    query_token = (qs or {}).get("token", [""])[0]
    return token == HUB_TOKEN or query_token == HUB_TOKEN


def post_json(url: str, payload: dict[str, Any], headers: dict[str, str] | None = None, timeout: int = 20) -> tuple[int, str]:
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=data, method="POST")
    req.add_header("Content-Type", "application/json; charset=utf-8")
    for key, value in (headers or {}).items():
        req.add_header(key, value)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, resp.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read().decode("utf-8", errors="replace")


def call_openai(system: str, user: str) -> str | None:
    if not OPENAI_API_KEY:
        return None
    payload = {
        "model": OPENAI_MODEL,
        "messages": [
            {"role": "system", "content": system},
            {"role": "user", "content": user},
        ],
        "temperature": 0.2,
    }
    code, body = post_json(
        f"{OPENAI_BASE_URL}/chat/completions",
        payload,
        headers={"Authorization": f"Bearer {OPENAI_API_KEY}"},
        timeout=60,
    )
    if code < 200 or code >= 300:
        return None
    try:
        return json.loads(body)["choices"][0]["message"]["content"].strip()
    except Exception:
        return None


def fetch_events(conn: sqlite3.Connection, since_ms: int) -> list[sqlite3.Row]:
    return conn.execute(
        "SELECT * FROM notification_events WHERE post_time >= ? ORDER BY post_time ASC",
        (since_ms,),
    ).fetchall()


def heuristic_digest(events: list[sqlite3.Row]) -> dict[str, Any]:
    urgent = [e for e in events if e["priority"] == "URGENT"]
    skipped = [e for e in events if e["status"] in ("blocked", "skipped")]
    todos = []
    todo_words = ("处理", "确认", "尽快", "异常", "提醒", "待办", "请", "需要", "打卡", "会议")
    for e in events:
        body = f"{e['title']} {e['text']}"
        if any(w in body for w in todo_words):
            todos.append(
                {
                    "title": e["title"] or e["app_name"],
                    "detail": e["text"],
                    "priority": e["priority"],
                    "source_event_ids": [e["id"]],
                    "recommended_action": "review",
                }
            )
    summary_lines = [
        f"共收到 {len(events)} 条通知，紧急 {len(urgent)} 条，跳过/屏蔽 {len(skipped)} 条。",
    ]
    if urgent:
        summary_lines.append("紧急事项：" + "；".join(f"{e['app_name']}：{e['title']}" for e in urgent[:5]))
    if todos:
        summary_lines.append("待处理事项：" + "；".join(t["title"] for t in todos[:8]))
    return {
        "summary": "\n".join(summary_lines),
        "todos": todos[:12],
        "risks": [f"{e['app_name']}：{e['title']}" for e in urgent[:5]],
    }


def ai_digest(events: list[sqlite3.Row]) -> dict[str, Any]:
    compact = [
        {
            "id": e["id"],
            "time": datetime.fromtimestamp(e["post_time"] / 1000).isoformat(),
            "app": e["app_name"],
            "package": e["package_name"],
            "title": e["title"],
            "text": e["text"],
            "priority": e["priority"],
            "status": e["status"],
            "error": e["error"],
        }
        for e in events[-120:]
    ]
    system = "你是个人通知助理。把通知整理成中文简报、风险提醒和 Todo 草稿。必须输出 JSON。"
    user = """
请根据通知列表输出 JSON：
{
  "summary": "给用户看的简报，分点但保持简短",
  "todos": [{"title":"","detail":"","priority":"LOW|NORMAL|URGENT","source_event_ids":[""],"suggested_due_at":"可为空","recommended_action":"bark_reminder|feishu_task|calendar_event|review"}],
  "risks": [""]
}
通知：
""" + json.dumps(compact, ensure_ascii=False)
    content = call_openai(system, user)
    if not content:
        return heuristic_digest(events)
    cleaned = content.strip().removeprefix("```json").removeprefix("```").removesuffix("```").strip()
    try:
        parsed = json.loads(cleaned)
        if isinstance(parsed, dict) and "summary" in parsed:
            return parsed
    except Exception:
        pass
    fallback = heuristic_digest(events)
    fallback["summary"] = content
    return fallback


def store_digest(conn: sqlite3.Connection, digest: dict[str, Any]) -> list[dict[str, Any]]:
    created = []
    ts = now_ms()
    conn.execute(
        "INSERT INTO memories(id, created_at, event_id, kind, summary, tags, deleted_at) VALUES(?,?,?,?,?,?,NULL)",
        (new_id("mem"), ts, None, "digest", digest.get("summary", ""), json.dumps(["digest"], ensure_ascii=False)),
    )
    for item in digest.get("todos", []) or []:
        todo_id = new_id("todo")
        todo = {
            "id": todo_id,
            "title": str(item.get("title") or "待处理事项"),
            "detail": str(item.get("detail") or ""),
            "priority": str(item.get("priority") or "NORMAL"),
            "source_event_ids": item.get("source_event_ids") or [],
            "suggested_due_at": item.get("suggested_due_at"),
            "recommended_action": str(item.get("recommended_action") or "review"),
            "status": "draft",
        }
        conn.execute(
            "INSERT INTO todo_drafts(id, created_at, source_event_ids, title, detail, suggested_due_at, priority, recommended_action, status) VALUES(?,?,?,?,?,?,?,?,?)",
            (
                todo_id,
                ts,
                json.dumps(todo["source_event_ids"], ensure_ascii=False),
                todo["title"],
                todo["detail"],
                todo["suggested_due_at"],
                todo["priority"],
                todo["recommended_action"],
                todo["status"],
            ),
        )
        action_id = new_id("act")
        payload = {"todo_id": todo_id, "title": todo["title"], "body": todo["detail"], "priority": todo["priority"]}
        conn.execute(
            "INSERT INTO action_requests(id, created_at, action_type, payload_json, status, decided_at) VALUES(?,?,?,?,?,NULL)",
            (action_id, ts, todo["recommended_action"], json.dumps(payload, ensure_ascii=False), "pending"),
        )
        todo["action_id"] = action_id
        created.append(todo)
    conn.commit()
    return created


def send_feishu_digest(digest: dict[str, Any], todos: list[dict[str, Any]]) -> None:
    if not FEISHU_WEBHOOK_URL:
        return
    lines = ["fkwk-push AI 简报", "", digest.get("summary", "暂无摘要")]
    if todos:
        lines.append("\n待确认 Todo：")
        for todo in todos[:8]:
            confirm = f"{PUBLIC_BASE_URL}/ai/actions/{todo['action_id']}/confirm?token={HUB_TOKEN}" if PUBLIC_BASE_URL else ""
            ignore = f"{PUBLIC_BASE_URL}/ai/actions/{todo['action_id']}/ignore?token={HUB_TOKEN}" if PUBLIC_BASE_URL else ""
            lines.append(f"- {todo['title']} [{todo['priority']}] {confirm} {ignore}")
    post_json(FEISHU_WEBHOOK_URL, {"msg_type": "text", "content": {"text": "\n".join(lines)}})


def send_bark(title: str, body: str, priority: str = "NORMAL") -> tuple[int | None, str]:
    if not BARK_DEVICE_KEY:
        return None, "BARK_DEVICE_KEY not configured"
    level = "timeSensitive" if priority == "URGENT" else "active"
    code, resp = post_json(
        f"{BARK_SERVER_URL}/push",
        {"device_key": BARK_DEVICE_KEY, "title": title, "body": body or "（无详情）", "group": "fkwk AI", "level": level, "isArchive": 1},
    )
    return code, resp


class Handler(BaseHTTPRequestHandler):
    server_version = "fkwk-ai-hub/0.1"

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        qs = parse_qs(parsed.query)
        if parsed.path == "/healthz":
            json_response(self, 200, {"ok": True})
            return
        if parsed.path == "/ai/events":
            if not authorize(self, qs):
                json_response(self, 401, {"error": "unauthorized"})
                return
            limit = int(qs.get("limit", ["50"])[0])
            with db() as conn:
                rows = conn.execute("SELECT * FROM notification_events ORDER BY received_at DESC LIMIT ?", (limit,)).fetchall()
            json_response(self, 200, {"events": [dict(r) for r in rows]})
            return
        json_response(self, 404, {"error": "not found"})

    def do_POST(self) -> None:
        parsed = urlparse(self.path)
        qs = parse_qs(parsed.query)
        if not authorize(self, qs):
            json_response(self, 401, {"error": "unauthorized"})
            return
        if parsed.path == "/ai/events":
            self.ingest_event()
            return
        if parsed.path == "/ai/digests/daily":
            self.daily_digest(qs)
            return
        parts = parsed.path.strip("/").split("/")
        if len(parts) == 4 and parts[:2] == ["ai", "actions"]:
            self.decide_action(parts[2], parts[3])
            return
        json_response(self, 404, {"error": "not found"})

    def do_DELETE(self) -> None:
        parsed = urlparse(self.path)
        qs = parse_qs(parsed.query)
        if parsed.path != "/ai/memory":
            json_response(self, 404, {"error": "not found"})
            return
        if not authorize(self, qs):
            json_response(self, 401, {"error": "unauthorized"})
            return
        package_name = qs.get("package", [""])[0]
        keyword = qs.get("keyword", [""])[0]
        before = int(qs.get("before", ["0"])[0] or 0)
        clauses = []
        args: list[Any] = []
        if package_name:
            clauses.append("package_name = ?")
            args.append(package_name)
        if keyword:
            clauses.append("(title LIKE ? OR text LIKE ?)")
            args.extend([f"%{keyword}%", f"%{keyword}%"])
        if before:
            clauses.append("post_time < ?")
            args.append(before)
        where = " AND ".join(clauses) if clauses else "1=1"
        with db() as conn:
            event_ids = [r["id"] for r in conn.execute(f"SELECT id FROM notification_events WHERE {where}", args).fetchall()]
            cur = conn.execute(f"DELETE FROM notification_events WHERE {where}", args)
            if event_ids:
                placeholders = ",".join("?" for _ in event_ids)
                conn.execute(
                    f"UPDATE memories SET deleted_at = ? WHERE deleted_at IS NULL AND event_id IN ({placeholders})",
                    [now_ms(), *event_ids],
                )
            elif not clauses:
                conn.execute("UPDATE memories SET deleted_at = ? WHERE deleted_at IS NULL", (now_ms(),))
            conn.commit()
        json_response(self, 200, {"deleted_events": cur.rowcount})

    def ingest_event(self) -> None:
        payload = read_json(self)
        event_id = str(payload.get("id") or new_id("evt"))
        status = "forwarded" if payload.get("forwarded") else "failed"
        error = str(payload.get("error") or "")
        if error.startswith("已屏蔽"):
            status = "blocked"
        elif error.startswith("已跳过"):
            status = "skipped"
        with db() as conn:
            conn.execute(
                "INSERT OR REPLACE INTO notification_events(id, received_at, post_time, package_name, app_name, title, text, priority, matched_rule_name, forwarded, http_code, error, status, raw_json) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                (
                    event_id,
                    now_ms(),
                    int(payload.get("postTime") or now_ms()),
                    str(payload.get("packageName") or ""),
                    str(payload.get("appName") or payload.get("packageName") or ""),
                    str(payload.get("title") or ""),
                    str(payload.get("text") or ""),
                    str(payload.get("priority") or "NORMAL"),
                    payload.get("matchedRuleName"),
                    1 if payload.get("forwarded") else 0,
                    payload.get("httpCode"),
                    error,
                    status,
                    json.dumps(payload, ensure_ascii=False),
                ),
            )
            conn.execute(
                "INSERT INTO memories(id, created_at, event_id, kind, summary, tags, deleted_at) VALUES(?,?,?,?,?,?,NULL)",
                (new_id("mem"), now_ms(), event_id, "notification", f"{payload.get('appName')}: {payload.get('title')} {payload.get('text')}", json.dumps([status], ensure_ascii=False)),
            )
            conn.commit()
        json_response(self, 201, {"ok": True, "id": event_id, "status": status})

    def daily_digest(self, qs: dict[str, list[str]]) -> None:
        hours = int(qs.get("hours", ["24"])[0])
        send = qs.get("send", ["0"])[0] in ("1", "true", "yes")
        since = now_ms() - hours * 3600 * 1000
        with db() as conn:
            events = fetch_events(conn, since)
            digest = ai_digest(events)
            todos = store_digest(conn, digest)
        if send:
            send_feishu_digest(digest, todos)
        json_response(self, 200, {"digest": digest, "todos": todos})

    def decide_action(self, action_id: str, decision: str) -> None:
        if decision not in {"confirm", "ignore", "snooze"}:
            json_response(self, 400, {"error": "unsupported decision"})
            return
        with db() as conn:
            row = conn.execute("SELECT * FROM action_requests WHERE id = ?", (action_id,)).fetchone()
            if not row:
                json_response(self, 404, {"error": "action not found"})
                return
            payload = json.loads(row["payload_json"])
            result: dict[str, Any] = {"decision": decision}
            if decision == "confirm" and row["action_type"] in ("bark_reminder", "review", "feishu_task", "calendar_event"):
                code, resp = send_bark(payload.get("title", "fkwk-push Todo"), payload.get("body", ""), payload.get("priority", "NORMAL"))
                result.update({"bark_code": code, "bark_response": resp})
            conn.execute(
                "UPDATE action_requests SET status = ?, decided_at = ? WHERE id = ?",
                (decision, now_ms(), action_id),
            )
            if payload.get("todo_id"):
                conn.execute("UPDATE todo_drafts SET status = ? WHERE id = ?", (decision, payload["todo_id"]))
            conn.commit()
        json_response(self, 200, {"ok": True, "result": result})


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default=os.getenv("AI_HUB_HOST", "0.0.0.0"))
    parser.add_argument("--port", type=int, default=int(os.getenv("AI_HUB_PORT", "8081")))
    args = parser.parse_args()
    with db():
        pass
    httpd = ThreadingHTTPServer((args.host, args.port), Handler)
    print(f"AI Hub listening on {args.host}:{args.port}", flush=True)
    httpd.serve_forever()


if __name__ == "__main__":
    main()
