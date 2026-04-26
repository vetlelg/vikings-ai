# RTS AI Battle — Project Plan

## Vision

A browser-based 2D RTS where two AI factions battle each other while the player watches. Each faction has a **strategic commander** (LLM agent) and **tactical unit agents** (behavior tree + LLM for judgment calls) that coordinate through Kafka. The game serves as both an entertaining spectator experience and a learning demo for event-driven multi-agent systems.

Inspired by Age of Empires 2 — simplified, with placeholder graphics first, real assets later.

## Starting Scope (v1)

- **Two factions**, mirror-symmetric fixed map, AoE2 small skirmish size
- **Resources:** wood, stone, gold, food
- **Buildings:** town center (starting), barracks
- **Units:** peasant/worker, melee warrior
- **Population cap:** 10 units per faction
- **Win condition:** destroy all enemy buildings and units
- **Fog of war:** visual only (spectator sees per-faction vision; AI agents receive full state)
- **Player role:** spectator (player control is a future phase)
- **Assets:** placeholders first, then [Epic RPG World Collection](https://rafaelmatos.itch.io/epic-rpg-world-collection) (32x32 PNG sprite sheets)

## Architecture

### Three-Tier AI Model

Inspired by how professional game AI works (Halo, F.E.A.R., AoE2). Each tier handles a different timescale — lower tiers can override higher ones for urgency.

```
┌─────────────────────────────────────────────────────────┐
│ TIER 1 — PHASER (instant, every frame)                  │
│                                                         │
│ - Auto-attack adjacent enemies                          │
│ - Flee when HP critical                                 │
│ - Path following + collision avoidance                  │
│ - Resource pickup/dropoff when adjacent                 │
│ - Death, spawn, damage application                      │
│                                                         │
│ Latency: 0ms. Hardcoded rules. No communication needed. │
├─────────────────────────────────────────────────────────┤
│ TIER 2 — BACKEND AGENTS (fast, event-driven)            │
│                                                         │
│ - Unit behavior trees (idle → gather → return → repeat) │
│ - React to game events ("enemy near my wood line")      │
│ - Execute commander orders ("switch to gold")           │
│ - Choose specific targets (which tree, which enemy)     │
│ - Share observations with faction-mates via Kafka       │
│ - LLM calls for non-trivial judgment calls              │
│                                                         │
│ Latency: 50-200ms (Kafka round-trip). Mostly rule-based │
│ with selective LLM reasoning for interesting decisions. │
├─────────────────────────────────────────────────────────┤
│ TIER 3 — COMMANDER AGENT (slow, periodic)               │
│                                                         │
│ - Strategic assessment ("they're militarizing early")   │
│ - Resource allocation ("prioritize gold for warriors")  │
│ - Build orders ("start barracks at safe location")      │
│ - Military directives ("raid their wood line")          │
│ - Re-planning when strategy fails                       │
│                                                         │
│ Latency: 1-5 seconds. LLM-driven strategic reasoning.  │
└─────────────────────────────────────────────────────────┘
```

**Urgency override example:**
1. Commander (LLM, Tier 3): "All workers gather wood"
2. Unit agent (behavior tree, Tier 2): assigns Worker 3 to north forest
3. Enemy warrior appears next to Worker 3
4. Phaser (Tier 1): Worker 3 instantly flees — no waiting
5. Unit agent (Tier 2): reassigns Worker 3 to a safer forest (~100ms)
6. Commander (Tier 3): notices raid pattern, orders warriors to defend (~3s)

### System Overview

```
CURRENT:
  Kotlin Engine (source of truth) → Kafka → Frontend (dumb viewer)

NEW:
  Phaser (game world) ←→ WebSocket ←→ Bridge ←→ Kafka ←→ AI Agents
  
  Phaser owns: physics, rendering, Tier 1 reflexes
  Agents own: decisions, memory, goals, behavior trees, LLM reasoning
```

Phaser is the **game world** — it simulates physics, renders, and handles instant reflexes. Backend agents are the **minds** — they perceive, remember, decide, and communicate.

### Frontend (Phaser 3)

Owns the game world simulation and rendering:

- Game loop at 60fps, with a fixed-timestep simulation tick (~10-20/sec)
- Tilemap rendering, camera (pan, zoom)
- Pathfinding (A* on tile grid), smooth unit movement
- Tier 1 reflex behaviors (auto-attack, auto-flee, resource pickup)
- Combat damage application, death, building construction progress
- Fog of war (visual only — dims/hides tiles for spectator per faction)
- Publishes game state snapshots to backend (1-2 per second, decoupled from render loop)
- Receives and executes agent decisions (move unit to X, build at Y, attack unit Z)
- Reports outcomes back to agents (unit arrived, gathering complete, unit died)

### Backend (Kotlin + Kafka)

Owns agent intelligence and inter-agent communication:

- **Bridge module** — WebSocket server. Receives game state and outcome events from Phaser, publishes to Kafka. Consumes agent decisions from Kafka, forwards to Phaser. Schema-agnostic — passes payloads through without deserializing.
- **Agent module** — persistent agent processes (coroutines) with behavior trees, memory, and LLM integration. One commander + one agent per unit, per faction.
- **Common module** — shared models, Kafka helpers, LLM providers, config.
- **Engine module** — removed. Phaser replaces it.

### Kafka Topics

| Topic | Publisher | Consumer | Content |
|-------|-----------|----------|---------|
| `game-state` | Bridge | All agents | Full world snapshot (1-2/sec) |
| `game-events` | Bridge | All agents | Outcome events: unit died, building complete, resource gathered |
| `commander-orders.{faction}` | Commander | Unit agents, Bridge | Strategic directives |
| `unit-decisions.{faction}` | Unit agents | Bridge → Phaser | Tactical commands: move, gather, build, attack |
| `unit-observations.{faction}` | Unit agents | Other unit agents, Commander | Peer-to-peer observations: resource finds, enemy sightings, area clear |

### AI Agent Structure (per faction)

Each agent is a **real persistent process** (Kotlin coroutine) with identity, state, memory, and autonomy. They are agents, not LLM wrappers.

```
Commander Agent (1 per faction)
  - Consumes: game-state, game-events, unit-observations.{faction}
  - Produces: commander-orders.{faction}
  - Has: strategic memory (economy trends, enemy behavior patterns)
  - Role: assess situation, set priorities, allocate units
  - Uses LLM: every decision cycle (~5-10 seconds)

Unit Agent (1 per unit)
  - Consumes: game-state, game-events, commander-orders.{faction}
  - Produces: unit-decisions.{faction}, unit-observations.{faction}
  - Has: behavior tree, memory (known resource locations, threats seen)
  - Role: execute commander intent with local tactical awareness
  - Uses LLM: selectively, for genuine judgment calls
  - Example behavior tree (worker):
    ├── Commander ordered retreat? → move to base
    ├── Carrying resources? → return to town center
    ├── Have gathering assignment? → pathfind to resource, gather
    ├── No assignment? → request orders from commander
    └── Default → idle near town center
```

### Decision Scope — Who Decides What

Inspired by military command structures and drone swarm architectures: unit agents have full tactical autonomy within the commander's strategic intent. Decisions stay at the lowest level that has enough context.

**Unit agent decides (local scope, no escalation):**
- Which specific tree to chop (nearest, safest path)
- Whether to flee or fight a single enemy (based on HP, unit type)
- Which route to take
- When to return resources vs. keep gathering (carry capacity full)
- Sharing observations: "found unguarded gold mine at (15, 8)"

**Commander decides (faction scope, LLM reasoning):**
- Overall strategy: rush, defend, or boom economy
- Resource allocation: "prioritize gold, we need warriors"
- Build orders: "build barracks at safe location near base"
- Military directives: "send warriors to raid enemy wood line"
- Reacting to aggregated intelligence from unit observations

**Neither decides (Phaser handles, Tier 1):**
- Auto-attack adjacent enemies
- Flee when HP critical
- Pick up resources when adjacent to node
- Return to TC when carrying capacity full and adjacent

### Peer-to-Peer Observations (Swarm Pattern)

Unit agents share local discoveries with the faction via Kafka — the group builds collective awareness from distributed local knowledge, like a drone swarm.

| Observation | Trigger | Example |
|-------------|---------|---------|
| Resource found | Unit sees resource cluster | "3 gold nodes near (15,8)" |
| Enemy spotted | Unit sees enemy units/buildings | "2 enemy warriors at (30,12)" |
| Area clear | No threats in area | "No enemies near (20,20)" |
| Resource depleted | Known resource gone | "Gold at (15,8) exhausted" |

- Unit agents broadcast to `unit-observations.{faction}`
- Other unit agents consume peer observations to inform their own decisions
- Commander consumes observations as field reports to inform strategy
- Observations are deduplicated — each finding reported once until conditions change

### Data Flow

```
1. Phaser runs game simulation tick
2. Phaser applies Tier 1 reflexes (auto-attack, flee, pickup)
3. Phaser broadcasts game state snapshot (1-2/sec) via WebSocket
4. Bridge publishes state to game-state topic
5. Commander agent reads state, reasons (LLM), publishes strategic orders
6. Unit agents read state + orders, traverse behavior trees, publish decisions
7. Bridge forwards decisions via WebSocket to Phaser
8. Phaser executes decisions (pathfind unit to target, start building, etc.)
9. Phaser reports outcomes via WebSocket ("unit 3 arrived", "gathering done")
10. Bridge publishes outcomes to game-events topic
11. Agents update their state/memory based on outcomes
12. Repeat
```

### Three Rate Clocks

| Clock | Rate | What |
|-------|------|------|
| Render loop | 60fps | Drawing, animations, camera |
| Game simulation tick | 10-20/sec | Movement, combat, resource depletion, Tier 1 reflexes |
| AI state broadcast | 1-2/sec | Game state snapshots sent to backend agents |

Agent decision rates vary: commander every 5-10s, unit agents react to events or when their current task completes.

## Game Balance (Starting Values)

### Resources

| Resource | Gathered from | Carry per trip | Gather time |
|----------|---------------|----------------|-------------|
| Wood | Trees | 10 | 3 seconds |
| Stone | Stone piles | 10 | 4 seconds |
| Gold | Gold mines | 10 | 5 seconds |
| Food | Berry bushes | 10 | 2 seconds |

### Buildings

| Building | Cost | Build time | HP |
|----------|------|------------|----|
| Town center | (starting building) | — | 500 |
| Barracks | 100 wood, 50 stone | 30 seconds | 300 |

### Units

| Unit | Trained at | Cost | Train time | HP | Attack | Speed |
|------|-----------|------|------------|-----|--------|-------|
| Worker | Town center | 50 food | 10 seconds | 30 | 3 | Medium |
| Warrior | Barracks | 60 food, 30 gold | 15 seconds | 60 | 10 | Medium |

### Starting Conditions (per faction)

- 1 town center
- 3 workers
- 50 food (enough for 1 extra worker immediately)
- 0 wood, 0 stone, 0 gold

### Population Cap

10 units per faction. Town center provides the cap (no houses needed for v1).

## Phases

### Phase 1: Phaser Foundation

Set up a working Phaser game with placeholder graphics. No backend connection yet.

- [ ] Initialize Phaser 3 project (TypeScript, Vite)
- [ ] Create a fixed tilemap (symmetric, two-player skirmish size)
  - Terrain types: grass, forest, stone, gold deposit, water, dirt
  - Symmetric layout with each faction's base area on opposite corners
  - Resources distributed symmetrically
- [ ] Render tilemap with colored rectangles as placeholder tiles
- [ ] Camera system (pan, zoom)
- [ ] Place town center (colored square) for each faction
- [ ] Spawn 3 worker units (colored circles) at each town center
- [ ] Basic unit movement with A* pathfinding on the tile grid
- [ ] Game simulation tick (fixed timestep, decoupled from render)

**Done when:** You see a symmetric map with two bases and workers that can pathfind to a clicked location (debug click-to-move for testing).

### Phase 2: Game Mechanics

Build the core RTS mechanics in Phaser, including Tier 1 reflex behaviors. Use a simple rule-based test AI in Phaser (throwaway code) to drive both factions for testing.

- [ ] Resource system
  - Resource nodes on the map (trees, stone piles, gold mines, berry bushes)
  - Workers gather resources (walk to node, harvest over time, carry fixed amount back to TC)
  - Faction resource counters (wood, stone, gold, food)
  - Resource nodes deplete after N harvests
- [ ] Building system
  - Barracks: costs 100 wood + 50 stone, takes 30s to build
  - Workers construct buildings (walk to site, build over time)
  - Buildings have HP, can be destroyed
  - Placement: any open ground
- [ ] Unit training
  - Town center trains workers (50 food, 10s)
  - Barracks trains warriors (60 food + 30 gold, 15s)
  - Training queue with build time
  - Population cap of 10
- [ ] Combat system
  - Melee combat: units auto-attack adjacent enemies (Tier 1 reflex)
  - Units have HP, attack damage, attack speed
  - Auto-flee when HP < 20% (Tier 1 reflex)
  - Death and removal
- [ ] Fog of war (visual only)
  - Each faction has a vision mask based on unit/building sight range
  - Spectator UI can toggle between faction views or show both
  - Unexplored: hidden. Explored but no vision: dimmed. Visible: full.
- [ ] Simple test AI (local, throwaway — not the real agent system)
  - Rule-based: gather nearest resource, build barracks when affordable, train warriors, attack-move toward enemy
  - Purpose: test that game mechanics work before wiring up the backend
- [ ] Win condition: first faction to complete a barracks

**Done when:** Two rule-based factions play a full game with placeholder graphics. Workers gather, buildings go up, warriors fight, someone wins.

### Phase 3: Backend Integration

Wire Phaser to the Kafka-based backend. Replace the throwaway test AI with the real agent communication pipeline.

- [ ] Define message schemas
  - Game state snapshot (units, buildings, resources, map state)
  - Game events (unit died, building complete, resource gathered, unit arrived)
  - Commander orders (strategic directives with assignments)
  - Unit decisions (move to, gather at, build at, attack target)
- [ ] Adapt Bridge module
  - Accept WebSocket connections from Phaser
  - Receive game state snapshots, publish to `game-state`
  - Receive outcome events, publish to `game-events`
  - Consume `unit-decisions.{faction}` and `commander-orders.{faction}`, forward to Phaser
- [ ] Remove Engine module
- [ ] Update Common module with new data models
- [ ] Phaser WebSocket integration
  - Publish game state snapshots at 1-2/sec (decoupled from render/sim loops)
  - Publish outcome events as they occur
  - Receive agent decisions, queue them, apply on next simulation tick
  - Fallback: if no decision arrives for a unit, it idles
- [ ] Verify message flow end-to-end with debug logging

**Done when:** Messages flow Phaser → Bridge → Kafka and back. You can see game state appearing on Kafka topics and dummy decisions being forwarded to Phaser.

### Phase 4: AI Agents

Build the real multi-agent system in the backend.

- [ ] Commander agent (one per faction)
  - Persistent coroutine with strategic memory
  - Consumes game state, reasons via LLM every ~5-10 seconds
  - Produces strategic orders: build priorities, unit assignments, attack/defend
  - Publishes to `commander-orders.{faction}`
- [ ] Unit agents (one per unit)
  - Persistent coroutine with behavior tree and memory
  - Consumes game state, game events, commander orders
  - Behavior tree handles routine decisions (no LLM)
  - LLM called for judgment calls (e.g., "commander says gather wood but I see an exposed enemy worker — what do I do?")
  - Publishes tactical decisions to `unit-decisions.{faction}`
  - New agents spawned when units are trained, removed when units die
- [ ] Inter-agent communication
  - Unit agents share observations within faction via Kafka
  - Commander consumes unit observations as field reports
- [ ] Stub provider for testing without API keys
- [ ] Rate limiting and concurrency control (semaphore for LLM calls)

**Done when:** Two LLM-powered factions play a full game autonomously. Commander issues strategy, unit agents execute via behavior trees with LLM judgment calls. One faction wins.

### Phase 5: Asset Integration

Swap placeholder graphics for real assets from the Epic RPG World Collection.

- [ ] Import and organize asset pack (32x32 sprite sheets)
- [ ] Create tilemap with real terrain tiles (grass, forest, water, stone, etc.)
- [ ] Replace unit placeholders with character sprites
  - Worker/peasant sprites with walk and gather animations
  - Warrior sprites with walk and attack animations
- [ ] Replace building placeholders with building sprites
  - Town center, barracks (construction stages if available)
- [ ] Resource node sprites (trees, mines, berry bushes)
- [ ] UI elements from the asset pack (resource bar, minimap frame, etc.)
- [ ] Death animations, combat effects
- [ ] Sound effects (if desired)

**Done when:** The game looks like an actual RTS with proper art, animations, and visual polish.

### Phase 6: Spectator Experience

Make it enjoyable to watch.

- [ ] Minimap showing full map with unit dots
- [ ] Ability to follow a specific unit or let the camera auto-follow action
- [ ] AI thought display — sidebar or overlay showing agent reasoning in real time
- [ ] Event log / commentary (narrator-style descriptions of what's happening)
- [ ] Game speed controls (pause, 1x, 2x, 4x)
- [ ] Match statistics (resources gathered, units trained, kills)
- [ ] Replay system (record and replay matches)

## Future Phases (Out of Scope for v1)

- **Player control mode** — player commands one faction, AI controls the other
- **More buildings** — walls, towers, farms, houses (population cap scaling)
- **More units** — ranged (archers), siege, cavalry
- **Tech tree / upgrades** — research at buildings to unlock units or improve stats
- **Larger maps and more factions** — 2v2, free-for-all
- **Multiplayer** — two players over network
- **More asset packs and themes**

## Tech Stack

| Component | Technology |
|-----------|------------|
| Game engine | Phaser 3 (TypeScript) |
| Build tool | Vite |
| Backend language | Kotlin |
| Messaging | Apache Kafka (KRaft, Docker) |
| WebSocket server | Ktor |
| AI providers | Claude, Gemini, Groq, Stub |
| Asset format | 32x32 PNG sprite sheets (Tiled-compatible) |

## Open Questions

- **Repo rename:** rename from `vikings-ai` to `rts-ai`.
- **Tick rate tuning:** game simulation at 10-20/sec is a starting guess. May need adjustment once we see how pathfinding and combat feel.
- **Map creation:** build the fixed map in Tiled Map Editor and export, or define programmatically? Tiled is probably better for a hand-crafted symmetric map.
- **LLM cost management:** one agent per unit means potentially 20 agents making LLM calls. Need to tune how often unit agents actually call the LLM vs. use behavior tree rules.
- **Agent lifecycle:** when a new unit is trained, a new agent coroutine spawns. When a unit dies, its agent is cleaned up. Need to handle this gracefully with Kafka consumer groups.
- **Three-tier tuning:** the balance between Tier 1 (Phaser reflexes), Tier 2 (backend behavior trees), and Tier 3 (LLM reasoning) will need testing and iteration. What feels right on paper may not work in practice — e.g., auto-flee threshold, how quickly behavior tree decisions arrive, when to invoke the LLM vs. use rules. Expect to revisit these boundaries throughout development.
