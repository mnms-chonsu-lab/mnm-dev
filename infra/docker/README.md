# infra/docker

스테이징 서버 배포용 설정. `.github/workflows/deploy.yml`이 이 폴더를 서버 `~/mnm`에 복사해서 씁니다.

- **담당**: @dooooing

| 파일 | 내용 |
| --- | --- |
| `compose.deploy.yml` | 서버에서 띄우는 `api`(GHCR 이미지) · `nginx` · `monitor` · `certbot`(인증서 발급·갱신 때만) |
| `monitor/monitor.py` | 감시·알람. 1분마다 API·AI 상태, 5xx 비율, AI 오류율 확인 → 웹훅 |
| `nginx/locations.conf` | 경로 접두사(`/api/farmer` 등)로 API 서버에 전달, 스웨거 바로가기, `/health` |
| `nginx/*.conf.template`, `nginx/start.sh` | 인증서가 있으면 HTTPS, 없으면 HTTP 설정으로 시작 |

- API 서버의 Dockerfile은 `services/api/Dockerfile`, 로컬 전체 실행은 레포 루트 `docker-compose.yml`입니다.
- 서버의 `.env` 키 목록은 레포 루트 `.env.example`을 따릅니다.

## 감시·알람 (`monitor`)

명세 REQ-OPS-06. 표준 라이브러리만 쓰는 파이썬 스크립트 하나입니다 (`python:3.12-alpine`, 메모리 상한 64MB).

| 알람 | 기준 (제안값, 실측으로 조정) | 원천 |
| --- | --- | --- |
| `api_down` | API `/actuator/health`가 `UP`이 아님, 연속 2회 | API 서버 |
| `ai_down` | AI `/health`가 `ok`가 아님, 연속 2회 | AI 서버 |
| `http_5xx` | 최근 5분 5xx 비율 5% 초과 (요청 10건 이상) | nginx 로그 파일 |
| `ai_error_rate` | 최근 5분 AI 오류율 5% 초과 (호출 10건 이상), 계산식 ⑪ | AI `/health`의 `calls_5m` |

- 같은 알람은 30분 동안 다시 보내지 않고, 기준 아래로 돌아오면 "해소"를 한 번 보냅니다.
- 보내는 곳: 서버 `.env`의 `ALERT_WEBHOOK_URL`(디스코드·슬랙 웹훅). 비어 있으면 `docker compose logs monitor`에만 남습니다. 슬랙이면 `ALERT_WEBHOOK_FORMAT=slack`.
- 알람 문구에는 개수·비율만 넣습니다 (개인정보·요청 본문·주소 없음).
- 기준값은 환경변수로 바꿀 수 있습니다 (`ALERT_5XX_RATE`, `ALERT_AI_ERROR_RATE`, `ALERT_MIN_REQUESTS`, `ALERT_MIN_CALLS`, `ALERT_COOLDOWN_SEC`, `MONITOR_INTERVAL_SEC`). 키 설명은 `monitor/monitor.py` 맨 위.

```bash
# 서버에서 확인
docker compose logs -f monitor                       # 알람 기록
docker compose exec monitor python /app/monitor.py --test   # 테스트 알람 1건
python -m unittest infra/docker/monitor/test_monitor.py     # 로컬 테스트
```

