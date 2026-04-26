import Phaser from 'phaser';
import { TILE_SIZE } from '../map/TileTypes';
import { findPath } from '../pathfinding/AStar';
import { Faction, UnitType, UnitState, UnitStats, UNIT_STATS, ResourceType } from '../types';

const FACTION_WORKER_COLORS: [number, number] = [0x5599ee, 0xee5555];
const FACTION_WARRIOR_COLORS: [number, number] = [0x2255bb, 0xbb2222];
const MOVE_SPEED = 120;
const FLEE_HP_THRESHOLD = 0.2;

let nextUnitId = 0;

export class Unit extends Phaser.GameObjects.Container {
  public readonly unitId: number;
  public readonly unitType: UnitType;
  public readonly faction: Faction;
  public readonly stats: UnitStats;

  public tileX: number;
  public tileY: number;
  public hp: number;
  public state: UnitState = UnitState.IDLE;

  public carryAmount = 0;
  public carryType: ResourceType | null = null;
  public gatherTargetId: number | null = null;
  public gatherProgress = 0;
  public buildTargetId: number | null = null;
  public buildProgress = 0;
  public attackTargetId: number | null = null;
  public attackTargetIsBuilding = false;
  public attackCooldown = 0;
  public moveTargetX: number | null = null;
  public moveTargetY: number | null = null;

  private path: { x: number; y: number }[] = [];
  private moving = false;
  private body_gfx: Phaser.GameObjects.Arc;
  private hpBar: Phaser.GameObjects.Graphics;

  constructor(scene: Phaser.Scene, tileX: number, tileY: number, faction: Faction, unitType: UnitType) {
    const worldX = tileX * TILE_SIZE + TILE_SIZE / 2;
    const worldY = tileY * TILE_SIZE + TILE_SIZE / 2;
    super(scene, worldX, worldY);

    this.unitId = nextUnitId++;
    this.unitType = unitType;
    this.faction = faction;
    this.stats = UNIT_STATS[unitType];
    this.tileX = tileX;
    this.tileY = tileY;
    this.hp = this.stats.maxHp;

    const color = unitType === UnitType.WORKER
      ? FACTION_WORKER_COLORS[faction]
      : FACTION_WARRIOR_COLORS[faction];
    const radius = unitType === UnitType.WORKER ? TILE_SIZE * 0.3 : TILE_SIZE * 0.38;

    this.body_gfx = new Phaser.GameObjects.Arc(scene, 0, 0, radius, 0, 360, false, color);
    this.body_gfx.setStrokeStyle(1, 0xffffff);
    this.add(this.body_gfx);

    this.hpBar = new Phaser.GameObjects.Graphics(scene);
    this.add(this.hpBar);
    this.updateHpBar();

    this.setDepth(2);
    scene.add.existing(this);
  }

  private updateHpBar(): void {
    this.hpBar.clear();
    if (this.hp >= this.stats.maxHp) return;

    const barWidth = TILE_SIZE * 0.7;
    const barHeight = 3;
    const x = -barWidth / 2;
    const y = -TILE_SIZE * 0.45;

    this.hpBar.fillStyle(0x333333, 0.8);
    this.hpBar.fillRect(x, y, barWidth, barHeight);

    const hpRatio = this.hp / this.stats.maxHp;
    const color = hpRatio > 0.5 ? 0x00cc00 : hpRatio > 0.25 ? 0xcccc00 : 0xcc0000;
    this.hpBar.fillStyle(color, 1);
    this.hpBar.fillRect(x, y, barWidth * hpRatio, barHeight);
  }

  public takeDamage(amount: number): boolean {
    this.hp = Math.max(0, this.hp - amount);
    this.updateHpBar();
    return this.hp <= 0;
  }

  public heal(amount: number): void {
    this.hp = Math.min(this.stats.maxHp, this.hp + amount);
    this.updateHpBar();
  }

  public shouldFlee(): boolean {
    return this.unitType === UnitType.WORKER && this.hp / this.stats.maxHp < FLEE_HP_THRESHOLD;
  }

  public commandMove(targetX: number, targetY: number, occupiedTiles?: Set<string>): boolean {
    const path = findPath(this.tileX, this.tileY, targetX, targetY, occupiedTiles);
    if (!path || path.length === 0) return false;

    this.path = path;
    this.moveTargetX = targetX;
    this.moveTargetY = targetY;
    if (!this.moving) {
      this.moving = true;
      this.moveToNextTile();
    }
    return true;
  }

  public isMoving(): boolean {
    return this.moving;
  }

  public isAdjacentTo(tx: number, ty: number): boolean {
    return Math.abs(this.tileX - tx) <= 1 && Math.abs(this.tileY - ty) <= 1
      && !(this.tileX === tx && this.tileY === ty);
  }

  public distanceTo(tx: number, ty: number): number {
    return Math.max(Math.abs(this.tileX - tx), Math.abs(this.tileY - ty));
  }

  public stopMoving(): void {
    this.path = [];
    this.moving = false;
    if (this.scene) {
      this.scene.tweens.killTweensOf(this);
    }
    this.x = this.tileX * TILE_SIZE + TILE_SIZE / 2;
    this.y = this.tileY * TILE_SIZE + TILE_SIZE / 2;
  }

  public resetTask(): void {
    this.stopMoving();
    this.state = UnitState.IDLE;
    this.gatherTargetId = null;
    this.gatherProgress = 0;
    this.buildTargetId = null;
    this.buildProgress = 0;
    this.attackTargetId = null;
    this.attackTargetIsBuilding = false;
    this.attackCooldown = 0;
    this.moveTargetX = null;
    this.moveTargetY = null;
  }

  public isDead(): boolean {
    return this.hp <= 0;
  }

  private moveToNextTile(): void {
    if (this.path.length === 0 || !this.scene || !this.active) {
      this.moving = false;
      return;
    }

    const next = this.path.shift()!;
    const targetWorldX = next.x * TILE_SIZE + TILE_SIZE / 2;
    const targetWorldY = next.y * TILE_SIZE + TILE_SIZE / 2;

    const dx = targetWorldX - this.x;
    const dy = targetWorldY - this.y;
    const distance = Math.sqrt(dx * dx + dy * dy);
    const duration = (distance / MOVE_SPEED) * 1000;

    this.scene.tweens.add({
      targets: this,
      x: targetWorldX,
      y: targetWorldY,
      duration,
      ease: 'Linear',
      onComplete: () => {
        if (!this.scene || !this.active) {
          this.moving = false;
          return;
        }
        this.tileX = next.x;
        this.tileY = next.y;
        this.moveToNextTile();
      },
    });
  }
}
