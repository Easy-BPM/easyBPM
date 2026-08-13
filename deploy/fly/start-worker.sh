#!/usr/bin/env sh
set -eu

. /app/bin/render-env.sh

exec java ${JAVA_OPTS:-} -jar /app/worker.jar
