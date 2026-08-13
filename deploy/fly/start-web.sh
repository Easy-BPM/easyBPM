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
nginx_pid=""

trap 'kill "$backend_pid" "$nginx_pid" 2>/dev/null || true' INT TERM

nginx -g 'pid /tmp/nginx.pid; daemon off;' &
nginx_pid="$!"

while kill -0 "$backend_pid" 2>/dev/null && kill -0 "$nginx_pid" 2>/dev/null; do
  sleep 2
done

status=1
if ! kill -0 "$backend_pid" 2>/dev/null; then
  wait "$backend_pid" || status="$?"
elif ! kill -0 "$nginx_pid" 2>/dev/null; then
  wait "$nginx_pid" || status="$?"
fi

kill "$backend_pid" "$nginx_pid" 2>/dev/null || true
wait "$backend_pid" 2>/dev/null || true
wait "$nginx_pid" 2>/dev/null || true
exit "$status"
