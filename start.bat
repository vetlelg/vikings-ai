@echo off
setlocal enabledelayedexpansion
REM RTS AI — Start all services (Windows)
REM Usage: start.bat [stub|claude|gemini|groq]

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

echo === RTS AI ===
echo LLM Provider: %LLM_PROVIDER%
echo LLM Model:    %LLM_MODEL%
echo.

REM 1. Start Kafka
echo [1/5] Starting Kafka...
docker compose up -d
echo Waiting for Kafka to be ready...
timeout /t 20 /nobreak >nul
echo.

REM 2. Pre-build backend (avoids Gradle daemon conflicts when running in parallel)
echo [2/5] Building backend...
cd /d %~dp0backend
call gradlew.bat :bridge:classes :agent:classes
if errorlevel 1 (
    echo BUILD FAILED
    pause
    exit /b 1
)
cd /d %~dp0
echo.

REM 3. Start Bridge (new window)
echo [3/5] Starting WebSocket Bridge...
start "RTS Bridge" cmd /c "cd /d %~dp0backend && gradlew.bat :bridge:run"
echo Waiting for Bridge to start...
timeout /t 10 /nobreak >nul
echo.

REM 4. Start Agents (new window)
echo [4/5] Starting AI Agents...
start "RTS Agents" cmd /c "cd /d %~dp0backend && set LLM_PROVIDER=%LLM_PROVIDER% && set LLM_MODEL=%LLM_MODEL% && set LLM_API_KEY=%LLM_API_KEY% && gradlew.bat :agent:run"
timeout /t 5 /nobreak >nul
echo.

REM 5. Start Frontend (new window)
echo [5/5] Starting Frontend...
start "RTS Frontend" cmd /c "cd /d %~dp0frontend && npm run dev"
timeout /t 3 /nobreak >nul

echo.
echo === All services started ===
echo.
echo Frontend:  http://localhost:5173
echo Bridge:    ws://localhost:8080/ws
echo.
echo The game starts with local test AI.
echo Press B in the browser to switch to backend AI agents.
echo.
pause
