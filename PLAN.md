# Viking Settlement — Implementation Plan

## Context

Build a browser-based 2D Viking colony game where 5 AI agents communicate through Apache Kafka. The project is a creative learning demo for event-driven architecture patterns (Blackboard, Orchestrator-Worker, Backpressure, Fault Recovery, Decoupling). The player watches autonomous agents make LLM-powered decisions in real time.

## Prerequisites to Install

| Tool | Notes |
|------|-------|
| Java 21+ | Already have Java 24 — works fine |
| Node.js LTS + npm | **Not installed** — needed for React frontend |
| Docker Desktop | Already have Docker 28.3.3 + Compose v2.39.2 |
| Git | For version control |

No Gradle CLI needed (project includes `gradlew`/`gradlew.bat`). No Kafka CLI needed (runs in Docker).

---

## Project Structure

```
C:\repos\vikings-ai\
├── docker-compose.yml
├── PLAN.md                           ← This file
├── backend/                          ← Gradle multi-module (Kotlin)
│   ├── settings.gradle.kts
│   ├── build.gradle.kts
│   ├── gradle.properties
│   ├── gradle/
│   │   ├── wrapper/
│   │   └── libs.versions.toml       ← Version catalog
│   ├── gradlew / gradlew.bat
│   ├── common/
│   │   ├── build.gradle.kts
│   │   └── src/main/kotlin/com/vikingsai/common/
│   │       ├── model/
│   │       │   ├── Enums.kt          ← TerrainType, ResourceType, AgentRole, ActionType, etc.
│   │       │   ├── Position.kt
│   │       │   ├── WorldState.kt     ← Full world snapshot per tick
│   │       │   ├── AgentAction.kt    ← Agent decision message
│   │       │   ├── WorldEvent.kt     ← Engine-generated narrative events
│   │       │   └── SagaLogEntry.kt   ← Skald narrations
│   │       ├── kafka/
│   │       │   ├── Topics.kt         ← Topic name constants
│   │       │   ├── JsonSerde.kt      ← kotlinx.serialization-based Kafka serde
│   │       │   └── KafkaHelpers.kt   ← Producer/consumer factory functions
│   │       ├── llm/
│   │       │   ├── LlmProvider.kt    ← Interface
│   │       │   ├── StubProvider.kt
│   │       │   ├── ClaudeProvider.kt
│   │       │   ├── GeminiProvider.kt
│   │       │   └── GroqProvider.kt
│   │       └── config/
│   │           └── GameConfig.kt     ← Typesafe config wrapper
│   ├── engine/
│   │   ├── build.gradle.kts
│   │   └── src/main/kotlin/com/vikingsai/engine/
│   │       ├── EngineMain.kt         ← Entry point
│   │       ├── GameLoop.kt           ← Tick loop orchestration
│   │       ├── world/
│   │       │   ├── MapGenerator.kt   ← Procedural 20x20 coastline map
│   │       │   └── WorldManager.kt   ← Mutable world state + action application
│   │       ├── entities/
│   │       │   ├── Dragon.kt         ← Dragon roaming AI
│   │       │   └── ThreatManager.kt  ← Wolves, raids, dragon spawning
│   │       └── tick/
│   │           ├── ActionResolver.kt ← Validate and apply agent actions
│   │           └── EventGenerator.kt ← Day/night, weather, hazard events
│   ├── agent/
│   │   ├── build.gradle.kts
│   │   └── src/main/kotlin/com/vikingsai/agent/
│   │       ├── AgentMain.kt          ← Launches all 5 agents as coroutines
│   │       ├── BaseAgent.kt          ← Abstract agent with Kafka consume/produce loop
│   │       ├── agents/
│   │       │   ├── JarlAgent.kt
│   │       │   ├── WarriorAgent.kt
│   │       │   ├── FishermanAgent.kt
│   │       │   ├── ShipbuilderAgent.kt
│   │       │   └── SkaldAgent.kt     ← Publishes to saga-log instead of agent-actions
│   │       └── prompt/
│   │           └── PromptBuilder.kt  ← Builds system + user prompts per agent role
│   └── bridge/
│       ├── build.gradle.kts
│       └── src/main/kotlin/com/vikingsai/bridge/
│           ├── BridgeMain.kt         ← Ktor server entry point
│           ├── BridgeEnvelope.kt     ← WebSocket message wrapper
│           ├── KafkaForwarder.kt     ← Consumes all topics, broadcasts to WS clients
│           └── ReplayRecorder.kt     ← Writes JSON Lines file for replay
└── frontend/                         ← Standalone Vite/React/TypeScript
    ├── package.json
    ├── vite.config.ts
    ├── tsconfig.json
    ├── .env
    └── src/
        ├── main.tsx
        ├── App.tsx
        ├── App.module.css
        ├── types/
        │   └── world.ts              ← TypeScript types mirroring backend models
        ├── store/
        │   └── gameStore.ts           ← Zustand store (single source of truth)
        ├── hooks/
        │   └── useGameSocket.ts       ← WebSocket hook dispatching to store
        ├── styles/
        │   ├── theme.css              ← CSS custom properties (colors, fonts, timing)
        │   ├── reset.css
        │   └── animations.css         ← Shared @keyframes
        └── components/
            ├── TopBar/
            │   ├── TopBar.tsx         ← Time, resources, agent status
            │   └── TopBar.module.css
            ├── GameGrid/
            │   ├── GameGrid.tsx       ← 20x20 CSS grid + token overlay layer
            │   ├── GameGrid.module.css
            │   ├── GridCell.tsx       ← Individual terrain tile
            │   ├── GridCell.module.css
            │   ├── AgentToken.tsx     ← Agent icon, smooth CSS transform glide
            │   ├── AgentToken.module.css
            │   ├── EntityToken.tsx    ← Wolves, resources
            │   ├── DragonToken.tsx    ← Pulsing glow, fire trail, oversized
            │   ├── DragonToken.module.css
            │   ├── DayNightOverlay.tsx
            │   └── WeatherOverlay.tsx ← CSS keyframe snow/storm particles
            ├── SidePanel/
            │   ├── SidePanel.tsx      ← Container: saga log on top, event log below
            │   ├── SagaLog.tsx        ← Parchment style, Cinzel font, fade-in
            │   ├── SagaLog.module.css
            │   ├── EventLog.tsx       ← Monospace, color-coded by event type
            │   └── EventLog.module.css
            ├── CommandPanel/
            │   ├── CommandPanel.tsx    ← Buttons: Summon Dragon, Winter, Raid
            │   └── CommandPanel.module.css
            └── shared/
                ├── ResourceIcon.tsx   ← Inline SVG icons for timber/fish/iron/furs
                └── AgentIcons.tsx     ← SVG icons per role (crown, sword, fish, hammer, lyre)
```

---

## Core Data Model

### Enums (common/model/Enums.kt)

```kotlin
enum class TerrainType { GRASS, FOREST, WATER, MOUNTAIN, BEACH, VILLAGE }
enum class ResourceType { TIMBER, FISH, IRON, FURS }
enum class AgentRole { JARL, WARRIOR, FISHERMAN, SHIPBUILDER, SKALD }
enum class ActionType { MOVE, GATHER, FIGHT, BUILD, PATROL, FLEE, SPEAK, IDLE }
enum class AgentStatus { ALIVE, DEAD, THINKING }
enum class TimeOfDay { DAWN, DAY, DUSK, NIGHT }
enum class Weather { CLEAR, SNOW, STORM }
enum class EventType {
    DRAGON_SIGHTED, DRAGON_DEFEATED, NIGHT_FALLING, DAWN_BREAKING,
    RAID_INCOMING, WOLF_SPOTTED, AGENT_DIED, BUILDING_COMPLETE,
    RESOURCE_GATHERED, COMBAT, WEATHER_CHANGE, AGENT_SPOKE
}
enum class EntityType { WOLF, DRAGON, RESOURCE_NODE }
enum class Severity { LOW, MEDIUM, HIGH, CRITICAL }
```

### Position (common/model/Position.kt)

```kotlin
@Serializable
data class Position(val x: Int, val y: Int)
```

### WorldState (published to `world-state` each tick)

```kotlin
@Serializable
data class WorldState(
    val tick: Int,
    val grid: List<List<TerrainType>>,          // 20x20
    val agents: List<AgentSnapshot>,
    val entities: List<EntitySnapshot>,          // wolves, dragon, resource nodes
    val colonyResources: ColonyResources,
    val timeOfDay: TimeOfDay,
    val weather: Weather,
    val threats: List<ThreatSnapshot>
)

@Serializable
data class AgentSnapshot(
    val name: String,                            // "Bjorn", "Astrid", etc.
    val role: AgentRole,
    val position: Position,
    val health: Int,                             // 0-100
    val inventory: Map<ResourceType, Int>,
    val status: AgentStatus
)

@Serializable
data class EntitySnapshot(
    val id: String,
    val type: EntityType,
    val position: Position,
    val subtype: String? = null                  // e.g., "timber" for resource nodes
)

@Serializable
data class ColonyResources(
    val timber: Int = 0,
    val fish: Int = 0,
    val iron: Int = 0,
    val furs: Int = 0
)

@Serializable
data class ThreatSnapshot(
    val id: String,
    val type: String,                            // "dragon", "wolf", "raiders"
    val position: Position,
    val severity: Severity
)
```

### AgentAction (published to `agent-actions`)

```kotlin
@Serializable
data class AgentAction(
    val tick: Int,
    val agentName: String,
    val action: ActionType,
    val direction: String? = null,               // "north", "south", "east", "west"
    val targetPosition: Position? = null,
    val reasoning: String                        // LLM's explanation
)
```

### WorldEvent (published to `world-events`)

```kotlin
@Serializable
data class WorldEvent(
    val tick: Int,
    val eventType: EventType,
    val description: String,
    val severity: Severity,
    val affectedPositions: List<Position> = emptyList()
)
```

### SagaLogEntry (published to `saga-log` by Skald)

```kotlin
@Serializable
data class SagaLogEntry(
    val tick: Int,
    val text: String                             // Poetic narration
)
```

### BridgeEnvelope (WebSocket message wrapper)

```kotlin
@Serializable
data class BridgeEnvelope(
    val topic: String,
    val timestamp: Long,
    val payload: JsonElement   // Raw JSON — bridge is schema-agnostic
)
```

---

## WebSocket Protocol

Every message from bridge to browser is a JSON envelope:

```json
{
  "topic": "world-state",
  "timestamp": 1712930400000,
  "payload": { ... }
}
```

The bridge does NOT deserialize payloads — it wraps the raw Kafka value as a `JsonElement`. This means the bridge never breaks when data model fields change.

Frontend routes by `topic`:
- `"world-state"` → replace entire world snapshot in store
- `"agent-actions"` → append to action log
- `"world-events"` → append to event log
- `"saga-log"` → append to saga panel

Optional command (frontend → bridge → Kafka):
```json
{ "command": "spawn_dragon" }
```

---

## Docker Compose (Kafka KRaft)

```yaml
services:
  kafka:
    image: apache/kafka:latest
    container_name: vikings-kafka
    ports:
      - "9092:9092"
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_LISTENERS: CONTROLLER://:29093,PLAINTEXT_HOST://:9092,PLAINTEXT://:19092
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT_HOST://localhost:9092,PLAINTEXT://kafka:19092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:29093
      CLUSTER_ID: VikingsAI_Kafka_Cluster_01
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
    volumes:
      - kafka-data:/tmp/kraft-combined-logs
    healthcheck:
      test: /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 20s

volumes:
  kafka-data:
```

Topics auto-create on first produce/consume. Named Docker volume avoids Windows file-locking issues.

---

## Configuration (HOCON with env var overrides)

```hocon
# common/src/main/resources/reference.conf
kafka {
  bootstrap-servers = "localhost:9092"
  bootstrap-servers = ${?KAFKA_BOOTSTRAP_SERVERS}
}

# engine/src/main/resources/application.conf
include "reference"
engine {
  tick-rate-ms = 5000
  tick-rate-ms = ${?TICK_RATE_MS}
  grid-width = 20
  grid-height = 20
}

# agent/src/main/resources/application.conf
include "reference"
llm {
  provider = "stub"
  provider = ${?LLM_PROVIDER}
  model = "claude-sonnet-4-20250514"
  model = ${?LLM_MODEL}
  api-key = ""
  api-key = ${?LLM_API_KEY}
  max-tokens = 150
}

# bridge/src/main/resources/application.conf
include "reference"
bridge {
  port = 8080
  port = ${?BRIDGE_PORT}
  replay-file = "recordings/replay.jsonl"
}
```

---

## Dependency Versions (gradle/libs.versions.toml)

```toml
[versions]
kotlin = "2.1.0"
ktor = "3.1.0"
kafka = "3.9.0"
kotlinx-serialization = "1.7.3"
kotlinx-coroutines = "1.9.0"
logback = "1.5.15"
typesafe-config = "1.4.3"

[libraries]
kafka-clients = { module = "org.apache.kafka:kafka-clients", version.ref = "kafka" }
ktor-server-core = { module = "io.ktor:ktor-server-core", version.ref = "ktor" }
ktor-server-netty = { module = "io.ktor:ktor-server-netty", version.ref = "ktor" }
ktor-server-websockets = { module = "io.ktor:ktor-server-websockets", version.ref = "ktor" }
ktor-server-cors = { module = "io.ktor:ktor-server-cors", version.ref = "ktor" }
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-cio = { module = "io.ktor:ktor-client-cio", version.ref = "ktor" }
ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinx-coroutines" }
logback = { module = "ch.qos.logback:logback-classic", version.ref = "logback" }
typesafe-config = { module = "com.typesafe:config", version.ref = "typesafe-config" }

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

---

## Frontend Design Tokens (theme.css)

```css
:root {
  /* Background */
  --color-bg: #0d0f14;
  --color-surface: #1a1d26;
  --color-surface-raised: #242835;

  /* Accent */
  --color-gold: #d4a843;
  --color-amber: #e8a627;
  --color-red: #c0392b;
  --color-red-bright: #ff4444;
  --color-green: #2ecc71;
  --color-blue: #3498db;
  --color-purple: #9b59b6;
  --color-brown: #8b6914;
  --color-orange: #e67e22;
  --color-text: #e8e0d4;
  --color-text-muted: #8a8577;

  /* Terrain */
  --terrain-grass: #3a5a2c;
  --terrain-forest: #1e3a1a;
  --terrain-water: #1a3a5c;
  --terrain-mountain: #4a4a52;
  --terrain-beach: #c4a44a;
  --terrain-village: #6b4c2a;

  /* Typography */
  --font-ui: 'Inter', system-ui, sans-serif;
  --font-saga: 'Cinzel', serif;
  --font-saga-accent: 'MedievalSharp', cursive;
  --font-mono: 'JetBrains Mono', 'Fira Code', monospace;

  /* Timing */
  --transition-fast: 150ms ease;
  --transition-medium: 300ms ease;
  --transition-slow: 800ms ease;
  --transition-glide: 1.2s cubic-bezier(0.25, 0.46, 0.45, 0.94);

  /* Grid */
  --cell-size: 44px;
  --grid-gap: 1px;
}
```

---

## Frontend State Management (Zustand)

```typescript
// src/store/gameStore.ts — single source of truth
interface GameState {
  // Latest world snapshot (replaced each tick)
  grid: GridCell[][];
  agents: Agent[];
  entities: Entity[];
  colonyResources: ColonyResources;
  timeOfDay: TimeOfDay;
  weather: Weather;
  threats: Threat[];
  tick: number;

  // Rolling logs (capped to prevent memory growth)
  agentActions: AgentAction[];       // last 50
  worldEvents: WorldEvent[];         // last 100
  sagaEntries: SagaEntry[];          // last 30

  // Connection
  connected: boolean;

  // Dispatch actions
  applyWorldState: (ws: WorldState) => void;
  addAgentAction: (a: AgentAction) => void;
  addWorldEvent: (e: WorldEvent) => void;
  addSagaEntry: (s: SagaEntry) => void;
  setConnected: (c: boolean) => void;
}
```

Data flow: `Ktor WebSocket → useGameSocket hook → Zustand store → React components (via selectors)`

---

## Frontend Animations (animations.css)

```css
@keyframes pulse-glow {
  0%, 100% { box-shadow: 0 0 8px 2px var(--glow-color); }
  50% { box-shadow: 0 0 20px 6px var(--glow-color); }
}

@keyframes thinking-pulse {
  0%, 100% { filter: drop-shadow(0 0 4px #f1c40f); opacity: 1; }
  50% { filter: drop-shadow(0 0 12px #f1c40f); opacity: 0.8; }
}

@keyframes fade-in-up {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes snowfall {
  0% { transform: translateY(-10px) translateX(0); opacity: 0; }
  10% { opacity: 1; }
  90% { opacity: 1; }
  100% { transform: translateY(100vh) translateX(20px); opacity: 0; }
}

@keyframes fire-trail {
  0% { opacity: 0.8; transform: scale(1); }
  50% { opacity: 0.4; transform: scale(1.5); }
  100% { opacity: 0; transform: scale(0.5); }
}

@keyframes dragon-glow {
  0%, 100% { box-shadow: 0 0 15px 5px rgba(255, 68, 0, 0.6); }
  50% { box-shadow: 0 0 35px 15px rgba(255, 68, 0, 0.9); }
}

@keyframes water-shimmer {
  0%, 100% { background-position: 0% 50%; }
  50% { background-position: 100% 50%; }
}
```

---

## Frontend Performance Strategy

- `React.memo` on `GridCell` — terrain rarely changes, string equality check
- `React.memo` with custom comparator on `AgentToken` — only re-render on position/status/health change
- `transform: translate()` for agent movement — GPU-composited, no layout reflow
- Zustand selectors — each component subscribes only to its slice, preventing cascade re-renders
- Weather snowflakes created once in `useMemo` — CSS drives animation, React never re-renders flakes
- Agent tokens use `transition: var(--transition-glide)` (1.2s) — agents glide ~1/4 of tick interval

---

## Implementation Phases

### Phase 1: Project Skeleton + Kafka
**Goal:** Gradle project compiles, Kafka runs in Docker.

**Files to create:**
- `docker-compose.yml` — Kafka KRaft (see Docker Compose section above)
- `backend/settings.gradle.kts` — includes `:common`, `:engine`, `:agent`, `:bridge`
- `backend/build.gradle.kts` — shared Kotlin config, version catalog
- `backend/gradle.properties` — JVM args, Kotlin settings
- `backend/gradle/libs.versions.toml` — all dependency versions (see section above)
- Per-module `build.gradle.kts`:
  - `common/` — kotlinx-serialization, kafka-clients, typesafe-config, ktor-client (for LLM providers)
  - `engine/` — depends on `:common`
  - `agent/` — depends on `:common`
  - `bridge/` — depends on `:common`, ktor-server, ktor-server-websockets, ktor-server-cors
- Gradle wrapper: `gradlew`, `gradlew.bat`, `gradle/wrapper/*`
- `.gitignore` — Gradle, IDE, Node, build outputs, .env, recordings/
- Skeleton `EngineMain.kt` that connects to Kafka and prints cluster info

**Verify:**
1. `docker compose up -d` → Kafka healthy (`docker compose ps`)
2. `cd backend && ./gradlew build` → all modules compile
3. `cd backend && ./gradlew :engine:run` → prints Kafka cluster ID

---

### Phase 2: Common Domain Model
**Goal:** All shared data classes, Kafka helpers, LLM provider interface in `common`.

**Files to create:**
- `common/.../model/Enums.kt` — all enums listed above
- `common/.../model/Position.kt` — `Position(x, y)`
- `common/.../model/WorldState.kt` — `WorldState`, `AgentSnapshot`, `EntitySnapshot`, `ColonyResources`, `ThreatSnapshot`
- `common/.../model/AgentAction.kt`
- `common/.../model/WorldEvent.kt`
- `common/.../model/SagaLogEntry.kt`
- `common/.../kafka/Topics.kt` — `object Topics { const val WORLD_STATE = "world-state"; ... }`
- `common/.../kafka/JsonSerde.kt` — generic `JsonSerializer<T>` / `JsonDeserializer<T>` using kotlinx.serialization
- `common/.../kafka/KafkaHelpers.kt` — `createProducer()` and `createConsumer(groupId, topics)` factory functions
- `common/.../llm/LlmProvider.kt` — `interface LlmProvider { suspend fun complete(systemPrompt, userMessage, maxTokens): String }`
- `common/.../llm/StubProvider.kt` — random plausible actions with configurable delay
- `common/.../llm/ClaudeProvider.kt` — POST to `api.anthropic.com/v1/messages`
- `common/.../llm/GeminiProvider.kt` — POST to Gemini API
- `common/.../llm/GroqProvider.kt` — POST to Groq (OpenAI-compatible)
- `common/.../config/GameConfig.kt` — Typesafe Config wrapper
- `common/src/main/resources/reference.conf` — shared Kafka config

**Verify:** Unit test round-tripping every data class through JSON serialization.

---

### Phase 3: Game Engine
**Goal:** Running engine that publishes world state to Kafka each tick. Testable without agents.

**Files to create:**
- `engine/.../world/MapGenerator.kt` — procedural 20x20 coastline (fjord on west edge, forest clusters, mountain range north, beach strip, village center). Deterministic seed.
- `engine/.../world/WorldManager.kt` — mutable world state, applies validated actions, spawns resource nodes
- `engine/.../tick/ActionResolver.kt` — validates AgentActions (range checks, resource availability, combat), applies to world
- `engine/.../tick/EventGenerator.kt` — day/night cycle (every N ticks), weather changes, wolf spawns, raid events
- `engine/.../entities/Dragon.kt` — roaming AI: moves toward nearest agent, CRITICAL world event on appearance
- `engine/.../entities/ThreatManager.kt` — wolf spawn/patrol, periodic raids, dragon scheduling
- `engine/.../GameLoop.kt` — tick loop: consume actions → apply → update entities → generate events → publish WorldState → publish WorldEvents → delay
- `engine/.../EngineMain.kt` — reads config, creates Kafka clients, starts loop
- `engine/src/main/resources/application.conf`
- Backpressure: if agent hasn't responded by next tick, action = IDLE

**Verify:**
1. Kafka + engine running
2. `kafka-console-consumer --topic world-state` shows JSON streaming every 5s
3. WorldState has 20x20 grid, 5 agents, day/night cycling

---

### Phase 4: WebSocket Bridge
**Goal:** Ktor server forwarding Kafka → WebSocket + replay recording.

**Files to create:**
- `bridge/.../BridgeMain.kt` — Ktor embedded server (port 8080), WebSocket plugin, CORS
- `bridge/.../BridgeEnvelope.kt` — envelope data class (see WebSocket Protocol section)
- `bridge/.../KafkaForwarder.kt` — background coroutine: consume all 4 topics, wrap in BridgeEnvelope, broadcast to all WebSocketSessions
- `bridge/.../ReplayRecorder.kt` — append envelopes as JSON Lines to `recordings/replay.jsonl`
- `bridge/src/main/resources/application.conf`
- WebSocket at `/ws`: add session on connect, remove on disconnect
- Accept incoming `WorldCommand` JSON from frontend, publish to `world-events`

**Verify:**
1. Kafka + engine + bridge running
2. Browser: `new WebSocket("ws://localhost:8080/ws").onmessage = e => console.log(JSON.parse(e.data))` → envelope messages
3. `recordings/replay.jsonl` growing

---

### Phase 5: React Frontend
**Goal:** Full visual UI connected to bridge WebSocket.

**Setup:**
- Scaffold with Vite: `npm create vite@latest frontend -- --template react-ts`
- Install: `zustand`, `react-use-websocket`, `@fontsource/cinzel`, `@fontsource/medievalsharp`
- Styling: CSS Modules (`.module.css` co-located), single `theme.css` for tokens

**Files to create (in order):**
1. `types/world.ts` — TypeScript types mirroring all backend models
2. `styles/theme.css` — CSS custom properties (see design tokens above)
3. `styles/reset.css` — minimal reset
4. `styles/animations.css` — shared @keyframes (see animations above)
5. `store/gameStore.ts` — Zustand store (see state management above)
6. `hooks/useGameSocket.ts` — WebSocket hook parsing envelopes, dispatching to Zustand by topic
7. `App.tsx` + `App.module.css` — CSS Grid layout: top bar | main (grid + side panel) | command bar
8. `components/GameGrid/GridCell.tsx` — terrain tile, CSS gradient per type
9. `components/GameGrid/GameGrid.tsx` — 20x20 CSS Grid + absolute token overlay layer
10. `components/GameGrid/AgentToken.tsx` — `transform: translate()` with 1.2s transition, role-colored ring, SVG icon
11. `components/GameGrid/DragonToken.tsx` — 1.8x cell size, pulsing orange glow, fire trail
12. `components/GameGrid/EntityToken.tsx` — wolves (grey), resource nodes (themed icons)
13. `components/GameGrid/DayNightOverlay.tsx` — full-grid overlay, color by time of day, 2s transition
14. `components/GameGrid/WeatherOverlay.tsx` — 50 CSS snowflake divs, staggered animation-delay
15. `components/TopBar/TopBar.tsx` — sun/moon icon, resource tallies with SVGs, agent status dots
16. `components/SidePanel/SagaLog.tsx` — parchment bg (#d4c4a0), Cinzel font, rough border, fade-in entries
17. `components/SidePanel/EventLog.tsx` — monospace, color-coded (combat=red, gather=green, dragon=orange)
18. `components/SidePanel/SidePanel.tsx` — 50/50 vertical split
19. `components/CommandPanel/CommandPanel.tsx` — buttons: Summon Dragon, Winter Comes, Rival Raid
20. `components/shared/ResourceIcon.tsx` + `AgentIcons.tsx` — inline SVG components

**Verify:**
1. Full stack running → grid renders with terrain, agents glide each tick
2. Day/night overlay transitions
3. Saga log and event log populate with entries
4. Kill bridge → red indicator → restart → auto-reconnects

---

### Phase 6: AI Agents
**Goal:** 5 Viking agents consuming world state, reasoning with LLMs, publishing actions.

**Files to create:**
- `agent/.../BaseAgent.kt` — abstract class: constructor(name, role, groupId, LlmProvider, producer, consumer). Loop: poll world-state + world-events → build prompt → call LLM → parse → publish. Self-contained for future extraction.
- `agent/.../prompt/PromptBuilder.kt` — system prompt (personality, rules, available actions, JSON response format) + user prompt (world state summary, nearby entities, recent events, inventory/health)
- `agent/.../agents/JarlAgent.kt` — "Bjorn the Jarl. Commands with authority. Prioritizes defense."
- `agent/.../agents/WarriorAgent.kt` — "Astrid the Warrior. Fierce protector. Seeks threats."
- `agent/.../agents/FishermanAgent.kt` — "Erik the Fisherman. Cautious. Watches weather."
- `agent/.../agents/ShipbuilderAgent.kt` — "Ingrid the Shipbuilder. Practical. Gathers timber, builds."
- `agent/.../agents/SkaldAgent.kt` — "Sigurd the Skald. Poet/narrator." Publishes to `saga-log` not `agent-actions`.
- `agent/.../AgentMain.kt` — reads config, creates LlmProvider from config, launches 5 agents as coroutines
- `agent/src/main/resources/application.conf`
- Rate limiter: shared semaphore to stagger LLM API calls

**LLM response format:** `{ "action": "move", "direction": "north", "reasoning": "I see timber to the north..." }`

**Verify:**
1. Full stack with `LLM_PROVIDER=stub` → agents move randomly, saga log shows entries
2. `LLM_PROVIDER=claude` + API key → intelligent decisions, reasoning in event log
3. Kill agent process → restart → resumes from Kafka offset (fault recovery)

---

### Phase 7: Polish + Replay
**Goal:** Visual polish, replay mode, game balance.

**Build:**
- Improved map generation (clustered forests, resource-rich areas)
- Combat resolution (damage, death, respawn)
- Dragon as major event (Skald narrates, Jarl defends, Warrior engages)
- Replay endpoint: `/replay` WebSocket streams recorded JSON Lines with original timing
- Frontend loading state before first world-state
- Fine-tune animations, colors, fonts

---

### Phase 8: Developer Experience
**Goal:** Easy to run, easy to demo.

**Build:**
- `start.sh` / `start.bat` — launch everything
- `.env.example`
- README with architecture diagram, setup, screenshots

---

## Phase Dependency Graph

```
Phase 1 → Phase 2 → Phase 3 → Phase 4 → Phase 5 → Phase 7 → Phase 8
                         ↓
                     Phase 6 ────────↗
```

Phase 4 (bridge) and Phase 6 (agents) can be built in parallel after Phase 3. Phase 5 (frontend) needs Phase 4.

---

## Development Workflow

```
Terminal 1:  docker compose up -d
Terminal 2:  cd backend && ./gradlew :engine:run
Terminal 3:  cd backend && ./gradlew :bridge:run
Terminal 4:  cd backend && ./gradlew :agent:run          # stub provider by default
Terminal 5:  cd frontend && npm run dev                   # http://localhost:3000
```

Kafka must be up first. Other services can start in any order (Kafka decouples them). Each should retry connections with backoff.
