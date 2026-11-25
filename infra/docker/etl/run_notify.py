"""수집 스크립트를 실행하고, 성공하면 디스코드 '수집-기록' 채널에 알린다 (#40).

    python /app/run_notify.py etl/outbreak/collect.py

- 수집 스크립트(mnm-data etl/)는 고치지 않는다. 출력은 그대로 흘려보내고(cron → journalctl),
  출력 중 '성공: <작업> <건수>건' 줄을 찾아 알린다
- 실패 알림은 수집 스크립트가 이미 ALERT_WEBHOOK_URL(스테이징-알림)로 보낸다. 여기서는 성공만 보낸다
- run_requested.py 는 요청이 있어 실제로 수집한 작업만 알린다 ('요청 없음'이면 안 보냄)
- NOTIFY_WEBHOOK_URL 이 비어 있으면 알림 없이 실행만 한다
- 종료 코드는 수집 스크립트 것을 그대로 돌려준다
"""
from __future__ import annotations

import json
import os
import re
import subprocess
import sys
import urllib.request
from datetime import datetime, timedelta, timezone

KST = timezone(timedelta(hours=9))
NAMES = {  # 어드민 수집 현황 화면과 같은 이름
    "outbreak": "가축 질병 발생 정보",
    "disinfection_facility": "거점소독시설",
    "farm_scale": "사육규모별 농장 통계",
}
SUCCESS = re.compile(r"^성공: (\w+) (\d+)건")
USER_AGENT = "mnm-etl-notify/1.0"  # 디스코드는 파이썬 기본 User-Agent 를 403 으로 막는다 (mnm-dev#48)


def message(job: str, rows: int, now: datetime) -> str:
    env = os.environ.get("MONITOR_ENV_NAME", "staging")
    return f"✅ [{env}] 수집 성공: {NAMES.get(job, job)} {rows:,}건 ({now:%H:%M})"


def send(text: str) -> None:
    url = os.environ.get("NOTIFY_WEBHOOK_URL", "").strip()
    if not url:
        return
    key = "text" if os.environ.get("ALERT_WEBHOOK_FORMAT", "discord") == "slack" else "content"
    req = urllib.request.Request(url, data=json.dumps({key: text}).encode(),
                                 headers={"Content-Type": "application/json", "User-Agent": USER_AGENT},
                                 method="POST")
    try:
        urllib.request.urlopen(req, timeout=10).close()
    except Exception as e:  # 알림 실패가 수집 결과를 바꾸지 않게
        print(f"[notify] 성공 알림 전송 실패 ({type(e).__name__})", flush=True)


def main(argv: list[str]) -> int:
    if len(argv) != 1:
        print(__doc__)
        return 2
    proc = subprocess.Popen([sys.executable, argv[0]], stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                            text=True, bufsize=1)
    done = []
    for line in proc.stdout:
        print(line, end="", flush=True)
        m = SUCCESS.match(line.strip())
        if m:
            done.append((m.group(1), int(m.group(2))))
    code = proc.wait()
    now = datetime.now(KST)
    for job, rows in done:
        send(message(job, rows, now))
    return code


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
