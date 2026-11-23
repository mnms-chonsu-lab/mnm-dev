#!/bin/sh
# 실행 중인 nginx 에 바뀐 설정을 끊김 없이 다시 읽힌다. deploy.yml 이 배포 끝에 실행한다.
# - start.sh 와 같은 기준으로 default.conf 를 다시 만든다 (템플릿이 바뀐 경우)
# - include 파일(common.conf, locations.conf)은 마운트라 이미 새 파일
# - 문법 검사(nginx -t)가 실패하면 default.conf 를 되돌리고 다시 읽지 않는다 → 지금 설정으로 계속 동작, 배포는 실패로 표시
set -e

if [ -f "/etc/letsencrypt/live/${DOMAIN}/fullchain.pem" ]; then
    template=tls
else
    template=http
fi

conf=/etc/nginx/conf.d/default.conf
cp "$conf" /tmp/default.conf.bak
envsubst '${DOMAIN}' < "/etc/nginx/mnm/${template}.conf.template" > "$conf"

if nginx -t; then
    nginx -s reload
    echo "nginx: ${template} 설정 다시 읽음 (${DOMAIN})"
else
    cp /tmp/default.conf.bak "$conf"
    echo "nginx: 설정 오류로 다시 읽지 않음 (지금 설정 유지)" >&2
    exit 1
fi
