# services/api

Java Spring Boot 백엔드. `mnm-data/migrations`가 정의한 스키마를 그대로 사용하고, 추론·에이전트 호출은 `mnm-ai`가 올린 이미지(GHCR)를 통해 수행합니다.

- **담당**: @joobogyeong, @dooooing
- **스택**: Java 21 · Spring Boot 4.1 · Gradle · PostgreSQL · springdoc(스웨거)

## 패키지 구조

| 패키지 | 내용 | API 경로 |
| --- | --- | --- |
| `farmer` | 농가 앱 컨트롤러·서비스·리포지토리·DTO | `/api/farmer/**` |
| `receiver` | 접수자 웹 컨트롤러·서비스·리포지토리·DTO | `/api/receiver/**` |
| `admin` | 어드민 컨트롤러·서비스·리포지토리·DTO | `/api/admin/**` |
| `auth` | 로그인·내 계정 | `/api/auth/**` |
| `domain` | DB 테이블과 1:1인 엔티티 (세 플랫폼이 공유). `domain/common`에 `BaseTimeEntity` | - |
| `global/config` | `ApiPaths`(경로 접두사), `OpenApiConfig`(스웨거), `WebConfig`(CORS), `SecurityConfig`(토큰·역할별 접근) | - |
| `global/response` | `BaseResponse`, `PageResponse` | - |
| `global/error` | `ErrorCode`, `BusinessException`, `ErrorResponse`, `GlobalExceptionHandler`, `ApiErrorController` | - |
| `global/web` | `RequestIdFilter` | - |

- 플랫폼 패키지(`farmer`·`receiver`·`admin`·`auth`)끼리는 서로 참조하지 않습니다. 함께 쓰는 것은 `domain`과 `global`뿐입니다.
- API 경로는 `ApiPaths` 접두사 아래에만 둡니다. 다른 `/api/**` 경로는 nginx가 404로 막습니다.
- 엔티티는 `mnm-data/migrations` 기준입니다. 스키마가 바뀌면 엔티티를 맞춥니다. 이 레포에서 테이블을 바꾸지 않습니다.
- 서버가 뜰 때 엔티티와 실제 테이블이 맞는지 검사합니다(`JPA_DDL_AUTO`, 기본 `validate`). 맞지 않으면 서버가 뜨지 않습니다.

## 공통 규칙 (`global/`)

| 할 일 | 쓰는 것 |
| --- | --- |
| 성공 응답 | `BaseResponse.of(data)` → `{ data, request_id }`. 목록은 `BaseResponse<PageResponse<T>>`(`{ items, has_next, next_cursor }`), 본문이 없으면 `204` |
| 오류 응답 | `throw new BusinessException(ErrorCode.XXX, "개발용 detail")` → `{ code, message, detail, request_id }` |
| 입력 검증 | 요청 DTO에 `@NotBlank` 등 + 컨트롤러에서 `@Valid`. 실패하면 `VALID-422`와 칸별 문구(`errors: [{ field, message }]`) |
| `created_at` 있는 엔티티 | `BaseTimeEntity` 상속 (값은 DB 기본값으로 채움) |
| 요청 추적 | `RequestIdFilter`가 요청마다 `request_id`를 정해 로그·응답 헤더(`X-Request-Id`)·본문에 같은 값으로 남김 |

### 오류 코드 (`ErrorCode`)

기준은 기능명세서 "오류·예외 처리" 탭입니다. 바꾸면 `docs/api/openapi.yaml`의 표도 같이 고칩니다.

| 코드 | HTTP | 언제 |
| --- | --- | --- |
| `AUTH-401` | 401 | 로그인 만료 |
| `AUTH-403` | 403 | 권한 없음 (관할 밖 포함) |
| `AUTH-SUSPENDED` | 403 | 정지된 계정 로그인 |
| `AUTH-FAIL` | 401 | 로그인 정보 불일치 |
| `AUTH-LOCK` | 423 | 5회 실패 → 10분 잠금 |
| `VALID-422` | 422 | 검증 실패 · 잘못된 JSON · 파라미터 오류 |
| `NF-404` | 404 | 없는 기록 · 없는 경로 |
| `METHOD-405` | 405 | 지원하지 않는 메서드 |
| `CONFLICT-409` | 409 | 이미 처리됨 · 중복 요청 · DB 제약 위반 · 동시 수정 |
| `UPLOAD-FAIL` | 413 | 업로드 용량 초과 |
| `CATTLE-NOTFOUND` | 404 | 귀표 번호로 소를 못 찾음 |
| `CATTLE-OTHER-FARM` | 409 | 다른 농장에 등록된 소 (농가 확인 후 다시 요청) |
| `TRACE-API-FAIL` | 502 | 축산물이력제 조회 실패 (직접 입력으로 등록) |
| `DELIVER-FAIL` | 500 | 수신함 전달 실패 |
| `SRV-5XX` | 500 | 처리하지 못한 예외 (원인은 로그에만) |

- `message`는 명세의 "농가 문구"입니다. 접수자 웹·어드민은 `code`를 보고 화면 문구를 따로 정합니다.
- 문구 속 `{중괄호}`(예: `{남은 분}`)는 변수입니다. 값을 채운 문구는 `new BusinessException(code, message, detail)`로 넘깁니다.
- 새 문구는 명세 문구 규칙을 따릅니다: 해요체, "무엇이 안 됐는지 + 지금 할 수 있는 것", 금지 표현(진단·확률·위험도·이상·질병 의심·감염·양성·판정·자동 신고) 없음. `ErrorCodeTest`가 확인합니다.
- `STT-FAIL`·`LLM-FAIL`·`PART-FAIL`·`BEHAVIOR-FAIL`·`RAG-NONE`은 AI 서비스가 이 서버에 주는 코드입니다. 서버가 대체 경로로 처리하고 클라이언트 오류로 내보내지 않습니다.

### JSON · 업로드 · CORS

| 항목 | 값 | 설정 위치 |
| --- | --- | --- |
| JSON 필드 이름 | snake_case (DTO에 `@JsonProperty` 불필요) | `application.yml` `spring.jackson` |
| 시간 | `Instant` → ISO-8601 UTC (예: `2026-11-17T03:00:00Z`) | Jackson 기본값 |
| 쿼리 파라미터 | JSON 설정과 무관. 여러 단어면 `@RequestParam("head_count")`로 직접 지정 | - |
| `Idempotency-Key` | 신고 전달처럼 두 번 처리되면 안 되는 요청에 필수 (`@RequestHeader`). 없으면 `VALID-422`, 이미 처리된 키는 DB 고유 제약 위반 → `CONFLICT-409` | 전달 API |
| 업로드 | 파일 1개 10MB, 요청 전체 20MB (nginx `client_max_body_size 20m`과 맞춤) | `spring.servlet.multipart` |
| CORS | `CORS_ALLOWED_ORIGINS`(쉼표 구분)에 적은 주소만 `/api/**` 허용. 비우면 CORS 꺼짐 | `WebConfig` |
| 로그 | 요청 처리 중 로그 줄마다 `[request_id]` | `logging.pattern.level` |

## 인증 (`SecurityConfig`, `auth`)

| 항목 | 값 |
| --- | --- |
| 로그인 | 농가 `POST /api/auth/farmer/login`(농장식별번호 + 휴대폰 번호), 접수자·운영자 `POST /api/auth/login`(아이디·비밀번호) |
| 토큰 | JWT(HS256, `JWT_SECRET`). claim: `sub`=`users_id`, `role`, `must_change_pw`. 농가 30일, 접수자·운영자 8시간. 갱신 토큰 없음 |
| 역할별 경로 | `/api/farmer/**` 농가 · `/api/receiver/**` 접수자 · `/api/admin/**` 운영자 · `/api/auth/**` 로그인한 누구나 |
| 임시 비밀번호 | `must_change_pw=true` 토큰은 역할 권한이 없어 `/api/auth/**`만 쓸 수 있음 → `POST /api/auth/password` 후 새 토큰 |
| 잠금 | 연속 5번 틀리면 10분(`users.failed_count`, `locked_until`). 잠긴 동안은 맞게 넣어도 `AUTH-LOCK` |
| 오류 | 토큰 없음·만료·위조 `AUTH-401`, 역할 불일치 `AUTH-403`, 정지 계정 `AUTH-SUSPENDED`, 정보 불일치 `AUTH-FAIL` |
| 컨트롤러에서 내 계정 | `@AuthenticationPrincipal Jwt jwt` → `Long.valueOf(jwt.getSubject())` |

## 농가 앱 API (`farmer`)

경로·요청·응답은 `docs/api/openapi.yaml`의 "농가 앱" 태그와 스웨거(`/docs/farmer`)를 봅니다.

| 항목 | 내용 |
| --- | --- |
| AI 호출 | `global/ai/AiClient`(stt·route·summary). 지금 구현은 `MockAiClient`뿐입니다. mnm-ai 내부 API 서버가 배포되면 HTTP 구현으로 바꿉니다 |
| AI 실패 | `AiException`을 받아 대체 경로로 진행합니다. 음성 인식 실패 → `fallback=text_input`, 정리 실패 → `fallback=select_input`, 요약 실패 → 원본 기록만 |
| 규칙 | `farmer/FarmerRules`: 신고 기한(토·일·공휴일 제외 5일)·귀표 30일·촬영 부위·질병별 안내 분기·신고서 빈칸. mnm-ai `rules/`와 같은 값이며, mnm-ai 서버가 `/rules/*`를 열지 않아 서버에서 계산합니다 |
| 이력제 조회 | `farmer/TraceClient`. 지금은 `MockTraceClient`: 마지막 자리 `0` = 못 찾음, `008…` = 다른 농장, `009…` = 조회 실패 |
| 저장하지 않는 것 | 음성 원본(MVP), 알림 생성(기한 D-3·D-1·평소와 다름 스케줄러는 MVP 제외). 처리 결과 알림은 접수자 상태 변경에서 생깁니다 |
| 전달 | `POST /api/farmer/deliveries`, `POST /api/farmer/history-reports/{id}/submit`(coop)는 `Idempotency-Key` 필수. 전달할 때 일지에 "신고"를 자동으로 남깁니다 |

## 접수자 웹 API (`receiver`)

경로·요청·응답은 `docs/api/openapi.yaml`의 "접수자 웹" 태그와 스웨거(`/docs/receiver`)를 봅니다.

| 항목 | 내용 |
| --- | --- |
| 수신함 | 우리 기관이 받은 기관(`deliveries.org_id`)이거나 함께 받은 축협(`cc_org_id`)이고 `status=delivered`인 전달만. 다른 기관 것은 `AUTH-403` |
| 처리 상태 | `receiver_check`에 상태 변경·메모마다 행을 추가하고 가장 최근 행이 현재 상태(없으면 `new`). 같은 전달은 행 잠금(`findForUpdate`)으로 동시에 고치지 않고, 보던 상태가 바뀌었으면 `CONFLICT-409` |
| 농가 알림 | 상태 변경(RCV-03·04)과 대신 신고 마침(RCV-10)에서 `alerts`(kind=`result`, ref_id=`deliveries_id`)를 만듭니다. 다시 알리기(RCV-07)는 48시간 넘게 안 읽은 알림을 한 번 더 넣습니다 |
| 감사 기록 | 한 장 요약·6개 항목 열람(`view`), 상태·메모·신고서 변경(`update`), 다시 알리기(`nudge`)를 `audit_log`에 남깁니다 |
| 규칙 | `receiver/ReceiverRules`: 상태 흐름, 처리 결과 코드(제안값), 미응답 48시간(제안값), 연락 우선순위(미응답 → 기한 → 두수), 6개 항목 복사 텍스트 |
| 하지 않는 것 | 사진·영상 원본 주소(이미지 저장소 연결 전), 영상 행동 비율, 연락처 "변경" 배지(바뀐 시각 컬럼 없음), 이력제 직접 연동 |

## DB 마이그레이션 (Flyway)

- 서버가 시작할 때 Flyway 가 `src/main/resources/db/migration`(스키마)과 `db/seed`(시연용 계정)를 적용한 뒤, Hibernate 가 엔티티와 테이블을 검사합니다.
- `db/migration/V{n}__*.sql`은 `mnm-data/migrations/{000n}_*.sql`의 사본입니다(`BEGIN;`·`COMMIT;` 두 줄만 뺌, Flyway 가 파일마다 트랜잭션으로 감쌈). 직접 고치지 않습니다. mnm-data 에 새 번호 파일이 머지되면 다음 `V` 번호로 복사해 넣습니다.
- Flyway 는 테이블을 만들고 고치므로 owner 급 계정이 필요합니다. 앱 계정(`DATABASE_USERNAME`)을 읽기·쓰기 전용으로 나누면 `SPRING_FLYWAY_USER`·`SPRING_FLYWAY_PASSWORD`에 owner 계정을 넣습니다(코드 수정 없음). 앱 계정에는 owner 가 만든 테이블·시퀀스 권한과 `ALTER DEFAULT PRIVILEGES`(이후 생길 테이블 권한)를 줘야 합니다.
- 적용된 파일은 `flyway_schema_history`에 체크섬과 함께 남습니다. 이미 적용된 `V` 파일을 고치면 서버가 뜨지 않습니다.
- Flyway 도입 전에 psql 로 0001~0003을 넣어 둔 DB(스테이징)는 처음 뜰 때 V3 까지 적용된 것으로 기록하고(baseline) 그 다음부터 실행합니다. 빈 DB 는 V1 부터 실행합니다.
- `db/seed/R__seed_demo_accounts.sql`은 내용이 바뀔 때마다 다시 실행되며, 이미 있는 기관·농장·아이디는 건드리지 않습니다. 실서비스 DB 는 `FLYWAY_LOCATIONS=classpath:db/migration`으로 시드를 뺍니다.
- 시연 계정의 임시 비밀번호 원문은 레포에 두지 않습니다(담당자가 따로 전달). 바꾸려면 BCrypt 해시를 새로 만들어 시드 파일을 고칩니다. 이미 만들어진 계정에는 반영되지 않습니다.

## 어드민 API (`admin`)

경로·요청·응답은 `docs/api/openapi.yaml`의 "어드민" 태그와 스웨거(`/docs/admin`)를 봅니다.

| 항목 | 내용 |
| --- | --- |
| 감사 기록 | 계정·기관·문구를 만들거나 바꾸면 `AdminSupport.audit()`으로 `audit_log`에 남깁니다(action: `create` · `update` · `publish` · `password_reset`) |
| 임시 비밀번호 | 접수자·운영자 계정 생성·초기화 때 서버가 만들어 응답에서 한 번만 보여줍니다. DB 에는 BCrypt 해시만 저장합니다 |
| 문구 버전 | 게시된 문구를 고치면 새 버전(초안)을 만들고, 새 버전을 게시하면 같은 자리의 이전 게시 버전을 초안으로 내립니다. 이전 버전을 가리키는 컬럼이 없어 "같은 자리"를 분류·질병·단계·순서·부위·조항으로 찾습니다 |
| 대시보드 | AI 상태는 `inference_log` 최근 1시간, 수집 상태는 `etl_job_run` 작업별 마지막 실행으로 봅니다. 날짜 경계는 한국 시간입니다 |
| 아직 없는 것 | `ADM-06` 전달 현황(농가 API 머지 후), `ADM-09~11`(수집 실행·모델·AI 로그) |

## 새 API 추가 순서

1. 해당 플랫폼 패키지(`farmer` 등)에 요청·응답 `record` DTO를 만듭니다. 검증 어노테이션과 명세 칸별 문구를 넣습니다.
2. 서비스에서 실패 상황은 `BusinessException(ErrorCode.XXX)`으로 던집니다. 조회만 하면 `@Transactional(readOnly = true)`.
3. 컨트롤러는 `@RequestMapping(ApiPaths.FARMER + "/...")` 아래에 두고 `BaseResponse`로 감싸 반환합니다. 엔티티를 그대로 반환하지 않습니다.
4. 스웨거(`/docs/farmer` 등)에서 요청·응답 필드가 snake_case로 보이는지 확인합니다. 공통 오류 응답(4XX·5XX)은 자동으로 붙습니다.
5. 테스트를 쓰고 `./gradlew build`를 통과시킵니다.
6. 응답 형식이 바뀌면 `docs/api/openapi.yaml`을 고치고 PR에 프론트 담당 확인을 요청합니다.

## 로컬 실행

JDK 21이 필요합니다. 레포 루트의 `.env`(키 목록은 `.env.example`)에 `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `JWT_SECRET`을 채운 뒤 실행합니다. 빈 DB 면 Flyway 가 테이블과 시연용 계정을 만듭니다. DB 는 pgvector 확장이 있어야 합니다(로컬은 `docker compose up -d db`).

```bash
./gradlew bootRun
```

| 주소 | 내용 |
| --- | --- |
| `/swagger-ui/index.html` | 스웨거 (오른쪽 위에서 농가 앱·접수자 웹·어드민·인증 선택) |
| `/actuator/health` | 헬스 체크 (DB 연결 상태 포함) |

## 테스트

```bash
./gradlew build
```

- 테스트 포함 빌드입니다. PR의 CI(`.github/workflows/ci.yml`)도 같은 명령을 돌립니다.
- `CommonResponseTest`는 DB 없이 전체 설정을 띄워 공통 응답·오류·`request_id`·스웨거·CORS를 확인합니다. DB 없이 띄우려면 `@ActiveProfiles("nodb")`(`src/test/resources/application-nodb.properties`)를 씁니다.
- 역할이 필요한 경로는 `TestTokens.bearer(jwtEncoder, "farmer")`로 만든 토큰을 붙입니다. 리포지토리가 필요한 서비스는 `AuthApiTest`처럼 `@MockitoBean`으로 바꿉니다.
- 엔티티와 실제 테이블이 맞는지(`validate`)는 DB가 있어야 확인됩니다. 스테이징 배포의 헬스 체크로 확인합니다.

## 배포

- `develop`에 머지되면 `.github/workflows/deploy.yml`이 이미지(`ghcr.io/mnms-chonsu-lab/mnm-api`)를 GHCR에 올리고 스테이징 서버에 배포합니다. 배포 후 `/health`가 `UP`인지 확인합니다.
- 서버에서는 nginx가 요청을 받아 경로 접두사로 구분한 뒤 API 서버로 넘깁니다. 설정은 `infra/docker/nginx/`에 있습니다 (@dooooing 담당).
- 스테이징에서는 `/docs/farmer`, `/docs/receiver`, `/docs/admin`, `/docs/auth`로 플랫폼별 스웨거에 바로 들어갈 수 있습니다.
- 되돌리기: Actions 탭에서 `deploy`를 수동 실행하고 `image_tag`에 이전 커밋 SHA를 넣습니다.
