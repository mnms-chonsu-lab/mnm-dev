# CLAUDE.md — mnm-dev

이 파일은 AI 도구(Claude 등)가 이 레포에서 이슈·PR·코드를 작성할 때 지켜야 할 규칙입니다.

## 이슈·PR 작성
- 이슈는 `.github/ISSUE_TEMPLATE/`의 5개 템플릿 중 하나의 섹션 순서·제목 형식을 **그대로** 사용합니다. 임의로 섹션을 생략·재배치하지 않습니다.
- PR은 `.github/pull_request_template.md`의 섹션 순서를 그대로 사용합니다.
- 이슈 제목: `[타입] 요약`. PR 제목: `[타입] 요약 (#이슈번호)`. 대괄호는 맨 앞 타입 하나만 씁니다.
- 타입: `feat` / `fix` / `docs` / `chore` / `test` / `refactor` / `design` / `review`.
- 브랜치명: `YYYYMMDD_#이슈번호_요약`.

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
- `services/api/` Spring Boot 백엔드
- `infra/docker/` Dockerfile·compose 보조 설정 · `infra/aws/` AWS 배포 구성
- `docs/api/openapi.yaml` API 명세의 기준 문서 · `docs/decisions/` 결정 기록
- 백엔드는 Java + Spring Boot로 만듭니다. Java·Spring Boot 버전과 빌드 도구는 아직 정하지 않았습니다. 사용자가 요청하지 않으면 프로젝트를 초기화하거나 의존성을 추가하지 않습니다.
- 화면 목록·기능 명세는 별도 문서에서 관리합니다. README·코드 주석에 복사해 두지 않습니다.

## 이 레포에서 하지 않는 것
- 질병 판정·확률·자동 신고로 읽히는 표현을 코드/문서/안내 문구에 쓰지 않습니다.
- 안내 문구는 공식 자료(SOP·법령) 원문만 사용합니다. (근거는 `mnm-data/knowledge-base/` 참고)
- 농가가 승인한 기록만 접수자에게 전달합니다.
- 모델 가중치·원본 데이터는 git에 커밋하지 않습니다. S3 / MLflow를 사용합니다.
- API 키·농가 개인정보를 코드·로그·커밋에 남기지 않습니다.
- 새 환경변수는 `.env.example`에 키 이름만 추가합니다. 값은 커밋하지 않습니다.

## 연결된 다른 레포
- `mnm-frontend` — 농가 앱·접수자 웹·어드민. API 형식이 바뀌면 이쪽 `packages/api-client/`에 영향이 갑니다.
- `mnm-ai` — 에이전트/모델 추론. 입출력 형식은 `mnm-ai/schemas` 기준.
- `mnm-data` — DB 마이그레이션·공공데이터·지식베이스. DB 구조는 `mnm-data/migrations`에서만 변경.
