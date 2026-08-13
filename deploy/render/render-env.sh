#!/usr/bin/env sh
set -eu

configure_database_url() {
  raw_database_url="${DATABASE_URL:-${NEON_DATABASE_URL:-}}"
  if [ -z "$raw_database_url" ]; then
    return
  fi

  case "$raw_database_url" in
    postgresql://*|postgres://*) ;;
    jdbc:postgresql://*)
      export EASY_BPM_SERVER_DATASOURCE_URL="${EASY_BPM_SERVER_DATASOURCE_URL:-$raw_database_url}"
      export EASY_BPM_WORKER_DATASOURCE_URL="${EASY_BPM_WORKER_DATASOURCE_URL:-$raw_database_url}"
      return
      ;;
    *)
      echo "Unsupported DATABASE_URL format. Use postgresql://... or jdbc:postgresql://..." >&2
      return 1
      ;;
  esac

  without_scheme="${raw_database_url#postgresql://}"
  without_scheme="${without_scheme#postgres://}"
  authority="${without_scheme%%/*}"
  path_and_query="${without_scheme#*/}"

  if [ "$authority" != "${authority#*@}" ]; then
    credentials="${authority%@*}"
    hostport="${authority#*@}"
    username="${credentials%%:*}"
    password="${credentials#*:}"
  else
    hostport="$authority"
    username=""
    password=""
  fi

  if [ "$path_and_query" != "${path_and_query#*\?}" ]; then
    database="${path_and_query%%\?*}"
    query="${path_and_query#*\?}"
    jdbc_query="$(printf '%s' "$query" | sed 's/channel_binding=/channelBinding=/g')"
    jdbc_url="jdbc:postgresql://$hostport/$database?$jdbc_query"
  else
    jdbc_url="jdbc:postgresql://$hostport/$path_and_query"
  fi

  export EASY_BPM_SERVER_DATASOURCE_URL="${EASY_BPM_SERVER_DATASOURCE_URL:-$jdbc_url}"
  export EASY_BPM_WORKER_DATASOURCE_URL="${EASY_BPM_WORKER_DATASOURCE_URL:-$jdbc_url}"

  if [ -n "$username" ]; then
    export EASY_BPM_SERVER_DATASOURCE_USERNAME="${EASY_BPM_SERVER_DATASOURCE_USERNAME:-$username}"
    export EASY_BPM_WORKER_DATASOURCE_USERNAME="${EASY_BPM_WORKER_DATASOURCE_USERNAME:-$username}"
  fi

  if [ -n "$password" ]; then
    export EASY_BPM_SERVER_DATASOURCE_PASSWORD="${EASY_BPM_SERVER_DATASOURCE_PASSWORD:-$password}"
    export EASY_BPM_WORKER_DATASOURCE_PASSWORD="${EASY_BPM_WORKER_DATASOURCE_PASSWORD:-$password}"
  fi
}

mirror_server_settings_for_worker() {
  export EASY_BPM_WORKER_RABBITMQ_HOST="${EASY_BPM_WORKER_RABBITMQ_HOST:-${EASY_BPM_SERVER_RABBITMQ_HOST:-localhost}}"
  export EASY_BPM_WORKER_RABBITMQ_PORT="${EASY_BPM_WORKER_RABBITMQ_PORT:-${EASY_BPM_SERVER_RABBITMQ_PORT:-5672}}"
  export EASY_BPM_WORKER_RABBITMQ_USERNAME="${EASY_BPM_WORKER_RABBITMQ_USERNAME:-${EASY_BPM_SERVER_RABBITMQ_USERNAME:-easybpm}}"
  export EASY_BPM_WORKER_RABBITMQ_PASSWORD="${EASY_BPM_WORKER_RABBITMQ_PASSWORD:-${EASY_BPM_SERVER_RABBITMQ_PASSWORD:-easybpm}}"
}

configure_database_url
mirror_server_settings_for_worker
