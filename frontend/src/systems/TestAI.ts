import { Unit } from '../entities/Unit';
import { Building } from '../entities/Building';
import { ResourceNode } from '../entities/ResourceNode';
import { FactionState } from './FactionState';
import {
  Faction,
  UnitType,
  UnitState,
  BuildingType,
  ResourceType,
  BUILDING_STATS,
  UNIT_TRAIN_INFO,
} from '../types';

export class TestAI {
  private barracksPlaced: [boolean, boolean] = [false, false];

  public update(
    faction: Faction,
    units: Unit[],
    buildings: Building[],
    resourceNodes: ResourceNode[],
    factionState: FactionState,
    allUnits: Unit[],
    allBuildings: Building[],
    requestBuild: (faction: Faction, type: BuildingType, x: number, y: number) => Building | null,
    requestTrain: (building: Building, type: UnitType) => boolean,
  ): void {
    const myUnits = units.filter((u) => u.faction === faction && !u.isDead());
    const myBuildings = buildings.filter((b) => b.faction === faction && !b.isDestroyed());
    const myWorkers = myUnits.filter((u) => u.unitType === UnitType.WORKER);
    const myWarriors = myUnits.filter((u) => u.unitType === UnitType.WARRIOR);
    const enemyUnits = allUnits.filter((u) => u.faction !== faction && !u.isDead());
    const enemyBuildings = allBuildings.filter((b) => b.faction !== faction && !b.isDestroyed());

    const tc = myBuildings.find((b) => b.buildingType === BuildingType.TOWN_CENTER);
    const barracks = myBuildings.find((b) => b.buildingType === BuildingType.BARRACKS);
    const hasBarracks = barracks && barracks.built;

    // Train workers early, then prioritize warriors
    const maxWorkers = hasBarracks ? 4 : 5;
    if (tc && myWorkers.length < maxWorkers && factionState.hasPopulationRoom()) {
      if (tc.trainingQueue.length === 0 && factionState.canAfford(UNIT_TRAIN_INFO[UnitType.WORKER].cost)) {
        requestTrain(tc, UnitType.WORKER);
      }
    }

    // Train warriors if we have barracks
    if (hasBarracks && barracks && factionState.hasPopulationRoom()) {
      if (barracks.trainingQueue.length === 0 && factionState.canAfford(UNIT_TRAIN_INFO[UnitType.WARRIOR].cost)) {
        requestTrain(barracks, UnitType.WARRIOR);
      }
    }

    // Place barracks when we can afford it
    if (!this.barracksPlaced[faction] && tc) {
      const barracksCost = BUILDING_STATS[BuildingType.BARRACKS].cost;
      if (factionState.canAfford(barracksCost)) {
        const bx = faction === 0 ? tc.tileX + 4 : tc.tileX - 4;
        const by = tc.tileY;
        const built = requestBuild(faction, BuildingType.BARRACKS, bx, by);
        if (built) {
          this.barracksPlaced[faction] = true;
        }
      }
    }

    // Assign idle workers
    for (const worker of myWorkers) {
      if (worker.state !== UnitState.IDLE) continue;

      // If barracks is under construction and worker has no task, send to build
      const unfinishedBarracks = myBuildings.find(
        (b) => b.buildingType === BuildingType.BARRACKS && !b.built,
      );
      if (unfinishedBarracks && !myWorkers.some(
        (w) => w.state === UnitState.BUILDING && w.buildTargetId === unfinishedBarracks.buildingId,
      )) {
        this.assignBuild(worker, unfinishedBarracks);
        continue;
      }

      // Gather resources - prioritize what we need
      const needed = this.getNeededResource(factionState, hasBarracks ?? false);
      const node = this.findNearestResource(worker, resourceNodes, needed);
      if (node) {
        this.assignGather(worker, node);
      }
    }

    // Workers that finished returning should gather again
    for (const worker of myWorkers) {
      if (worker.state === UnitState.IDLE && worker.carryAmount === 0) {
        const needed = this.getNeededResource(factionState, hasBarracks ?? false);
        const node = this.findNearestResource(worker, resourceNodes, needed);
        if (node) {
          this.assignGather(worker, node);
        }
      }
    }

    // Warriors attack-move toward enemy
    for (const warrior of myWarriors) {
      if (warrior.state !== UnitState.IDLE) continue;

      const target = this.findNearestEnemy(warrior, enemyUnits, enemyBuildings);
      if (target) {
        warrior.state = UnitState.MOVING;
        warrior.attackTargetId = null;
        warrior.commandMove(target.x, target.y);
      }
    }
  }

  private getNeededResource(state: FactionState, hasBarracks: boolean): ResourceType {
    if (!hasBarracks) {
      if (state.resources[ResourceType.WOOD] < 100) return ResourceType.WOOD;
      if (state.resources[ResourceType.STONE] < 50) return ResourceType.STONE;
    }

    if (state.resources[ResourceType.FOOD] < 50) return ResourceType.FOOD;
    if (state.resources[ResourceType.GOLD] < 30) return ResourceType.GOLD;
    if (state.resources[ResourceType.WOOD] < 50) return ResourceType.WOOD;

    return ResourceType.FOOD;
  }

  private findNearestResource(
    unit: Unit,
    nodes: ResourceNode[],
    type: ResourceType,
  ): ResourceNode | null {
    let nearest: ResourceNode | null = null;
    let nearestDist = Infinity;

    for (const node of nodes) {
      if (node.isDepleted() || node.resourceType !== type) continue;
      const dist = unit.distanceTo(node.tileX, node.tileY);
      if (dist < nearestDist) {
        nearestDist = dist;
        nearest = node;
      }
    }

    if (!nearest) {
      for (const node of nodes) {
        if (node.isDepleted()) continue;
        const dist = unit.distanceTo(node.tileX, node.tileY);
        if (dist < nearestDist) {
          nearestDist = dist;
          nearest = node;
        }
      }
    }

    return nearest;
  }

  private findNearestEnemy(
    unit: Unit,
    enemyUnits: Unit[],
    enemyBuildings: Building[],
  ): { x: number; y: number } | null {
    let nearest: { x: number; y: number } | null = null;
    let nearestDist = Infinity;

    for (const enemy of enemyUnits) {
      const dist = unit.distanceTo(enemy.tileX, enemy.tileY);
      if (dist < nearestDist) {
        nearestDist = dist;
        nearest = { x: enemy.tileX, y: enemy.tileY };
      }
    }

    for (const enemy of enemyBuildings) {
      const dist = unit.distanceTo(enemy.tileX, enemy.tileY);
      if (dist < nearestDist) {
        nearestDist = dist;
        nearest = { x: enemy.tileX, y: enemy.tileY };
      }
    }

    return nearest;
  }

  private assignGather(worker: Unit, node: ResourceNode): void {
    worker.state = UnitState.MOVING_TO_RESOURCE;
    worker.gatherTargetId = node.nodeId;
    worker.gatherProgress = 0;
    worker.commandMove(node.tileX, node.tileY);
  }

  private assignBuild(worker: Unit, building: Building): void {
    worker.state = UnitState.MOVING_TO_BUILD;
    worker.buildTargetId = building.buildingId;
    worker.buildProgress = 0;
    worker.commandMove(building.tileX, building.tileY);
  }
}
