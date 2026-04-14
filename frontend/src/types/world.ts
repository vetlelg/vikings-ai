// --- Enums (string unions matching backend Kotlin enums) ---
export type TerrainType = 'GRASS' | 'FOREST' | 'WATER' | 'MOUNTAIN' | 'BEACH' | 'VILLAGE';
export type ResourceType = 'TIMBER' | 'FISH' | 'IRON' | 'FURS';
export type AgentRole = 'JARL' | 'WARRIOR' | 'FISHERMAN' | 'SHIPBUILDER' | 'SKALD';
export type ActionType = 'MOVE' | 'GATHER' | 'FIGHT' | 'BUILD' | 'FLEE' | 'SPEAK' | 'IDLE';
export type TaskType = 'GATHER' | 'DEPOSIT' | 'FIGHT' | 'FLEE' | 'MOVE_TO' | 'IDLE';
export type AgentStatus = 'ALIVE' | 'DEAD' | 'THINKING';
export type TimeOfDay = 'DAWN' | 'DAY' | 'DUSK' | 'NIGHT';
export type Weather = 'CLEAR' | 'SNOW' | 'STORM';
export type GameStatus = 'IN_PROGRESS' | 'VICTORY';
export type EventType =
  | 'DRAGON_SIGHTED' | 'DRAGON_DEFEATED' | 'NIGHT_FALLING' | 'DAWN_BREAKING'
  | 'RAID_INCOMING' | 'WOLF_SPOTTED' | 'AGENT_DIED' | 'BUILDING_COMPLETE'
  | 'RESOURCE_GATHERED' | 'COMBAT' | 'WEATHER_CHANGE' | 'AGENT_SPOKE'
  | 'LONGSHIP_COMPLETE';
export type EntityType = 'WOLF' | 'DRAGON' | 'RESOURCE_NODE';
export type Severity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

// --- Core data ---
export interface Position {
  x: number;
  y: number;
}

export interface AgentSnapshot {
  name: string;
  role: AgentRole;
  position: Position;
  health: number;
  inventory: Record<string, number>;
  status: AgentStatus;
  currentAction?: ActionType;
  currentDirection?: string;
  currentTaskType?: TaskType;
  currentTaskReasoning?: string;
  kills: number;
  deaths: number;
  totalDeposited: Record<string, number>;
}

export interface EntitySnapshot {
  id: string;
  type: EntityType;
  position: Position;
  subtype?: string;
  remaining?: number;
  capacity?: number;
}

export interface ColonyResources {
  timber: number;
  fish: number;
  iron: number;
  furs: number;
}

export interface ThreatSnapshot {
  id: string;
  type: string;
  position: Position;
  severity: Severity;
}

// --- Topic messages ---
export interface WorldState {
  tick: number;
  grid: TerrainType[][];
  agents: AgentSnapshot[];
  entities: EntitySnapshot[];
  colonyResources: ColonyResources;
  timeOfDay: TimeOfDay;
  weather: Weather;
  threats: ThreatSnapshot[];
  gameStatus: GameStatus;
  voyageGoal: ColonyResources;
}

export interface AgentAction {
  tick: number;
  agentName: string;
  action: ActionType;
  direction?: string;
  targetPosition?: Position;
  reasoning: string;
}

export interface WorldEvent {
  tick: number;
  eventType: EventType;
  description: string;
  severity: Severity;
  affectedPositions: Position[];
}

export interface SagaLogEntry {
  tick: number;
  text: string;
}

export interface AgentTask {
  tick: number;
  agentName: string;
  taskType: TaskType;
  targetResourceType?: string;
  targetPosition?: Position;
  reasoning: string;
}

// --- WebSocket envelope ---
export type TopicType = 'world-state' | 'agent-tasks' | 'world-events' | 'saga-log';

export interface WSMessage {
  topic: TopicType;
  timestamp: number;
  payload: WorldState | AgentTask | AgentAction | WorldEvent | SagaLogEntry;
}

// --- Toast notification ---
export interface Toast {
  id: string;
  text: string;
  severity: Severity;
  eventType: EventType;
  tick: number;
}

// --- Outbound command ---
export interface WorldCommand {
  command: 'spawn_dragon' | 'start_winter' | 'rival_raid';
}
