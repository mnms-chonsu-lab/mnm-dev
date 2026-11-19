"""mnm-ai/schemas/*.json -> OpenAPI 3.1 내부 AI API 명세 생성.

사용: python docs/api/gen_ai_internal.py <mnm-ai/schemas 폴더> docs/api/ai-internal.yaml
필요: pip install pyyaml
"""
import copy
import json
import sys
from pathlib import Path

import yaml

SCHEMAS = Path(sys.argv[1])
OUT = Path(sys.argv[2])

# 엔드포인트별 메타데이터 (schemas에 없는 것만)
META = {
    # retry503: 503(외부 API 오류)이 "바로" 왔을 때 서버가 1회 다시 부르는지
    # retry_note: 재시도 규칙 설명. 504(시간 초과)는 모든 엔드포인트에서 재시도하지 않음
    #   (앱 타임아웃이 8초라 다시 부르면 앱이 먼저 끊김 — mnm-dev#5 합의)
    "stt": dict(file="stt.json", prefix="Stt", summary="음성 → 글", timeout=8,
                retry503=True, retry_note="503이 바로 온 경우·연결 실패(응답 없음)일 때 1회",
                fallback="글로 쓰기 화면 (F-S02T)", spec=None),
    "route": dict(file="router.json", prefix="Route", summary="농가 발화 → 일지·신고 항목 분류",
                  timeout=8, retry503=True, retry_note="503이 바로 온 경우·연결 실패(응답 없음)일 때 1회",
                  fallback="선택형 입력 화면 (F-S03E)", spec=None),
    "part_check": dict(file="part_check.json", prefix="PartCheck", summary="요청한 부위가 찍혔는지 확인",
                       timeout=10, retry503=False,
                       retry_note="안 함 (대체 경로가 같은 호출 안에서 처리)",
                       fallback="서버가 \"확인 안 됨\"으로 저장하고 진행", spec="REQ-AI-03",
                       note=("모델(SigLIP 2) 목표 속도 p95 1.5초. 넘거나 추론 오류면 같은 호출 안에서 "
                             "OpenAI Vision으로 판정하고 `fallback: true` (`PART_FALLBACK_VISION=1`, 실측 약 1초·최대 8초). "
                             "앱은 최대 3초 기다리고, 늦게 온 결과는 요약 화면(F-S11)에 표시 (#15 유민님 정리)")),
    "behavior": dict(file="behavior.json", prefix="Behavior", summary="영상 → 서기·눕기·먹기 비율",
                     timeout=8, retry503=False, retry_note="연결 실패(응답 없음)일 때만 1회. 503·504는 안 함",
                     fallback="서버가 \"확인 안 됨\"으로 저장하고 진행", spec="REQ-AI-11"),
    "guide": dict(file="guide.json", prefix="Guide", summary="공식 지침 조항 찾기 (원문 그대로 반환)",
                  timeout=8, retry503=True, retry_note="503·연결 실패(응답 없음)일 때 1회. 근거 없음(404)은 안 함",
                  fallback="[바로 신고]·담당 축협 연결", spec=None),
    "summary": dict(file="summary.json", prefix="Summary", summary="접수자용 한 장 요약·신고서 6항목",
                    timeout=8, retry503=False, retry_note="안 함 (규칙 기반이라 결과 같음)",
                    fallback="원본 기록을 그대로 표시", spec="REQ-AI-05"),
}

components: dict = {}


def hoist(schema: dict, prefix: str, name: str) -> dict:
    """$defs를 components로 올리고 $ref를 고친 뒤, 본 스키마 $ref를 돌려준다."""
    s = copy.deepcopy(schema)
    defs = s.pop("$defs", {})
    rename = {k: f"{prefix}{k}" if not k.startswith(prefix) else k for k in defs}

    def fix(node):
        if isinstance(node, dict):
            ref = node.get("$ref")
            if isinstance(ref, str) and ref.startswith("#/$defs/"):
                node["$ref"] = f"#/components/schemas/{rename[ref.split('/')[-1]]}"
            for v in node.values():
                fix(v)
        elif isinstance(node, list):
            for v in node:
                fix(v)

    for k, v in defs.items():
        fix(v)
        target = rename[k]
        if target in components and components[target] != v:
            raise SystemExit(f"components 이름 충돌: {target}")
        components[target] = v
    fix(s)
    components[name] = s
    return {"$ref": f"#/components/schemas/{name}"}


def err(desc: str) -> dict:
    return {"description": desc, "content": {"application/json": {"schema": {"$ref": "#/components/schemas/AiError"}}}}


paths = {}
for feature, m in META.items():
    d = json.loads((SCHEMAS / m["file"]).read_text())
    ep = d["endpoint"]
    p = m["prefix"]
    # 오류 코드: 대부분 const 하나, guide 는 enum(RAG-NONE, LLM-FAIL) — 근거 없음(404)만 RAG-NONE, 나머지는 장애 코드
    code_schema = d["error"]["properties"]["code"]
    codes = [code_schema["const"]] if "const" in code_schema else list(code_schema["enum"])
    code = next((c for c in codes if c != "RAG-NONE"), codes[0])
    hoist(d["error"], p, f"{p}Error")
    resp_ref = hoist(d["response"], p, f"{p}Response")

    if feature == "stt":
        body = {"required": True, "content": {"multipart/form-data": {"schema": {
            "type": "object", "required": ["file"],
            "properties": {"file": {"type": "string", "contentMediaType": "audio/webm",
                                    "description": d["request"]["file"]}}}}}}
    else:
        body = {"required": True, "content": {"application/json": {
            "schema": hoist(d["request"], p, f"{p}Request")}}}

    responses = {"200": {"description": "성공", "content": {"application/json": {"schema": resp_ref}}}}
    if feature == "guide":
        responses["404"] = err("`RAG-NONE` — 근거 조항 없음. `detail.fallback = true`. 재시도하지 않음")
        responses["422"] = err(f"`{code}` — 요청 형식 오류 (`detail.reason = bad_request`). 재시도하지 않음")
    else:
        example = {"stt": "파일 형식·60초 초과·무음", "summary": "요청 형식 오류. 신고서 빈칸은 오류가 아님(200 + `missing`)"}
        responses["422"] = err(f"`{code}` — 입력 때문에 실패 ({example.get(feature, '요청 형식 오류')}). 재시도하지 않음")
    responses["503"] = err(f"`{code}` — 외부 API·모델 호출 실패. "
                           + ("바로 온 경우 서버가 1회 재시도 (연결 실패도 1회)" if m["retry503"] else "재시도하지 않음"))
    responses["504"] = err(f"`{code}` — 시간 제한 초과 ({m['timeout']}초). 재시도하지 않음")
    responses["401"] = err(f"`{code}` — `X-Internal-Token`이 없거나 틀림 (`detail.reason = unauthorized`). 재시도하지 않음")
    responses = dict(sorted(responses.items()))

    paths[ep] = {"post": {
        "operationId": feature,
        "tags": ["AI 내부"],
        "summary": m["summary"],
        "description": "\n".join([
            f"- 시간 제한: {m['timeout']}초" + ("" if m.get("note") else " (`common.TIMEOUT_SEC`)"),
            *([f"- {m['note']}"] if m.get("note") else []),
            f"- 서버 재시도: {m['retry_note']} (mnm-dev#5 합의, 스테이징 실측으로 조정)",
            f"- 실패 시 대체 경로: {m['fallback']}",
            f"- 형식 원본: `mnm-ai/schemas/{m['file']}`",
            f"- 관련 명세: {m['spec'] + ', ' if m['spec'] else ''}REQ-AI-12",
        ]),
        "requestBody": body,
        "responses": responses,
    }}

paths["/health"] = {"get": {
    "operationId": "health", "tags": ["운영"], "summary": "AI 서버 상태 확인",
    "security": [],
    "responses": {"200": {"description": "정상", "content": {"application/json": {"schema": {
        "type": "object", "required": ["status"],
        "properties": {"status": {"const": "ok"},
                       "models": {"type": "object", "additionalProperties": {"type": "string"},
                                  "description": "기능별 `model_version`"}}}}}}}}}

components["AiError"] = {
    "type": "object", "required": ["code", "message"], "additionalProperties": False,
    "description": "모든 AI 오류 공통 형식. 화면 분기는 `code`만 본다.",
    "properties": {
        "code": {"enum": ["STT-FAIL", "LLM-FAIL", "PART-FAIL", "BEHAVIOR-FAIL", "RAG-NONE"]},
        "message": {"type": "string"},
        "detail": {"anyOf": [{"type": "object", "additionalProperties": True}, {"type": "null"}]},
    },
}

doc = {
    "openapi": "3.1.0",
    "info": {
        "title": "mnm-ai 내부 API",
        "version": "0.1.0",
        "description": (
            "`services/api`(Spring)가 같은 Docker 네트워크 안에서 호출하는 AI 서버(FastAPI) 명세입니다.\n"
            "외부(nginx)로는 열지 않습니다.\n\n"
            "- 요청·응답 형식은 `mnm-ai/schemas/`가 기준이고, 이 파일은 그 형식을 그대로 옮긴 것입니다.\n"
            "- 재생성: `python docs/api/gen_ai_internal.py <mnm-ai/schemas> docs/api/ai-internal.yaml`"
        ),
    },
    "servers": [{"url": "http://mnm-ai:8000", "description": "Docker 내부 주소 (`AI_BASE_URL`)"}],
    "security": [{"internalToken": []}],
    "paths": paths,
    "components": {
        "securitySchemes": {"internalToken": {
            "type": "apiKey", "in": "header", "name": "X-Internal-Token",
            "description": "서버 간 공유 토큰 (`AI_INTERNAL_TOKEN`). 값은 비밀 저장소에만 보관"}},
        "schemas": dict(sorted(components.items())),
    },
}

OUT.write_text("# 자동 생성 파일 — 직접 고치지 말고 mnm-ai/schemas를 고친 뒤 재생성\n"
               + yaml.safe_dump(doc, allow_unicode=True, sort_keys=False, width=120))
print(f"wrote {OUT}: paths={len(paths)} schemas={len(components)}")
