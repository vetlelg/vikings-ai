export const SIMULATION_TICK_MS = 100; // 10 ticks per second

export type TickCallback = (tick: number) => void;

export class GameClock {
  private tick = 0;
  private accumulator = 0;
  private callbacks: TickCallback[] = [];
  private paused = false;

  public onTick(callback: TickCallback): void {
    this.callbacks.push(callback);
  }

  public update(deltaMs: number): void {
    if (this.paused) return;

    this.accumulator += deltaMs;

    // Cap at 3 ticks per frame to prevent death spiral
    let ticksThisFrame = 0;
    while (this.accumulator >= SIMULATION_TICK_MS && ticksThisFrame < 3) {
      this.accumulator -= SIMULATION_TICK_MS;
      this.tick++;
      ticksThisFrame++;
      for (const cb of this.callbacks) {
        cb(this.tick);
      }
    }

    // Discard excess accumulated time instead of trying to catch up
    if (this.accumulator > SIMULATION_TICK_MS * 3) {
      this.accumulator = 0;
    }
  }

  public getTick(): number {
    return this.tick;
  }

  public setPaused(paused: boolean): void {
    this.paused = paused;
  }

  public isPaused(): boolean {
    return this.paused;
  }
}
