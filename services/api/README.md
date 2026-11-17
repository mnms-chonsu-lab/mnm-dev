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
| `global/config` | `ApiPaths`(경로 접두사), `OpenApiConfig`(스웨거), `WebConfig`(CORS) | - |
| `global/response` | `BaseResponse`, `PageResponse` | - |
| `global/error` | `ErrorCode`, `BusinessException`, `ErrorResponse`, `GlobalExceptionHandler`, `ApiErrorController` | - |
| `global/web` | `RequestIdFilter` | - |

- 플랫폼 패키지(`farmer`·`receiver`·`admin`·`auth`)끼리는 서로 참조하지 않습니다. 함께 쓰는 것은 `domain`과 `global`뿐입니다.
- API 경로는 `ApiPaths` 접두사 아래에만 둡니다. 다른 `/api/**` 경로는 nginx가 404로 막습니다.
- 엔티티는 `mnm-data/migrations/0001_init_operational.sql` 기준입니다. 스키마가 바뀌면 엔티티를 맞춥니다. 이 레포에서 테이블을 바꾸지 않습니다.
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
| `AUTH-FAIL` | 401 | 로그인 정보 불일치 |
| `AUTH-LOCK` | 423 | 5회 실패 → 10분 잠금 |
| `VALID-422` | 422 | 검증 실패 · 잘못된 JSON · 파라미터 오류 |
| `NF-404` | 404 | 없는 기록 · 없는 경로 |
| `METHOD-405` | 405 | 지원하지 않는 메서드 |
| `CONFLICT-409` | 409 | 이미 처리됨 · 중복 요청 · DB 제약 위반 · 동시 수정 |
| `UPLOAD-FAIL` | 413 | 업로드 용량 초과 |
| `SRV-5XX` | 500 | 처리하지 못한 예외 (원인은 로그에만) |

### JSON · 업로드 · CORS

| 항목 | 값 | 설정 위치 |
| --- | --- | --- |
| JSON 필드 이름 | snake_case (DTO에 `@JsonProperty` 불필요) | `application.yml` `spring.jackson` |
| 시간 | `Instant` → ISO-8601 UTC (예: `2026-11-17T03:00:00Z`) | Jackson 기본값 |
| 쿼리 파라미터 | JSON 설정과 무관. 여러 단어면 `@RequestParam("head_count")`로 직접 지정 | - |
| 업로드 | 파일 1개 10MB, 요청 전체 20MB (nginx `client_max_body_size 20m`과 맞춤) | `spring.servlet.multipart` |
| CORS | `CORS_ALLOWED_ORIGINS`(쉼표 구분)에 적은 주소만 `/api/**` 허용. 비우면 CORS 꺼짐 | `WebConfig` |
| 로그 | 요청 처리 중 로그 줄마다 `[request_id]` | `logging.pattern.level` |

## 새 API 추가 순서

1. 해당 플랫폼 패키지(`farmer` 등)에 요청·응답 `record` DTO를 만듭니다. 검증 어노테이션과 명세 칸별 문구를 넣습니다.
2. 서비스에서 실패 상황은 `BusinessException(ErrorCode.XXX)`으로 던집니다. 조회만 하면 `@Transactional(readOnly = true)`.
3. 컨트롤러는 `@RequestMapping(ApiPaths.FARMER + "/...")` 아래에 두고 `BaseResponse`로 감싸 반환합니다. 엔티티를 그대로 반환하지 않습니다.
4. 스웨거(`/docs/farmer` 등)에서 요청·응답 필드가 snake_case로 보이는지 확인합니다. 공통 오류 응답(4XX·5XX)은 자동으로 붙습니다.
5. 테스트를 쓰고 `./gradlew build`를 통과시킵니다.
6. 응답 형식이 바뀌면 `docs/api/openapi.yaml`을 고치고 PR에 프론트 담당 확인을 요청합니다.

## 로컬 실행

JDK 21이 필요합니다. 레포 루트의 `.env`(키 목록은 `.env.example`)에 `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`를 채운 뒤 실행합니다. 테이블이 아직 없는 DB면 `JPA_DDL_AUTO=none`으로 띄웁니다.

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
- `CommonResponseTest`는 DB 없이 전체 설정을 띄워 공통 응답·오류·`request_id`·스웨거·CORS를 확인합니다. DB 없이 컨텍스트를 띄우는 속성 설정은 이 클래스를 참고합니다.
- 엔티티와 실제 테이블이 맞는지(`validate`)는 DB가 있어야 확인됩니다. 스테이징 배포의 헬스 체크로 확인합니다.

## 배포

- `develop`에 머지되면 `.github/workflows/deploy.yml`이 이미지(`ghcr.io/mnms-chonsu-lab/mnm-api`)를 GHCR에 올리고 스테이징 서버에 배포합니다. 배포 후 `/health`가 `UP`인지 확인합니다.
- 서버에서는 nginx가 요청을 받아 경로 접두사로 구분한 뒤 API 서버로 넘깁니다. 설정은 `infra/docker/nginx/`에 있습니다 (@dooooing 담당).
- 스테이징에서는 `/docs/farmer`, `/docs/receiver`, `/docs/admin`, `/docs/auth`로 플랫폼별 스웨거에 바로 들어갈 수 있습니다.
- 되돌리기: Actions 탭에서 `deploy`를 수동 실행하고 `image_tag`에 이전 커밋 SHA를 넣습니다.
