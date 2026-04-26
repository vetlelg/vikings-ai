#!/usr/bin/env bash
# RTS AI — Start all services
# Usage: ./start.sh [stub|claude|gemini|groq]

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Load .env file if it exists
if [ -f "$SCRIPT_DIR/.env" ]; then
    set -a
    source "$SCRIPT_DIR/.env"
    set +a
fi

# Command-line arg overrides .env
if [ -n "$1" ]; then
    export LLM_PROVIDER="$1"
fi
PROVIDER="${LLM_PROVIDER:-stub}"
export LLM_PROVIDER="$PROVIDER"

echo "=== RTS AI ==="
echo "LLM Provider: $LLM_PROVIDER"
echo "LLM Model:    ${LLM_MODEL:-default}"
echo ""

# 1. Start Kafka
echo "[1/5] Starting Kafka..."
docker compose -f "$SCRIPT_DIR/docker-compose.yml" up -d
echo "Waiting for Kafka to be healthy..."
until docker compose -f "$SCRIPT_DIR/docker-compose.yml" ps | grep -q "healthy"; do
    sleep 2
done
echo "Kafka is ready."
echo ""

# 2. Pre-build backend
echo "[2/5] Building backend..."
cd "$SCRIPT_DIR/backend"
./gradlew :bridge:classes :agent:classes
echo ""

# 3. Start Bridge
echo "[3/5] Starting WebSocket Bridge..."
./gradlew :bridge:run &
BRIDGE_PID=$!
echo "Waiting for Bridge to start..."
sleep 10

# 4. Start Agents
echo "[4/5] Starting AI Agents (provider: $LLM_PROVIDER)..."
./gradlew :agent:run &
AGENT_PID=$!
echo "Waiting for Agents to start..."
sleep 5

# 5. Start Frontend
echo "[5/5] Starting Frontend..."
cd "$SCRIPT_DIR/frontend"
npm run dev &
FRONTEND_PID=$!
sleep 3

echo ""
echo "=== All services running ==="
echo "Frontend:  http://localhost:5173"
echo "Bridge:    ws://localhost:8080/ws"
echo ""
echo "The game starts with local test AI."
echo "Press B in the browser to switch to backend AI agents."
echo ""
echo "Press Ctrl+C to stop all services."

trap 'echo "Shutting down..."; kill $BRIDGE_PID $AGENT_PID $FRONTEND_PID 2>/dev/null; docker compose -f "$SCRIPT_DIR/docker-compose.yml" down; exit 0' INT TERM

wait
