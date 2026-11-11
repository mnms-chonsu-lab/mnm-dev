<div align="center">
  <h3>촌수의사 · mnm-dev</h3>
  <p>고령·소규모 한우농가가 말로 남긴 농장 일지를, 신고할 때 접수자(축협·방역기관)에게 한 장 요약으로 함께 전달하는 신고·기록·방역 업무 지원 AI</p>
  <p><b>FastAPI 백엔드 · 배포 인프라</b></p>
  <p>프론트엔드는 <a href="https://github.com/mnms-chonsu-lab/mnm-frontend">mnm-frontend</a>에서 관리합니다.</p>
  <p>
    <img src="https://img.shields.io/badge/FastAPI-Backend-009688?logo=fastapi&logoColor=white" alt="FastAPI" />
    <img src="https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white" alt="PostgreSQL" />
    <img src="https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white" alt="Docker" />
    <img src="https://img.shields.io/badge/AWS-Deploy-232F3E?logo=amazonaws&logoColor=white" alt="AWS" />
  </p>
  <sub>팀 M&amp;M's · 공모전 본선 MVP · 조직 <a href="https://github.com/mnms-chonsu-lab">mnms-chonsu-lab</a></sub>
</div>

---

## 목차

- [이 레포가 맡는 것](#이-레포가-맡는-것)
- [빠른 시작](#빠른-시작)
- [환경변수](#환경변수)
- [기술 스택](#기술-스택)
- [디렉터리 구조](#디렉터리-구조)
- [서비스 구성과 데이터 흐름](#서비스-구성과-데이터-흐름)
- [다른 레포와의 연결 방식](#다른-레포와의-연결-방식)
- [팀 소개](#팀-소개)
- [작업 흐름](#작업-흐름)
- [데이터 출처·라이선스](#데이터-출처라이선스)

---

## 이 레포가 맡는 것

| 핵심 기능 | 이 레포 담당 부분 |
| --- | --- |
| ① 말로 쓰는 농장 일지 | 음성·사진 업로드·저장 API |
| ② 신고 기한 알림 + 신고서 초안 | 신고서 API (기한·상태 계산은 `mnm-ai/rules/` 결과 사용) |
| ③ 기존 신고 경로로 연결 | 전화·사이트·축협 연결과 기록 전달 API |
| ④ 접수자용 한 장 요약 보기 | 요약 카드 조회 API |

그 외: 전체 `docker-compose.yml`, 배포 인프라(`infra/`), API 명세(`docs/api/openapi.yaml`).

> 현재 상태: 디렉터리 골격과 README만 있고 애플리케이션 코드는 아직 없습니다. 아래 🚧 표시는 모두 구현 예정입니다.

---

## 빠른 시작

```bash
git clone https://github.com/mnms-chonsu-lab/mnm-dev.git
cd mnm-dev
cp .env.example .env      # 키 값 채우기
```

| 단계 | 명령 | 상태 |
| --- | --- | --- |
| 전체 실행 | `docker compose up -d` | 🚧 구현 예정 (`docker-compose.yml`은 뼈대, `services/api` Dockerfile 없음) |
| 백엔드 | `services/api/` 의존성 설치·실행 | 🚧 구현 예정 |

`docker-compose.yml` 구성: `db`(postgres:16), `api`(`services/api` 빌드), `mnm-ai`(`ghcr.io/mnms-chonsu-lab/mnm-ai-inference:latest`). private GHCR 이미지는 `GHCR_READ_TOKEN`으로 로그인해야 받을 수 있습니다.

> 모델 가중치·원본 데이터는 git에 넣지 않습니다. S3 / MLflow에서 가져와 사용합니다.

---

## 환경변수

값은 `.env.example`을 복사해 `.env`에 채웁니다. 실제 키는 커밋하지 않습니다.

| 변수 | 위치 | 용도 | 없으면 |
| --- | --- | --- | --- |
| `DATABASE_URL` | `services/api` | PostgreSQL 접속 문자열 | API가 DB에 연결하지 못함 |
| `JWT_SECRET` | `services/api` | 인증 토큰 서명 | 로그인·인증 불가 |
| `AWS_ACCESS_KEY_ID` | `services/api`, 배포 | AWS 자격 증명 | S3 업로드·배포 불가 |
| `AWS_SECRET_ACCESS_KEY` | `services/api`, 배포 | AWS 자격 증명 | S3 업로드·배포 불가 |
| `AWS_REGION` | `services/api`, 배포 | AWS 리전 | S3 업로드·배포 불가 |
| `S3_BUCKET_NAME` | `services/api` | 사진·음성 업로드 버킷 | 파일 업로드 불가 |
| `MNM_AI_SERVICE_URL` | `services/api` | mnm-ai 추론·에이전트 서비스 주소 | AI 기능 호출 불가 |
| `GHCR_READ_TOKEN` | 로컬·배포 | private GHCR의 `mnm-ai-*` 이미지 pull | mnm-ai 이미지를 받지 못함 |

`docker-compose.yml`은 `POSTGRES_PASSWORD`도 읽습니다 (기본값 있음, `.env.example`에는 없음).

---

## 기술 스택

| 영역 | 기술 | 용도 |
| --- | --- | --- |
| 백엔드 | FastAPI | REST API |
| DB | PostgreSQL 16 | 일지·신고서·요약 카드 저장 (스키마는 `mnm-data/migrations`) |
| 저장소 | AWS S3 | 사진·음성 파일 |
| 배포 | Docker, AWS, GitHub Actions | 컨테이너 실행·운영 배포 |
| 모니터링 | CloudWatch | 운영 로그·지표 |
| AI 연동 | `mnm-ai` 이미지 (GHCR) | 에이전트·규칙·인식 모델 호출 |

---

## 디렉터리 구조

```
mnm-dev/
├── services/
│   └── api/               # FastAPI 백엔드 — @joobogyeong, @dooooing
├── infra/
│   ├── docker/            # Dockerfile·compose 보조 설정
│   └── aws/               # AWS 배포 구성
├── docs/
│   ├── api/               # openapi.yaml — API 명세의 기준 문서
│   └── decisions/         # 설계·의사결정 기록
├── .github/               # 이슈·PR 템플릿, CODEOWNERS, 라벨
├── docker-compose.yml     # 전체 실행 뼈대 (api, db, mnm-ai)
├── .env.example           # 환경변수 키 이름
├── CONTRIBUTING.md · CLAUDE.md · AGENTS.md
└── README.md
```

---

## 서비스 구성과 데이터 흐름

이 레포는 흐름 전체를 잇는 API를 맡고, 화면(농가 앱·접수자 웹·어드민)은 `mnm-frontend`가 맡습니다. 단계별 처리(STT·구조화·검증)는 `mnm-ai`가 수행하고, 이 레포의 API가 호출합니다.

```
농가 음성·사진
  → STT · LLM 구조화
  → 일지 · 신고서 초안
  → 부위 일치 검증
  → 농가 승인
  → 요약 카드
  → 선택한 신고 경로로 전달 (전화 · 사이트 · 축협)
  → 접수자 웹 열람
```

**지키는 원칙**

| 원칙 | 내용 |
| --- | --- |
| 판정하지 않음 | 질병·신고 여부를 판정하지 않는다. 확률·자동 신고로 읽히는 표현을 쓰지 않는다 |
| `[바로 신고]` 상시 노출 | 모든 화면에 항상 보인다 |
| 공식 문구만 | 안내 문구는 공식 지침(SOP·법령) 원문만 사용한다 |
| 승인된 기록만 전달 | 농가가 승인한 기록만 접수자에게 넘어간다 |

---

## 다른 레포와의 연결 방식

| 방향 | 규칙 |
| --- | --- |
| mnm-ai → mnm-dev | mnm-ai는 `main` 머지 시 GitHub Actions로 이미지를 GHCR(`ghcr.io/mnms-chonsu-lab/mnm-ai-*`)에 올립니다. 이 레포는 태그로 그 이미지를 가져다 씁니다. **입출력 형식은 `mnm-ai/schemas`가 기준**입니다 |
| mnm-data → mnm-dev | DB 구조 변경은 `mnm-data/migrations`에서만 합니다. 이 레포의 백엔드는 그 마이그레이션을 적용해서 씁니다 (스키마 직접 변경 금지) |
| API 명세 | `docs/api/openapi.yaml`이 기준입니다. 바꿀 땐 PR에 관련 레포 담당자를 리뷰어로 지정합니다 |
| PR 링크 | 다른 레포에 영향을 주는 PR은 본문에 관련 레포 PR·이슈 링크를 반드시 넣습니다 |

- [mnm-frontend](https://github.com/mnms-chonsu-lab/mnm-frontend) — 농가 앱·접수자 웹·어드민
- [mnm-ai](https://github.com/mnms-chonsu-lab/mnm-ai) — 에이전트·규칙·인식 모델
- [mnm-data](https://github.com/mnms-chonsu-lab/mnm-data) — DB 마이그레이션·공공데이터·지식베이스

---

## 팀 소개

| 이름 | 역할 | GitHub |
| --- | --- | --- |
| 이유민 | PM · Frontend | [@kkamret](https://github.com/kkamret) |
| 이인서 | Backend · Infra · MLOps | [@dooooing](https://github.com/dooooing) |
| 박명훈 | AI | [@parkmyeonghun](https://github.com/parkmyeonghun) |
| 서인교 | Data | [@tjdlsry](https://github.com/tjdlsry) |

**일정**: 11/21 기능 개발 마감(이후 버그 수정만) → 11/22 운영 배포 → 11/22~27 QA·안정화 → 11/28 제출

---

## 작업 흐름

```
이슈 (템플릿 선택) → 브랜치 → develop으로 PR → 리뷰 → squash merge
```

| 항목 | 규칙 |
| --- | --- |
| 이슈 | `.github/ISSUE_TEMPLATE/`의 템플릿 5개 중 선택 (빈 이슈 금지) |
| 브랜치 | `YYYYMMDD_#이슈번호_요약` — develop에서 분기, 예) `20261112_#12_농가_라우터_STT` |
| PR | `.github/pull_request_template.md` 사용, 대상은 `develop` |
| PR 제목 | `type(영역): 요약 (#이슈번호)` — type: `feat` `fix` `docs` `chore` `test` `refactor` |
| 머지 | squash merge만, 머지 후 브랜치 자동 삭제 |
| 원칙 | 1 이슈 = 1 브랜치 = 1 PR |

자세한 규칙: [CONTRIBUTING.md](./CONTRIBUTING.md) · AI 도구 사용 시: [CLAUDE.md](./CLAUDE.md), [AGENTS.md](./AGENTS.md)

### 배포 Secrets (예정)

`.github/workflows/`는 비워뒀고, 배포(`deploy.yml`)는 @dooooing이 구현 시 추가합니다. 필요한 Secrets 이름:

| Secret | 용도 |
| --- | --- |
| `AWS_ACCESS_KEY_ID` | AWS 배포용 자격 증명 |
| `AWS_SECRET_ACCESS_KEY` | AWS 배포용 자격 증명 |
| `AWS_REGION` | 배포 리전 |
| `DATABASE_URL` | 운영 DB 접속 문자열 |
| `GHCR_READ_TOKEN` | `mnm-ai` 이미지 pull용 (private GHCR) |
| `S3_BUCKET_NAME` | 업로드 파일 버킷 |

---

## 데이터 출처·라이선스

| 데이터 | 출처 | 이 서비스에서의 용도 | 이용 조건 |
| --- | --- | --- | --- |
| 가축 질병 발생 정보 | 민관협력 지원 플랫폼 공공데이터 | 발생 현황 참고 | 제공처 이용 조건 확인 필요 |
| 사육규모별 농장 통계 | 민관협력 지원 플랫폼 공공데이터 | 농가 규모 정보 | 제공처 이용 조건 확인 필요 |
| 거점소독시설 | 민관협력 지원 플랫폼 공공데이터 | 소독 시설 안내 | 제공처 이용 조건 확인 필요 |
| 구제역·럼피스킨 SOP, 가축전염병 예방법 | 공식 지침·법령 | 안내 문구 원문 | 공문서·법령 원문 (출처 표기) |
| 한우 행동 데이터 | AI-Hub | 소 행동 인식 모델(YOLO11s) 학습·평가 | **AI-Hub 이용약관 준수** — 원본 재배포 금지, git 커밋 금지 |
| 부위 판정 이미지 | FMD Cattle, Cattle side/back view | 부위 일치 검증 모델(SigLIP 2) 학습·평가 | FMD Cattle: **CC BY 4.0** (출처 표기). Cattle side/back view: 🚧 라이선스 확인 중 |

> 🚧 레포 자체 라이선스는 아직 정하지 않았습니다. 정해지면 이 절에 추가합니다.
> 원본 데이터·모델 가중치는 git에 넣지 않고 S3 / MLflow에서 관리합니다.
