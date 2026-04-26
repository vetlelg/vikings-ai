import { Faction, ResourceType, POPULATION_CAP } from '../types';

export class FactionState {
  public readonly faction: Faction;
  public resources: Record<ResourceType, number> = {
    [ResourceType.WOOD]: 0,
    [ResourceType.STONE]: 0,
    [ResourceType.GOLD]: 0,
    [ResourceType.FOOD]: 50,
  };
  public population = 3;

  constructor(faction: Faction) {
    this.faction = faction;
  }

  public canAfford(cost: Partial<Record<ResourceType, number>>): boolean {
    for (const [resource, amount] of Object.entries(cost)) {
      if ((this.resources[resource as ResourceType] ?? 0) < (amount ?? 0)) {
        return false;
      }
    }
    return true;
  }

  public spend(cost: Partial<Record<ResourceType, number>>): boolean {
    if (!this.canAfford(cost)) return false;
    for (const [resource, amount] of Object.entries(cost)) {
      this.resources[resource as ResourceType] -= amount ?? 0;
    }
    return true;
  }

  public addResource(type: ResourceType, amount: number): void {
    this.resources[type] += amount;
  }

  public hasPopulationRoom(): boolean {
    return this.population < POPULATION_CAP;
  }
}
