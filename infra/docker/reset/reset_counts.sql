-- 초기화 전후 건수 (#40). [초기화] = 지우고 데모 시드로 다시 채우는 표, [유지] = 건드리지 않는 표
SELECT t, n FROM (VALUES
  ('[초기화] receiver_check',   (SELECT count(*) FROM receiver_check)),
  ('[초기화] deliveries',       (SELECT count(*) FROM deliveries)),
  ('[초기화] report_summary',   (SELECT count(*) FROM report_summary)),
  ('[초기화] behavior_record',  (SELECT count(*) FROM behavior_record)),
  ('[초기화] diary_entry',      (SELECT count(*) FROM diary_entry)),
  ('[초기화] field_media',      (SELECT count(*) FROM field_media)),
  ('[초기화] symptom_report',   (SELECT count(*) FROM symptom_report)),
  ('[초기화] history_report',   (SELECT count(*) FROM history_report)),
  ('[초기화] tasks',            (SELECT count(*) FROM tasks)),
  ('[초기화] alerts',           (SELECT count(*) FROM alerts)),
  ('[초기화] audit_log',        (SELECT count(*) FROM audit_log)),
  ('[초기화] trace_lookups',    (SELECT count(*) FROM trace_lookups)),
  ('[초기화] inference_log',    (SELECT count(*) FROM inference_log)),
  ('[초기화] cows',             (SELECT count(*) FROM cows)),
  ('[초기화] consents',         (SELECT count(*) FROM consents)),
  ('[유지] users',            (SELECT count(*) FROM users)),
  ('[유지] farm_info',        (SELECT count(*) FROM farm_info)),
  ('[유지] receiver_org',     (SELECT count(*) FROM receiver_org)),
  ('[유지] biosecurity_rule', (SELECT count(*) FROM biosecurity_rule))
) AS c(t, n);
