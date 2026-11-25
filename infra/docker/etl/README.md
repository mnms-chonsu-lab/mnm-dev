# infra/docker/etl

공공데이터 3종 수집(mnm-data `etl/`)을 스테이징 서버에서 매일 자동으로 돌리는 설정입니다.

- **담당**: @dooooing (실행 환경·예약) · 수집 코드는 @tjdlsry (mnm-data `etl/`)
- 명세: DATA-02(정기 수집) · ADM-09([지금 수집]) · REQ-OPS-06(실패 알림)

## 구성

| 무엇 | 서버 위치 | 내용 |
| --- | --- | --- |
| 컨테이너 `mnm-etl` | `/root/mnm-etl/compose.yml` | 공식 `python:3.12-slim` + 패키지. 대기만 함 |
| 수집 코드 | `/root/mnm-etl/etl/` | mnm-data `etl/` 사본 (읽기 전용으로 붙임) |
| 설정 | `/root/mnm-etl/.env` (권한 600) | 아래 표. 값은 서버에만 |
| 예약 | `/etc/cron.d/mnm-etl` | `mnm-etl.cron` |

| 작업 | 예약 (KST) |
| --- | --- |
| `outbreak` 가축질병발생정보 | 매일 05:10 |
| `disinfection_facility` 거점소독시설 | 매일 05:20 |
| `farm_scale` 사육규모 | 매월 1일 05:40 |
| `run_requested.py` 어드민 [지금 수집] | 5분마다 |

## `.env` 키 (값은 커밋하지 않음)

| 키 | 값 |
| --- | --- |
| `DB_HOST` `DB_PORT` `DB_NAME` `DB_USER` `DB_PASSWORD` | Cloud DB, 계정 `mnm_api` (mnm-data#8 PM 결정) |
| `MAFRA_API_KEY` · `PUBLIC_DATA_API_KEY` | 공공데이터 인증키 (@tjdlsry) |
| `ALERT_WEBHOOK_URL` · `ALERT_WEBHOOK_FORMAT=discord` | 감시 프로그램과 같은 디스코드 웹훅 |
| `S3_ENDPOINT_URL` `AWS_ACCESS_KEY_ID` `AWS_SECRET_ACCESS_KEY` `S3_BUCKET_NAME` | 원본 저장 (없으면 저장만 건너뜀) |

## 확인

```bash
journalctl -t mnm-etl --since today          # 예약 실행 출력
sudo docker exec mnm-etl python etl/outbreak/collect.py   # 손으로 1회
```

- 결과는 DB `etl_job_run`(어드민 수집 현황)에 남습니다. 실패하면 디스코드 `스테이징-알림`으로 옵니다
- 수집 코드가 바뀌면 `/root/mnm-etl/etl/`만 새 사본으로 바꿉니다 (컨테이너 재시작 필요 없음)
