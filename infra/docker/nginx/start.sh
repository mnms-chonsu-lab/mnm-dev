#!/bin/sh
# 인증서가 있으면 HTTPS 설정, 없으면 HTTP 전용 설정으로 nginx를 띄운다.
# 최초 발급 전에도 nginx가 떠 있어야 certbot 검증(/.well-known)이 가능하기 때문.
set -e

if [ -f "/etc/letsencrypt/live/${DOMAIN}/fullchain.pem" ]; then
    template=tls
else
    template=http
fi

envsubst '${DOMAIN}' < "/etc/nginx/mnm/${template}.conf.template" > /etc/nginx/conf.d/default.conf
echo "nginx: ${template} 설정으로 시작 (${DOMAIN})"
exec nginx -g 'daemon off;'
