export interface BridgeEnvelope {
  topic: string;
  timestamp: number;
  payload: unknown;
}

export type MessageHandler = (envelope: BridgeEnvelope) => void;

export class WebSocketClient {
  private ws: WebSocket | null = null;
  private url: string;
  private handlers: MessageHandler[] = [];
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private reconnectDelay = 1000;
  private maxReconnectDelay = 10000;
  private connected = false;

  constructor(url: string) {
    this.url = url;
  }

  public connect(): void {
    if (this.ws) return;

    console.log(`[WS] Attempting connection to ${this.url}...`);
    try {
      this.ws = new WebSocket(this.url);

      this.ws.onopen = () => {
        console.log('[WS] Connected to bridge');
        this.connected = true;
        this.reconnectDelay = 1000;
      };

      this.ws.onmessage = (event: MessageEvent) => {
        try {
          const envelope = JSON.parse(event.data) as BridgeEnvelope;
          for (const handler of this.handlers) {
            handler(envelope);
          }
        } catch (e) {
          console.warn('[WS] Failed to parse message:', e);
        }
      };

      this.ws.onclose = () => {
        console.log('[WS] Disconnected');
        this.connected = false;
        this.ws = null;
        this.scheduleReconnect();
      };

      this.ws.onerror = () => {
        this.ws?.close();
      };
    } catch {
      this.scheduleReconnect();
    }
  }

  public onMessage(handler: MessageHandler): void {
    this.handlers.push(handler);
  }

  public send(topic: string, payload: unknown): void {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return;

    const message = JSON.stringify({ topic, payload });
    this.ws.send(message);
  }

  public isConnected(): boolean {
    return this.connected;
  }

  public disconnect(): void {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    this.ws?.close();
    this.ws = null;
    this.connected = false;
  }

  private scheduleReconnect(): void {
    if (this.reconnectTimer) return;

    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      this.connect();
    }, this.reconnectDelay);

    this.reconnectDelay = Math.min(this.reconnectDelay * 1.5, this.maxReconnectDelay);
  }
}
