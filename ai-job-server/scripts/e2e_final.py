# -*- coding: utf-8 -*-
"""
最终 E2E 验收（MD + RAG + SSE + Sources + QA 落库 + 用户隔离 403）

覆盖：
  登录/JWT → 上传 MD → PARSED → ingest → READY
  → retrieve Top-K → ask（Sources + qaId）
  → ask/stream（meta/delta/done）
  → A 可见自己的知识库；B 检索 0 命中；B 访问 A documentId → 403

用法（仓库内）：
  python ai-job-server/scripts/e2e_final.py
"""
from __future__ import annotations

import json
import sys
import time
import uuid
from pathlib import Path

import urllib.error
import urllib.request

BASE = "http://127.0.0.1:8080"
FIXTURE = Path(__file__).resolve().parent / "fixtures" / "e2e-javaguide-hashmap.md"
REPORT_PATH = Path(__file__).resolve().parent / "e2e_final_report.txt"
REPORT: list[str] = []


def log(msg: str) -> None:
    print(msg, flush=True)
    REPORT.append(msg)


def http_json(method: str, path: str, token: str | None = None, body=None, timeout=180):
    data = None
    headers: dict[str, str] = {}
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json; charset=utf-8"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8", errors="replace")
            try:
                return resp.status, json.loads(raw) if raw else None
            except json.JSONDecodeError:
                return resp.status, raw
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", errors="replace")
        try:
            parsed = json.loads(raw) if raw else None
        except json.JSONDecodeError:
            parsed = raw
        return e.code, parsed


def upload_md_raw(token: str, file_path: Path):
    boundary = "----E2E" + uuid.uuid4().hex
    body = bytearray()
    body.extend(f"--{boundary}\r\n".encode())
    body.extend(
        b'Content-Disposition: form-data; name="file"; filename="e2e-javaguide-hashmap.md"\r\n'
    )
    body.extend(b"Content-Type: text/markdown\r\n\r\n")
    body.extend(file_path.read_bytes())
    body.extend(f"\r\n--{boundary}--\r\n".encode())
    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": f"multipart/form-data; boundary={boundary}",
    }
    req = urllib.request.Request(
        BASE + "/api/knowledge/documents", data=bytes(body), headers=headers, method="POST"
    )
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", errors="replace")
        try:
            return e.code, json.loads(raw)
        except json.JSONDecodeError:
            return e.code, raw


def read_sse(token: str, body: dict, timeout=180) -> tuple[int, dict]:
    data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json; charset=utf-8",
        "Accept": "text/event-stream",
    }
    req = urllib.request.Request(
        BASE + "/api/knowledge/ai/ask/stream", data=data, headers=headers, method="POST"
    )
    events: dict[str, list] = {"meta": [], "delta": [], "done": [], "error": []}
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            code = resp.status
            buf = ""
            while True:
                chunk = resp.read(256)
                if not chunk:
                    break
                buf += chunk.decode("utf-8", errors="replace")
                while "\n\n" in buf:
                    block, buf = buf.split("\n\n", 1)
                    event_name = "message"
                    data_lines: list[str] = []
                    for line in block.splitlines():
                        if line.startswith("event:"):
                            event_name = line[6:].strip()
                        elif line.startswith("data:"):
                            data_lines.append(line[5:].lstrip())
                    if not data_lines:
                        continue
                    payload = "\n".join(data_lines)
                    try:
                        parsed = json.loads(payload)
                    except json.JSONDecodeError:
                        parsed = payload
                    events.setdefault(event_name, []).append(parsed)
            return code, events
    except urllib.error.HTTPError as e:
        return e.code, {"error": [e.read().decode("utf-8", errors="replace")]}


def main() -> int:
    t0 = time.time()
    if not FIXTURE.exists():
        log(f"FAIL fixture missing: {FIXTURE}")
        return 1

    # ① 登录 JWT
    code, login_a = http_json(
        "POST", "/api/auth/login", body={"username": "myq", "password": "123456"}
    )
    assert code == 200 and login_a.get("token"), login_a
    token_a = login_a["token"]
    user_a = login_a["userId"]
    log(f"PASS ① login A JWT userId={user_a}")

    http_json("POST", "/api/auth/register", body={"username": "chroma_b", "password": "123456"})
    code, login_b = http_json(
        "POST", "/api/auth/login", body={"username": "chroma_b", "password": "123456"}
    )
    assert code == 200 and login_b.get("token"), login_b
    token_b = login_b["token"]
    user_b = login_b["userId"]
    log(f"PASS ① login B JWT userId={user_b}")

    # ② 上传 JavaGuide 风格 MD → PARSED
    code, doc = upload_md_raw(token_a, FIXTURE)
    if code != 200 or not isinstance(doc, dict):
        log(f"FAIL ② upload MD http={code} {str(doc)[:300]}")
        return 1
    doc_id = doc["id"]
    if doc.get("status") != "PARSED" or doc.get("fileType") != "MD":
        log(f"FAIL ② expect PARSED/MD got status={doc.get('status')} type={doc.get('fileType')} err={doc.get('errorMessage')}")
        return 1
    log(f"PASS ② upload MD id={doc_id} status=PARSED fileType=MD pageCount={doc.get('pageCount')}")

    # ③ ingest → Chunk → Embedding → Chroma → READY
    code, ing = http_json(
        "POST", f"/api/knowledge/documents/{doc_id}/ingest", token_a, timeout=300
    )
    d = (ing or {}).get("document") if isinstance(ing, dict) else None
    if code != 200 or not isinstance(d, dict) or d.get("status") != "READY":
        log(f"FAIL ③ ingest http={code} {str(ing)[:300]}")
        return 1
    chunks = ing.get("chunkCount") or d.get("chunkCount")
    chroma_n = ing.get("chromaVectorCount")
    log(
        f"PASS ③ ingest READY chunks={chunks} chromaVectors={chroma_n} sampleId={ing.get('sampleVectorId')}"
    )

    code, chunk_list = http_json("GET", f"/api/knowledge/documents/{doc_id}/chunks", token_a)
    n_chunks = len(chunk_list) if isinstance(chunk_list, list) else 0
    if code != 200 or n_chunks <= 0:
        log(f"FAIL ③ list chunks http={code} n={n_chunks}")
    else:
        log(f"PASS ③ list chunks n={n_chunks}")

    # ④ Top-K retrieve
    code, ret = http_json(
        "POST",
        "/api/knowledge/ai/retrieve",
        token_a,
        body={"question": "HashMap 为什么线程不安全？", "topK": 5, "documentId": doc_id},
    )
    if code != 200 or not isinstance(ret, dict) or (ret.get("hitCount") or 0) < 1:
        log(f"FAIL ④ retrieve http={code} {str(ret)[:300]}")
    else:
        log(f"PASS ④ retrieve hitCount={ret.get('hitCount')} topK={ret.get('topK')}")

    # ⑤ sync ask → Context → DeepSeek → Sources → qaId（问答历史）
    code, ask = http_json(
        "POST",
        "/api/knowledge/ai/ask",
        token_a,
        body={"question": "HashMap 为什么线程不安全？", "topK": 5, "documentId": doc_id},
        timeout=180,
    )
    if (
        code != 200
        or not isinstance(ask, dict)
        or not ask.get("sources")
        or not ask.get("answer")
        or not ask.get("qaId")
    ):
        log(f"FAIL ⑤ ask http={code} {str(ask)[:400]}")
    else:
        s0 = ask["sources"][0]
        log(
            f"PASS ⑤ ask Sources={len(ask['sources'])} hit={ask.get('hitCount')} "
            f"qaId={ask.get('qaId')} topScore={round(s0.get('score') or 0, 4)} chunkId={s0.get('chunkId')}"
        )
        log(f"INFO answer: {(ask.get('answer') or '')[:160]}")

    # ⑥ SSE stream
    code, ev = read_sse(
        token_a,
        {"question": "ConcurrentHashMap 和 HashMap 有什么区别？", "topK": 5, "documentId": doc_id},
    )
    has_meta = bool(ev.get("meta"))
    has_delta = bool(ev.get("delta"))
    has_done = bool(ev.get("done"))
    has_err = bool(ev.get("error"))
    done = ev.get("done")[0] if has_done else {}
    if code != 200 or has_err or not (has_meta and has_delta and has_done):
        log(f"FAIL ⑥ SSE http={code} meta={has_meta} delta={has_delta} done={has_done} err={ev.get('error')}")
    else:
        sources = done.get("sources") if isinstance(done, dict) else None
        qa_id = done.get("qaId") if isinstance(done, dict) else None
        log(
            f"PASS ⑥ SSE meta/delta/done ok deltas={len(ev.get('delta') or [])} "
            f"sources={len(sources or [])} qaId={qa_id}"
        )

    # ⑦ A 可见自己的知识库
    code, docs_a = http_json("GET", "/api/knowledge/documents", token_a)
    ids_a = {d.get("id") for d in docs_a} if isinstance(docs_a, list) else set()
    if code == 200 and doc_id in ids_a:
        log(f"PASS ⑦ A lists own knowledge docs n={len(ids_a)} contains={doc_id}")
    else:
        log(f"FAIL ⑦ A list docs http={code} contains={doc_id in ids_a}")

    # ⑧ B 看不到 A：retrieve 0；强行 documentId → 403
    code, ret_b = http_json(
        "POST",
        "/api/knowledge/ai/retrieve",
        token_b,
        body={"question": "HashMap 为什么线程不安全？", "topK": 5},
    )
    hits_b = (ret_b or {}).get("hitCount") if isinstance(ret_b, dict) else None
    if code == 200 and hits_b == 0:
        log("PASS ⑧ B retrieve hitCount=0")
    else:
        log(f"FAIL ⑧ B retrieve http={code} hitCount={hits_b}")

    code, ret_b_doc = http_json(
        "POST",
        "/api/knowledge/ai/retrieve",
        token_b,
        body={"question": "HashMap", "topK": 3, "documentId": doc_id},
    )
    if code == 403:
        log("PASS ⑧ B retrieve with A's documentId → 403")
    else:
        log(f"FAIL ⑧ B retrieve A's documentId http={code} body={str(ret_b_doc)[:200]}")

    code, get_b = http_json("GET", f"/api/knowledge/documents/{doc_id}", token_b)
    if code == 403:
        log("PASS ⑧ B GET A's document → 403")
    else:
        log(f"FAIL ⑧ B GET A's document http={code}")

    code, ask_b = http_json(
        "POST",
        "/api/knowledge/ai/ask",
        token_b,
        body={"question": "HashMap", "documentId": doc_id, "topK": 3},
    )
    if code == 403:
        log("PASS ⑧ B ask with A's documentId → 403")
    else:
        log(f"FAIL ⑧ B ask A's documentId http={code}")

    code, docs_b = http_json("GET", "/api/knowledge/documents", token_b)
    ids_b = {d.get("id") for d in docs_b} if isinstance(docs_b, list) else set()
    if code == 200 and doc_id not in ids_b:
        log(f"PASS ⑧ B list docs does not contain A's id (n={len(ids_b)})")
    else:
        log(f"FAIL ⑧ B list leaked A's doc http={code}")

    elapsed = time.time() - t0
    fails = [x for x in REPORT if x.startswith("FAIL")]
    log(f"==== SUMMARY fails={len(fails)} elapsed={elapsed:.1f}s ====")
    REPORT_PATH.write_text("\n".join(REPORT), encoding="utf-8")
    log(f"REPORT_FILE={REPORT_PATH}")
    return 1 if fails else 0


if __name__ == "__main__":
    # remove dead upload_md stub usage
    sys.exit(main())
