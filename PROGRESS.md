# Viking Settlement — Progress Log

## What Has Been Built

This document tracks the implementation progress of the Viking Settlement project — a browser-based 2D colony game where AI agents communicate through Apache Kafka.

---

## Phase 1: Project Skeleton + Kafka (Complete)

**Goal:** Establish the project infrastructure — Gradle multi-module build, Docker-based Kafka broker, and verified connectivity between them.

### Docker Compose — Kafka KRaft

File: `docker-compose.yml`

A single Apache Kafka broker running in KRaft mode (no Zookeeper) inside Docker. Key configuration:

- **Image:** `apache/kafka:latest` — the official Apache Kafka image
- **KRaft mode:** Kafka acts as both broker and controller in a single node (`KAFKA_PROCESS_ROLES: broker,controller`)
- **Listeners:** Two listener pairs — `PLAINTEXT_HOST` on port 9092 for host-machine access (`localhost:9092`), and `PLAINTEXT` on port 19092 for inter-container communication (`kafka:19092`)
- **Auto-create topics:** Enabled (`KAFKA_AUTO_CREATE_TOPICS_ENABLE: true`) so the four game topics (`world-state`, `agent-actions`, `world-events`, `saga-log`) are created automatically on first produce/consume. No init scripts needed.
- **Named volume:** `kafka-data` stored at `/tmp/kraft-combined-logs` inside the container. Using a Docker named volume instead of a bind mount avoids file-locking issues on Windows.
- **Health check:** Runs `kafka-topics.sh --list` every 10 seconds. Kafka reports as healthy once it can respond to topic listing requests (start period: 20 seconds).

### Gradle Multi-Module Project

Root: `backend/`

The backend is a Kotlin multi-module Gradle project with four submodules:

```
backend/
├── settings.gradle.kts    — includes: common, engine, agent, bridge
├── build.gradle.kts       — shared config: Kotlin JVM target 21, repositories
├── gradle.properties      — JVM args for Gradle daemon
├── gradle/
│   ├── wrapper/           — Gradle 8.14 wrapper (supports JDK 24)
│   └── libs.versions.toml — version catalog for all dependencies
├── common/                — shared data classes, Kafka helpers, LLM providers, config
├── engine/                — game engine (tick loop, world simulation)
├── agent/                 — AI agent runner (5 Viking agents)
└── bridge/                — WebSocket bridge (Kafka → browser)
```

**Module dependency graph:**
```
common  ← no project dependencies
engine  ← depends on common
agent   ← depends on common
bridge  ← depends on common
```

**Why Gradle 8.14:** The machine has JDK 24 installed. Gradle 8.12 doesn't support JDK 24 (fails with `Type T not present` in test task creation). Gradle 8.14 added JDK 24 support. The JVM target is set to 21 in the root `build.gradle.kts` so compiled bytecode is JVM 21 compatible, while the build itself runs on JDK 24.

**Version catalog** (`gradle/libs.versions.toml`) pins all dependency versions:

| Dependency | Version | Purpose |
|-----------|---------|---------|
| Kotlin | 2.1.0 | Language and compiler |
| Ktor | 3.1.0 | HTTP server (bridge) and client (LLM API calls) |
| kafka-clients | 3.9.0 | Official Apache Kafka client for producers/consumers |
| kotlinx-serialization | 1.7.3 | JSON serialization for all Kafka messages |
| kotlinx-coroutines | 1.9.0 | Async Kafka consumption and LLM API calls |
| Logback | 1.5.15 | SLF4J logging backend |
| Typesafe Config | 1.4.3 | HOCON configuration files |

### Verification

1. `docker compose up -d` → Kafka container starts and reports healthy
2. `./gradlew build` → all 4 modules compile successfully (BUILD SUCCESSFUL)
3. `./gradlew :engine:run` → connects to Kafka, prints:
   ```
   Connected to Kafka cluster: VikingsAI_Kafka_Cluster_01
   Broker nodes: localhost:9092
   Existing topics: (none)
   Kafka connection verified. Engine ready.
   ```

---

## Phase 2: Common Domain Model (Complete)

**Goal:** Define all shared data classes, enums, Kafka helpers, LLM provider interface, and configuration in the `common` module. This is the shared contract that all other modules depend on.

### Data Model (`common/.../model/`)

All message types that flow through Kafka topics are `@Serializable` data classes using `kotlinx.serialization`.

#### Enums (`Enums.kt`)

12 enums defining the game's type system:

| Enum | Values | Used By |
|------|--------|---------|
| `TerrainType` | GRASS, FOREST, WATER, MOUNTAIN, BEACH, VILLAGE | 20×20 grid cells |
| `ResourceType` | TIMBER, FISH, IRON, FURS | Agent inventories, colony stockpile |
| `AgentRole` | JARL, WARRIOR, FISHERMAN, SHIPBUILDER, SKALD | Agent identity and behavior |
| `ActionType` | MOVE, GATHER, FIGHT, BUILD, PATROL, FLEE, SPEAK, IDLE | Agent decision output |
| `AgentStatus` | ALIVE, DEAD, THINKING | Agent state for UI rendering |
| `TimeOfDay` | DAWN, DAY, DUSK, NIGHT | Day/night cycle |
| `Weather` | CLEAR, SNOW, STORM | Weather overlay in frontend |
| `EventType` | 12 values (DRAGON_SIGHTED, COMBAT, etc.) | World event classification |
| `EntityType` | WOLF, DRAGON, RESOURCE_NODE | Non-agent entities on the grid |
| `Severity` | LOW, MEDIUM, HIGH, CRITICAL | Event/threat urgency |

#### Position (`Position.kt`)

```kotlin
data class Position(val x: Int, val y: Int)  // 0-19 for 20×20 grid
```

#### WorldState (`WorldState.kt`) — published to `world-state` topic each tick

The complete world snapshot. This is the **Blackboard** that all agents read.

```kotlin
data class WorldState(
    val tick: Int,                              // monotonically increasing tick counter
    val grid: List<List<TerrainType>>,          // 20×20 terrain grid
    val agents: List<AgentSnapshot>,            // all 5 agents with position, health, inventory
    val entities: List<EntitySnapshot>,         // wolves, dragon, resource nodes
    val colonyResources: ColonyResources,       // shared colony stockpile
    val timeOfDay: TimeOfDay,
    val weather: Weather,
    val threats: List<ThreatSnapshot>           // active dangers with position and severity
)
```

Supporting data classes: `AgentSnapshot` (name, role, position, health, inventory, status), `EntitySnapshot` (id, type, position, subtype), `ColonyResources` (timber/fish/iron/furs counts), `ThreatSnapshot` (id, type, position, severity).

#### AgentAction (`AgentAction.kt`) — published to `agent-actions` topic

An agent's decision for one tick:

```kotlin
data class AgentAction(
    val tick: Int,
    val agentName: String,
    val action: ActionType,
    val direction: String? = null,              // for MOVE actions: "north"/"south"/"east"/"west"
    val targetPosition: Position? = null,       // for targeted actions
    val reasoning: String = ""                  // LLM's explanation (shown in event log)
)
```

#### WorldEvent (`WorldEvent.kt`) — published to `world-events` topic

Engine-generated narrative events:

```kotlin
data class WorldEvent(
    val tick: Int,
    val eventType: EventType,
    val description: String,
    val severity: Severity,
    val affectedPositions: List<Position> = emptyList()
)
```

#### SagaLogEntry (`SagaLogEntry.kt`) — published to `saga-log` topic

The Skald's poetic narrations:

```kotlin
data class SagaLogEntry(
    val tick: Int,
    val text: String                            // dramatic narration displayed in parchment panel
)
```

### Kafka Helpers (`common/.../kafka/`)

#### Topics (`Topics.kt`)

Constants for the four Kafka topic names:
- `Topics.WORLD_STATE` = `"world-state"`
- `Topics.AGENT_ACTIONS` = `"agent-actions"`
- `Topics.WORLD_EVENTS` = `"world-events"`
- `Topics.SAGA_LOG` = `"saga-log"`

#### JsonSerde (`JsonSerde.kt`)

Generic Kafka serializer/deserializer backed by `kotlinx.serialization`:

- `GameJson` — shared `Json` instance with `ignoreUnknownKeys = true` and `encodeDefaults = true` for forward-compatible deserialization
- `JsonKafkaSerializer<T>` — implements `org.apache.kafka.common.serialization.Serializer`, encodes any `@Serializable` class to UTF-8 JSON bytes
- `JsonKafkaDeserializer<T>` — implements `Deserializer`, decodes UTF-8 JSON bytes back

These are available for typed producers/consumers but the current implementation uses String serde with manual JSON encoding for simplicity.

#### KafkaHelpers (`KafkaHelpers.kt`)

Factory functions for creating Kafka clients:

- `createProducer(bootstrapServers)` — returns a `KafkaProducer<String, String>` with `acks=1` and String serializers
- `createConsumer(bootstrapServers, groupId, topics, fromBeginning)` — returns a `KafkaConsumer<String, String>` subscribed to the given topics, with auto-commit enabled and configurable offset reset

### LLM Providers (`common/.../llm/`)

A provider-agnostic interface allowing the game to use different LLM backends with zero code changes — just change a config value.

#### LlmProvider Interface (`LlmProvider.kt`)

```kotlin
interface LlmProvider {
    suspend fun complete(systemPrompt: String, userMessage: String, maxTokens: Int): String
}
```

All providers implement this single interface. The engine and agents only depend on the interface, never on a specific provider.

#### StubProvider (`StubProvider.kt`)

Returns random plausible actions as JSON strings after a configurable delay (default 500ms). Used for development and testing the full Kafka pipeline without any API key or LLM costs. Actions include random moves, gather, patrol, and idle with canned reasoning text.

#### ClaudeProvider (`ClaudeProvider.kt`)

Calls the Anthropic Messages API directly via Ktor HTTP client:
- Endpoint: `POST https://api.anthropic.com/v1/messages`
- Headers: `x-api-key`, `anthropic-version: 2023-06-01`
- Default model: `claude-sonnet-4-20250514`
- Parses the `content[0].text` field from the response

#### GeminiProvider (`GeminiProvider.kt`)

Calls the Google Generative Language API:
- Endpoint: `POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent`
- API key passed as query parameter
- Default model: `gemini-2.0-flash-lite` (free tier eligible)
- Parses `candidates[0].content.parts[0].text` from the response

#### GroqProvider (`GroqProvider.kt`)

Calls the Groq API (OpenAI-compatible format):
- Endpoint: `POST https://api.groq.com/openai/v1/chat/completions`
- Authorization via Bearer token
- Default model: `llama-3.1-8b-instant` (free tier, very fast)
- Parses `choices[0].message.content` from the response

### Configuration (`common/.../config/`)

#### GameConfig (`GameConfig.kt`)

A singleton that reads HOCON configuration via Typesafe Config and exposes typed config objects:

- `GameConfig.kafka` → `KafkaConfig(bootstrapServers)`
- `GameConfig.engine` → `EngineConfig(tickRateMs, gridWidth, gridHeight)`
- `GameConfig.llm` → `LlmConfig(provider, model, apiKey, maxTokens)`
- `GameConfig.bridge` → `BridgeConfig(port, replayFile)`
- `GameConfig.createLlmProvider()` — factory method that returns the correct `LlmProvider` implementation based on the `llm.provider` config value

#### reference.conf (`common/src/main/resources/reference.conf`)

Default configuration with environment variable overrides:

```hocon
kafka.bootstrap-servers = "localhost:9092"  # or ${KAFKA_BOOTSTRAP_SERVERS}
engine.tick-rate-ms = 5000                  # or ${TICK_RATE_MS}
llm.provider = "stub"                      # or ${LLM_PROVIDER}: "claude", "gemini", "groq"
llm.api-key = ""                           # or ${LLM_API_KEY}
bridge.port = 8080                         # or ${BRIDGE_PORT}
```

### Verification

`./gradlew build` → BUILD SUCCESSFUL. All 18 Kotlin source files across 4 modules compile cleanly.

---

## File Inventory

### Infrastructure
| File | Purpose |
|------|---------|
| `docker-compose.yml` | Kafka KRaft broker in Docker |
| `.gitignore` | Ignores build artifacts, IDE files, node_modules, .env |
| `PLAN.md` | Full implementation plan for all 8 phases |

### Gradle Build System (7 files)
| File | Purpose |
|------|---------|
| `backend/settings.gradle.kts` | Defines 4 submodules |
| `backend/build.gradle.kts` | Shared Kotlin config, JVM 21 target |
| `backend/gradle.properties` | Gradle daemon JVM args |
| `backend/gradle/libs.versions.toml` | Version catalog for all dependencies |
| `backend/common/build.gradle.kts` | Common module dependencies |
| `backend/engine/build.gradle.kts` | Engine module dependencies + application plugin |
| `backend/agent/build.gradle.kts` | Agent module dependencies + application plugin |
| `backend/bridge/build.gradle.kts` | Bridge module dependencies + application plugin |

### Common Module (14 Kotlin files + 1 config)
| File | Purpose |
|------|---------|
| `common/.../model/Position.kt` | Grid coordinate (x, y) |
| `common/.../model/Enums.kt` | 12 game enums |
| `common/.../model/WorldState.kt` | World snapshot + 4 supporting data classes |
| `common/.../model/AgentAction.kt` | Agent decision message |
| `common/.../model/WorldEvent.kt` | Engine narrative event |
| `common/.../model/SagaLogEntry.kt` | Skald narration |
| `common/.../kafka/Topics.kt` | Topic name constants |
| `common/.../kafka/JsonSerde.kt` | kotlinx.serialization Kafka serde |
| `common/.../kafka/KafkaHelpers.kt` | Producer/consumer factory functions |
| `common/.../llm/LlmProvider.kt` | Provider interface |
| `common/.../llm/StubProvider.kt` | Mock provider for testing |
| `common/.../llm/ClaudeProvider.kt` | Anthropic Claude API |
| `common/.../llm/GeminiProvider.kt` | Google Gemini API |
| `common/.../llm/GroqProvider.kt` | Groq API (OpenAI-compatible) |
| `common/.../config/GameConfig.kt` | HOCON config wrapper + LLM factory |
| `common/.../resources/reference.conf` | Default config with env var overrides |

### Skeleton Entry Points
| File | Purpose |
|------|---------|
| `agent/.../AgentMain.kt` | Placeholder — Phase 6 |
| `bridge/.../BridgeMain.kt` | Placeholder — Phase 4 |

---

## Phase 3: Game Engine (Complete)

**Goal:** A running game engine that generates a procedural map, manages world state, handles threats, and publishes the full game state to Kafka every tick.

### MapGenerator (`engine/.../world/MapGenerator.kt`)

Generates a 20x20 Viking coastline map with deterministic seeding:

- **West edge (x=0..2):** Fjord — WATER tiles with an irregular sine-wave coastline
- **Beach strip:** BEACH tiles adjacent to any WATER tile (auto-detected via neighbor check)
- **North (y=0..3):** Mountain range with decreasing density further south
- **Center (x=8..12, y=8..12):** Village clearing — VILLAGE tiles where agents spawn
- **Scattered:** 9 forest cluster seeds, each expanding 1-3 tiles with 70% fill probability
- **Remainder:** GRASS plains

Also provides `walkablePositions()` — returns all positions that are not WATER or MOUNTAIN, used for spawning agents and entities.

### WorldManager (`engine/.../world/WorldManager.kt`)

Holds all mutable game state and exposes operations:

- **Tick counter** — monotonically increasing
- **Time of day** — cycles through DAWN → DAY → DUSK → NIGHT every 5 ticks (full day = 20 ticks = ~100 seconds)
- **Weather** — random transitions every ~30 ticks (CLEAR 50%, SNOW 35%, STORM 15%)
- **Agent movement** — validates direction, checks terrain (water/mountain blocked), clamps to grid bounds
- **Resource gathering** — picks up RESOURCE_NODE entities or gathers TIMBER from forest tiles. Adds to agent inventory.
- **Resource deposit** — transfers agent inventory to colony stockpile when agent is on VILLAGE tile
- **Combat resolution** — role-based damage (Warrior 40-60, Jarl 30-45, others 15-25). Entity counterattacks. Agent dies at 0 HP.
- **Resource node spawning** — places new RESOURCE_NODE entities near appropriate terrain
- **Snapshot export** — `toWorldState()` produces an immutable `WorldState` for Kafka publishing

Mutable state classes (`MutableAgent`, `MutableEntity`, `MutableColonyResources`) are internal to the engine. Each has a `toSnapshot()` method that produces the immutable common model class.

### ActionResolver (`engine/.../tick/ActionResolver.kt`)

Validates and applies `AgentAction` messages received from the `agent-actions` Kafka topic:

| Action | Behavior |
|--------|----------|
| MOVE | Validates direction, moves agent one tile (blocked by water/mountain) |
| GATHER | Picks up resource node at position, or gathers timber from forest |
| FIGHT | Finds nearest threat entity. If adjacent: combat. If distant: moves toward it. |
| BUILD | If on VILLAGE tile: deposits all inventory to colony stockpile |
| PATROL | Moves in the specified (or random) direction |
| FLEE | Moves away from nearest threat entity |
| SPEAK | Generates an AGENT_SPOKE event with the reasoning text |
| IDLE | Does nothing |

Each action returns a list of `WorldEvent`s generated (e.g., RESOURCE_GATHERED, COMBAT, AGENT_DIED, DRAGON_DEFEATED).

### Dragon (`engine/.../entities/Dragon.kt`)

The dragon is a persistent, powerful threat:

- **Spawning:** Appears at a map edge position. HP: 200.
- **Movement:** Each tick, moves one step toward the nearest living agent (Manhattan distance pathfinding).
- **Auto-attack:** If adjacent to any agent, deals 15-25 damage automatically.
- **Events:** DRAGON_SIGHTED (CRITICAL) on spawn, COMBAT (CRITICAL) on attack.

### ThreatManager (`engine/.../entities/ThreatManager.kt`)

Orchestrates all hazards on a schedule:

| Threat | Schedule | Details |
|--------|----------|---------|
| **Dragon** | Every ~60-80 ticks | Spawns at map edge, despawns after ~30-50 ticks if not killed |
| **Wolves** | Every ~12-22 ticks | Max 3 active. Spawn at map edges. Move toward agents within range 8. Deal 5-13 damage when adjacent. |
| **Raids** | Every ~70-110 ticks | Spawns 2 "raider" entities (wolf-type, 60 HP) at southeast edge. RAID_INCOMING event. |

### EventGenerator (`engine/.../tick/EventGenerator.kt`)

Generates ambient world events each tick:

- **Time transitions:** DAWN_BREAKING / NIGHT_FALLING events with atmospheric descriptions
- **Weather changes:** WEATHER_CHANGE events with descriptive text
- **Resource spawning:** Every 8 ticks, spawns 2 resource nodes (cap: 15 total)

### GameLoop (`engine/.../GameLoop.kt`)

The main tick loop orchestrating everything:

```
Each tick:
  1. Advance tick counter
  2. Consume agent-actions from Kafka (non-blocking poll, Duration.ZERO)
  3. Apply valid actions via ActionResolver → collect events
  4. Update threats via ThreatManager (dragon/wolf movement, spawning, raids) → collect events
  5. Generate tick events via EventGenerator (time, weather, resources) → collect events
  6. Publish WorldState to world-state topic
  7. Publish all WorldEvents to world-events topic
  8. Log summary every 10 ticks
  9. Delay for tick rate (default 5000ms)
```

On init: generates map, places 5 agents in the village, spawns 8 initial resource nodes.

### EngineMain (`engine/.../EngineMain.kt`)

Entry point with:
- Kafka connection retry (10 attempts with exponential backoff)
- Reads all config from GameConfig (HOCON with env var overrides)
- Creates Kafka producer for publishing and consumer for agent-actions
- Graceful shutdown hook (closes Kafka clients)
- Runs the game loop via `runBlocking`

### Logging (`engine/src/main/resources/logback.xml`)

Logback config with:
- Console appender with timestamp, thread, level, logger format
- Kafka client logs suppressed to WARN (very verbose otherwise)
- Engine logs at DEBUG level

### Verification

1. `docker compose up -d` → Kafka healthy
2. `./gradlew :engine:run` → Engine starts, connects to Kafka, logs tick info
3. `kafka-console-consumer --topic world-state` → JSON messages every 5 seconds containing:
   - 20x20 terrain grid (WATER, BEACH, GRASS, FOREST, MOUNTAIN, VILLAGE)
   - 5 agents (Bjorn/JARL, Astrid/WARRIOR, Erik/FISHERMAN, Ingrid/SHIPBUILDER, Sigurd/SKALD) at village positions
   - Resource nodes scattered around the map
   - Time: DAWN, Weather: CLEAR
4. `kafka-console-consumer --topic world-events` → Events published (DAWN_BREAKING, etc.)

---

## File Inventory Update

### Engine Module (8 files)
| File | Purpose |
|------|---------|
| `engine/.../EngineMain.kt` | Entry point with Kafka retry and config |
| `engine/.../GameLoop.kt` | Main tick loop orchestrating all systems |
| `engine/.../world/MapGenerator.kt` | Procedural 20x20 coastline map generation |
| `engine/.../world/WorldManager.kt` | Mutable world state + all game operations |
| `engine/.../tick/ActionResolver.kt` | Validates and applies agent actions |
| `engine/.../tick/EventGenerator.kt` | Day/night, weather, resource spawning |
| `engine/.../entities/Dragon.kt` | Dragon roaming AI and combat |
| `engine/.../entities/ThreatManager.kt` | Wolves, raids, dragon scheduling |
| `engine/src/main/resources/logback.xml` | Logging configuration |

---

## Phase 4: WebSocket Bridge (Complete)

**Goal:** A Ktor WebSocket server that consumes all 4 Kafka topics, wraps each message in a `BridgeEnvelope`, broadcasts to all connected browser clients, and records everything to a JSON Lines file for replay.

### BridgeEnvelope (`bridge/.../BridgeEnvelope.kt`)

The message format sent over WebSocket to the browser:

```kotlin
data class BridgeEnvelope(
    val topic: String,       // "world-state", "agent-actions", "world-events", "saga-log"
    val timestamp: Long,     // Kafka record timestamp
    val payload: JsonElement  // Raw JSON — bridge is schema-agnostic
)
```

The bridge never deserializes the payload contents — it wraps the raw Kafka value as a `JsonElement`. This means the bridge code never needs to change when data model fields are added or modified.

Also defines `WorldCommand` for incoming frontend commands:
```kotlin
data class WorldCommand(val command: String)  // "spawn_dragon", "start_winter", "rival_raid"
```

### KafkaForwarder (`bridge/.../KafkaForwarder.kt`)

A background coroutine running on `Dispatchers.IO` that:

1. Creates a Kafka consumer subscribed to all 4 topics (world-state, agent-actions, world-events, saga-log) with a unique consumer group per session (`bridge-{timestamp}`)
2. Polls every 500ms
3. For each record: parses the raw value into a `JsonElement`, wraps it in a `BridgeEnvelope`, serializes to JSON
4. Records the envelope JSON to the replay file
5. Broadcasts to all connected `WebSocketSession` instances via `Frame.Text`
6. Cleans up dead sessions (catches send failures and removes them)

### ReplayRecorder (`bridge/.../ReplayRecorder.kt`)

Writes every `BridgeEnvelope` as a JSON Lines file (one JSON object per line):

- Creates the output directory on startup if it doesn't exist
- Uses a `PrintWriter` with auto-flush for immediate writes
- Gracefully degrades if the file can't be opened (logs a warning, recording disabled)
- File path configurable via `bridge.replay-file` config (default: `recordings/replay.jsonl`)

### BridgeMain (`bridge/.../BridgeMain.kt`)

Ktor embedded server (Netty) entry point:

- **WebSocket plugin**: ping every 15s, timeout 30s
- **CORS plugin**: allows `localhost:3000`, `localhost:5173`, `127.0.0.1:3000`, `127.0.0.1:5173` for frontend dev server
- **WebSocket route `/ws`**: 
  - On connect: adds session to `ConcurrentHashMap.KeySetView` broadcast set
  - On incoming text frames: parses as `WorldCommand`, converts to a `WorldEvent`, publishes to Kafka `world-events` topic
  - On disconnect: removes session from broadcast set
- **Command handling**: Accepts 3 commands from the frontend:
  - `"spawn_dragon"` → publishes DRAGON_SIGHTED event
  - `"start_winter"` → publishes WEATHER_CHANGE event  
  - `"rival_raid"` → publishes RAID_INCOMING event
- **Shutdown hook**: closes replay recorder and command producer

### Verification

1. `./gradlew :bridge:run` → `Bridge listening on ws://localhost:8080/ws`
2. Kafka forwarder connects and consumes all 4 topics
3. Replay recorder initializes at `recordings/replay.jsonl`
4. Ktor responds at `http://127.0.0.1:8080`
5. With engine running concurrently: bridge forwards world-state envelopes to any connected WebSocket client

---

## File Inventory Update

### Bridge Module (5 files)
| File | Purpose |
|------|---------|
| `bridge/.../BridgeMain.kt` | Ktor server entry point, WebSocket route, command handling |
| `bridge/.../BridgeEnvelope.kt` | Envelope data class + WorldCommand |
| `bridge/.../KafkaForwarder.kt` | Background Kafka consumer → WebSocket broadcaster |
| `bridge/.../ReplayRecorder.kt` | JSON Lines replay file writer |
| `bridge/src/main/resources/logback.xml` | Logging config (Kafka WARN, Netty WARN) |

---

## Phase 5: React Frontend (Complete)

**Goal:** Full visual UI connecting to the bridge WebSocket — 20x20 CSS grid with terrain gradients, animated agent tokens, dragon glow, day/night overlay, weather particles, parchment saga log, and color-coded event log.

### Project Setup

- **Vite** React TypeScript template (`npm create vite@latest frontend -- --template react-ts`)
- **Node v24.14.1**, npm 11.11.0
- **Dependencies**: `zustand` (state), `react-use-websocket` (WS), `@fontsource-variable/cinzel` (saga font)
- **Styling**: CSS Modules (`.module.css`) co-located with components + shared `theme.css` design tokens

### TypeScript Types (`src/types/world.ts`)

All types mirror the backend Kotlin data classes exactly. String unions for enums (e.g., `type TerrainType = 'GRASS' | 'FOREST' | ...`). Interfaces for `WorldState`, `AgentSnapshot`, `AgentAction`, `WorldEvent`, `SagaLogEntry`, `WSMessage` envelope, and `WorldCommand`.

### Zustand Store (`src/store/gameStore.ts`)

Single global store with:
- **World snapshot** (replaced each tick): `grid`, `agents`, `entities`, `colonyResources`, `timeOfDay`, `weather`, `threats`, `tick`
- **Rolling logs** (capped): `agentActions` (last 50), `worldEvents` (last 100), `sagaEntries` (last 30)
- **Connection flag**: `connected` boolean
- **Actions**: `applyWorldState`, `addAgentAction`, `addWorldEvent`, `addSagaEntry`, `setConnected`

Components subscribe via selectors to avoid unnecessary re-renders.

### WebSocket Hook (`src/hooks/useGameSocket.ts`)

Uses `react-use-websocket` with:
- Auto-reconnect (50 attempts, exponential backoff capped at 10s)
- `onMessage` parses `WSMessage` envelope, routes by `topic` to the correct store action
- Returns `sendCommand` callback for the CommandPanel
- URL from `VITE_WS_URL` env var (default `ws://localhost:8080/ws`)

### Component Hierarchy

```
App
├── TopBar         — time/weather, colony resources, agent status dots, connection indicator
├── main
│   ├── GameGrid   — 20×20 CSS grid + token overlay
│   │   ├── GridCell (×400)     — terrain tile with CSS gradient
│   │   ├── EntityToken (×N)    — wolves, resource nodes
│   │   ├── AgentToken (×5)     — role icon, colored ring, smooth CSS glide
│   │   ├── DragonToken (×0-1)  — oversized, pulsing orange glow
│   │   ├── DayNightOverlay     — time-of-day color tint
│   │   └── WeatherOverlay      — CSS keyframe snowflakes
│   └── SidePanel
│       ├── SagaLog             — parchment panel, Cinzel font, fade-in
│       └── EventLog            — monospace, color-coded by event type
└── CommandPanel   — Summon Dragon, Winter Comes, Rival Raid buttons
```

### Visual Design Details

**Grid**: CSS Grid `repeat(20, 38px)` with 1px gap. Each terrain type has a distinct gradient:
- GRASS: deep greens, FOREST: darker green with radial shadow, WATER: animated blue shimmer
- MOUNTAIN: grey-to-white gradient (snow peaks), BEACH: sandy gold, VILLAGE: warm brown with gold glow

**Agent tokens**: Positioned via `transform: translate()` with 1.2s cubic-bezier transition for smooth gliding. Each role has a unique SVG icon and colored ring (Jarl=gold, Warrior=red, Fisherman=blue, Shipbuilder=brown, Skald=purple). Dead agents are greyed/translucent. Thinking agents pulse yellow.

**Dragon**: 1.8× cell size, `dragon-glow` keyframe animation (pulsing orange box-shadow), positioned above all other tokens (z-index 20).

**Day/night**: Full-grid overlay div with `pointer-events: none`, background color transitions over 2 seconds between warm (dawn), clear (day), orange (dusk), and dark blue (night).

**Weather**: 50 absolutely-positioned snowflake divs with staggered `animation-delay` and `animation-duration`, using the `snowfall` keyframe. Storm mode halves duration and adds a dark overlay.

**Saga log**: Parchment panel — tan linear gradient background (#d4c4a0 → #c4b490), brown border, Cinzel variable font, entries fade in with `fade-in-up` animation, auto-scrolls to bottom.

**Event log**: Dark surface panel, monospace font, entries color-coded by CSS class matching event type (COMBAT=red, DRAGON=orange bold, RESOURCE_GATHERED=green, NIGHT_FALLING=grey, etc.).

**Command panel**: Three gold-bordered buttons with hover glow, Cinzel font, uppercase letters.

### Performance

- `React.memo` on `GridCell` — terrain string equality check, 0 re-renders most ticks
- `React.memo` on `AgentToken` with custom comparator — only position, status, health
- `transform: translate()` — GPU-composited, no layout reflow
- Zustand selectors — each component subscribes to its own slice
- Weather snowflakes created once in `useMemo`

### Verification

1. `npx tsc --noEmit` → zero TypeScript errors
2. `npx vite build` → 62 modules, 226KB JS + 9.6KB CSS (70KB gzipped)
3. Dev server: `npm run dev` → http://localhost:5173
4. Without backend: shows "Awaiting world state..." loading state, red connection dot
5. With full backend (Kafka + engine + bridge): grid renders terrain, agents glide, events and saga populate

---

## File Inventory Update

### Frontend (24 files)
| File | Purpose |
|------|---------|
| `frontend/src/types/world.ts` | All TypeScript types mirroring backend |
| `frontend/src/store/gameStore.ts` | Zustand store — world state + rolling logs |
| `frontend/src/hooks/useGameSocket.ts` | WebSocket hook with auto-reconnect |
| `frontend/src/styles/theme.css` | CSS custom properties (colors, fonts, timing) |
| `frontend/src/styles/reset.css` | Minimal reset |
| `frontend/src/styles/animations.css` | Shared @keyframes (glow, snow, fire, shimmer) |
| `frontend/src/main.tsx` | Entry point, font + style imports |
| `frontend/src/App.tsx` | Root layout (top bar, grid+panel, commands) |
| `frontend/src/App.module.css` | App CSS Grid layout |
| `frontend/src/components/TopBar/TopBar.tsx` | Time, weather, resources, agent status |
| `frontend/src/components/TopBar/TopBar.module.css` | TopBar styles |
| `frontend/src/components/GameGrid/GameGrid.tsx` | 20×20 grid container + token layer |
| `frontend/src/components/GameGrid/GameGrid.module.css` | Grid wrapper + loading state |
| `frontend/src/components/GameGrid/GridCell.tsx` | Terrain tile (memoized) |
| `frontend/src/components/GameGrid/GridCell.module.css` | Terrain gradients per type |
| `frontend/src/components/GameGrid/AgentToken.tsx` | Agent icon with smooth glide |
| `frontend/src/components/GameGrid/AgentToken.module.css` | Role colors, thinking/dead states |
| `frontend/src/components/GameGrid/DragonToken.tsx` | Oversized dragon with glow |
| `frontend/src/components/GameGrid/DragonToken.module.css` | Dragon glow animation |
| `frontend/src/components/GameGrid/EntityToken.tsx` | Wolves + resource nodes |
| `frontend/src/components/GameGrid/DayNightOverlay.tsx` | Time-of-day color overlay |
| `frontend/src/components/GameGrid/WeatherOverlay.tsx` | CSS snowflake particles |
| `frontend/src/components/SidePanel/SidePanel.tsx` | Container: saga + event log |
| `frontend/src/components/SidePanel/SagaLog.tsx` | Parchment-styled saga entries |
| `frontend/src/components/SidePanel/SagaLog.module.css` | Parchment styles |
| `frontend/src/components/SidePanel/EventLog.tsx` | Color-coded event log |
| `frontend/src/components/SidePanel/EventLog.module.css` | Event type color classes |
| `frontend/src/components/CommandPanel/CommandPanel.tsx` | World event trigger buttons |
| `frontend/src/components/CommandPanel/CommandPanel.module.css` | Button styles |
| `frontend/src/components/shared/AgentIcons.tsx` | SVG icons per agent role |
| `frontend/src/components/shared/ResourceIcon.tsx` | Inline SVG resource icons |
| `frontend/.env` | WebSocket URL config |

---

## Phase 6: AI Agents (Complete)

**Goal:** 5 Viking agents, each with its own Kafka consumer group, consuming world state, reasoning with an LLM (or stub), and publishing actions.

### PromptBuilder (`agent/.../prompt/PromptBuilder.kt`)

Constructs LLM prompts from world state:

- **System prompt**: agent name, role, personality text, available actions as JSON examples, rules about terrain and threats
- **User prompt** (for action agents): agent's own status (position, HP, inventory, terrain), world conditions (tick, time, weather, colony resources), 5x5 nearby terrain grid, other Vikings' positions and status, nearby entities within distance 8, active threats with distance, last 5 events
- **Skald user prompt**: world overview, all Vikings' status, active threats, last 8 events, instruction to write 1-3 sentences of Norse saga narration

### BaseAgent (`agent/.../BaseAgent.kt`)

Abstract class implementing the core agent loop:

1. Creates Kafka consumer (own group ID: `agent-{name}`) subscribed to `world-state` + `world-events`
2. Polls every 1 second on `Dispatchers.IO`
3. Tracks `latestWorldState` and `recentEvents` (last 20)
4. On new tick: checks if agent is alive, builds prompts, calls LLM provider, parses JSON response, publishes to `agent-actions`
5. **JSON extraction**: handles LLM responses wrapped in markdown code blocks or with extra text — finds first `{...}` block
6. **Fallback**: if LLM response can't be parsed, publishes IDLE action
7. **Rate limiting**: shared `Semaphore` acquired before LLM call, released after

Self-contained — receives all dependencies via constructor. Can be extracted to a separate `main()` for independent process mode.

### 5 Agent Subclasses

Each passes a unique personality string to BaseAgent:

| Agent | Name | Role | Personality |
|-------|------|------|-------------|
| `JarlAgent` | Bjorn | JARL | Leader. Prioritizes defense, stays near village, coordinates others. Brave but strategic. |
| `WarriorAgent` | Astrid | WARRIOR | Fighter. Seeks threats, never flees, patrols perimeter. Aggressive and fearless. |
| `FishermanAgent` | Erik | FISHERMAN | Gatherer. Moves to BEACH/water, gathers fish, deposits at village. Flees from threats. Cautious. |
| `ShipbuilderAgent` | Ingrid | SHIPBUILDER | Builder. Gathers timber from FOREST, deposits at village. Practical and hardworking. |
| `SkaldAgent` | Sigurd | SKALD | Narrator. Does not fight or gather. Observes events and publishes poetic saga narrations to `saga-log` topic every 2nd tick. |

### SkaldAgent Special Behavior

Overrides `processTickAction`:
- Only narrates every 2nd tick (avoids spam)
- Uses `buildSkaldUserPrompt` instead of `buildUserPrompt`
- Publishes `SagaLogEntry` to `saga-log` topic instead of `AgentAction` to `agent-actions`
- Cleans narration text (strips any JSON/markdown artifacts from LLM output)

### AgentMain (`agent/.../AgentMain.kt`)

Entry point:
- Reads config from `GameConfig` (Kafka, LLM provider, max tokens)
- Creates single shared `KafkaProducer` and `LlmProvider`
- Creates shared `Semaphore(2)` rate limiter (max 2 concurrent LLM calls)
- Launches all 5 agents as coroutines via `runBlocking { launch { agent.run() } }`
- Shutdown hook closes the producer

### Verification

1. `docker compose up -d` → Kafka healthy
2. `./gradlew :engine:run` → engine publishing world state
3. `./gradlew :agent:run` → all 5 agents awaken:
   ```
   Bjorn the JARL awakens
   Astrid the WARRIOR awakens
   Erik the FISHERMAN awakens
   Ingrid the SHIPBUILDER awakens
   Sigurd the SKALD awakens
   ```
4. Agent decisions logged:
   ```
   [Tick 6] Bjorn: PATROL — I need to keep the settlement safe.
   [Tick 6] Erik: MOVE west — Moving to a better position.
   [Tick 7] Astrid: PATROL — I need to keep the settlement safe.
   [Tick 7] Erik: GATHER — There are resources nearby to collect.
   [Tick 7] Ingrid: GATHER — There are resources nearby to collect.
   ```
5. `kafka-console-consumer --topic agent-actions` → JSON actions from all agents

---

## File Inventory Update

### Agent Module (10 files)
| File | Purpose |
|------|---------|
| `agent/.../AgentMain.kt` | Entry point — launches 5 agents as coroutines |
| `agent/.../BaseAgent.kt` | Core agent loop: Kafka consume → LLM → publish |
| `agent/.../prompt/PromptBuilder.kt` | System + user prompt construction from world state |
| `agent/.../agents/JarlAgent.kt` | Bjorn — leader personality |
| `agent/.../agents/WarriorAgent.kt` | Astrid — warrior personality |
| `agent/.../agents/FishermanAgent.kt` | Erik — fisherman personality |
| `agent/.../agents/ShipbuilderAgent.kt` | Ingrid — shipbuilder personality |
| `agent/.../agents/SkaldAgent.kt` | Sigurd — saga narrator (publishes to saga-log) |
| `agent/src/main/resources/logback.xml` | Logging config |

---

## Project Summary

All 6 core phases are complete. The full stack:

```
[Kafka Broker (Docker)]
    ↕
[Game Engine]     → publishes world-state + world-events every 5s
    ↕
[5 AI Agents]     → consume world-state, reason (stub/Claude/Gemini/Groq), publish agent-actions + saga-log
    ↕
[WebSocket Bridge] → consumes all 4 topics, forwards to browser via WebSocket
    ↕
[React Frontend]   → renders 20×20 grid, agent tokens, overlays, saga log, event log
```

**Total files created:** ~60+ across backend (Kotlin/Gradle) and frontend (React/TypeScript)

### Running the full stack:
```
Terminal 1:  docker compose up -d
Terminal 2:  cd backend && ./gradlew :engine:run
Terminal 3:  cd backend && ./gradlew :bridge:run
Terminal 4:  cd backend && ./gradlew :agent:run
Terminal 5:  cd frontend && npm run dev → http://localhost:5173
```

## Phase 7: Polish + Replay (Complete)

**Goal:** Replay mode, improved stub provider, health regen/respawn, visual polish.

### StubProvider Improvements

Added 12 Norse saga narrations so the Skald produces meaningful output in stub mode. The provider detects Skald prompts (checks for "Norse saga" or "Skald" in the prompt text) and returns a random saga narration instead of a JSON action. Also expanded the action list with more variety (fight, build, directional reasoning).

### Replay System

**ReplayRecorder** — now wraps each recorded envelope in a `ReplayLine` with relative timestamp (`offsetMs` from session start):
```json
{"offsetMs":5032,"envelope":"{\"topic\":\"world-state\",\"timestamp\":...}"}
```

**ReplayStreamer** (`bridge/.../ReplayStreamer.kt`) — reads a replay file and streams it over WebSocket with original timing:
- Reads the `.jsonl` file line by line
- Calculates delay between consecutive entries from `offsetMs` difference
- Caps maximum delay at 10 seconds (avoids long pauses)
- Sends a `replay-end` message when done

**Bridge `/replay` endpoint** — new WebSocket route at `ws://localhost:8080/replay`. Frontend connects to `/replay` instead of `/ws` to watch a recorded session. The React frontend works identically in both modes (it receives the same envelope format).

### Health Regen and Respawn

Added `WorldManager.healAndRespawn()` called each tick:
- **Village healing**: agents on VILLAGE tiles regenerate +5 HP per tick
- **Passive regen**: all alive agents heal +1 HP every 3 ticks
- **Respawn**: dead agents respawn at a random VILLAGE position after 10 ticks with full HP

### Frontend Polish

- **Page title**: "Viking Settlement" instead of "frontend"
- **Loading screen**: shows a sword icon (&#9876;) with pulsing gold glow, "Viking Settlement" title in Cinzel font, connection status ("Connecting to bridge..." vs "Awaiting world state..."), and backend startup instructions in monospace
- **Removed `react-use-websocket`** dependency — replaced with native WebSocket hook for better error handling and no crash on failed connection

### Verification

1. `./gradlew build` → BUILD SUCCESSFUL (backend)
2. `npx tsc --noEmit && npx vite build` → zero errors, clean build (frontend)
3. Open `http://localhost:5173` without backend → shows loading screen with sword icon and instructions
4. With full backend → grid renders and agents move

---

## What's Next

**Phase 8: Developer Experience** — Start scripts, `.env.example`, README with architecture diagram.
