import Phaser from 'phaser';
import { TILE_SIZE } from '../map/TileTypes';
import { Faction, BuildingType, BuildingStats, BUILDING_STATS, UnitType, UNIT_TRAIN_INFO } from '../types';

const FACTION_BUILDING_COLORS: Record<BuildingType, [number, number]> = {
  [BuildingType.TOWN_CENTER]: [0x3366cc, 0xcc3333],
  [BuildingType.BARRACKS]: [0x224488, 0x882222],
};

let nextBuildingId = 0;

export interface TrainingQueueItem {
  unitType: UnitType;
  remainingTicks: number;
}

export class Building extends Phaser.GameObjects.Container {
  public readonly buildingId: number;
  public readonly buildingType: BuildingType;
  public readonly faction: Faction;
  public readonly stats: BuildingStats;
  public readonly tileX: number;
  public readonly tileY: number;

  public hp: number;
  public constructionProgress: number;
  public built: boolean;
  public trainingQueue: TrainingQueueItem[] = [];

  private body_gfx: Phaser.GameObjects.Rectangle;
  private hpBar: Phaser.GameObjects.Graphics;
  private constructionOverlay: Phaser.GameObjects.Rectangle | null = null;

  constructor(
    scene: Phaser.Scene,
    tileX: number,
    tileY: number,
    faction: Faction,
    buildingType: BuildingType,
    preBuilt = true,
  ) {
    const worldX = tileX * TILE_SIZE + TILE_SIZE / 2;
    const worldY = tileY * TILE_SIZE + TILE_SIZE / 2;
    super(scene, worldX, worldY);

    this.buildingId = nextBuildingId++;
    this.buildingType = buildingType;
    this.faction = faction;
    this.stats = BUILDING_STATS[buildingType];
    this.tileX = tileX;
    this.tileY = tileY;

    this.built = preBuilt;
    this.constructionProgress = preBuilt ? this.stats.buildTicks : 0;
    this.hp = preBuilt ? this.stats.maxHp : 1;

    const colors = FACTION_BUILDING_COLORS[buildingType];
    const size = buildingType === BuildingType.TOWN_CENTER ? TILE_SIZE * 2 : TILE_SIZE * 1.6;

    this.body_gfx = new Phaser.GameObjects.Rectangle(scene, 0, 0, size, size, colors[faction]);
    this.body_gfx.setStrokeStyle(2, 0xffffff);
    this.add(this.body_gfx);

    this.hpBar = new Phaser.GameObjects.Graphics(scene);
    this.add(this.hpBar);

    if (!preBuilt) {
      this.constructionOverlay = new Phaser.GameObjects.Rectangle(
        scene, 0, 0, size, size, 0x000000, 0.5,
      );
      this.add(this.constructionOverlay);
      this.updateConstructionVisual();
    }

    this.setDepth(1);
    scene.add.existing(this);
  }

  public advanceConstruction(ticks: number): boolean {
    if (this.built) return true;

    this.constructionProgress += ticks;
    if (this.constructionProgress >= this.stats.buildTicks) {
      this.constructionProgress = this.stats.buildTicks;
      this.built = true;
      this.hp = this.stats.maxHp;
      if (this.constructionOverlay) {
        this.constructionOverlay.destroy();
        this.constructionOverlay = null;
      }
      return true;
    }

    this.updateConstructionVisual();
    return false;
  }

  private updateConstructionVisual(): void {
    if (!this.constructionOverlay || this.built) return;
    const ratio = this.constructionProgress / this.stats.buildTicks;
    this.constructionOverlay.setAlpha(0.5 * (1 - ratio));
  }

  public queueUnit(unitType: UnitType): boolean {
    if (!this.built) return false;
    if (!this.stats.trains.includes(unitType)) return false;

    const trainInfo = UNIT_TRAIN_INFO[unitType];
    this.trainingQueue.push({
      unitType,
      remainingTicks: trainInfo.trainTicks,
    });
    return true;
  }

  public advanceTraining(): UnitType | null {
    if (!this.built || this.trainingQueue.length === 0) return null;

    const front = this.trainingQueue[0];
    front.remainingTicks--;

    if (front.remainingTicks <= 0) {
      this.trainingQueue.shift();
      return front.unitType;
    }

    return null;
  }

  public takeDamage(amount: number): boolean {
    this.hp = Math.max(0, this.hp - amount);
    this.updateHpBar();
    return this.hp <= 0;
  }

  private updateHpBar(): void {
    this.hpBar.clear();
    if (this.hp >= this.stats.maxHp) return;

    const barWidth = TILE_SIZE * 1.2;
    const barHeight = 3;
    const x = -barWidth / 2;
    const y = -TILE_SIZE * 1.1;

    this.hpBar.fillStyle(0x333333, 0.8);
    this.hpBar.fillRect(x, y, barWidth, barHeight);

    const hpRatio = this.hp / this.stats.maxHp;
    const color = hpRatio > 0.5 ? 0x00cc00 : hpRatio > 0.25 ? 0xcccc00 : 0xcc0000;
    this.hpBar.fillStyle(color, 1);
    this.hpBar.fillRect(x, y, barWidth * hpRatio, barHeight);
  }

  public isDestroyed(): boolean {
    return this.hp <= 0;
  }

  public getOccupiedTiles(): { x: number; y: number }[] {
    return [
      { x: this.tileX, y: this.tileY },
      { x: this.tileX + 1, y: this.tileY },
      { x: this.tileX, y: this.tileY + 1 },
      { x: this.tileX + 1, y: this.tileY + 1 },
    ];
  }

  public getSpawnTile(): { x: number; y: number } {
    return { x: this.tileX + 2, y: this.tileY + 1 };
  }
}
