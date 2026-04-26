import Phaser from 'phaser';
import { MAP_DATA, MAP_WIDTH, MAP_HEIGHT, FACTION_STARTS } from '../map/MapData';
import { TILE_SIZE, TILE_COLORS, TileType } from '../map/TileTypes';
import { Unit } from '../entities/Unit';
import { Building } from '../entities/Building';
import { ResourceNode, tileToResourceType } from '../entities/ResourceNode';
import { GameClock } from '../systems/GameClock';
import { FactionState } from '../systems/FactionState';
import { FogOfWar, FogView } from '../systems/FogOfWar';
import { TestAI } from '../systems/TestAI';
import { WebSocketClient } from '../systems/WebSocketClient';
import { StateBroadcaster } from '../systems/StateBroadcaster';
import { DecisionReceiver, UnitDecision, CommanderOrder } from '../systems/DecisionReceiver';
import {
  Faction,
  UnitType,
  UnitState,
  BuildingType,
  BUILDING_STATS,
  UNIT_TRAIN_INFO,
  ResourceType,
} from '../types';

export class GameScene extends Phaser.Scene {
  private units: Unit[] = [];
  private buildings: Building[] = [];
  private resourceNodes: ResourceNode[] = [];
  private factionStates: [FactionState, FactionState] = [
    new FactionState(0),
    new FactionState(1),
  ];
  private gameClock = new GameClock();
  private fogOfWar!: FogOfWar;
  private testAI = new TestAI();
  private wsClient = new WebSocketClient('ws://localhost:8080/ws');
  private stateBroadcaster = new StateBroadcaster(this.wsClient);
  private decisionReceiver = new DecisionReceiver(this.wsClient);
  private useBackendAgents = false;
  private gameOver = false;
  private winner: Faction | null = null;

  private selectedUnit: Unit | null = null;
  private selectedBuilding: Building | null = null;
  private selectedResource: ResourceNode | null = null;
  private selectionIndicator: Phaser.GameObjects.Arc | Phaser.GameObjects.Rectangle | null = null;
  private isDragging = false;
  private dragStartX = 0;
  private dragStartY = 0;
  private lastPointerX = 0;
  private lastPointerY = 0;

  private uiText!: Phaser.GameObjects.Text;
  private inspectText!: Phaser.GameObjects.Text;
  private mapGraphics!: Phaser.GameObjects.Graphics;

  constructor() {
    super({ key: 'GameScene' });
  }

  create(): void {
    this.mapGraphics = this.add.graphics();
    this.renderMap();
    this.spawnResourceNodes();
    this.spawnFactions();
    this.fogOfWar = new FogOfWar(this);
    this.setupCamera();
    this.setupInput();
    this.createUI();

    this.wsClient.connect();

    this.gameClock.onTick((tick) => {
      if (!this.gameOver) this.simulationTick(tick);
    });
  }

  private renderMap(): void {
    for (let y = 0; y < MAP_HEIGHT; y++) {
      for (let x = 0; x < MAP_WIDTH; x++) {
        const tile = MAP_DATA[y][x];
        const color = TILE_COLORS[tile];
        this.mapGraphics.fillStyle(color, 1);
        this.mapGraphics.fillRect(x * TILE_SIZE, y * TILE_SIZE, TILE_SIZE, TILE_SIZE);

        this.mapGraphics.lineStyle(1, 0x000000, 0.1);
        this.mapGraphics.strokeRect(x * TILE_SIZE, y * TILE_SIZE, TILE_SIZE, TILE_SIZE);
      }
    }
  }

  private spawnResourceNodes(): void {
    for (let y = 0; y < MAP_HEIGHT; y++) {
      for (let x = 0; x < MAP_WIDTH; x++) {
        const resType = tileToResourceType(MAP_DATA[y][x]);
        if (resType) {
          this.resourceNodes.push(new ResourceNode(this, x, y, resType));
        }
      }
    }
  }

  private spawnFactions(): void {
    for (let f = 0; f < 2; f++) {
      const faction = f as Faction;
      const start = FACTION_STARTS[f];

      const tc = new Building(this, start.townCenterX, start.townCenterY, faction, BuildingType.TOWN_CENTER, true);
      this.buildings.push(tc);

      for (const pos of start.workerPositions) {
        const unit = new Unit(this, pos.x, pos.y, faction, UnitType.WORKER);
        this.units.push(unit);
      }
    }
  }

  private setupCamera(): void {
    const worldWidth = MAP_WIDTH * TILE_SIZE;
    const worldHeight = MAP_HEIGHT * TILE_SIZE;

    this.cameras.main.setBounds(0, 0, worldWidth, worldHeight);
    this.cameras.main.centerOn(worldWidth / 2, worldHeight / 2);

    this.input.on('wheel', (_pointer: Phaser.Input.Pointer, _gameObjects: unknown[], _deltaX: number, deltaY: number) => {
      const cam = this.cameras.main;
      const newZoom = Phaser.Math.Clamp(cam.zoom + (deltaY > 0 ? -0.1 : 0.1), 0.5, 3);
      cam.setZoom(newZoom);
    });
  }

  private setupInput(): void {
    this.input.on('pointerdown', (pointer: Phaser.Input.Pointer) => {
      this.isDragging = false;
      this.dragStartX = pointer.x;
      this.dragStartY = pointer.y;
      this.lastPointerX = pointer.x;
      this.lastPointerY = pointer.y;
    });

    this.input.on('pointermove', (pointer: Phaser.Input.Pointer) => {
      if (!pointer.isDown) return;

      const dx = pointer.x - this.dragStartX;
      const dy = pointer.y - this.dragStartY;
      if (Math.abs(dx) > 4 || Math.abs(dy) > 4) {
        this.isDragging = true;
      }

      if (this.isDragging) {
        const cam = this.cameras.main;
        cam.scrollX -= (pointer.x - this.lastPointerX) / cam.zoom;
        cam.scrollY -= (pointer.y - this.lastPointerY) / cam.zoom;
      }

      this.lastPointerX = pointer.x;
      this.lastPointerY = pointer.y;
    });

    this.input.on('pointerup', (pointer: Phaser.Input.Pointer) => {
      if (this.isDragging) return;

      const worldPoint = this.cameras.main.getWorldPoint(pointer.x, pointer.y);
      const tileX = Math.floor(worldPoint.x / TILE_SIZE);
      const tileY = Math.floor(worldPoint.y / TILE_SIZE);

      if (pointer.button === 0) {
        this.handleLeftClick(tileX, tileY);
      } else if (pointer.button === 2) {
        this.handleRightClick(tileX, tileY);
      }
    });

    this.input.mouse?.disableContextMenu();

    this.input.keyboard?.on('keydown-F', () => {
      const views: FogView[] = ['full', 0, 1];
      const currentIdx = views.indexOf(this.fogOfWar.getView());
      this.fogOfWar.setView(views[(currentIdx + 1) % views.length]);
    });

    this.input.keyboard?.on('keydown-SPACE', () => {
      this.gameClock.setPaused(!this.gameClock.isPaused());
    });

    this.input.keyboard?.on('keydown-B', () => {
      this.useBackendAgents = !this.useBackendAgents;
      console.log(`AI mode: ${this.useBackendAgents ? 'backend agents' : 'local test AI'}`);
    });
  }

  private handleLeftClick(tileX: number, tileY: number): void {
    const clickedUnit = this.units.find(
      (u) => u.tileX === tileX && u.tileY === tileY && !u.isDead(),
    );
    if (clickedUnit) {
      this.selectEntity(clickedUnit, null, null);
      return;
    }

    const clickedBuilding = this.buildings.find(
      (b) => !b.isDestroyed()
        && tileX >= b.tileX && tileX <= b.tileX + 1
        && tileY >= b.tileY && tileY <= b.tileY + 1,
    );
    if (clickedBuilding) {
      this.selectEntity(null, clickedBuilding, null);
      return;
    }

    const clickedResource = this.resourceNodes.find(
      (r) => r.tileX === tileX && r.tileY === tileY && !r.isDepleted(),
    );
    if (clickedResource) {
      this.selectEntity(null, null, clickedResource);
      return;
    }

    this.clearSelection();
  }

  private handleRightClick(tileX: number, tileY: number): void {
    if (!this.selectedUnit) return;

    const occupiedTiles = this.getOccupiedTiles();
    occupiedTiles.delete(`${this.selectedUnit.tileX},${this.selectedUnit.tileY}`);

    this.selectedUnit.resetTask();
    this.selectedUnit.state = UnitState.MOVING;
    this.selectedUnit.commandMove(tileX, tileY, occupiedTiles);
  }

  private selectEntity(unit: Unit | null, building: Building | null, resource: ResourceNode | null): void {
    this.clearSelection();

    this.selectedUnit = unit;
    this.selectedBuilding = building;
    this.selectedResource = resource;

    if (unit) {
      const indicator = this.add.arc(unit.x, unit.y, TILE_SIZE * 0.45, 0, 360, false);
      indicator.setStrokeStyle(2, 0x00ff00);
      indicator.setFillStyle(0x00ff00, 0);
      indicator.setDepth(3);
      this.selectionIndicator = indicator;
    } else if (building) {
      const size = building.buildingType === BuildingType.TOWN_CENTER ? TILE_SIZE * 2.2 : TILE_SIZE * 1.8;
      const indicator = this.add.rectangle(building.x, building.y, size, size);
      indicator.setStrokeStyle(2, 0x00ff00);
      indicator.setFillStyle(0x00ff00, 0);
      indicator.setDepth(3);
      this.selectionIndicator = indicator;
    } else if (resource) {
      const wx = resource.tileX * TILE_SIZE + TILE_SIZE / 2;
      const wy = resource.tileY * TILE_SIZE + TILE_SIZE / 2;
      const indicator = this.add.arc(wx, wy, TILE_SIZE * 0.45, 0, 360, false);
      indicator.setStrokeStyle(2, 0x00ff00);
      indicator.setFillStyle(0x00ff00, 0);
      indicator.setDepth(3);
      this.selectionIndicator = indicator;
    }
  }

  private clearSelection(): void {
    this.selectedUnit = null;
    this.selectedBuilding = null;
    this.selectedResource = null;
    if (this.selectionIndicator) {
      this.selectionIndicator.destroy();
      this.selectionIndicator = null;
    }
  }

  private createUI(): void {
    this.uiText = this.add.text(10, 10, '', {
      fontSize: '14px',
      color: '#ffffff',
      backgroundColor: '#000000aa',
      padding: { x: 8, y: 6 },
    });
    this.uiText.setScrollFactor(0);
    this.uiText.setDepth(20);

    this.inspectText = this.add.text(10, 0, '', {
      fontSize: '13px',
      color: '#ffffff',
      backgroundColor: '#000000cc',
      padding: { x: 8, y: 6 },
    });
    this.inspectText.setScrollFactor(0);
    this.inspectText.setDepth(20);
    this.inspectText.setVisible(false);
  }

  private updateUI(): void {
    const s0 = this.factionStates[0];
    const s1 = this.factionStates[1];
    const fogView = this.fogOfWar.getView();
    const fogLabel = fogView === 'full' ? 'Full' : `Faction ${fogView === 0 ? 'Blue' : 'Red'}`;
    const pauseLabel = this.gameClock.isPaused() ? ' [PAUSED]' : '';
    const tickLabel = `Tick: ${this.gameClock.getTick()}`;

    const wsLabel = this.wsClient.isConnected() ? 'WS: connected' : 'WS: disconnected';
    const aiLabel = this.useBackendAgents ? 'AI: backend' : 'AI: local';
    let text = `${tickLabel}${pauseLabel}  |  ${wsLabel}  |  ${aiLabel}  |  Fog: ${fogLabel} (F)  |  Space: pause  |  B: toggle AI\n`;
    text += `Blue — W:${s0.resources.wood} S:${s0.resources.stone} G:${s0.resources.gold} F:${s0.resources.food} | Pop: ${s0.population}/10\n`;
    text += `Red  — W:${s1.resources.wood} S:${s1.resources.stone} G:${s1.resources.gold} F:${s1.resources.food} | Pop: ${s1.population}/10`;

    if (this.gameOver && this.winner !== null) {
      text += `\n\n${this.winner === 0 ? 'BLUE' : 'RED'} WINS!`;
    }

    this.uiText.setText(text);
    this.updateInspectPanel();
  }

  private updateInspectPanel(): void {
    const factionName = (f: Faction) => f === 0 ? 'Blue' : 'Red';

    if (this.selectedUnit && !this.selectedUnit.isDead()) {
      const u = this.selectedUnit;
      let text = `${factionName(u.faction)} ${u.unitType} #${u.unitId}\n`;
      text += `HP: ${u.hp}/${u.stats.maxHp}  |  ATK: ${u.stats.attack}\n`;
      text += `State: ${u.state}\n`;
      text += `Position: (${u.tileX}, ${u.tileY})`;
      if (u.carryAmount > 0 && u.carryType) {
        text += `\nCarrying: ${u.carryAmount} ${u.carryType}`;
      }
      if (u.state === UnitState.GATHERING) {
        const node = this.resourceNodes.find((n) => n.nodeId === u.gatherTargetId);
        if (node) {
          const gatherTime = u.stats.gatherTicks[node.resourceType];
          text += `\nGathering: ${u.gatherProgress}/${gatherTime}`;
        }
      }
      if (u.state === UnitState.BUILDING) {
        const bld = this.buildings.find((b) => b.buildingId === u.buildTargetId);
        if (bld) {
          text += `\nBuilding: ${bld.constructionProgress}/${bld.stats.buildTicks}`;
        }
      }
      this.showInspect(text);
    } else if (this.selectedBuilding && !this.selectedBuilding.isDestroyed()) {
      const b = this.selectedBuilding;
      let text = `${factionName(b.faction)} ${b.buildingType} #${b.buildingId}\n`;
      text += `HP: ${b.hp}/${b.stats.maxHp}\n`;
      text += `Position: (${b.tileX}, ${b.tileY})`;
      if (!b.built) {
        const pct = Math.floor((b.constructionProgress / b.stats.buildTicks) * 100);
        text += `\nConstruction: ${pct}%`;
      }
      if (b.trainingQueue.length > 0) {
        const front = b.trainingQueue[0];
        const trainInfo = UNIT_TRAIN_INFO[front.unitType];
        const pct = Math.floor(((trainInfo.trainTicks - front.remainingTicks) / trainInfo.trainTicks) * 100);
        text += `\nTraining: ${front.unitType} (${pct}%)`;
        if (b.trainingQueue.length > 1) {
          text += ` +${b.trainingQueue.length - 1} queued`;
        }
      }
      this.showInspect(text);
    } else if (this.selectedResource && !this.selectedResource.isDepleted()) {
      const r = this.selectedResource;
      let text = `${r.resourceType} node\n`;
      text += `Remaining: ${r.remaining}\n`;
      text += `Position: (${r.tileX}, ${r.tileY})`;
      this.showInspect(text);
    } else {
      this.inspectText.setVisible(false);
    }
  }

  private showInspect(text: string): void {
    this.inspectText.setText(text);
    this.inspectText.setVisible(true);
    const gameHeight = this.scale.height;
    this.inspectText.setPosition(10, gameHeight - this.inspectText.height - 10);
  }

  update(_time: number, delta: number): void {
    this.gameClock.update(delta);

    if (this.selectedUnit && this.selectionIndicator) {
      if (this.selectedUnit.isDead()) {
        this.clearSelection();
      } else {
        this.selectionIndicator.setPosition(this.selectedUnit.x, this.selectedUnit.y);
      }
    }

    if (this.selectedBuilding && this.selectedBuilding.isDestroyed()) {
      this.clearSelection();
    }

    if (this.selectedResource && this.selectedResource.isDepleted()) {
      this.clearSelection();
    }

    this.updateUI();
  }

  private simulationTick(tick: number): void {
    this.processUnitStates();
    this.processCombat();
    this.processTraining();
    this.cleanupDead();
    this.checkWinCondition();

    this.fogOfWar.update(this.units, this.buildings);

    // Broadcast state to backend
    this.stateBroadcaster.maybeBroadcast(
      tick, this.units, this.buildings, this.resourceNodes, this.factionStates,
    );

    if (this.useBackendAgents) {
      this.applyBackendDecisions();
    } else {
      if (tick % 5 === 0) {
        for (let f = 0; f < 2; f++) {
          this.testAI.update(
            f as Faction,
            this.units,
            this.buildings,
            this.resourceNodes,
            this.factionStates[f],
            this.units,
            this.buildings,
            (faction, type, x, y) => this.requestBuild(faction, type, x, y),
            (building, type) => this.requestTrain(building, type),
          );
        }
      }
    }
  }

  private applyBackendDecisions(): void {
    // Apply commander orders (build, train, unit assignments)
    for (const order of this.decisionReceiver.drainCommanderOrders()) {
      const faction: Faction = order.faction === 'blue' ? 0 : 1;

      if (order.buildOrders) {
        for (const bo of order.buildOrders) {
          const type = bo.buildingType as BuildingType;
          this.requestBuild(faction, type, bo.tileX, bo.tileY);
        }
      }

      if (order.trainOrders) {
        for (const to of order.trainOrders) {
          const building = this.buildings.find((b) => b.buildingId === to.buildingId);
          if (building) {
            this.requestTrain(building, to.unitType as UnitType);
          }
        }
      }

      if (order.assignments) {
        for (const assignment of order.assignments) {
          const unit = this.units.find((u) => u.unitId === assignment.unitId && !u.isDead());
          if (!unit) continue;
          this.applyUnitDecision(unit, {
            tick: order.tick,
            faction: order.faction,
            unitId: assignment.unitId,
            action: assignment.task,
            targetX: assignment.targetX,
            targetY: assignment.targetY,
            targetId: assignment.targetId,
          });
        }
      }
    }

    // Apply unit decisions (move, gather, build, attack)
    for (const decision of this.decisionReceiver.drainUnitDecisions()) {
      const unit = this.units.find((u) => u.unitId === decision.unitId && !u.isDead());
      if (!unit) continue;

      this.applyUnitDecision(unit, decision);
    }
  }

  private applyUnitDecision(unit: Unit, decision: UnitDecision): void {
    switch (decision.action) {
      case 'move':
        if (decision.targetX != null && decision.targetY != null) {
          unit.resetTask();
          unit.state = UnitState.MOVING;
          unit.commandMove(decision.targetX, decision.targetY);
        }
        break;

      case 'gather':
        if (decision.targetId != null) {
          const node = this.resourceNodes.find((n) => n.nodeId === decision.targetId);
          if (node && !node.isDepleted()) {
            unit.resetTask();
            unit.state = UnitState.MOVING_TO_RESOURCE;
            unit.gatherTargetId = node.nodeId;
            unit.commandMove(node.tileX, node.tileY);
          }
        }
        break;

      case 'build':
        if (decision.targetId != null) {
          const building = this.buildings.find((b) => b.buildingId === decision.targetId);
          if (building && !building.built && !building.isDestroyed()) {
            unit.resetTask();
            unit.state = UnitState.MOVING_TO_BUILD;
            unit.buildTargetId = building.buildingId;
            unit.commandMove(building.tileX, building.tileY);
          }
        }
        break;

      case 'attack':
        if (decision.targetId != null) {
          const enemyUnit = this.units.find((u) => u.unitId === decision.targetId && !u.isDead());
          if (enemyUnit) {
            unit.resetTask();
            unit.state = UnitState.ATTACKING;
            unit.attackTargetId = enemyUnit.unitId;
            unit.attackTargetIsBuilding = false;
            unit.commandMove(enemyUnit.tileX, enemyUnit.tileY);
          } else {
            const enemyBuilding = this.buildings.find(
              (b) => b.buildingId === decision.targetId && !b.isDestroyed(),
            );
            if (enemyBuilding) {
              unit.resetTask();
              unit.state = UnitState.ATTACKING;
              unit.attackTargetId = enemyBuilding.buildingId;
              unit.attackTargetIsBuilding = true;
              unit.commandMove(enemyBuilding.tileX, enemyBuilding.tileY);
            }
          }
        }
        break;

      case 'idle':
        unit.resetTask();
        break;
    }
  }

  private processUnitStates(): void {
    for (const unit of this.units) {
      if (unit.isDead()) continue;

      switch (unit.state) {
        case UnitState.MOVING:
          if (!unit.isMoving()) {
            unit.state = UnitState.IDLE;
          }
          break;

        case UnitState.MOVING_TO_RESOURCE:
          this.processMovingToResource(unit);
          break;

        case UnitState.GATHERING:
          this.processGathering(unit);
          break;

        case UnitState.RETURNING:
          this.processReturning(unit);
          break;

        case UnitState.MOVING_TO_BUILD:
          this.processMovingToBuild(unit);
          break;

        case UnitState.BUILDING:
          this.processBuilding(unit);
          break;

        case UnitState.ATTACKING:
          this.processAttacking(unit);
          break;

        case UnitState.FLEEING:
          if (!unit.isMoving()) {
            unit.state = UnitState.IDLE;
          }
          break;
      }
    }
  }

  private processMovingToResource(unit: Unit): void {
    if (unit.isMoving()) return;

    const node = this.resourceNodes.find((n) => n.nodeId === unit.gatherTargetId);
    if (!node || node.isDepleted()) {
      unit.state = UnitState.IDLE;
      unit.gatherTargetId = null;
      return;
    }

    if (unit.isAdjacentTo(node.tileX, node.tileY) || (unit.tileX === node.tileX && unit.tileY === node.tileY)) {
      unit.state = UnitState.GATHERING;
      unit.gatherProgress = 0;
    } else if (!unit.commandMove(node.tileX, node.tileY)) {
      unit.state = UnitState.IDLE;
      unit.gatherTargetId = null;
    }
  }

  private processGathering(unit: Unit): void {
    const node = this.resourceNodes.find((n) => n.nodeId === unit.gatherTargetId);
    if (!node || node.isDepleted()) {
      unit.state = UnitState.IDLE;
      unit.gatherTargetId = null;
      return;
    }

    unit.gatherProgress++;
    const gatherTime = unit.stats.gatherTicks[node.resourceType];
    if (unit.gatherProgress >= gatherTime) {
      const amount = node.harvest(unit.stats.carryCapacity);
      unit.carryAmount = amount;
      unit.carryType = node.resourceType;
      unit.gatherProgress = 0;

      if (node.isDepleted()) {
        this.clearDepletedTile(node.tileX, node.tileY);
      }

      const tc = this.buildings.find(
        (b) => b.faction === unit.faction && b.buildingType === BuildingType.TOWN_CENTER && !b.isDestroyed(),
      );
      if (tc) {
        unit.state = UnitState.RETURNING;
        unit.commandMove(tc.tileX + 1, tc.tileY + 1);
      } else {
        unit.state = UnitState.IDLE;
      }
    }
  }

  private processReturning(unit: Unit): void {
    if (unit.isMoving()) return;

    const tc = this.buildings.find(
      (b) => b.faction === unit.faction && b.buildingType === BuildingType.TOWN_CENTER && !b.isDestroyed(),
    );
    if (!tc) {
      unit.state = UnitState.IDLE;
      return;
    }

    const dist = unit.distanceTo(tc.tileX + 1, tc.tileY + 1);
    if (dist <= 2) {
      if (unit.carryType && unit.carryAmount > 0) {
        this.factionStates[unit.faction].addResource(unit.carryType, unit.carryAmount);
        unit.carryAmount = 0;
        unit.carryType = null;
      }
      unit.state = UnitState.IDLE;
    } else if (!unit.commandMove(tc.tileX + 1, tc.tileY + 1)) {
      // Can't path to TC — drop resources and go idle
      unit.carryAmount = 0;
      unit.carryType = null;
      unit.state = UnitState.IDLE;
    }
  }

  private processMovingToBuild(unit: Unit): void {
    if (unit.isMoving()) return;

    const building = this.buildings.find((b) => b.buildingId === unit.buildTargetId);
    if (!building || building.built || building.isDestroyed()) {
      unit.state = UnitState.IDLE;
      unit.buildTargetId = null;
      return;
    }

    if (unit.distanceTo(building.tileX, building.tileY) <= 2) {
      unit.state = UnitState.BUILDING;
    } else if (!unit.commandMove(building.tileX, building.tileY)) {
      unit.state = UnitState.IDLE;
      unit.buildTargetId = null;
    }
  }

  private processBuilding(unit: Unit): void {
    const building = this.buildings.find((b) => b.buildingId === unit.buildTargetId);
    if (!building || building.isDestroyed()) {
      unit.state = UnitState.IDLE;
      unit.buildTargetId = null;
      return;
    }

    if (building.built) {
      unit.state = UnitState.IDLE;
      unit.buildTargetId = null;
      return;
    }

    const justFinished = building.advanceConstruction(1);
    if (justFinished && building.built) {
      this.stateBroadcaster.broadcastEvent({
        tick: this.gameClock.getTick(),
        eventType: 'building_complete',
        faction: this.factionName(building.faction),
        buildingId: building.buildingId,
        position: [building.tileX, building.tileY],
        description: `${this.factionName(building.faction)} ${building.buildingType} #${building.buildingId} complete`,
      });
    }
  }

  private processAttacking(unit: Unit): void {
    if (unit.attackCooldown > 0) unit.attackCooldown--;

    if (unit.attackTargetIsBuilding) {
      const target = this.buildings.find(
        (b) => b.buildingId === unit.attackTargetId && !b.isDestroyed(),
      );
      if (!target) {
        unit.state = UnitState.IDLE;
        unit.attackTargetId = null;
        unit.attackTargetIsBuilding = false;
        return;
      }

      if (unit.distanceTo(target.tileX, target.tileY) <= 2) {
        unit.stopMoving();
        if (unit.attackCooldown <= 0) {
          const destroyed = target.takeDamage(unit.stats.attack);
          unit.attackCooldown = unit.stats.attackCooldownTicks;
          if (destroyed) {
            target.setActive(false);
            target.setVisible(false);
            unit.state = UnitState.IDLE;
            unit.attackTargetId = null;
            unit.attackTargetIsBuilding = false;
          }
        }
      } else if (!unit.isMoving()) {
        if (!unit.commandMove(target.tileX, target.tileY)) {
          unit.state = UnitState.IDLE;
          unit.attackTargetId = null;
          unit.attackTargetIsBuilding = false;
        }
      }
    } else {
      const target = this.units.find(
        (u) => u.unitId === unit.attackTargetId && !u.isDead(),
      );
      if (!target) {
        unit.state = UnitState.IDLE;
        unit.attackTargetId = null;
        return;
      }

      if (unit.isAdjacentTo(target.tileX, target.tileY)) {
        unit.stopMoving();
        if (unit.attackCooldown <= 0) {
          const killed = target.takeDamage(unit.stats.attack);
          unit.attackCooldown = unit.stats.attackCooldownTicks;
          if (killed) {
            unit.state = UnitState.IDLE;
            unit.attackTargetId = null;
          }
        }
      } else if (!unit.isMoving()) {
        if (!unit.commandMove(target.tileX, target.tileY)) {
          unit.state = UnitState.IDLE;
          unit.attackTargetId = null;
        }
      }
    }
  }

  private processCombat(): void {
    for (const unit of this.units) {
      if (unit.isDead()) continue;

      // Tier 1: auto-flee for wounded workers (flee to TC, or just run away from danger)
      if (unit.shouldFlee() && unit.state !== UnitState.FLEEING) {
        unit.resetTask();
        unit.state = UnitState.FLEEING;
        const tc = this.buildings.find(
          (b) => b.faction === unit.faction && b.buildingType === BuildingType.TOWN_CENTER && !b.isDestroyed(),
        );
        if (tc) {
          unit.commandMove(tc.tileX + 1, tc.tileY + 1);
        } else {
          // No TC — run away from nearest enemy
          const nearestEnemy = this.units.find(
            (e) => e.faction !== unit.faction && !e.isDead() && unit.distanceTo(e.tileX, e.tileY) <= 5,
          );
          if (nearestEnemy) {
            const dx = unit.tileX - nearestEnemy.tileX;
            const dy = unit.tileY - nearestEnemy.tileY;
            const fleeX = Math.max(0, Math.min(MAP_WIDTH - 1, unit.tileX + Math.sign(dx) * 5));
            const fleeY = Math.max(0, Math.min(MAP_HEIGHT - 1, unit.tileY + Math.sign(dy) * 5));
            unit.commandMove(fleeX, fleeY);
          }
        }
        continue;
      }

      // Tier 1: auto-attack adjacent enemies (units first, then buildings)
      if (unit.state === UnitState.IDLE || unit.state === UnitState.MOVING) {
        const enemy = this.units.find(
          (e) => e.faction !== unit.faction && !e.isDead() && unit.isAdjacentTo(e.tileX, e.tileY),
        );
        if (enemy) {
          if (unit.unitType === UnitType.WORKER) {
            const tc = this.buildings.find(
              (b) => b.faction === unit.faction && b.buildingType === BuildingType.TOWN_CENTER && !b.isDestroyed(),
            );
            if (tc) {
              unit.resetTask();
              unit.state = UnitState.FLEEING;
              unit.commandMove(tc.tileX + 1, tc.tileY + 1);
            } else {
              // No TC — fight back
              unit.stopMoving();
              unit.state = UnitState.ATTACKING;
              unit.attackTargetId = enemy.unitId;
              unit.attackTargetIsBuilding = false;
            }
          } else if (unit.unitType === UnitType.WARRIOR) {
            unit.stopMoving();
            unit.state = UnitState.ATTACKING;
            unit.attackTargetId = enemy.unitId;
            unit.attackTargetIsBuilding = false;
          }
        } else if (unit.unitType === UnitType.WARRIOR) {
          const enemyBuilding = this.buildings.find(
            (b) => b.faction !== unit.faction && !b.isDestroyed() && unit.distanceTo(b.tileX, b.tileY) <= 2,
          );
          if (enemyBuilding) {
            unit.stopMoving();
            unit.state = UnitState.ATTACKING;
            unit.attackTargetId = enemyBuilding.buildingId;
            unit.attackTargetIsBuilding = true;
          }
        }
      }
    }
  }

  private processTraining(): void {
    for (const building of this.buildings) {
      if (building.isDestroyed()) continue;

      const trainedType = building.advanceTraining();
      if (trainedType) {
        const spawn = building.getSpawnTile();
        const newUnit = new Unit(this, spawn.x, spawn.y, building.faction, trainedType);
        this.units.push(newUnit);
        this.factionStates[building.faction].population++;
        this.stateBroadcaster.broadcastEvent({
          tick: this.gameClock.getTick(),
          eventType: 'unit_trained',
          faction: this.factionName(building.faction),
          unitId: newUnit.unitId,
          buildingId: building.buildingId,
          description: `${this.factionName(building.faction)} trained ${trainedType} #${newUnit.unitId}`,
        });
      }
    }
  }

  private factionName(f: Faction): string {
    return f === 0 ? 'blue' : 'red';
  }

  private cleanupDead(): void {
    for (let i = this.units.length - 1; i >= 0; i--) {
      const unit = this.units[i];
      if (unit.isDead() && unit.active) {
        this.stateBroadcaster.broadcastEvent({
          tick: this.gameClock.getTick(),
          eventType: 'unit_killed',
          faction: this.factionName(unit.faction),
          unitId: unit.unitId,
          position: [unit.tileX, unit.tileY],
          description: `${this.factionName(unit.faction)} ${unit.unitType} #${unit.unitId} killed`,
        });
        unit.stopMoving();
        unit.setActive(false);
        unit.setVisible(false);
        unit.destroy();
        if (this.selectedUnit === unit) this.clearSelection();
        this.factionStates[unit.faction].population--;
        this.units.splice(i, 1);
      }
    }

    for (let i = this.buildings.length - 1; i >= 0; i--) {
      const building = this.buildings[i];
      if (building.isDestroyed() && building.active) {
        this.stateBroadcaster.broadcastEvent({
          tick: this.gameClock.getTick(),
          eventType: 'building_destroyed',
          faction: this.factionName(building.faction),
          buildingId: building.buildingId,
          position: [building.tileX, building.tileY],
          description: `${this.factionName(building.faction)} ${building.buildingType} #${building.buildingId} destroyed`,
        });
        building.setActive(false);
        building.setVisible(false);
        building.trainingQueue.length = 0;
        building.destroy();
        if (this.selectedBuilding === building) this.clearSelection();
        this.buildings.splice(i, 1);
      }
    }

    for (let i = this.resourceNodes.length - 1; i >= 0; i--) {
      const node = this.resourceNodes[i];
      if (node.isDepleted()) {
        if (this.selectedResource === node) this.clearSelection();
        this.resourceNodes.splice(i, 1);
      }
    }
  }

  private checkWinCondition(): void {
    for (let f = 0; f < 2; f++) {
      const enemy = (f === 0 ? 1 : 0) as Faction;
      const enemyUnitsAlive = this.units.some((u) => u.faction === enemy && !u.isDead());
      const enemyBuildingsAlive = this.buildings.some((b) => b.faction === enemy && !b.isDestroyed());

      if (!enemyUnitsAlive && !enemyBuildingsAlive) {
        this.gameOver = true;
        this.winner = f as Faction;
        this.gameClock.setPaused(true);
        return;
      }
    }
  }

  private requestBuild(faction: Faction, type: BuildingType, x: number, y: number): Building | null {
    const state = this.factionStates[faction];
    const cost = BUILDING_STATS[type].cost;

    if (!state.canAfford(cost)) return null;
    state.spend(cost);

    const building = new Building(this, x, y, faction, type, false);
    this.buildings.push(building);
    return building;
  }

  private requestTrain(building: Building, unitType: UnitType): boolean {
    const state = this.factionStates[building.faction];
    const cost = UNIT_TRAIN_INFO[unitType].cost;

    if (!state.canAfford(cost)) return false;
    if (!state.hasPopulationRoom()) return false;

    state.spend(cost);
    building.queueUnit(unitType);
    return true;
  }

  private getOccupiedTiles(): Set<string> {
    const occupied = new Set<string>();
    for (const building of this.buildings) {
      if (building.isDestroyed()) continue;
      for (const tile of building.getOccupiedTiles()) {
        occupied.add(`${tile.x},${tile.y}`);
      }
    }
    return occupied;
  }

  private clearDepletedTile(x: number, y: number): void {
    MAP_DATA[y][x] = TileType.GRASS;
    this.mapGraphics.fillStyle(TILE_COLORS[TileType.GRASS], 1);
    this.mapGraphics.fillRect(x * TILE_SIZE, y * TILE_SIZE, TILE_SIZE, TILE_SIZE);
    this.mapGraphics.lineStyle(1, 0x000000, 0.1);
    this.mapGraphics.strokeRect(x * TILE_SIZE, y * TILE_SIZE, TILE_SIZE, TILE_SIZE);
  }
}
