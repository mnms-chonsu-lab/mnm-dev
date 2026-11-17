-- 사본: mnm-data develop 67c437c migrations/0001_init_operational.sql — 이 파일을 직접 고치지 않는다 (변경은 mnm-data 에서 다음 번호로)
-- 원본과 다른 점: BEGIN;/COMMIT; 두 줄만 뺐다. Flyway 가 파일마다 트랜잭션으로 감싸므로 원자성은 같다.

-- 0001_init_operational.sql
-- 촌수의사 운영 DB 초기 스키마
-- 기준: MVP 기능명세서 v1.0(2026-11-16) 데이터 모델 + 산출값 요구사항. 이름 결정은 migrations/rename_decisions.md
-- 대상: PostgreSQL (Cloud DB for PostgreSQL 17). 로컬 PostgreSQL 16에서 적용 확인.
-- 빈 DB에 한 번에 적용한다.
-- 개인정보 컬럼: users.name, users.phone, farm_info.address (접근 제어는 백엔드와 협의)
-- biosecurity_rule.embedding(벡터)은 0003에서 추가한다.


-- =========================================================
-- 1. 접수 기관·계정
-- =========================================================

CREATE TABLE receiver_org (
  org_id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name               VARCHAR(100) NOT NULL,
  type               VARCHAR(20)  NOT NULL,          -- 축협|방역기관|시군청
  region_codes       CHAR(10)[]   NOT NULL DEFAULT '{}',  -- 관할 법정동 코드 목록
  phone              VARCHAR(20),
  inboxes            JSONB        NOT NULL DEFAULT '[]'   -- [{inbox_id, type(history|biosecurity), name, phone}]
);
CREATE INDEX idx_receiver_org_regions ON receiver_org USING GIN (region_codes);

CREATE TABLE users (
  users_id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  role               VARCHAR(20)  NOT NULL CHECK (role IN ('farmer', 'receiver', 'admin')),
  name               VARCHAR(50)  NOT NULL,          -- 개인정보
  phone              VARCHAR(20),                    -- 개인정보. 농가 로그인 키(농장식별번호와 함께)
  login_id           VARCHAR(50) UNIQUE,             -- 접수자·운영자만
  password_hash      VARCHAR(255),                   -- 접수자·운영자만. 원문 저장 금지
  must_change_pw     BOOLEAN      NOT NULL DEFAULT FALSE,
  org_id             BIGINT REFERENCES receiver_org (org_id),  -- 접수자만
  inbox_id           VARCHAR(50),                    -- 접수자 소속 창구 (receiver_org.inboxes의 inbox_id, FK 없음)
  status             VARCHAR(20)  NOT NULL DEFAULT 'active',  -- active|suspended|invited
  failed_count       INT          NOT NULL DEFAULT 0,
  locked_until       TIMESTAMPTZ,                    -- 5회 실패 시 10분 잠금
  created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
  last_login_at      TIMESTAMPTZ
);
CREATE INDEX idx_users_org ON users (org_id);

CREATE TABLE user_settings (
  users_id           BIGINT   PRIMARY KEY REFERENCES users (users_id),
  font_step          SMALLINT NOT NULL DEFAULT 1 CHECK (font_step BETWEEN 0 AND 4),
  high_contrast      BOOLEAN  NOT NULL DEFAULT FALSE,
  auto_speak         BOOLEAN  NOT NULL DEFAULT FALSE
);

CREATE TABLE consents (
  consents_id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  users_id           BIGINT      NOT NULL REFERENCES users (users_id),
  consent_type       VARCHAR(50) NOT NULL,
  version            VARCHAR(20) NOT NULL,
  agreed_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_consents_user ON consents (users_id);

CREATE TABLE alerts (
  alerts_id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  users_id           BIGINT       NOT NULL REFERENCES users (users_id),
  kind               VARCHAR(20)  NOT NULL CHECK (kind IN ('diff', 'deadline', 'result')),
  title              VARCHAR(200) NOT NULL,
  body               TEXT,
  ref_id             BIGINT,                         -- kind별 대상: diff→cows.cow_id, deadline→tasks.tasks_id, result→deliveries.deliveries_id (FK 없음)
  feedback           VARCHAR(10) CHECK (feedback IN ('seen', 'ok')),
  created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
  read_at            TIMESTAMPTZ                     -- 열람 시각 (F-S01 미확인 알림 수)
);
CREATE INDEX idx_alerts_user ON alerts (users_id, read_at);

CREATE TABLE audit_log (
  audit_log_id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  users_id           BIGINT      NOT NULL REFERENCES users (users_id),
  action             VARCHAR(30) NOT NULL,           -- view|update|deliver|approve|promote 등
  target_type        VARCHAR(30) NOT NULL,
  target_id          BIGINT,
  at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
  ip                 INET
);
CREATE INDEX idx_audit_user ON audit_log (users_id, at);

-- =========================================================
-- 2. 농장·개체
-- =========================================================

CREATE TABLE farm_info (
  farm_id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  farm_no            CHAR(6)      NOT NULL UNIQUE,   -- 농장식별번호. 농가 로그인 키
  owner_user_id      BIGINT       NOT NULL REFERENCES users (users_id),  -- 농가 계정 (신고서 소유자 성명 = users.name)
  name               VARCHAR(100) NOT NULL,
  address            VARCHAR(255),                   -- 개인정보
  region_code        CHAR(10),                       -- 법정동 10자리
  lat                NUMERIC(10, 7),
  lng                NUMERIC(10, 7),
  head_count         INT,                            -- 두수 (R-S03 50두 미만 개수, 계산식 ⑩)
  org_id             BIGINT REFERENCES receiver_org (org_id)  -- 담당 축협
);
CREATE INDEX idx_farm_owner  ON farm_info (owner_user_id);
CREATE INDEX idx_farm_org    ON farm_info (org_id);
CREATE INDEX idx_farm_region ON farm_info (region_code);

CREATE TABLE cows (
  cow_id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  farm_id            BIGINT      NOT NULL REFERENCES farm_info (farm_id),
  tag_last4          CHAR(4),                        -- 귀표 끝 4자리
  tag_full           CHAR(12),                       -- 귀표 12자리. 미부착 송아지는 NULL
  breed              VARCHAR(30),                    -- 이력제 조회값
  sex                VARCHAR(10),
  birth_date         DATE,
  vaccine_last_date  DATE,                           -- 이력제 조회값
  vaccine_last_order INT,                            -- 이력제 조회값
  barn               VARCHAR(50),
  status             VARCHAR(20) NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'removed')),
  source             VARCHAR(10) NOT NULL DEFAULT 'manual' CHECK (source IN ('trace', 'manual')),
  trace_farm_no      VARCHAR(20),
  trace_checked_at   TIMESTAMPTZ,
  CONSTRAINT uk_cows_farm_tag UNIQUE (farm_id, tag_full)
);
CREATE INDEX idx_cows_last4 ON cows (farm_id, tag_last4);

-- =========================================================
-- 3. 증상 기록·미디어·일지·행동
-- =========================================================

CREATE TABLE symptom_report (
  symptom_report_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  farm_id            BIGINT      NOT NULL REFERENCES farm_info (farm_id),
  cow_id             BIGINT REFERENCES cows (cow_id),  -- NULL = 개체 미확인
  answers            JSONB,                          -- {drool, blister, feed, nodule}
  matched_count      SMALLINT,                       -- 공식 증상 일치 수 (판정 아님)
  found_at           TIMESTAMPTZ,
  barn               VARCHAR(50),
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_symptom_farm ON symptom_report (farm_id, created_at);

CREATE TABLE field_media (
  media_id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  symptom_report_id  BIGINT REFERENCES symptom_report (symptom_report_id),  -- 일지 음성은 NULL
  kind               VARCHAR(10)  NOT NULL CHECK (kind IN ('audio', 'photo', 'video')),
  requested_part     VARCHAR(30),
  predicted_part     VARCHAR(30),
  match              BOOLEAN,                        -- 요청 부위와 일치 여부
  score              NUMERIC(5, 4),
  model_version      VARCHAR(50),
  file_path          VARCHAR(500) NOT NULL,          -- Object Storage field/ 경로
  brightness         NUMERIC(6, 2)
);
CREATE INDEX idx_media_symptom ON field_media (symptom_report_id);

CREATE TABLE diary_entry (
  entry_id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  farm_id            BIGINT      NOT NULL REFERENCES farm_info (farm_id),
  date               DATE        NOT NULL,
  category           VARCHAR(20) NOT NULL CHECK (category IN
                       ('사육현황', '접종', '소독', '관찰', '출입', '출생', '폐사', '이동', '신고', '메모')),
  cow_id             BIGINT REFERENCES cows (cow_id),  -- NULL = 농장 전체 기록
  value              VARCHAR(500),
  vaccine_type       VARCHAR(50),
  head_count         INT,
  raw_text           TEXT,                           -- 음성 원문 (STT)
  audio_media_id     BIGINT REFERENCES field_media (media_id)
);
CREATE INDEX idx_diary_farm_date    ON diary_entry (farm_id, date);
CREATE INDEX idx_diary_cow_cat_date ON diary_entry (cow_id, category, date);

CREATE TABLE behavior_record (
  behavior_record_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  media_id           BIGINT      NOT NULL REFERENCES field_media (media_id),
  cow_id             BIGINT REFERENCES cows (cow_id),
  label              VARCHAR(20) NOT NULL CHECK (label IN ('stand', 'lie', 'eat')),
  confidence         NUMERIC(5, 4),
  observed_at        TIMESTAMPTZ,
  model_version      VARCHAR(50)
);
CREATE INDEX idx_behavior_media ON behavior_record (media_id);

-- =========================================================
-- 4. 신고서·전달·처리·할 일
-- =========================================================

CREATE TABLE history_report (
  history_report_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  farm_id            BIGINT      NOT NULL REFERENCES farm_info (farm_id),
  type               VARCHAR(20) NOT NULL,           -- 출생|폐사|이동 등
  fields             JSONB,                          -- 신고서 항목 (어미 소 번호 등)
  missing            JSONB,                          -- 빠진 항목 목록
  due_date           DATE,
  mode               VARCHAR(10) CHECK (mode IN ('self', 'coop')),
  status             VARCHAR(20) NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'sent', 'checking', 'done'))
);
CREATE INDEX idx_history_farm ON history_report (farm_id);

CREATE TABLE report_summary (
  report_summary_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  symptom_report_id  BIGINT      NOT NULL REFERENCES symptom_report (symptom_report_id),
  card_json          JSONB,                          -- 접수자용 한 장 요약
  form6_json         JSONB,                          -- 신고서 6개 항목 (시행규칙 제13조)
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_summary_symptom ON report_summary (symptom_report_id);

CREATE TABLE deliveries (
  deliveries_id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  report_summary_id  BIGINT REFERENCES report_summary (report_summary_id),
  history_report_id  BIGINT REFERENCES history_report (history_report_id),
  route              VARCHAR(10) NOT NULL CHECK (route IN ('call', 'coop', 'web', 'gov')),
  org_id             BIGINT REFERENCES receiver_org (org_id),   -- 받는 기관
  cc_org_id          BIGINT REFERENCES receiver_org (org_id),   -- 전화 경로 시 함께 받는 담당 축협
  share_phone        BOOLEAN     NOT NULL DEFAULT TRUE,
  idempotency_key    VARCHAR(64) NOT NULL UNIQUE,    -- 같은 신고 두 번 전달 방지
  approved_at        TIMESTAMPTZ NOT NULL,           -- 농가가 누른 시각
  received_at        TIMESTAMPTZ NOT NULL DEFAULT now(),  -- 서버 도착 시각
  status             VARCHAR(20) NOT NULL DEFAULT 'queued' CHECK (status IN ('delivered', 'queued', 'failed')),
  CONSTRAINT chk_deliveries_one_report CHECK ((report_summary_id IS NULL) <> (history_report_id IS NULL))
);
CREATE INDEX idx_deliveries_summary ON deliveries (report_summary_id);
CREATE INDEX idx_deliveries_history ON deliveries (history_report_id);
CREATE INDEX idx_deliveries_org     ON deliveries (org_id, received_at);
CREATE INDEX idx_deliveries_cc_org  ON deliveries (cc_org_id, received_at);

CREATE TABLE receiver_check (
  receiver_check_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  deliveries_id      BIGINT      NOT NULL REFERENCES deliveries (deliveries_id),
  org_id             BIGINT REFERENCES receiver_org (org_id),
  users_id           BIGINT      NOT NULL REFERENCES users (users_id),  -- 처리한 접수자
  status             VARCHAR(20) NOT NULL CHECK (status IN ('new', 'checking', 'done')),
  result             VARCHAR(30),                    -- 표준 코드 (목록 확정 필요)
  memo               TEXT,
  at                 TIMESTAMPTZ NOT NULL DEFAULT now()  -- 상태 변경 시각. 변경마다 행 추가
);
CREATE INDEX idx_check_delivery ON receiver_check (deliveries_id, at);

CREATE TABLE tasks (
  tasks_id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  farm_id            BIGINT       NOT NULL REFERENCES farm_info (farm_id),
  kind               VARCHAR(30)  NOT NULL,          -- birth_report|ear_tag 등
  title              VARCHAR(200) NOT NULL,          -- 화면 제목 (기한 규칙 결과)
  due_date           DATE         NOT NULL,
  ref_id             BIGINT,                         -- 참조 번호 (일지·이력 신고서, FK 없음)
  done_at            TIMESTAMPTZ                     -- NULL = 미완료
);
CREATE INDEX idx_tasks_farm_due ON tasks (farm_id, done_at, due_date);

-- =========================================================
-- 5. 조회용 데이터 (FK 없음)
-- 공공데이터 3종은 원본 컬럼 이름(소문자)을 쓴다.
-- =========================================================

CREATE TABLE outbreak_info (
  outbreak_info_id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  ictsd_occrrnc_no   VARCHAR(100) NOT NULL UNIQUE,   -- 원본 발생 번호. 중복 적재 방지
  lknts_nm           VARCHAR(50)  NOT NULL,          -- 가축전염병명
  farm_locplc        VARCHAR(255),                   -- 농장 소재지
  region_code        CHAR(10),                       -- 원본 FARM_LOCPLC_LEGALDONG_CODE를 현재 법정동 코드로 바꾼 값
  occrrnc_de         DATE,                           -- 발생일
  lvstckspc_nm       VARCHAR(50),                    -- 축종
  occrrnc_lvstckcnt  INT,                            -- 발생 두수
  dgnss_engn_nm      VARCHAR(100),                   -- 진단기관
  cessation_de       DATE,                           -- 종식일
  collected_at       TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_outbreak_region_date ON outbreak_info (region_code, occrrnc_de);

-- 출처: 가축위생방역지원본부_전국 시군별 한우 사육규모 현황 (data.go.kr 15147310 OpenAPI, 연 1회, 시군·연도별 1행)
-- 컬럼명: 포털 '데이터 항목(컬럼) 정보'의 영문명. 호수 = 농장(농가) 수, 두수 = 마릿수
CREATE TABLE farm_scale_statistics (
  farm_scale_statistics_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  seq_no             INT,                            -- 순번
  breed              VARCHAR(20) NOT NULL,           -- 품종 (한우)
  province           VARCHAR(20) NOT NULL,           -- 시도 (원본 약칭: 서울, 경기 …)
  city_county        VARCHAR(30) NOT NULL,           -- 시군 (광역시·세종은 시도명과 같음)
  year               SMALLINT    NOT NULL,           -- 년도
  total_farms        INT NOT NULL,                   -- 전체호수
  total_heads        INT NOT NULL,                   -- 전체두수
  farms_over_100     INT NOT NULL,                   -- 100두 이상(호수)
  heads_over_100     INT NOT NULL,                   -- 100두 이상(두수)
  farms_50to100      INT NOT NULL,                   -- 100두-50두 이상(호수)
  heads_50to100      INT NOT NULL,                   -- 100두-50두 이상(두수)
  farms_30to50       INT NOT NULL,                   -- 50두-30두 이상(호수)
  heads_30to50       INT NOT NULL,                   -- 50두-30두 이상(두수)
  farms_7to30        INT NOT NULL,                   -- 30두-7두 이상(호수)
  heads_7to30        INT NOT NULL,                   -- 30두-7두 이상(두수)
  farms_under7       INT NOT NULL,                   -- 7두-0두 이상(호수)
  heads_under7       INT NOT NULL,                   -- 7두-0두 이상(두수)
  region_code        CHAR(10),                       -- 추가: 법정동 코드로 변환 (못 찾으면 NULL)
  reference_ym       CHAR(7)     NOT NULL,           -- 추가: 기준 시점 'YYYY.01' (포털 시간범위 "~2026년 1월")
  collected_at       TIMESTAMPTZ NOT NULL,           -- 추가: 적재 시각
  CONSTRAINT uq_scale_region_year UNIQUE (province, city_county, year),
  CONSTRAINT chk_scale_farm_sum CHECK (total_farms = farms_over_100 + farms_50to100 + farms_30to50 + farms_7to30 + farms_under7)
);
CREATE INDEX idx_scale_region ON farm_scale_statistics (region_code, year);

CREATE TABLE disinfection_facility (
  disinfection_facility_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  pstn_dsnf_plc_nm   VARCHAR(100) NOT NULL,          -- 거점소독장소명
  addr               VARCHAR(255),
  oper_hr            VARCHAR(100),                   -- 운영시간
  pic_telno          VARCHAR(20),                    -- 담당자 전화번호
  lot                NUMERIC(10, 7),                 -- 경도
  lat                NUMERIC(10, 7),                 -- 위도
  region_code        CHAR(10),
  collected_at       TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_facility_region ON disinfection_facility (region_code);

CREATE TABLE biosecurity_rule (
  biosecurity_rule_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  kind               VARCHAR(20)  NOT NULL CHECK (kind IN ('question', 'photo_guide', 'action', 'form_field', 'summary', 'clause')),  -- clause = RAG 검색용 조항 본문
  disease            VARCHAR(50),
  stage              VARCHAR(20) CHECK (stage IN ('suspect', 'after_report', 'regional')),  -- NULL = 단계 없음
  text               TEXT         NOT NULL,          -- 공식 원문만
  source_doc         VARCHAR(200) NOT NULL,          -- 문서명
  clause             VARCHAR(200),                   -- 조항 (장·절·번호 + PDF 쪽)
  revised_at         DATE,
  status             VARCHAR(20)  NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'review', 'published')),
  reviewer           VARCHAR(50),
  version            INT          NOT NULL DEFAULT 1,
  example_image      VARCHAR(500),
  display_order      INT,                            -- 화면 표시 순서 (질문·할 일)
  yes_required_parts JSONB,                          -- "예"일 때 촬영할 부위 코드 목록
  part_code          VARCHAR(30),                    -- 촬영 안내 대상 부위 코드
  photo_part         VARCHAR(30)                     -- 부위 한글 이름
  -- embedding: 0003_biosecurity_rule_embedding.sql에서 추가 (pgvector)
);
CREATE INDEX idx_rule_lookup ON biosecurity_rule (kind, status, disease);
CREATE INDEX idx_rule_doc    ON biosecurity_rule (source_doc, clause, version);

CREATE TABLE holidays (
  date               DATE        PRIMARY KEY,
  name               VARCHAR(50) NOT NULL
);

CREATE TABLE region_code_map (
  region_code        CHAR(10)    PRIMARY KEY,        -- 법정동 10자리
  region_level       VARCHAR(10) NOT NULL,           -- sido|sigungu|emd|ri
  sido_code          CHAR(2)     NOT NULL,
  sigungu_code       CHAR(5),
  sido_name          VARCHAR(30) NOT NULL,
  sigungu_name       VARCHAR(30),
  emd_name           VARCHAR(30),
  is_active          BOOLEAN     NOT NULL DEFAULT TRUE  -- 폐지 코드는 FALSE
);
CREATE INDEX idx_region_sigungu ON region_code_map (sigungu_code);

CREATE TABLE trace_lookups (
  ear_tag_no         VARCHAR(20) PRIMARY KEY,
  response           TEXT,                           -- 이력제 API 응답 원문(XML)
  status             VARCHAR(20) NOT NULL,           -- ok|notfound|fail
  checked_at         TIMESTAMPTZ NOT NULL
);

CREATE TABLE etl_job_run (
  etl_job_run_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  job                VARCHAR(50) NOT NULL,           -- outbreak|disinfection_facility|farm_scale (주기는 수집 코드에 고정)
  started_at         TIMESTAMPTZ NOT NULL,
  finished_at        TIMESTAMPTZ,
  status             VARCHAR(20) NOT NULL,           -- running|success|failed
  rows               INT,
  error              TEXT
);
CREATE INDEX idx_runs_job ON etl_job_run (job, started_at);

CREATE TABLE inference_log (
  inference_log_id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  feature            VARCHAR(50)  NOT NULL,
  model_version      VARCHAR(50),
  input_kind         VARCHAR(30),
  result_summary     VARCHAR(500),                   -- 개인정보 넣지 않음
  latency_ms         INT,
  error              TEXT,
  at                 TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_inference_feature ON inference_log (feature, at);

