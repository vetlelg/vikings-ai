import Phaser from 'phaser';
import { TILE_SIZE, TileType } from '../map/TileTypes';
import { ResourceType, RESOURCE_NODE_AMOUNTS } from '../types';

const TILE_TO_RESOURCE: Partial<Record<TileType, ResourceType>> = {
  [TileType.FOREST]: ResourceType.WOOD,
  [TileType.STONE]: ResourceType.STONE,
  [TileType.GOLD]: ResourceType.GOLD,
  [TileType.BERRIES]: ResourceType.FOOD,
};

export function tileToResourceType(tile: TileType): ResourceType | null {
  return TILE_TO_RESOURCE[tile] ?? null;
}

let nextNodeId = 0;

export class ResourceNode extends Phaser.GameObjects.Rectangle {
  public readonly nodeId: number;
  public readonly resourceType: ResourceType;
  public readonly tileX: number;
  public readonly tileY: number;
  public remaining: number;

  constructor(scene: Phaser.Scene, tileX: number, tileY: number, resourceType: ResourceType) {
    const worldX = tileX * TILE_SIZE + TILE_SIZE / 2;
    const worldY = tileY * TILE_SIZE + TILE_SIZE / 2;
    super(scene, worldX, worldY, TILE_SIZE * 0.6, TILE_SIZE * 0.6);

    this.nodeId = nextNodeId++;
    this.resourceType = resourceType;
    this.tileX = tileX;
    this.tileY = tileY;
    this.remaining = RESOURCE_NODE_AMOUNTS[resourceType];

    this.setStrokeStyle(1, 0xffffff, 0.3);
    this.setFillStyle(0x000000, 0);
    this.setDepth(0.5);

    scene.add.existing(this);
  }

  public harvest(amount: number): number {
    const taken = Math.min(amount, this.remaining);
    this.remaining -= taken;
    if (this.remaining <= 0) {
      this.destroy();
    }
    return taken;
  }

  public isDepleted(): boolean {
    return this.remaining <= 0;
  }
}
