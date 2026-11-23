# infra/docker/reset

스테이징 데이터 초기화 명령입니다 (#40). **테스트 기록 지우기 → 데모 시드 다시 넣기**를 한 트랜잭션으로 합니다.

- **담당**: @dooooing
- 데모 시드: `mnm-data/seed/` (@tjdlsry). 이 폴더는 시드를 만들지 않고 실행만 합니다
- 표 구조는 바꾸지 않습니다. 기본 계정 `mnm_api`(데이터만 바꿈)

## 무엇을 지우고 무엇을 남기나

| 구분 | 표 |
| --- | --- |
| [초기화] 지우고 시드로 다시 채움 | `receiver_check` `deliveries` `report_summary` `behavior_record` `diary_entry` `field_media` `symptom_report` `history_report` `tasks` `alerts` `audit_log` `trace_lookups` `inference_log` `cows` `consents` |
| [유지] 건드리지 않음 | 계정(`users` `user_settings`) · 기관(`receiver_org`) · 농장(`farm_info`) · 공공데이터 3종 · `biosecurity_rule` · `holidays` · `region_code_map` · `etl_job_run` · 학습 데이터 메타 |

- 소·동의는 모든 계정에서 지웁니다. 데모 계정 것은 시드가 다시 넣고, 등록 시연용 계정은 빈 상태로 남습니다
- 저장소는 `mnm-raw-stg`의 `field/` · `audio/`만 지웁니다. `demo/`는 지우지 않습니다

## 실행 (스테이징 서버)

```bash
cd ~/reset
./reset.sh --dry-run                 # 지금 건수만 (바뀌는 것 없음)
./reset.sh ~/seed/demo_seed.sql      # 지우기 + 시드. yes 입력해야 진행
```

- 중간에 하나라도 실패하면 전부 되돌립니다 (`psql -1` + `ON_ERROR_STOP`)
- 끝나면 앱에서 데모 계정 로그인 → 기록 1건 확인

저장소 파일:

```bash
# 키는 ~/os.env (권한 600)에 NCP_ACCESS_KEY / NCP_SECRET_KEY, 쓰고 나면 바로 지움
sudo docker run --rm --env-file ~/os.env -v "$PWD/reset_storage.py:/app/r.py:ro" python:3.12-slim \
  sh -c "pip install -q boto3 && python /app/r.py"            # 목록만
# 지우려면 마지막을 python /app/r.py --apply
rm -f ~/os.env
```

## 시험 (로컬, PostgreSQL 16 + V1·V2, 가짜 데이터)

| 시험 | 결과 |
| --- | --- |
| 시드에 오류 | 전부 되돌림, 지워진 것 없음 |
| 정상 실행 | [초기화] 15개 표 비우고 시드 다시 넣음, [유지] 그대로. 18ms |
| 다시 실행 | 결과 같음 (데모 농장 소 3, 등록 시연 농장 소 0) |
| 저장소 (가짜 S3) | `field/` · `audio/`만 지움, `demo/`·그 밖 경로 남음 |
