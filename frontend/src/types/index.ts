export type Faction = 0 | 1;

export enum UnitType {
  WORKER = 'worker',
  WARRIOR = 'warrior',
}

export enum BuildingType {
  TOWN_CENTER = 'townCenter',
  BARRACKS = 'barracks',
}

export enum ResourceType {
  WOOD = 'wood',
  STONE = 'stone',
  GOLD = 'gold',
  FOOD = 'food',
}

export enum UnitState {
  IDLE = 'idle',
  MOVING = 'moving',
  MOVING_TO_RESOURCE = 'movingToResource',
  GATHERING = 'gathering',
  RETURNING = 'returning',
  MOVING_TO_BUILD = 'movingToBuild',
  BUILDING = 'building',
  ATTACKING = 'attacking',
  FLEEING = 'fleeing',
}

export interface UnitStats {
  maxHp: number;
  attack: number;
  attackCooldownTicks: number;
  sightRange: number;
  gatherTicks: Record<ResourceType, number>;
  carryCapacity: number;
}

export interface BuildingStats {
  maxHp: number;
  sightRange: number;
  cost: Partial<Record<ResourceType, number>>;
  buildTicks: number;
  trains: UnitType[];
}

export interface UnitTrainInfo {
  cost: Partial<Record<ResourceType, number>>;
  trainTicks: number;
}

// 10 ticks per second
export const UNIT_STATS: Record<UnitType, UnitStats> = {
  [UnitType.WORKER]: {
    maxHp: 30,
    attack: 3,
    attackCooldownTicks: 15,
    sightRange: 8,
    gatherTicks: {
      [ResourceType.WOOD]: 30,
      [ResourceType.STONE]: 40,
      [ResourceType.GOLD]: 50,
      [ResourceType.FOOD]: 20,
    },
    carryCapacity: 10,
  },
  [UnitType.WARRIOR]: {
    maxHp: 60,
    attack: 10,
    attackCooldownTicks: 10,
    sightRange: 8,
    gatherTicks: {
      [ResourceType.WOOD]: 0,
      [ResourceType.STONE]: 0,
      [ResourceType.GOLD]: 0,
      [ResourceType.FOOD]: 0,
    },
    carryCapacity: 0,
  },
};

export const BUILDING_STATS: Record<BuildingType, BuildingStats> = {
  [BuildingType.TOWN_CENTER]: {
    maxHp: 500,
    sightRange: 10,
    cost: {},
    buildTicks: 0,
    trains: [UnitType.WORKER],
  },
  [BuildingType.BARRACKS]: {
    maxHp: 300,
    sightRange: 8,
    cost: { [ResourceType.WOOD]: 100, [ResourceType.STONE]: 50 },
    buildTicks: 300,
    trains: [UnitType.WARRIOR],
  },
};

export const UNIT_TRAIN_INFO: Record<UnitType, UnitTrainInfo> = {
  [UnitType.WORKER]: {
    cost: { [ResourceType.FOOD]: 50 },
    trainTicks: 100,
  },
  [UnitType.WARRIOR]: {
    cost: { [ResourceType.FOOD]: 60, [ResourceType.GOLD]: 30 },
    trainTicks: 150,
  },
};

export const POPULATION_CAP = 10;

export const RESOURCE_NODE_AMOUNTS: Record<ResourceType, number> = {
  [ResourceType.WOOD]: 100,
  [ResourceType.STONE]: 200,
  [ResourceType.GOLD]: 200,
  [ResourceType.FOOD]: 100,
};
