# Architecture

This document explains *why* the system is designed the way it is. For setup instructions, see [README.md](README.md). For implementation details, read the code — the data models are in `backend/common/src/main/kotlin/.../model/`, config in `reference.conf`, and types in `frontend/src/types/world.ts`.

## System Overview

```
                         +-----------------+
                         |  Apache Kafka   |
                         |  (Docker/KRaft) |
                         +--------+--------+
                                  |
              +-------------------+-------------------+
              |                   |                   |
         world-state        agent-tasks          world-events
         agent-directives                             |
              |                   |                   |
   +----------v------+  +--------v--------+  +-------v--------+
   |   Game Engine    |  |   AI Agents     |  | WebSocket      |
   |                  |  |   (4 coroutines)|  | Bridge (Ktor)  |
   |  - 40x40 grid   |  |                 |  |                |
   |  - Tick loop     |  |  - Bjorn (Jarl) |  |  /ws (live)    |
   |  - Task executor |  |  - Astrid (War) |  |  /replay       |
   |  - Threats       |  |  - Erik (Fish)  |  +-------+--------+
   |  - Day/night     |  |  - Ingrid (Ship)|          |
   |  - Weather       |  |                 |     WebSocket
   +---------+--------+  +--------+--------+          |
             |                     |          +--------v--------+
             +------> Kafka <------+          |  React Frontend  |
                                              |                  |
                                              |  - CSS Grid map  |
                                              |  - Agent tokens  |
                                              |  - Directives    |
                                              |  - Event log     |
                                              +------------------+
```

Four independent processes communicate exclusively through Kafka topics. Any process can be restarted without affecting the others — they resume from their Kafka offset.

## Why Kafka?

The project demonstrates event-driven architecture patterns. Kafka is the enabling technology:

- **Decoupling** — Agents know nothing about each other or the engine. They read `world-state`, write `agent-tasks`. The engine never calls agent code.
- **Backpressure** — LLM calls are slow (seconds). The engine ticks regardless. If an agent hasn't responded by the next tick, the engine continues with whatever task that agent already has.
- **Fault Recovery** — Kill an agent process, restart it. It resumes from its Kafka consumer offset. No state to restore.
- **Replay** — The bridge records all topic messages to a JSON Lines file. Replaying is just re-reading the file with original timing.

A simpler project could use direct WebSocket or in-process calls. Kafka exists here because the architecture patterns *are* the point.

## Kafka Topics

| Topic | Publisher | Subscriber | Content |
|-------|-----------|------------|---------|
| `world-state` | Engine | Agents, Bridge | Full world snapshot every tick (the Blackboard) |
| `agent-tasks` | Agents | Engine | High-level agent decisions (gather, fight, flee...) |
| `world-events` | Engine | Agents, Bridge | Narrative events (dragon sighted, night falling...) |
| `agent-directives` | Jarl agent | Agents, Bridge | Strategic colony-level directives from the Jarl |
| `agent-observations` | All agents | All agents, Bridge | Shared observations: resource finds, threats, area clear |

Topics auto-create on first produce/consume. Single partition, single broker — this is a demo, not a production cluster.

## The Task/Action Two-Layer Design

Agents don't submit low-level move commands. Instead:

1. **Agent decides a task** — e.g., `GATHER` with a target resource type. This is the LLM's output.
2. **Engine executes the task mechanically** — `TaskExecutor` pathfinds toward the resource, moves one tile per tick, picks up the resource when adjacent, and marks the task complete.

This separation exists because LLM calls take seconds and a 5-second tick rate means agents can only reason once per tick. If agents had to micro-manage each step ("move north, move north, move east, gather"), they'd need an LLM call per tile. With tasks, a single LLM call produces a goal that the engine pursues across multiple ticks.

The agent re-plans when:
- The current task completes
- A threat appears nearby (distance <= 4)
- Health drops below 30%

## How Agents Reason: Reactive + Deliberative Layers

Agents use a two-layer decision architecture inspired by the subsumption pattern from robotics:

### Reactive Layer (no LLM)

A fast, rule-based layer (`ReactiveLayer`) handles urgent, predictable situations instantly — no LLM call, no latency. Rules are checked in priority order:

1. **Threat nearby + combatant** (Warrior, Jarl) → FIGHT immediately
2. **Threat nearby + non-combatant** (Fisherman, Shipbuilder) → FLEE immediately
3. **Health critical** (< 30 HP) → IDLE to rest

These decisions are deterministic: a warrior always fights a nearby wolf, a fisherman always flees. Spending an LLM call on a predictable outcome wastes latency and tokens.

Reactive tasks are tagged with `[reactive]` in their reasoning text, making them distinguishable in logs and the frontend.

### Deliberative Layer (LLM)

When no reactive rule matches, the agent falls through to the LLM for strategic reasoning:

1. Build a prompt: system prompt (personality, rules, available tasks) + user prompt (nearby terrain, threats, inventory, recent events)
2. Call the LLM provider (or stub)
3. Parse the JSON response into an `AgentTask`
4. Publish to `agent-tasks` topic

The prompt includes an 11x11 terrain minimap centered on the agent, nearby entities with distances, and colony resource totals. The LLM returns a JSON object with a task type and reasoning text.

This is where genuine reasoning happens: which resource to prioritize, whether to deposit now or keep gathering, where to explore.

### Why Two Layers?

This mirrors how real autonomous systems work. A self-driving car doesn't call an LLM to brake for a pedestrian — it has a fast reactive layer for safety-critical decisions. The deliberative layer handles route planning, not emergency stops. The same principle applies here: survival is reactive, strategy is deliberative.

A shared `Semaphore(2)` limits concurrent LLM calls across all 4 agents to avoid rate limits.

## Hierarchical Planning: The Jarl as Orchestrator

The Jarl (Bjorn) has a dual role that demonstrates hierarchical orchestration:

1. **Personal tasks** — Like any agent, the Jarl gets reactive/deliberative tasks for his own actions (gather, fight, etc.)
2. **Strategic directives** — Every ~10 ticks, the Jarl evaluates the colony's overall situation and publishes a directive to the `agent-directives` topic

A directive contains:
- **Assessment**: A colony-level situation summary ("Colony urgently needs timber. Dragon approaching from the east.")
- **Assignments**: Per-agent orders ("Astrid: intercept the dragon", "Ingrid: prioritize timber in the northern forest")

Other agents subscribe to `agent-directives` and include the latest directive in their LLM prompt as context. Crucially, agents may **follow or override** directives based on local conditions — if Erik is ordered to fish but sees a wolf 3 tiles away, the reactive layer overrides the directive with FLEE.

This models a real tension in distributed systems: **central coordination vs. local autonomy**. The Jarl publishes intent, agents interpret and adapt. The same pattern appears in:
- Kubernetes schedulers (desired state vs. actual state)
- Supply chain orchestration (central planning vs. warehouse-level decisions)
- Military C2 systems (mission-type orders that subordinates adapt to local conditions)

The stub provider generates plausible directives based on resource progress and threat state, so the pattern works without an LLM API key.

## Distributed Knowledge: Agent Observations

Each agent has partial visibility — they see the world state but reason locally about their surroundings. By broadcasting observations to the `agent-observations` topic, the colony builds **collective situational awareness** from distributed local knowledge.

### How It Works

Each agent runs an `ObservationGenerator` every tick that scans the nearby environment (8-tile range) and produces rule-based observations:

| Observation | Trigger | Example |
|-------------|---------|---------|
| **RESOURCE_FOUND** | Cluster of 2+ resource sources nearby | "3 timber sources near (8,25)" |
| **RESOURCE_DEPLETED** | Previously reported resources are gone | "timber sources near (8,25) depleted" |
| **THREAT_SPOTTED** | New threat within scan range | "wolf at (30,15)" |
| **AREA_CLEAR** | No threats nearby (periodic) | "Area around (20,20) clear of threats" |

Observations are **deduplicated** — each finding is reported once until conditions change. No LLM is involved; observations are purely rule-based.

### How Observations Flow

1. Each agent scans its surroundings and publishes observations to `agent-observations`
2. All agents subscribe and store **peer** observations (filtering out their own)
3. The latest peer observations are included in the LLM prompt as "SHARED OBSERVATIONS"
4. The Jarl's strategic directive prompt includes them as "FIELD REPORTS"

This creates an information loop: agents discover resources → share findings → Jarl issues directives informed by field reports → agents act on directives with shared context.

### Why This Matters

This is the same pattern used in:
- **Sensor networks** — each node has local readings, the system builds a global picture
- **Distributed monitoring** (Prometheus/Grafana) — agents emit metrics, dashboards aggregate
- **Multi-robot exploration** — robots share map fragments to build collective maps

## The Bridge: Schema-Agnostic Forwarding

The WebSocket bridge wraps each Kafka message in an envelope:

```json
{
  "topic": "world-state",
  "timestamp": 1712930400000,
  "payload": { ... }
}
```

The bridge never deserializes the payload — it passes the raw JSON through. This means the bridge code never needs to change when data model fields are added or modified. The frontend routes by `topic` string.

The bridge also accepts commands from the frontend (spawn dragon, start winter, trigger raid) and publishes them as `world-events`.

## World Simulation

The engine runs a fixed-rate tick loop (default 5 seconds):

1. Consume agent tasks from Kafka
2. Execute each agent's current task (one step per tick via TaskExecutor)
3. Update threats — dragon moves toward nearest agent, wolves patrol
4. Generate ambient events — day/night cycle, weather changes, resource spawning
5. Heal agents and respawn dead ones
6. Publish `world-state` and `world-events`

### Map Generation

`MapGenerator` produces a procedural coastline: fjord on the west edge, beach strip, mountains in the north, village clearing in the center, scattered forest clusters. Resource sources (trees, mines, fishing spots, hunting grounds) are placed on appropriate terrain.

### Threats

| Threat | Behavior |
|--------|----------|
| **Dragon** | Spawns at map edge every ~60-80 ticks. HP 200. Moves toward nearest agent. Auto-attacks adjacent agents. |
| **Wolves** | Spawn at map edges. Move toward agents within range 8. Max 3 active. |
| **Raids** | Spawn 2 raider entities at the southeast edge every ~70-110 ticks. |

### Victory Condition

The colony wins by accumulating enough deposited resources to complete a longship: 50 timber, 30 iron, 20 furs. The voyage progress is shown in the frontend's top bar.

## Frontend Architecture

The frontend is a standalone React app connecting via WebSocket. Key design choices:

- **Zustand store** as single source of truth — world state replaced each tick, logs capped (50 tasks, 100 events, 20 directives)
- **CSS `transform: translate()`** for agent movement — GPU-composited, no layout reflow, 1.2s cubic-bezier glide
- **`React.memo`** on GridCell (terrain rarely changes) and AgentToken (custom comparator on position/status/health)
- **Movement trails** tracked in the store — last 4 positions for the grid, full 500-position history for the agent inspector mini-map
- **Toast notifications** auto-created for high-impact events (dragon, death, raid, victory)
- **Zoom/pan viewport** via mouse wheel and drag

The WebSocket hook handles reconnection with exponential backoff. The frontend works identically with the live `/ws` endpoint or the recorded `/replay` endpoint.

## Patterns Demonstrated

| Pattern | How |
|---------|-----|
| **Blackboard** | `world-state` topic as shared context all agents read |
| **Orchestrator-Worker** | Engine orchestrates ticks, agents are independent workers |
| **Hierarchical Planning** | Jarl publishes strategic directives; agents follow or override based on local conditions |
| **Subsumption / Hybrid AI** | Reactive layer (rules) handles survival; deliberative layer (LLM) handles strategy |
| **Distributed Knowledge** | Agents broadcast local observations; colony builds collective awareness via Kafka |
| **Backpressure** | Agents are slow (LLM latency), engine ticks continue regardless |
| **Fault Recovery** | Kill an agent process, restart — it resumes from Kafka offset |
| **Decoupling** | Agents know nothing about each other, only Kafka topics |
