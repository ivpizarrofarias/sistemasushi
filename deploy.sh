#!/usr/bin/env bash
# Actualiza y redespliega la aplicación en el servidor.
#
# Uso:
#   ./deploy.sh            # git pull + rebuild + reinicio
#   ./deploy.sh --no-pull  # solo rebuild + reinicio (sin tocar Git)
set -euo pipefail

cd "$(dirname "$0")"

PULL=1
if [[ "${1:-}" == "--no-pull" ]]; then
  PULL=0
fi

if [[ ! -f .env ]]; then
  echo "ERROR: falta el archivo .env (copia .env.example y define DB_PASSWORD)." >&2
  exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "ERROR: Docker no está instalado." >&2
  exit 1
fi

if [[ $PULL -eq 1 && -d .git ]]; then
  echo "==> Actualizando código (git pull --ff-only)"
  git pull --ff-only
fi

echo "==> Construyendo y reiniciando el contenedor"
docker compose up -d --build

echo "==> Estado del contenedor"
docker compose ps

PORT="$(sed -n 's/^APP_PORT=//p' .env | tail -1)"
PORT="${PORT:-8000}"

echo "==> Verificando http://127.0.0.1:${PORT}/"
for _ in $(seq 1 20); do
  if curl -fsS -o /dev/null "http://127.0.0.1:${PORT}/" 2>/dev/null; then
    echo "OK: la aplicación responde en el puerto ${PORT}."
    exit 0
  fi
  sleep 2
done

echo "ADVERTENCIA: la aplicación no respondió. Revisa: docker compose logs -f app" >&2
exit 1
