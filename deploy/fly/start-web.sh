#!/usr/bin/env sh
set -eu

export PORT="${PORT:-8080}"
export EASY_BPM_SERVER_PORT="${EASY_BPM_SERVER_PORT:-8081}"
export EASY_BPM_SERVER_ADDRESS="${EASY_BPM_SERVER_ADDRESS:-127.0.0.1}"

envsubst '${PORT} ${EASY_BPM_SERVER_PORT}' \
  < /etc/nginx/templates/easybpm.conf.template \
  > /etc/nginx/conf.d/default.conf

java ${JAVA_OPTS:-} -jar /app/backend.jar &
backend_pid="$!"

trap 'kill "$backend_pid" 2>/dev/null || true' INT TERM

exec nginx -g 'daemon off;'
