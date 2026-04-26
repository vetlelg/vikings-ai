import Phaser from 'phaser';
import { MAP_WIDTH, MAP_HEIGHT } from '../map/MapData';
import { TILE_SIZE } from '../map/TileTypes';
import { Faction } from '../types';
import { Unit } from '../entities/Unit';
import { Building } from '../entities/Building';

export enum Visibility {
  UNEXPLORED = 0,
  EXPLORED = 1,
  VISIBLE = 2,
}

export type FogView = 'full' | Faction;

export class FogOfWar {
  private explored: [boolean[][], boolean[][]] = [
    this.createGrid(false),
    this.createGrid(false),
  ];
  private visible: [boolean[][], boolean[][]] = [
    this.createGrid(false),
    this.createGrid(false),
  ];
  private overlay: Phaser.GameObjects.Graphics;
  private currentView: FogView = 'full';
  private dirty = true;

  constructor(private scene: Phaser.Scene) {
    this.overlay = scene.add.graphics();
    this.overlay.setDepth(10);
  }

  private createGrid(value: boolean): boolean[][] {
    const grid: boolean[][] = [];
    for (let y = 0; y < MAP_HEIGHT; y++) {
      grid[y] = [];
      for (let x = 0; x < MAP_WIDTH; x++) {
        grid[y][x] = value;
      }
    }
    return grid;
  }

  public update(units: Unit[], buildings: Building[]): void {
    if (this.currentView === 'full') {
      if (this.dirty) {
        this.overlay.clear();
        this.dirty = false;
      }
      return;
    }

    const faction = this.currentView;
    this.visible[faction] = this.createGrid(false);

    for (const unit of units) {
      if (unit.faction !== faction || unit.isDead()) continue;
      this.revealAround(faction, unit.tileX, unit.tileY, unit.stats.sightRange);
    }

    for (const building of buildings) {
      if (building.faction !== faction || building.isDestroyed()) continue;
      this.revealAround(faction, building.tileX, building.tileY, building.stats.sightRange);
    }

    this.render(faction);
  }

  private revealAround(faction: Faction, cx: number, cy: number, range: number): void {
    for (let dy = -range; dy <= range; dy++) {
      for (let dx = -range; dx <= range; dx++) {
        if (dx * dx + dy * dy > range * range) continue;
        const x = cx + dx;
        const y = cy + dy;
        if (x < 0 || x >= MAP_WIDTH || y < 0 || y >= MAP_HEIGHT) continue;
        this.visible[faction][y][x] = true;
        this.explored[faction][y][x] = true;
      }
    }
  }

  private render(faction: Faction): void {
    this.overlay.clear();

    for (let y = 0; y < MAP_HEIGHT; y++) {
      for (let x = 0; x < MAP_WIDTH; x++) {
        if (this.visible[faction][y][x]) continue;

        if (this.explored[faction][y][x]) {
          this.overlay.fillStyle(0x000000, 0.5);
        } else {
          this.overlay.fillStyle(0x000000, 0.85);
        }
        this.overlay.fillRect(x * TILE_SIZE, y * TILE_SIZE, TILE_SIZE, TILE_SIZE);
      }
    }
    this.dirty = true;
  }

  public setView(view: FogView): void {
    this.currentView = view;
    this.dirty = true;
    if (view === 'full') {
      this.overlay.clear();
    }
  }

  public getView(): FogView {
    return this.currentView;
  }

  public getVisibility(faction: Faction, x: number, y: number): Visibility {
    if (x < 0 || x >= MAP_WIDTH || y < 0 || y >= MAP_HEIGHT) return Visibility.UNEXPLORED;
    if (this.visible[faction][y][x]) return Visibility.VISIBLE;
    if (this.explored[faction][y][x]) return Visibility.EXPLORED;
    return Visibility.UNEXPLORED;
  }
}
