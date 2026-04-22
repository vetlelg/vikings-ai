# Viking Settlement

A browser-based 2D colony game where AI agents play Viking colonists. Each agent runs as an independent coroutine, communicating through Apache Kafka. A React frontend renders the world and shows agent reasoning in real time.

Built as a learning demo for event-driven architecture patterns: Blackboard, Orchestrator-Worker, Backpressure, Fault Recovery, and Decoupling.

## Characters

| Name | Role | Personality |
|------|------|-------------|
| **Bjorn** | Jarl (Leader) | Commands with authority. Issues colony-wide strategic directives. Prioritizes defense. |
| **Astrid** | Warrior | Fierce fighter. Seeks threats, never flees. Patrols the perimeter. |
| **Erik** | Fisherman | Cautious gatherer. Fishes near the fjord. Flees from danger. |
| **Ingrid** | Shipbuilder | Practical builder. Gathers timber from forests. Deposits at village. |

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| Java | 21+ | JDK required (JDK 24 works) |
| Docker | 20+ | With Docker Compose v2 |
| Node.js | 18+ | For the React frontend |

No Gradle CLI needed (wrapper included). No Kafka CLI needed (runs in Docker).

## Quick Start

### Option A: Start scripts

```bash
# Linux/macOS/Git Bash
./start.sh           # uses StubProvider (no API key needed)
./start.sh claude    # uses Claude API

# Windows
start.bat            # uses StubProvider
start.bat claude     # uses Claude API
```

### Option B: Manual (5 terminals)

```bash
# Terminal 1 - Kafka
docker compose up -d

# Terminal 2 - Game Engine
cd backend && ./gradlew :engine:run

# Terminal 3 - WebSocket Bridge
cd backend && ./gradlew :bridge:run

# Terminal 4 - AI Agents (stub mode, no API key)
cd backend && ./gradlew :agent:run

# Terminal 5 - Frontend
cd frontend && npm install && npm run dev
```

Open **http://localhost:5173** in your browser.

## LLM Providers

The AI agents can use different LLM backends. Set via environment variable or `.env` file:

| Provider | Value | API Key Env | Default Model |
|----------|-------|-------------|---------------|
| **Stub** | `stub` | None needed | N/A (rule-based actions, no API key) |
| **Claude** | `claude` | `LLM_API_KEY` | `claude-sonnet-4-20250514` |
| **Gemini** | `gemini` | `LLM_API_KEY` | `gemini-2.5-flash` |
| **Groq** | `groq` | `LLM_API_KEY` | `llama-3.1-8b-instant` |

```bash
# Example: use Claude
LLM_PROVIDER=claude LLM_API_KEY=sk-ant-... ./gradlew :agent:run
```

## Configuration

All config lives in `backend/common/src/main/resources/reference.conf` with environment variable overrides:

| Setting | Default | Env Var |
|---------|---------|---------|
| Kafka servers | `localhost:9092` | `KAFKA_BOOTSTRAP_SERVERS` |
| Tick rate | `5000` ms | `TICK_RATE_MS` |
| Grid size | `40x40` | (in reference.conf) |
| LLM provider | `stub` | `LLM_PROVIDER` |
| LLM model | `claude-sonnet-4-20250514` | `LLM_MODEL` |
| LLM API key | (empty) | `LLM_API_KEY` |
| LLM max tokens | `512` | (in reference.conf) |
| Bridge port | `8080` | `BRIDGE_PORT` |

Copy `.env.example` to `.env` to configure locally.

## Replay Mode

The bridge records all messages to `recordings/replay.jsonl`. Connect to `ws://localhost:8080/replay` to watch a recorded session with original timing. The frontend works identically in live and replay mode.

## Project Structure

```
vikings-ai/
  docker-compose.yml          # Kafka KRaft broker
  start.sh / start.bat        # One-command startup
  .env.example                # Environment variable template
  backend/                    # Gradle multi-module (Kotlin)
    common/                   # Shared models, Kafka helpers, LLM providers, config
    engine/                   # Game engine (tick loop, world simulation, threats)
    agent/                    # 4 AI agents with LLM integration
    bridge/                   # Ktor WebSocket bridge (Kafka -> browser)
  frontend/                   # React / TypeScript / Vite
```

## Tech Stack

**Backend:** Kotlin 2.1, Gradle 8.14, Apache Kafka 3.9 (KRaft), Ktor 3.1, kotlinx.serialization, kotlinx.coroutines

**Frontend:** React 19, TypeScript 6, Vite 8, Zustand 5, CSS Modules

**Infrastructure:** Docker Compose, Apache Kafka (KRaft mode, single broker)

See [ARCHITECTURE.md](ARCHITECTURE.md) for design decisions and system internals.
