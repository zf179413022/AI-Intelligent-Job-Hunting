@echo off
REM Prefer repo-root infra (MySQL+Redis+Chroma). This script only starts Chroma.
cd /d "%~dp0.."
echo [hint] Full stack: cd repo root ^&^& docker compose up -d
docker compose -f docker-compose.chroma.yml up -d
docker compose -f docker-compose.chroma.yml ps
curl -s http://127.0.0.1:8000/api/v2/heartbeat
echo.
