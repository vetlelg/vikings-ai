@echo off
REM Viking Settlement — Start all services (Windows)
REM Usage: start.bat [stub|claude|gemini|groq]

set PROVIDER=%1
if "%PROVIDER%"=="" set PROVIDER=stub

echo === Viking Settlement ===
echo LLM Provider: %PROVIDER%
echo.

REM 1. Start Kafka
echo [1/4] Starting Kafka...
docker compose up -d
echo Waiting for Kafka...
timeout /t 20 /nobreak >nul
echo.

REM 2. Start Engine (new window)
echo [2/4] Starting Game Engine...
start "Viking Engine" cmd /c "cd backend && set LLM_PROVIDER=%PROVIDER% && gradlew.bat :engine:run"
timeout /t 5 /nobreak >nul

REM 3. Start Bridge (new window)
echo [3/4] Starting WebSocket Bridge...
start "Viking Bridge" cmd /c "cd backend && gradlew.bat :bridge:run"
timeout /t 3 /nobreak >nul

REM 4. Start Agents (new window)
echo [4/4] Starting AI Agents...
start "Viking Agents" cmd /c "cd backend && set LLM_PROVIDER=%PROVIDER% && gradlew.bat :agent:run"

echo.
echo === Services starting in separate windows ===
echo.
echo Start the frontend manually:
echo   cd frontend
echo   npm run dev
echo.
echo Then open http://localhost:5173
echo.
pause
