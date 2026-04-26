import { Unit } from '../entities/Unit';
import { Building } from '../entities/Building';
import { ResourceNode } from '../entities/ResourceNode';
import { FactionState } from './FactionState';
import { WebSocketClient } from './WebSocketClient';
import { Faction, UNIT_TRAIN_INFO } from '../types';

const BROADCAST_INTERVAL_TICKS = 5; // Every 5 sim ticks = ~2/sec at 10 ticks/sec

export class StateBroadcaster {
  private tickCounter = 0;

  constructor(private ws: WebSocketClient) {}

  public maybeBroadcast(
    tick: number,
    units: Unit[],
    buildings: Building[],
    resourceNodes: ResourceNode[],
    factionStates: [FactionState, FactionState],
  ): void {
    this.tickCounter++;
    if (this.tickCounter < BROADCAST_INTERVAL_TICKS) return;
    this.tickCounter = 0;

    if (!this.ws.isConnected()) return;

    const snapshot = {
      tick,
      factions: [0, 1].map((f) => this.serializeFaction(
        f as Faction, units, buildings, factionStates[f],
      )),
      resourceNodes: resourceNodes.map((n) => ({
        nodeId: n.nodeId,
        resourceType: n.resourceType,
        tileX: n.tileX,
        tileY: n.tileY,
        remaining: n.remaining,
      })),
    };

    this.ws.send('game-state', snapshot);
    if (tick % 50 === 0) {
      console.log(`[State] Broadcast tick ${tick} (${snapshot.factions[0].units.length}+${snapshot.factions[1].units.length} units)`);
    }
  }

  public broadcastEvent(event: {
    tick: number;
    eventType: string;
    faction?: string;
    unitId?: number;
    buildingId?: number;
    targetId?: number;
    position?: [number, number];
    description?: string;
  }): void {
    if (!this.ws.isConnected()) return;
    this.ws.send('game-events', event);
  }

  private serializeFaction(
    faction: Faction,
    units: Unit[],
    buildings: Building[],
    state: FactionState,
  ) {
    const factionName = faction === 0 ? 'blue' : 'red';
    const myUnits = units.filter((u) => u.faction === faction);
    const myBuildings = buildings.filter((b) => b.faction === faction);

    return {
      faction: factionName,
      resources: {
        wood: state.resources.wood,
        stone: state.resources.stone,
        gold: state.resources.gold,
        food: state.resources.food,
      },
      population: state.population,
      units: myUnits.map((u) => ({
        unitId: u.unitId,
        unitType: u.unitType,
        tileX: u.tileX,
        tileY: u.tileY,
        hp: u.hp,
        maxHp: u.stats.maxHp,
        state: u.state,
        carryType: u.carryType,
        carryAmount: u.carryAmount,
      })),
      buildings: myBuildings.map((b) => ({
        buildingId: b.buildingId,
        buildingType: b.buildingType,
        tileX: b.tileX,
        tileY: b.tileY,
        hp: b.hp,
        maxHp: b.stats.maxHp,
        built: b.built,
        constructionProgress: b.stats.buildTicks > 0
          ? b.constructionProgress / b.stats.buildTicks
          : 1.0,
        trainingQueue: b.trainingQueue.map((q) => ({
          unitType: q.unitType,
          progress: 1 - (q.remainingTicks / UNIT_TRAIN_INFO[q.unitType].trainTicks),
        })),
      })),
    };
  }
}
