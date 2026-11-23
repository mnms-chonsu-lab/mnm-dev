-- 사본: mnm-data PR #10 (20261122_#9_inference_log_http_status) migrations/0004_inference_log_http_status.sql — 이 파일을 직접 고치지 않는다 (변경은 mnm-data 에서 다음 번호로)
-- 원본과 다른 점: BEGIN;/COMMIT; 두 줄만 뺐다. Flyway 가 파일마다 트랜잭션으로 감싸므로 원자성은 같다.

-- 0004_inference_log_http_status.sql
-- AI 호출 기록에 응답 HTTP 상태를 남긴다 (mnms-chonsu-lab/mnm-ai#14, 명세 계산식 ⑪).
-- 오류율 = 503·504만 실패. 404(근거 없음)·422(입력 문제)는 호출 수에만 셈 → error 코드만으로는 구분이 안 됨.
-- 스테이징에는 2026-11-22 수동으로 먼저 적용했다. 다시 실행해도 오류가 나지 않게 IF NOT EXISTS 로 쓴다.

ALTER TABLE inference_log ADD COLUMN IF NOT EXISTS http_status SMALLINT;  -- AI 서버 응답 상태 (200·404·422·503·504 등)

