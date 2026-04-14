@echo off
REM Viking Settlement — Start all services (Windows)
REM Usage: start.bat [stub|claude|gemini|groq]
REM If no argument given, reads LLM_PROVIDER from .env (defaults to stub)

REM Load .env file if it exists
if exist "%~dp0.env" (
    for /f "usebackq tokens=1,* delims==" %%A in ("%~dp0.env") do (
        set "line=%%A"
        if not "!line:~0,1!"=="#" (
            set "%%A=%%B"
        )
    )
)

REM Command-line arg overrides .env
if not "%1"=="" set LLM_PROVIDER=%1
if "%LLM_PROVIDER%"=="" set LLM_PROVIDER=stub

setlocal enabledelayedexpansion

echo === Viking Settlement ===
echo LLM Provider: %LLM_PROVIDER%
echo LLM Model:    %LLM_MODEL%
echo.

REM 1. Start Kafka
echo [1/5] Starting Kafka...
docker compose up -d
echo Waiting for Kafka...
timeout /t 20 /nobreak >nul
echo.

REM 2. Start Engine (new window)
echo [2/5] Starting Game Engine...
start "Viking Engine" cmd /c "cd /d %~dp0backend && set LLM_PROVIDER=%LLM_PROVIDER% && set LLM_MODEL=%LLM_MODEL% && set LLM_API_KEY=%LLM_API_KEY% && gradlew.bat :engine:run"
timeout /t 5 /nobreak >nul

REM 3. Start Bridge (new window)
echo [3/5] Starting WebSocket Bridge...
start "Viking Bridge" cmd /c "cd /d %~dp0backend && gradlew.bat :bridge:run"
timeout /t 3 /nobreak >nul

REM 4. Start Agents (new window)
echo [4/5] Starting AI Agents...
start "Viking Agents" cmd /c "cd /d %~dp0backend && set LLM_PROVIDER=%LLM_PROVIDER% && set LLM_MODEL=%LLM_MODEL% && set LLM_API_KEY=%LLM_API_KEY% && gradlew.bat :agent:run"
timeout /t 2 /nobreak >nul

REM 5. Start Frontend (new window)
echo [5/5] Starting Frontend...
start "Viking Frontend" cmd /c "cd /d %~dp0frontend && npm run dev"

echo.
echo === All services starting in separate windows ===
echo.
echo Frontend:  http://localhost:5173
echo Bridge:    ws://localhost:8080/ws
echo.
pause
