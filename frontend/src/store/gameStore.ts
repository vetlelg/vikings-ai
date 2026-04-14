import { create } from 'zustand';
import type {
  WorldState, AgentAction, AgentTask, WorldEvent, SagaLogEntry, Toast,
  AgentSnapshot, ColonyResources, TimeOfDay, Weather,
  ThreatSnapshot, EntitySnapshot, TerrainType, Position, GameStatus,
} from '../types/world';

const TOAST_EVENT_TYPES = new Set([
  'DRAGON_SIGHTED', 'DRAGON_DEFEATED', 'AGENT_DIED',
  'RAID_INCOMING', 'WOLF_SPOTTED', 'LONGSHIP_COMPLETE',
]);

interface GameState {
  // Latest world snapshot
  grid: TerrainType[][];
  agents: AgentSnapshot[];
  entities: EntitySnapshot[];
  colonyResources: ColonyResources;
  timeOfDay: TimeOfDay;
  weather: Weather;
  threats: ThreatSnapshot[];
  tick: number;
  gameStatus: GameStatus;
  voyageGoal: ColonyResources;

  // Rolling logs (capped)
  agentTasks: AgentTask[];
  worldEvents: WorldEvent[];
  sagaEntries: SagaLogEntry[];

  // Latest task per agent (for action bubbles)
  latestTaskByAgent: Record<string, AgentTask>;

  // Selected agent (for inspector)
  selectedAgent: string | null;

  // Movement trails (last N previous positions per agent)
  agentTrails: Record<string, Position[]>;

  // Full movement history for mini-map (capped at 500)
  agentFullTrails: Record<string, Position[]>;

  // Toast notifications
  toasts: Toast[];

  // Connection
  connected: boolean;

  // Actions
  applyWorldState: (ws: WorldState) => void;
  addAgentTask: (t: AgentTask) => void;
  addWorldEvent: (e: WorldEvent) => void;
  addSagaEntry: (s: SagaLogEntry) => void;
  setConnected: (c: boolean) => void;
  setSelectedAgent: (name: string | null) => void;
  removeToast: (id: string) => void;
}

export const useGameStore = create<GameState>((set) => ({
  grid: [],
  agents: [],
  entities: [],
  colonyResources: { timber: 0, fish: 0, iron: 0, furs: 0 },
  timeOfDay: 'DAWN',
  weather: 'CLEAR',
  threats: [],
  tick: 0,
  gameStatus: 'IN_PROGRESS',
  voyageGoal: { timber: 50, fish: 0, iron: 30, furs: 20 },

  agentTasks: [],
  worldEvents: [],
  sagaEntries: [],

  latestTaskByAgent: {},
  selectedAgent: null,
  agentTrails: {},
  agentFullTrails: {},
  toasts: [],

  connected: false,

  applyWorldState: (ws) => set((state) => {
    const newTrails: Record<string, Position[]> = {};
    const newFullTrails: Record<string, Position[]> = {};
    for (const agent of ws.agents) {
      const prevAgent = state.agents.find((a) => a.name === agent.name);
      const prevTrail = state.agentTrails[agent.name] || [];
      const prevFull = state.agentFullTrails[agent.name] || [];
      const moved = prevAgent &&
          (prevAgent.position.x !== agent.position.x || prevAgent.position.y !== agent.position.y);
      if (moved) {
        newTrails[agent.name] = [...prevTrail.slice(-3), prevAgent!.position];
        newFullTrails[agent.name] = [...prevFull.slice(-499), prevAgent!.position];
      } else {
        newTrails[agent.name] = prevTrail;
        newFullTrails[agent.name] = prevFull;
      }
    }
    return {
      grid: ws.grid,
      agents: ws.agents,
      entities: ws.entities,
      colonyResources: ws.colonyResources,
      timeOfDay: ws.timeOfDay,
      weather: ws.weather,
      threats: ws.threats,
      tick: ws.tick,
      gameStatus: ws.gameStatus,
      voyageGoal: ws.voyageGoal,
      agentTrails: newTrails,
      agentFullTrails: newFullTrails,
    };
  }),

  addAgentTask: (t) => set((state) => ({
    agentTasks: [...state.agentTasks.slice(-49), t],
    latestTaskByAgent: { ...state.latestTaskByAgent, [t.agentName]: t },
  })),

  addWorldEvent: (e) => set((state) => {
    const newEvents = [...state.worldEvents.slice(-99), e];
    if (TOAST_EVENT_TYPES.has(e.eventType)) {
      const toast: Toast = {
        id: `${e.tick}-${e.eventType}-${Date.now()}`,
        text: e.description,
        severity: e.severity,
        eventType: e.eventType,
        tick: e.tick,
      };
      return { worldEvents: newEvents, toasts: [...state.toasts.slice(-4), toast] };
    }
    return { worldEvents: newEvents };
  }),

  addSagaEntry: (s) => set((state) => ({
    sagaEntries: [...state.sagaEntries.slice(-29), s],
  })),

  setConnected: (c) => set({ connected: c }),

  setSelectedAgent: (name) => set({ selectedAgent: name }),

  removeToast: (id) => set((state) => ({
    toasts: state.toasts.filter((t) => t.id !== id),
  })),
}));
