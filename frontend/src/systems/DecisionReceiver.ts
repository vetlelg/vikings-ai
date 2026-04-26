import { BridgeEnvelope, WebSocketClient } from './WebSocketClient';

export interface UnitDecision {
  tick: number;
  faction: string;
  unitId: number;
  action: string;
  targetX?: number;
  targetY?: number;
  targetId?: number;
  reasoning?: string;
}

export interface CommanderOrder {
  tick: number;
  faction: string;
  strategy: string;
  assignments?: {
    unitId: number;
    task: string;
    targetX?: number;
    targetY?: number;
    targetId?: number;
    reasoning?: string;
  }[];
  buildOrders?: {
    buildingType: string;
    tileX: number;
    tileY: number;
  }[];
  trainOrders?: {
    buildingId: number;
    unitType: string;
  }[];
}

export class DecisionReceiver {
  private unitDecisionQueue: UnitDecision[] = [];
  private commanderOrderQueue: CommanderOrder[] = [];

  constructor(ws: WebSocketClient) {
    ws.onMessage((envelope: BridgeEnvelope) => {
      this.handleMessage(envelope);
    });
  }

  private static readonly MAX_QUEUE_SIZE = 100;

  private handleMessage(envelope: BridgeEnvelope): void {
    if (envelope.topic.startsWith('unit-decisions.')) {
      console.log(`[Decision] Unit decision received:`, envelope.payload);
      if (this.unitDecisionQueue.length < DecisionReceiver.MAX_QUEUE_SIZE) {
        this.unitDecisionQueue.push(envelope.payload as UnitDecision);
      }
    } else if (envelope.topic.startsWith('commander-orders.')) {
      console.log(`[Decision] Commander order received:`, envelope.payload);
      if (this.commanderOrderQueue.length < DecisionReceiver.MAX_QUEUE_SIZE) {
        this.commanderOrderQueue.push(envelope.payload as CommanderOrder);
      }
    }
  }

  public drainUnitDecisions(): UnitDecision[] {
    const decisions = this.unitDecisionQueue.splice(0);
    return decisions;
  }

  public drainCommanderOrders(): CommanderOrder[] {
    const orders = this.commanderOrderQueue.splice(0);
    return orders;
  }

  public hasDecisions(): boolean {
    return this.unitDecisionQueue.length > 0 || this.commanderOrderQueue.length > 0;
  }
}
