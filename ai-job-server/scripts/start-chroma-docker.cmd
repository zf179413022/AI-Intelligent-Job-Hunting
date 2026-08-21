@echo off
REM Start Chroma via Docker (RAG 5.5). Maps 127.0.0.1:8000 with persistent volume.
cd /d "%~dp0.."
docker compose -f docker-compose.chroma.yml up -d
docker compose -f docker-compose.chroma.yml ps
curl -s http://127.0.0.1:8000/api/v2/heartbeat
echo.
