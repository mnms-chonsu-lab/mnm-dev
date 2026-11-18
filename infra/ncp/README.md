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
