#!/bin/sh
# Écrit config.json à partir des variables d'environnement, puis démarre nginx.
# BACKEND_URL : URL complète du backend, par exemple http://backend:8081
# CONTEXT_PATH : préfixe global du backend, par exemple /apigen
# Laisser les deux vides pour conserver le proxy nginx du même conteneur.
set -e

CONFIG=/usr/share/nginx/html/config.json

escape_json() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

if [ -n "${BACKEND_URL}" ] || [ -n "${CONTEXT_PATH}" ]; then
  printf '{\n  "backendUrl": "%s",\n  "contextPath": "%s"\n}\n' \
    "$(escape_json "${BACKEND_URL:-}")" "$(escape_json "${CONTEXT_PATH:-}")" > "$CONFIG"
  echo "[api-gen] config.json : backendUrl=${BACKEND_URL:-} contextPath=${CONTEXT_PATH:-}"
fi

exec nginx -g 'daemon off;'
