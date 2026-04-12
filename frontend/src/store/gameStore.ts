import { create } from 'zustand';
import type {
  WorldState, AgentAction, WorldEvent, SagaLogEntry,
  AgentSnapshot, ColonyResources, TimeOfDay, Weather,
  ThreatSnapshot, EntitySnapshot, TerrainType,
} from '../types/world';

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

  // Rolling logs (capped)
  agentActions: AgentAction[];
  worldEvents: WorldEvent[];
  sagaEntries: SagaLogEntry[];

  // Connection
  connected: boolean;

  // Actions
  applyWorldState: (ws: WorldState) => void;
  addAgentAction: (a: AgentAction) => void;
  addWorldEvent: (e: WorldEvent) => void;
  addSagaEntry: (s: SagaLogEntry) => void;
  setConnected: (c: boolean) => void;
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

  agentActions: [],
  worldEvents: [],
  sagaEntries: [],

  connected: false,

  applyWorldState: (ws) => set({
    grid: ws.grid,
    agents: ws.agents,
    entities: ws.entities,
    colonyResources: ws.colonyResources,
    timeOfDay: ws.timeOfDay,
    weather: ws.weather,
    threats: ws.threats,
    tick: ws.tick,
  }),

  addAgentAction: (a) => set((state) => ({
    agentActions: [...state.agentActions.slice(-49), a],
  })),

  addWorldEvent: (e) => set((state) => ({
    worldEvents: [...state.worldEvents.slice(-99), e],
  })),

  addSagaEntry: (s) => set((state) => ({
    sagaEntries: [...state.sagaEntries.slice(-29), s],
  })),

  setConnected: (c) => set({ connected: c }),
}));
