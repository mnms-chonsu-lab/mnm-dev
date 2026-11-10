# mnm-dev

촌수의사 — 한우농가 신고·기록·방역 업무 지원 AI. 농가 앱·접수자 웹·백엔드 API·배포 인프라를 담당하는 레포입니다. (M&M's, 공모전 본선 MVP)

## 담당
- `apps/**` — 이유민 (Frontend)
- `services/api/**`, `infra/**` — 이인서 (Backend·Infra)

## 구성
| 경로 | 내용 |
|---|---|
| `apps/farmer-app/` | 농가용 앱 (React+TypeScript+Vite PWA, KRDS 디자인 토큰) |
| `apps/receiver-web/` | 접수자(공무원)용 웹 |
| `services/api/` | FastAPI 백엔드 |
| `infra/docker/`, `infra/aws/` | Docker·AWS 배포 인프라 |
| `docs/api/` | OpenAPI 명세 (`openapi.yaml`) — API 기준 문서 |
| `docs/decisions/` | 설계/의사결정 기록 |

## 로컬 실행 방법
1. `.env.example`을 복사해 `.env` 생성 후 키 값 채우기
2. 루트 `docker-compose.yml`로 `api`, `db`, `mnm-ai` 이미지 실행
   ```
   docker compose up -d
   ```
3. 프론트엔드는 각 `apps/*` 디렉터리에서 `npm install && npm run dev`

> 모델 가중치·원본 데이터는 git에 넣지 않습니다. S3 / MLflow에서 가져와 사용합니다.

## 브랜치·PR 규칙
- 브랜치: `YYYYMMDD_#이슈번호_요약` (develop에서 분기, 한글 가능, 공백은 `_`)
- 1 이슈 = 1 브랜치 = 1 PR, **develop**으로 PR
- PR 제목: `type(영역): 요약 (#이슈번호)`
- squash merge만 허용, 머지 후 브랜치 자동 삭제
- 자세한 규칙은 [CONTRIBUTING.md](./CONTRIBUTING.md) 참고

## 연결 방식
- **mnm-ai → mnm-dev**: mnm-ai는 `main` 머지 시 GitHub Actions로 도커 이미지를 GHCR(`ghcr.io/mnms-chonsu-lab/mnm-ai-*`)에 올립니다. mnm-dev는 루트 `docker-compose.yml`에서 태그로 그 이미지를 가져와 실행합니다. 입출력 형식은 `mnm-ai`의 `schemas/`가 기준입니다.
- **mnm-data → mnm-dev**: DB 구조 변경은 `mnm-data/migrations/`에서만 합니다. `services/api/`는 그 마이그레이션을 적용해서 사용합니다 (직접 스키마를 바꾸지 않음).
- **API 명세**: `docs/api/openapi.yaml`이 기준입니다. 바꿀 땐 PR에 관련 레포(mnm-ai, mnm-data) 담당자를 리뷰어로 지정합니다.
- 다른 레포에 영향 주는 PR은 본문에 관련 레포 PR·이슈 링크를 반드시 포함합니다.

## 배포(`deploy.yml`)에 필요한 Secrets
현재 단계는 TODO만 있습니다. 실제 배포 구현 시 아래 Secrets를 Organization 또는 Repository 단위로 등록해야 합니다 (@dooooing 설정).

| Secret | 용도 |
|---|---|
| `AWS_ACCESS_KEY_ID` | AWS 배포용 자격 증명 |
| `AWS_SECRET_ACCESS_KEY` | AWS 배포용 자격 증명 |
| `AWS_REGION` | 배포 리전 |
| `DATABASE_URL` | 운영 DB 접속 문자열 |
| `GHCR_READ_TOKEN` | `mnm-ai` 이미지 pull용 (private GHCR) |
| `S3_BUCKET_NAME` | 업로드 파일 버킷 |
