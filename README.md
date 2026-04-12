# Viking Settlement

A browser-based 2D colony survival game where AI agents play Viking colonists, each running as an independent process communicating through Apache Kafka. A React frontend renders the grid and shows agent reasoning in real time.

## Architecture

```
                         +-----------------+
                         |  Apache Kafka   |
                         |  (Docker/KRaft) |
                         +--------+--------+
                                  |
              +-------------------+-------------------+
              |                   |                   |
     world-state          agent-actions          world-events
     saga-log                                         |
              |                   |                   |
   +----------v------+  +--------v--------+  +-------v--------+
   |   Game Engine    |  |   AI Agents     |  | WebSocket      |
   |                  |  |                 |  | Bridge (Ktor)  |
   |  - 20x20 grid   |  |  - Bjorn (Jarl) |  |                |
   |  - Tick loop     |  |  - Astrid (War) |  |  /ws (live)    |
   |  - Threats       |  |  - Erik (Fish)  |  |  /replay       |
   |  - Day/night     |  |  - Ingrid (Ship)|  +-------+--------+
   |  - Weather       |  |  - Sigurd (Skld)|          |
   +---------+--------+  +--------+--------+     WebSocket
             |                     |               |
             +------> Kafka <------+      +--------v--------+
                                          |  React Frontend  |
                                          |                  |
                                          |  - CSS Grid map  |
                                          |  - Agent tokens  |
                                          |  - Saga log      |
                                          |  - Event log     |
                                          +------------------+
```

## Characters

| Name | Role | Personality |
|------|------|-------------|
| **Bjorn** | Jarl (Leader) | Commands with authority. Prioritizes defense. Stays near the village. |
| **Astrid** | Warrior | Fierce fighter. Seeks threats, never flees. Patrols the perimeter. |
| **Erik** | Fisherman | Cautious gatherer. Fishes near the fjord. Flees from danger. |
| **Ingrid** | Shipbuilder | Practical builder. Gathers timber from forests. Deposits at village. |
| **Sigurd** | Skald (Poet) | Observes and narrates events in the style of a Norse saga. |

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
# Terminal 1 — Kafka
docker compose up -d

# Terminal 2 — Game Engine
cd backend && ./gradlew :engine:run

# Terminal 3 — WebSocket Bridge
cd backend && ./gradlew :bridge:run

# Terminal 4 — AI Agents (stub mode, no API key)
cd backend && ./gradlew :agent:run

# Terminal 5 — Frontend
cd frontend && npm install && npm run dev
```

Open **http://localhost:5173** in your browser.

## LLM Providers

The AI agents can use different LLM backends. Set via environment variable or config:

| Provider | Env Value | API Key Env | Notes |
|----------|-----------|-------------|-------|
| **Stub** | `stub` | None needed | Random actions + saga narrations. For development. |
| **Claude** | `claude` | `LLM_API_KEY` | Best quality. `claude-sonnet-4-20250514` default. |
| **Gemini** | `gemini` | `LLM_API_KEY` | Free tier available. `gemini-2.0-flash-lite` default. |
| **Groq** | `groq` | `LLM_API_KEY` | Free tier, fast inference. `llama-3.1-8b-instant` default. |

```bash
# Example: use Claude
LLM_PROVIDER=claude LLM_API_KEY=sk-ant-... ./gradlew :agent:run

# Example: use Groq
LLM_PROVIDER=groq LLM_API_KEY=gsk_... ./gradlew :agent:run
```

## Configuration

All config is in `backend/common/src/main/resources/reference.conf` with environment variable overrides:

| Config | Default | Env Var |
|--------|---------|---------|
| Kafka servers | `localhost:9092` | `KAFKA_BOOTSTRAP_SERVERS` |
| Tick rate | `5000` ms | `TICK_RATE_MS` |
| LLM provider | `stub` | `LLM_PROVIDER` |
| LLM model | `claude-sonnet-4-20250514` | `LLM_MODEL` |
| LLM API key | (empty) | `LLM_API_KEY` |
| Bridge port | `8080` | `BRIDGE_PORT` |

## Kafka Topics

| Topic | Publisher | Description |
|-------|----------|-------------|
| `world-state` | Engine | Full world snapshot every tick (the Blackboard) |
| `agent-actions` | Agents | Agent decisions (move, gather, fight, etc.) |
| `world-events` | Engine | Narrative events (dragon sighted, night falling, etc.) |
| `saga-log` | Skald | Dramatic Norse saga narrations |

## Architectural Patterns Demonstrated

| Pattern | How |
|---------|-----|
| **Blackboard** | `world-state` topic as shared context all agents read |
| **Orchestrator-Worker** | Engine orchestrates ticks, agents are independent workers |
| **Backpressure** | Agents are slow (LLM latency), engine ticks continue regardless |
| **Fault Recovery** | Kill an agent process, restart — it resumes from Kafka offset |
| **Decoupling** | Agents know nothing about each other, only Kafka topics |

## Replay Mode

The bridge records all messages to a JSON Lines file (`recordings/replay.jsonl`). Connect to `ws://localhost:8080/replay` to watch a pre-recorded session with original timing. The frontend works identically in live and replay mode.

## Project Structure

```
viking-settlement/
  docker-compose.yml          # Kafka KRaft broker
  start.sh / start.bat        # One-command startup
  .env.example                # Environment variable template
  backend/                    # Gradle multi-module (Kotlin)
    common/                   # Shared models, Kafka helpers, LLM providers
    engine/                   # Game engine (tick loop, map, threats)
    agent/                    # 5 AI agents with LLM integration
    bridge/                   # Ktor WebSocket bridge (Kafka → browser)
  frontend/                   # React/TypeScript/Vite
```

## Tech Stack

**Backend:** Kotlin 2.1, Gradle 8.14, Apache Kafka (KRaft), Ktor 3.1, kotlinx.serialization, kotlinx.coroutines

**Frontend:** React 19, TypeScript, Vite, Zustand, CSS Modules

**Infrastructure:** Docker Compose, Apache Kafka (KRaft mode, single broker)
