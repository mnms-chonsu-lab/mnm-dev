# infra/docker

스테이징 서버 배포용 설정. `.github/workflows/deploy.yml`이 이 폴더를 서버 `~/mnm`에 복사해서 씁니다.

- **담당**: @dooooing

| 파일 | 내용 |
| --- | --- |
| `compose.deploy.yml` | 서버에서 띄우는 `api`(GHCR 이미지) · `nginx` · `certbot`(인증서 발급·갱신 때만) |
| `nginx/locations.conf` | 경로 접두사(`/api/farmer` 등)로 API 서버에 전달, 스웨거 바로가기, `/health` |
| `nginx/*.conf.template`, `nginx/start.sh` | 인증서가 있으면 HTTPS, 없으면 HTTP 설정으로 시작 |

- API 서버의 Dockerfile은 `services/api/Dockerfile`, 로컬 전체 실행은 레포 루트 `docker-compose.yml`입니다.
- 서버의 `.env` 키 목록은 레포 루트 `.env.example`을 따릅니다.
