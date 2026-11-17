# CLAUDE.md — mnm-dev

이 파일은 AI 도구(Claude 등)가 이 레포에서 이슈·PR·코드를 작성할 때 지켜야 할 규칙입니다.
`AGENTS.md`와 내용이 같습니다 (제목과 이 줄만 다름). 한쪽을 고치면 다른 쪽도 같이 고칩니다.

## 이슈·PR·브랜치
- 이슈는 `.github/ISSUE_TEMPLATE/`의 5개 템플릿 중 하나의 섹션 순서·제목 형식을 **그대로** 사용합니다. 임의로 섹션을 생략·재배치하지 않습니다. 추가 내용은 기존 섹션 안에 굵은 소제목으로 넣습니다.
- PR은 `.github/pull_request_template.md`의 섹션 순서를 그대로 사용합니다.
- 이슈 제목: `[타입] 요약`. PR 제목: `[타입] 요약 (#이슈번호)`. 대괄호는 맨 앞 타입 하나만 씁니다.
- 타입: `feat` / `fix` / `docs` / `chore` / `test` / `refactor` / `design` / `review`.
- 브랜치는 최신 `develop`에서 만들고 이름은 `YYYYMMDD_#이슈번호_요약`입니다. PR 대상도 `develop`입니다.
- 1 이슈 = 1 브랜치 = 1 PR. 이슈 범위 밖 수정은 같은 PR에 섞지 않고 새 이슈로 남깁니다.
- 사용자가 요청하지 않으면 커밋·push·PR 생성·이슈 생성을 하지 않습니다.

## AI 사용 표시 금지
이슈·PR·커밋 메시지·코드 주석·문서 등 외부에 남는 모든 글에 다음을 넣지 않습니다.
- `🤖 Generated with Claude Code` 같은 생성 도구 표시
- `Co-Authored-By: Claude` 같은 AI 공동 작성자 줄
- 그 밖에 AI가 작성·생성했다는 어떤 표시

## 문체
- 짧은 문장, 개조식. 비교·결과는 표로 작성합니다.
- 파일 경로·함수·환경변수·API는 백틱으로 표기합니다.
- 수치는 측정값 + 측정 조건을 함께 적습니다. 제안값이면 "(제안값, 실측으로 조정)"이라고 명시합니다.
- 확인하지 않은 내용은 "확인 안 한 것"에 솔직히 적습니다. 추측을 사실처럼 쓰지 않습니다.
- 다른 사람 담당 영역(CODEOWNERS 참고)은 직접 수정하지 않고 `@멘션`으로 확인을 요청합니다.
- 관련 이슈·PR·기획서 절 번호는 "관련:" 줄에 모아서 링크합니다.

## 이 레포의 구조
| 경로 | 내용 | 담당 |
| --- | --- | --- |
| `services/api/` | Spring Boot 백엔드 (Java 21 · Spring Boot 4.1 · Gradle · PostgreSQL) | @joobogyeong, @dooooing |
| `infra/docker/` | 스테이징 배포용 compose(`compose.deploy.yml`) · nginx 설정 | @dooooing |
| `infra/aws/` | 비어 있음. 배포는 네이버 클라우드 서버 + GHCR 이미지 (정리 여부 확인 중) | @dooooing |
| `.github/` | 이슈·PR 템플릿, CODEOWNERS, `ci.yml`(PR 빌드), `deploy.yml`(`develop` 머지 → 스테이징 배포) | @dooooing |
| `docs/api/openapi.yaml` | API 명세의 기준 문서 (공통 응답·오류 형식 포함) | @joobogyeong, @dooooing |
| `docs/decisions/` | 결정 기록 (파일당 1건) | @joobogyeong, @dooooing |

- 화면 목록·기능 명세는 별도 문서(기능명세서)에서 관리합니다. README·코드 주석에 복사해 두지 않습니다.
- 사용자가 요청하지 않으면 의존성을 추가하지 않습니다. 추가하면 PR "변경 이유"에 왜 필요한지 적습니다.

## 백엔드 코드 규칙 (`services/api`)

### 패키지
- `farmer` / `receiver` / `admin` / `auth`는 각자 컨트롤러·서비스·리포지토리·DTO를 가집니다. 이 네 패키지끼리는 서로 참조하지 않습니다.
- 여러 플랫폼이 함께 쓰는 것은 `domain`(엔티티)과 `global`(공통 설정·응답·오류)뿐입니다.
- API 경로는 `ApiPaths`의 접두사(`/api/farmer`, `/api/receiver`, `/api/admin`, `/api/auth`) 아래에만 둡니다. 다른 경로는 nginx가 404로 막고 스웨거 그룹에도 나오지 않습니다. 접두사를 바꾸면 `infra/docker/nginx/locations.conf`도 같이 바뀌어야 하므로 @dooooing 확인을 받습니다.

### 응답·오류 (`global/`)
- 성공 응답은 `BaseResponse.of(data)`(`{ data, request_id }`)로 감쌉니다. 목록은 `BaseResponse<PageResponse<T>>`, 본문이 없으면 `204`.
- 엔티티를 응답으로 그대로 내보내지 않습니다. 요청·응답은 `record` DTO로 만듭니다.
- 오류는 `throw new BusinessException(ErrorCode.XXX, "개발용 detail")`로만 냅니다. 컨트롤러에서 try-catch로 오류 본문을 직접 만들거나 `ResponseEntity`에 다른 오류 형식을 담지 않습니다.
- 오류 형식 `{ code, message, detail, request_id, errors }`는 `GlobalExceptionHandler` · `ApiErrorController`가 만듭니다. 새 형식을 만들지 않습니다.
- 새 오류 코드는 `ErrorCode`에 추가하고, 같은 PR에서 `docs/api/openapi.yaml`과 `services/api/README.md`의 오류 코드 표도 고칩니다. 코드·문구는 기능명세서 "오류·예외 처리" 탭과 맞추고, 명세에 없으면 PR에 "(제안)"으로 적어 프론트 담당 확인을 받습니다.
- `message`에는 명세의 "농가 문구"를 씁니다. 접수자 웹·어드민은 `code`로 화면 문구를 정하므로 플랫폼별 코드를 따로 만들지 않습니다.
- 문구 속 `{중괄호}`는 변수입니다. 값을 채운 문구를 `BusinessException(code, message, detail)`로 넘기고, 값이 없으면 명세의 대체 문구를 씁니다.
- 직접 쓰는 문구는 명세 문구 규칙을 따릅니다: 해요체, "무엇이 안 됐는지 + 지금 할 수 있는 것", 금지 표현(진단·확률·위험도·이상·질병 의심·감염·양성·판정·자동 신고) 없음, 오류 코드·기술 용어 없음. `ErrorCodeTest`가 기본 문구를 검사합니다.
- 외부 API(축산물이력제 등)·AI 호출이 실패해도 사용자 흐름을 멈추지 않습니다. 명세의 대체 경로(직접 입력, 판정 없이 저장 등)로 진행하고, 화면에 알릴 때만 해당 오류 코드(`TRACE-API-FAIL` 등)를 씁니다.
- `detail`에는 개인정보·SQL·스택·내부 클래스 이름을 넣지 않습니다. 처리하지 못한 예외(`SRV-5XX`)는 원인을 로그에만 남깁니다.

### 요청·검증·JSON
- 요청 DTO에 `@NotBlank` 등 검증 어노테이션을 달고 컨트롤러에서 `@Valid`를 씁니다. 실패하면 자동으로 `VALID-422`와 칸별 문구(`errors`)가 나갑니다.
- 검증 문구는 기능명세서의 칸별 문구를 그대로 씁니다.
- JSON 필드는 snake_case, 시간은 `Instant`(ISO-8601 UTC)입니다. 전역 설정이므로 DTO에 `@JsonProperty`로 이름을 따로 붙이지 않습니다.
- 쿼리 파라미터는 JSON 설정의 영향을 받지 않습니다. 여러 단어면 `@RequestParam("head_count")`처럼 snake_case 이름을 직접 적습니다.
- 같은 요청이 두 번 처리되면 안 되는 API(신고 전달 등)는 `@RequestHeader("Idempotency-Key")`를 필수로 받습니다. 없으면 `VALID-422`, 이미 처리된 키면 DB 고유 제약 위반으로 `CONFLICT-409`가 자동으로 나갑니다.
- 저장·전달(POST)은 클라이언트가 자동 재시도하지 않습니다(명세 규칙). 서버는 실패해도 사용자가 [다시 시도]로 같은 요청을 보낼 수 있게 부분 저장을 남기지 않습니다(한 요청 = 한 트랜잭션).

### 엔티티·DB
- DB 구조는 `mnm-data/migrations`에서만 바꿉니다. 이 레포에서는 테이블·컬럼을 만들거나 바꾸지 않고, 엔티티를 스키마에 맞춥니다.
- 서버는 시작할 때 엔티티와 테이블이 맞는지 검사합니다(`JPA_DDL_AUTO` 기본 `validate`). 맞지 않으면 서버가 뜨지 않습니다.
- `created_at`이 있는 테이블의 엔티티는 `BaseTimeEntity`를 상속합니다. 값은 DB 기본값으로 채웁니다.
- 엔티티는 `@Getter` + `@NoArgsConstructor(access = PROTECTED)`만 두고 setter를 열지 않습니다. 값 변경은 의미 있는 이름의 메서드로 합니다.
- 연관관계는 `FetchType.LAZY`가 기본입니다. `open-in-view: false`이므로 지연 로딩은 서비스의 `@Transactional` 안에서 끝냅니다. 조회만 하면 `@Transactional(readOnly = true)`.

### 로그·보안
- 이름·휴대폰 번호·농장식별번호 등 개인정보와 요청 본문을 로그에 남기지 않습니다.
- 요청 처리 중 남긴 로그 줄에는 `request_id`가 자동으로 붙습니다(`RequestIdFilter`). 로그 형식을 바꾸지 않습니다.
- 비밀값은 환경변수로만 받습니다(`application.yml`에서 `${KEY}`). 새 환경변수는 그 키를 읽는 코드를 넣는 PR에서 `.env.example`에 키 이름만 추가하고 값은 커밋하지 않습니다. 아직 쓰지 않는 키는 미리 넣지 않습니다.

### 테스트·확인
- PR 전에 `services/api`에서 `./gradlew build`(테스트 포함)가 통과해야 합니다. CI(`ci.yml`)도 같은 명령을 돌립니다.
- 분기·검증·권한·오류 처리가 있는 코드는 테스트를 같이 씁니다. DB 없이 전체 설정을 띄우는 방법은 `CommonResponseTest`를 참고합니다.
- 실행해 보지 않은 것은 "확인했다"고 쓰지 않습니다. 테스트 결과는 통과·실패 수를 그대로 적습니다.

## 이 레포에서 하지 않는 것
- 질병 판정·확률·자동 신고로 읽히는 표현을 코드/문서/안내 문구에 쓰지 않습니다.
- 안내 문구는 공식 자료(SOP·법령) 원문만 사용합니다. (근거는 `mnm-data/knowledge-base/` 참고)
- 농가가 승인한 기록만 접수자에게 전달합니다.
- 모델 가중치·원본 데이터는 git에 커밋하지 않습니다. Object Storage / MLflow를 사용합니다.
- API 키·농가 개인정보를 코드·로그·커밋에 남기지 않습니다.

## 연결된 다른 레포
- `mnm-frontend` — 농가 앱·접수자 웹·어드민. 응답·오류 형식이나 API가 바뀌면 이쪽 `packages/api-client/`에 영향이 갑니다. PR에 프론트 담당 확인을 요청합니다.
- `mnm-ai` — 에이전트/모델 추론. 입출력 형식은 `mnm-ai/schemas` 기준. AI 오류 코드(`STT-FAIL` 등)는 서버 안에서 대체 경로로 처리하고 클라이언트 오류 코드로 내보내지 않습니다.
- `mnm-data` — DB 마이그레이션·공공데이터·지식베이스. DB 구조는 `mnm-data/migrations`에서만 변경.
