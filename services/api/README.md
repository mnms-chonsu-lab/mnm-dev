# services/api

Java Spring Boot 백엔드. `mnm-data/migrations`가 정의한 스키마를 적용해서 사용하고, 추론·에이전트 호출은 `mnm-ai`가 올린 이미지(GHCR)를 통해 수행합니다.

- **담당**: @dooooing, @joobogyeong
- **스택**: Java 21 · Spring Boot 4.1 · Gradle · PostgreSQL

## 패키지 구조

| 패키지 | 내용 | API 경로 |
| --- | --- | --- |
| `farmer` | 농가 앱 컨트롤러·서비스·리포지토리 | `/api/farmer/**` |
| `receiver` | 접수자 웹 컨트롤러·서비스·리포지토리 | `/api/receiver/**` |
| `admin` | 어드민 컨트롤러·서비스·리포지토리 | `/api/admin/**` |
| `auth` | 로그인·내 계정 | `/api/auth/**` |
| `domain` | DB 테이블과 1:1인 엔티티 (세 플랫폼이 공유) | - |
| `global` | 공통 설정 | - |

- 플랫폼 패키지끼리는 서로 참조하지 않습니다. 함께 쓰는 것은 `domain`의 엔티티뿐입니다.
- 엔티티는 `mnm-data/migrations/0001_init_operational.sql` 기준입니다. 스키마가 바뀌면 엔티티를 맞춥니다.
- 서버가 뜰 때 엔티티와 실제 테이블이 맞는지 검사합니다(`JPA_DDL_AUTO`, 기본 `validate`). 맞지 않으면 서버가 뜨지 않습니다.

## 로컬 실행

레포 루트의 `.env`에 `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`를 채운 뒤 실행합니다.

```bash
./gradlew bootRun
```

| 주소 | 내용 |
| --- | --- |
| `/swagger-ui/index.html` | 스웨거 (오른쪽 위에서 농가 앱·접수자 웹·어드민·인증 선택) |
| `/actuator/health` | 헬스 체크 (DB 연결 상태 포함) |

## 배포

- `develop` 머지 → `.github/workflows/deploy.yml`이 이미지를 GHCR에 올리고 스테이징 서버에 배포합니다.
- 서버에서는 nginx가 요청을 받아 경로 접두사로 구분한 뒤 API 서버로 넘깁니다. 설정은 `infra/docker/nginx/`에 있습니다.
- 스테이징에서는 `/docs/farmer`, `/docs/receiver`, `/docs/admin`, `/docs/auth`로 플랫폼별 스웨거에 바로 들어갈 수 있습니다.
