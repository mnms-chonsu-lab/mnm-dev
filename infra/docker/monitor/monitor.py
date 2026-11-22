"""스테이징 감시·알람 (명세 REQ-OPS-06). 표준 라이브러리만 쓴다.

1분마다 확인하고, 기준을 넘으면 웹훅(디스코드·슬랙)으로 보낸다. 웹훅이 비어 있으면 로그에만 남긴다.

| 알람 | 기준 (환경변수, 제안값) |
| --- | --- |
| `api_down` | API `/actuator/health` 가 `UP` 이 아님 (연속 `MONITOR_DOWN_CHECKS`=2회) |
| `ai_down` | AI `/health` 가 `ok` 가 아님 (연속 2회) |
| `http_5xx` | 최근 5분 nginx 응답 중 5xx 비율 > `ALERT_5XX_RATE`=0.05 (요청 `ALERT_MIN_REQUESTS`=10건 이상일 때) |
| `ai_error_rate` | AI `/health` `calls_5m.error_rate` > `ALERT_AI_ERROR_RATE`=0.05 (호출 `ALERT_MIN_CALLS`=10건 이상일 때) |

- 같은 알람은 `ALERT_COOLDOWN_SEC`=1800초 동안 다시 보내지 않는다. 기준 아래로 돌아오면 "해소"를 한 번 보낸다.
- 5xx 는 nginx 가 따로 쓰는 로그 파일(`/logs/access.log`)을 읽는다. 파일이 `MONITOR_LOG_MAX_BYTES`=20MB 를 넘으면 비운다.
- 알람 문구에 개인정보·요청 본문·주소를 넣지 않는다 (개수·비율만).
- 수동 확인: `python monitor.py --test` (테스트 알람 1건), `python monitor.py --once` (한 번만 확인)
"""

from __future__ import annotations

import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
from collections import deque
from dataclasses import dataclass, field
from typing import Callable, Optional

STATUS_RE = re.compile(r'" (\d{3}) \d+ platform=')


def env_float(name: str, default: float) -> float:
    try:
        return float(os.getenv(name, default))
    except ValueError:
        return default


@dataclass
class Config:
    webhook_url: str = ""
    webhook_format: str = "discord"  # discord | slack
    api_health_url: str = "http://api:8080/actuator/health"
    ai_health_url: str = "http://mnm-ai:8000/health"
    log_path: str = "/logs/access.log"
    log_max_bytes: int = 20 * 1024 * 1024
    interval_sec: float = 60
    window_sec: float = 300
    cooldown_sec: float = 1800
    down_checks: int = 2
    rate_5xx: float = 0.05
    min_requests: int = 10
    ai_error_rate: float = 0.05
    min_calls: int = 10
    env_name: str = "staging"

    @classmethod
    def from_env(cls) -> "Config":
        return cls(
            webhook_url=os.getenv("ALERT_WEBHOOK_URL", ""),
            webhook_format=os.getenv("ALERT_WEBHOOK_FORMAT", "discord"),
            api_health_url=os.getenv("MONITOR_API_HEALTH_URL", cls.api_health_url),
            ai_health_url=os.getenv("MONITOR_AI_HEALTH_URL", cls.ai_health_url),
            log_path=os.getenv("MONITOR_LOG_PATH", cls.log_path),
            log_max_bytes=int(env_float("MONITOR_LOG_MAX_BYTES", cls.log_max_bytes)),
            interval_sec=env_float("MONITOR_INTERVAL_SEC", cls.interval_sec),
            cooldown_sec=env_float("ALERT_COOLDOWN_SEC", cls.cooldown_sec),
            down_checks=int(env_float("MONITOR_DOWN_CHECKS", cls.down_checks)),
            rate_5xx=env_float("ALERT_5XX_RATE", cls.rate_5xx),
            min_requests=int(env_float("ALERT_MIN_REQUESTS", cls.min_requests)),
            ai_error_rate=env_float("ALERT_AI_ERROR_RATE", cls.ai_error_rate),
            min_calls=int(env_float("ALERT_MIN_CALLS", cls.min_calls)),
            env_name=os.getenv("MONITOR_ENV_NAME", cls.env_name),
        )


def http_json(url: str, timeout: float = 5) -> tuple[Optional[int], Optional[dict]]:
    try:
        with urllib.request.urlopen(url, timeout=timeout) as r:
            return r.status, json.loads(r.read() or b"{}")
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read() or b"{}")
        except ValueError:
            return e.code, None
    except Exception:  # noqa: BLE001 — 연결 실패·시간 초과는 "응답 없음"
        return None, None


class LogTail:
    """nginx 로그 파일을 이어 읽어 최근 5분 응답 상태를 센다 (읽은 시각 기준)."""

    def __init__(self, path: str, window_sec: float, max_bytes: int):
        self.path, self.window_sec, self.max_bytes = path, window_sec, max_bytes
        self.offset = None  # 처음에는 파일 끝부터 (예전 기록으로 알람이 나지 않게)
        self.events: deque[tuple[float, int]] = deque()

    def poll(self, now: float) -> None:
        try:
            size = os.path.getsize(self.path)
        except OSError:
            return
        if self.offset is None or size < self.offset:
            self.offset = size if self.offset is None else 0
        with open(self.path, "rb") as f:
            f.seek(self.offset)
            chunk = f.read()
            self.offset = f.tell()
        for line in chunk.decode("utf-8", "replace").splitlines():
            m = STATUS_RE.search(line)
            if m:
                self.events.append((now, int(m.group(1))))
        if self.offset > self.max_bytes:  # nginx 는 O_APPEND 로 쓰므로 비워도 이어서 쓴다
            with open(self.path, "r+b") as f:
                f.truncate(0)
            self.offset = 0
        while self.events and now - self.events[0][0] > self.window_sec:
            self.events.popleft()

    def stats(self) -> tuple[int, int]:
        total = len(self.events)
        return total, sum(1 for _, s in self.events if s >= 500)


@dataclass
class AlertState:
    active: dict[str, float] = field(default_factory=dict)  # 알람 이름 → 마지막으로 보낸 시각
    down_count: dict[str, int] = field(default_factory=dict)


class Monitor:
    def __init__(self, cfg: Config, send: Optional[Callable[[str], None]] = None,
                 fetch: Callable[[str], tuple] = http_json, clock: Callable[[], float] = time.monotonic):
        self.cfg, self.fetch, self.clock = cfg, fetch, clock
        self.send = send or self._send_webhook
        self.tail = LogTail(cfg.log_path, cfg.window_sec, cfg.log_max_bytes)
        self.state = AlertState()

    # ------------------------------------------------------------------ 확인
    def check(self) -> dict[str, Optional[str]]:
        """알람 이름 → 문구 (문제 없으면 None)."""
        now = self.clock()
        out: dict[str, Optional[str]] = {}

        status, body = self.fetch(self.cfg.api_health_url)
        up = status == 200 and (body or {}).get("status") == "UP"
        out["api_down"] = self._down("api_down", up, f"API 서버 응답 없음 또는 비정상 (HTTP {status})")

        status, ai = self.fetch(self.cfg.ai_health_url)
        ok = status == 200 and (ai or {}).get("status") == "ok"
        out["ai_down"] = self._down("ai_down", ok, f"AI 서버 응답 없음 또는 비정상 (HTTP {status})")

        w = (ai or {}).get("calls_5m") or {}
        calls, failures = int(w.get("calls") or 0), int(w.get("failures") or 0)
        rate = failures / calls if calls else 0.0
        out["ai_error_rate"] = (f"AI 오류율 {rate:.0%} (최근 5분 {failures}/{calls}건, 기준 {self.cfg.ai_error_rate:.0%})"
                                if calls >= self.cfg.min_calls and rate > self.cfg.ai_error_rate else None)

        self.tail.poll(now)
        total, errors = self.tail.stats()
        r5 = errors / total if total else 0.0
        out["http_5xx"] = (f"서버 오류(5xx) {r5:.0%} (최근 5분 {errors}/{total}건, 기준 {self.cfg.rate_5xx:.0%})"
                           if total >= self.cfg.min_requests and r5 > self.cfg.rate_5xx else None)
        return out

    def _down(self, name: str, healthy: bool, msg: str) -> Optional[str]:
        n = 0 if healthy else self.state.down_count.get(name, 0) + 1
        self.state.down_count[name] = n
        return msg if n >= self.cfg.down_checks else None

    # ------------------------------------------------------------------ 보내기
    def step(self) -> list[str]:
        now, sent = self.clock(), []
        for name, msg in self.check().items():
            last = self.state.active.get(name)
            if msg and (last is None or now - last >= self.cfg.cooldown_sec):
                self._emit(f"[{self.cfg.env_name}] 🚨 {msg}")
                self.state.active[name] = now
                sent.append(name)
            elif not msg and last is not None:
                self._emit(f"[{self.cfg.env_name}] ✅ 해소: {name}")
                del self.state.active[name]
                sent.append(f"{name}:resolved")
        return sent

    def _emit(self, text: str) -> None:
        print(f"[ALERT] {text}", flush=True)
        if self.cfg.webhook_url:
            try:
                self.send(text)
            except Exception as e:  # noqa: BLE001 — 웹훅 실패로 감시가 멈추면 안 된다
                print(f"[monitor] 웹훅 전송 실패 ({type(e).__name__})", flush=True)

    def _send_webhook(self, text: str) -> None:
        key = "text" if self.cfg.webhook_format == "slack" else "content"
        req = urllib.request.Request(self.cfg.webhook_url, data=json.dumps({key: text}).encode(),
                                     headers={"Content-Type": "application/json"}, method="POST")
        urllib.request.urlopen(req, timeout=10).close()


def main(argv: list[str]) -> int:
    cfg = Config.from_env()
    mon = Monitor(cfg)
    if "--test" in argv:
        mon._emit(f"[{cfg.env_name}] 테스트 알람입니다 (monitor.py --test)")
        return 0
    print(f"[monitor] 시작: {cfg.interval_sec:.0f}초마다, 웹훅 {'있음' if cfg.webhook_url else '없음(로그만)'}", flush=True)
    while True:
        mon.step()
        if "--once" in argv:
            return 0
        time.sleep(cfg.interval_sec)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
