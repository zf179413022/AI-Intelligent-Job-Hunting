@echo off
REM Start MySQL + Redis + Chroma (infra only). Run from repo root or via this script.
cd /d "%~dp0.."
docker compose up -d
docker compose ps
echo.
echo MySQL  127.0.0.1:3306  (root / root, db=ai_job_platform)
echo Redis  127.0.0.1:6379
echo Chroma http://127.0.0.1:8000
echo.
curl -s http://127.0.0.1:8000/api/v2/heartbeat
echo.
