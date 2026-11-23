# infra/ncp

네이버 클라우드(NCP) 리소스 구성 기록. 콘솔에서 만든 리소스라 정의 파일은 없고, 무엇이 어떻게 설정됐는지만 적습니다.

- **담당**: @dooooing

| 리소스 | 용도 | 상태 |
| --- | --- | --- |
| Server (스테이징) | `mnm-api` + nginx 컨테이너 실행 (`infra/docker/compose.deploy.yml`) | 운영 중 |
| Cloud DB for PostgreSQL | 스테이징 DB(`mnm-staging`). 스키마는 `mnm-data/migrations` | 운영 중 |
| ACG | 서버·DB 접근 제어 | 운영 중 |
| Object Storage | 사진·음성·영상 원본 (DB에는 경로만) | 운영 중 (스테이징 버킷) |

- 배포 이미지는 GHCR(`ghcr.io/mnms-chonsu-lab/mnm-api`)에 올리고, `.github/workflows/deploy.yml`이 SSH로 서버에 배포합니다.
- 비밀값(DB 접속 정보 등)은 서버 `~/mnm/.env`와 GitHub Secrets에만 둡니다. 여기에 적지 않습니다.
- 서버에서만 한 설정(Docker 설치, `~/mnm/.env`, 인증서 갱신 cron, SSH 키 전용 로그인, 스왑)은 레포에 없습니다. 서버를 다시 만들면 수동으로 다시 해야 합니다.

## Server (스테이징)

| 항목 | 값 |
| --- | --- |
| 스펙 | `c2-g3a` (vCPU 2 · 메모리 4GB, OS 기준 3.8GB) |
| 디스크 | 20GB |
| 스왑 | 2GB (`/swapfile`), `vm.swappiness=10` — 11/18 추가 (#17) |
| SSH | 키 전용 로그인. 팀원별 계정·공개 키 등록 |

11/18 기준 사용량 (`mnm-api` + nginx, AI 서버 올리기 전):

| 항목 | 값 |
| --- | --- |
| 메모리 사용 / 가용 | 0.94GB / 2.9GB |
| 디스크 사용 / 남음 | 6.6GB / 13GB |

- AI 서버(mnms-chonsu-lab/mnm-ai#7) 메모리는 약 2GB로 예상(제안값, 실측으로 조정). 올린 뒤 `docker stats`로 측정해서 #17에 기록하고, 부족하면 스펙 변경을 검토합니다.
- 스펙 변경은 서버를 정지해야 합니다. 같은 상품 유형 안에서만 바꿀 수 있습니다.

### 스왑 다시 만들기 (서버를 새로 만든 경우)

```bash
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
echo 'vm.swappiness=10' | sudo tee /etc/sysctl.d/99-swappiness.conf
sudo sysctl -p /etc/sysctl.d/99-swappiness.conf
free -h   # Swap 2.0Gi 확인
```

## Object Storage

| 항목 | 값 |
| --- | --- |
| 버킷 | `mnm-raw-stg` (스테이징) |
| 엔드포인트 | `https://kr.object.ncloudstorage.com` · 리전 `kr-standard` |
| 공개 여부 | 비공개 (서명 없는 주소는 403) |
| 접근 방식 | 서버: 접근 키 / AI 서버·앱: 서명 URL (`s3v4`) |
| CORS | `https://chonsu-farmer.vercel.app`, `http://localhost:5173` · `POST`·`GET` · `Content-Type` (#24) |
| 키 | ETL(mnms-chonsu-lab/mnm-data#1)과 같은 키. 서비스용 분리 (제안) |

11/20 점검 (#24, 스테이징 서버에서 실행):

| 점검 | 결과 |
| --- | --- |
| 버킷 접근 · 키로 업로드 · 다운로드 | 통과 |
| 서명 없는 주소 | 403 (비공개) |
| 서명 URL 다운로드 · 업로드 | 200 |
| 9MB 서명 URL 다운로드 (무작위 9MB 파일, 서버 → 버킷, 3회) | 0.06~0.10초 |
| 서명 URL 만료 (10초 URL → 15초 뒤) | 403 |
| 업로드 10MB 제한 (POST `content-length-range`) | 11MB 거절 (`EntityTooLarge`) |
| CORS 사전 요청 (목록 밖 주소) | 403 |

- 최신 `boto3`는 `request_checksum_calculation="when_required"`를 주지 않으면 업로드가 `AccessDenied`로 실패합니다 (mnms-chonsu-lab/mnm-data#1).
- CORS 설정(`put_bucket_cors`)은 `Content-MD5` 헤더가 필요합니다. `boto3`는 `before-sign` 이벤트로 직접 붙여야 합니다.
- 키 값은 여기에 적지 않습니다. 서버 `.env`와 GitHub Secrets에만 둡니다.

## Cloud DB for PostgreSQL (스테이징)

| 항목 | 값 |
| --- | --- |
| DB 서비스 | `mnm-staging` · DB `mnm` (접속 주소는 서버 `.env`) |
| 접근 | Private Subnet. 스테이징 서버에서만 (ACG) |
| 백업 | 매일 자동, 보관 1일, 시점 복원 가능 (11/20 콘솔 확인, mnms-chonsu-lab/mnm-data#1) |
| SSL 접속 | 미지원 (사설망이라 당장은 괜찮음, 운영 전 확인) |

| 계정 | 용도 | 권한 |
| --- | --- | --- |
| `mnm_owner` | 소유자. Flyway(테이블 생성·변경) | 전체 |
| `mnm_api` | API 서버 평소 요청 (#40 진행 중) | 테이블 읽기·쓰기·삭제 |
| `mnm_ai_log` | AI 서버 호출 기록 (mnms-chonsu-lab/mnm-ai#9) | `inference_log` INSERT만 |
| 개인 읽기 계정 | 팀원 조회용 | 읽기 |

- 비밀번호는 여기에 적지 않습니다. 서버 `.env`와 개인 전달로만 둡니다.

## 운영 환경 만들 때 할 일

지금은 스테이징 1대만 있습니다. 운영을 분리하기로 하면 아래 순서로 만듭니다 (#40). 값은 스테이징에서 측정·점검한 것을 기준으로 했습니다.

| 순서 | 할 일 | 스테이징 기준 / 비고 |
| --- | --- | --- |
| 1 | 서버 생성 | `c2-g3a`부터 시작 (스테이징 측정: AI 추론 최대 약 1.1GB, #17). 스왑 2GB (위 "스왑 다시 만들기") |
| 2 | ACG | SSH는 팀원 IP만, 80·443만 외부 공개. AI 서버는 외부 포트 없음 |
| 3 | Cloud DB 생성 | **백업 보관 7일** (제안값, mnms-chonsu-lab/mnm-data#1). SSL 접속 가능 여부 확인 |
| 4 | DB 계정 | `mnm_owner`(Flyway) · `mnm_api` · `mnm_ai_log` 처음부터 나눠서 생성. `mnm_owner`는 서버 사설 대역만 |
| 5 | Object Storage | 운영 버킷 따로. 비공개, CORS(운영 주소만), `audio/` 365일 자동 삭제 |
| 6 | 접근 키 | 서비스용 키를 ETL 키와 따로. 운영 버킷만 쓰게 권한 좁히기 (가능 여부 확인 안 함) |
| 7 | 도메인·HTTPS | 실제 도메인 또는 새 서버 IP의 `sslip.io` + Let's Encrypt |
| 8 | 서버 `.env` | 스테이징과 다른 값 (`JWT_SECRET`, DB 계정, 저장소 키, `AI_INTERNAL_TOKEN`, `FLYWAY_LOCATIONS=classpath:db/migration`으로 시드 빼기) |
| 9 | 배포 | `main` → 운영 자동 배포 (GitHub Secrets에 운영 서버 정보 따로) |
| 10 | 감시·알람 | `monitor`를 같이 띄우고, 외부 감시 서비스로 서버 꺼짐 확인 (#38) |
| 11 | 확인 | 헬스 체크, 로그인, 사진 업로드, AI 호출 1회, 오류 번호로 로그 찾기 |
