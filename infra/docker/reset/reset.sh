#!/bin/sh
# 스테이징 데이터 초기화 (#40): 테스트 기록 지우기 → 데모 시드 다시 넣기. 한 트랜잭션.
#
#   ./reset.sh --dry-run          지울 건수만 본다 (아무것도 안 바뀜)
#   ./reset.sh <데모 시드.sql>     지우기 + 시드 + 결과 건수. 중간에 하나라도 실패하면 전부 되돌린다
#
# - DB 비밀번호는 실행할 때 입력받는다 (화면·기록에 안 남음). 기본 계정 mnm_api (데이터만 바꿈, 표 구조는 못 바꿈)
# - 데모 시드는 mnm-data/seed/ 의 파일을 쓴다. 이 스크립트는 표 구조를 바꾸지 않는다
# - 저장소 파일(field/·audio/) 정리는 reset_storage.py 로 따로 한다
set -eu

DIR=$(cd "$(dirname "$0")" && pwd)
DB_HOST=${DB_HOST:-pg-4c2g71.vpc-cdb-kr.ntruss.com}
DB_NAME=${DB_NAME:-mnm}
DB_USER=${DB_USER:-mnm_api}
PG_IMAGE=${PG_IMAGE:-postgres:17}

usage() { sed -n 4,5p "$0" | sed 's/^# *//'; exit 2; }
[ $# -eq 1 ] || usage

if [ "$1" = "--dry-run" ]; then
  MODE=dry; SEED_MOUNT=""
else
  SEED=$(cd "$(dirname "$1")" && pwd)/$(basename "$1")
  [ -f "$SEED" ] || { echo "시드 파일이 없어요: $1" >&2; exit 2; }
  # 시드 파일 맨 앞뒤의 BEGIN; / COMMIT; 줄만 뺀다. 지우기와 시드를 한 트랜잭션(-1)으로 묶기 위해서
  # (mnm-dev Flyway 사본과 같은 방식. DO 블록 안의 BEGIN 은 ; 가 없어서 남는다)
  SEED_DIR=$(mktemp -d)
  sed -E '/^[[:space:]]*(BEGIN|COMMIT)[[:space:]]*;[[:space:]]*$/d' "$SEED" > "$SEED_DIR/seed.sql"
  chmod 755 "$SEED_DIR"; chmod 644 "$SEED_DIR/seed.sql"
  MODE=run; SEED_MOUNT="-v $SEED_DIR/seed.sql:/seed/seed.sql:ro"
fi

# 비밀번호는 권한 600 임시 파일로만 넘긴다. 명령줄(-e PGPASSWORD=값)에 넣으면 sudo 기록(auth.log)에 남는다
ENV_FILE=$(mktemp); chmod 600 "$ENV_FILE"; trap 'rm -f "$ENV_FILE"; [ -n "${SEED_DIR:-}" ] && rm -rf "$SEED_DIR"' EXIT INT TERM
printf '%s 비밀번호: ' "$DB_USER"; stty -echo 2>/dev/null || true; read -r pw; stty echo 2>/dev/null || true; echo
printf 'PGPASSWORD=%s\n' "$pw" > "$ENV_FILE"; unset pw

psql() {
  # shellcheck disable=SC2086  # SEED_MOUNT 는 비어 있거나 -v 한 쌍
  sudo docker run --rm -i --env-file "$ENV_FILE" -v "$DIR:/reset:ro" $SEED_MOUNT "$PG_IMAGE" \
    psql "host=$DB_HOST port=5432 dbname=$DB_NAME user=$DB_USER" -v ON_ERROR_STOP=1 -X -q "$@"
}

if [ "$MODE" = dry ]; then
  echo "== 지금 건수 (dry-run, 바뀌는 것 없음)"
  psql -f /reset/reset_counts.sql
  exit 0
fi

printf '%s 의 테스트 기록을 지우고 %s 를 다시 넣어요. 계속하려면 yes: ' "$DB_NAME" "$(basename "$SEED")"
read -r ok
[ "$ok" = yes ] || { echo "멈춤"; exit 1; }

START=$(date +%s)
# -1: 아래 파일 전부를 한 트랜잭션으로. ON_ERROR_STOP: 하나라도 실패하면 전부 되돌림
psql -1 \
  -c "\\echo == 지우기 전" -f /reset/reset_counts.sql \
  -f /reset/reset_delete.sql \
  -f /seed/seed.sql \
  -c "\\echo == 시드 넣은 뒤" -f /reset/reset_counts.sql
echo "끝: $(( $(date +%s) - START ))초. 앱에서 데모 계정 로그인 → 기록 1건 확인하세요"
