# infra/ncp

네이버 클라우드(NCP) 리소스 구성 기록. 콘솔에서 만든 리소스라 정의 파일은 없고, 무엇이 어떻게 설정됐는지만 적습니다.

- **담당**: @dooooing

| 리소스 | 용도 | 상태 |
| --- | --- | --- |
| Server (스테이징) | `mnm-api` + nginx 컨테이너 실행 (`infra/docker/compose.deploy.yml`) | 운영 중 |
| Cloud DB for PostgreSQL | 운영 DB. 스키마는 `mnm-data/migrations` | 운영 중 |
| ACG | 서버·DB 접근 제어 | 운영 중 |
| Object Storage | 사진·음성·영상 원본 (DB에는 경로만) | 🚧 구축 전 |

- 배포 이미지는 GHCR(`ghcr.io/mnms-chonsu-lab/mnm-api`)에 올리고, `.github/workflows/deploy.yml`이 SSH로 서버에 배포합니다.
- 비밀값(DB 접속 정보 등)은 서버 `~/mnm/.env`와 GitHub Secrets에만 둡니다. 여기에 적지 않습니다.
- 서버에서만 한 설정(Docker 설치, `~/mnm/.env`, 인증서 갱신 cron, SSH 키 전용 로그인)은 레포에 없습니다. 서버를 다시 만들면 수동으로 다시 해야 합니다.
