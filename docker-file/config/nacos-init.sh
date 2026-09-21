#!/bin/sh

set -eu

curl -sS -X POST "${NACOS_URL}/nacos/v3/auth/user/admin" \
  --data-urlencode "password=${NACOS_PASSWORD}" >/dev/null || true

login_response=$(curl -fsS -X POST "${NACOS_URL}/nacos/v3/auth/user/login" \
  --data-urlencode "username=${NACOS_USERNAME}" \
  --data-urlencode "password=${NACOS_PASSWORD}")
access_token=$(printf '%s' "${login_response}" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')

if [ -z "${access_token}" ]; then
  echo "Failed to obtain a Nacos access token." >&2
  exit 1
fi

if [ ! -f /state/config-published ]; then
  for config_file in /config/*.yml; do
    data_id=$(basename "${config_file}")
    curl -fsS -X POST "${NACOS_URL}/nacos/v3/admin/cs/config" \
      -H "accessToken:${access_token}" \
      --data-urlencode "dataId=${data_id}" \
      --data-urlencode "groupName=${NACOS_CONFIG_GROUP}" \
      --data-urlencode "content@${config_file}" >/dev/null
  done
  touch /state/config-published
fi
