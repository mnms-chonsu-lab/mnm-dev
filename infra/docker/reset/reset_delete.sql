-- 테스트 기록 지우기 (#40). 연결(FK)된 쪽부터 지운다.
-- 남기는 것: 계정(users·user_settings)·기관(receiver_org)·농장(farm_info), 공공데이터 3종,
--            방역지침(biosecurity_rule), 공휴일·지역 코드, etl_job_run, 학습 데이터 메타(dataset_version 등)
-- 소(cows)·동의(consents)는 모든 계정에서 지우고, 데모 계정 것은 데모 시드가 다시 넣는다
DELETE FROM receiver_check;
DELETE FROM deliveries;
DELETE FROM report_summary;
DELETE FROM behavior_record;
DELETE FROM diary_entry;
DELETE FROM field_media;
DELETE FROM symptom_report;
DELETE FROM history_report;
DELETE FROM tasks;
DELETE FROM alerts;
DELETE FROM audit_log;
DELETE FROM trace_lookups;
DELETE FROM inference_log;
DELETE FROM cows;
DELETE FROM consents;
