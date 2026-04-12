import { useGameStore } from '../../store/gameStore';
import { GridCell } from './GridCell';
import { AgentToken } from './AgentToken';
import { EntityToken } from './EntityToken';
import { DragonToken } from './DragonToken';
import { DayNightOverlay } from './DayNightOverlay';
import { WeatherOverlay } from './WeatherOverlay';
import styles from './GameGrid.module.css';

export function GameGrid() {
  const grid = useGameStore((s) => s.grid);
  const agents = useGameStore((s) => s.agents);
  const entities = useGameStore((s) => s.entities);
  const timeOfDay = useGameStore((s) => s.timeOfDay);
  const weather = useGameStore((s) => s.weather);
  const connected = useGameStore((s) => s.connected);

  if (grid.length === 0) {
    return (
      <div className={styles.gridWrapper}>
        <div className={styles.loading}>
          <div className={styles.loadingIcon}>&#9876;</div>
          <div className={styles.loadingTitle}>Viking Settlement</div>
          <div className={styles.loadingSubtitle}>
            {connected ? 'Awaiting world state...' : 'Connecting to bridge...'}
          </div>
          <div className={styles.loadingHint}>
            Start the backend: docker compose up -d, then ./gradlew :engine:run and ./gradlew :bridge:run
          </div>
        </div>
      </div>
    );
  }

  const dragons = entities.filter((e) => e.type === 'DRAGON');
  const nonDragons = entities.filter((e) => e.type !== 'DRAGON');

  return (
    <div className={styles.gridWrapper}>
      <div className={styles.grid}>
        {grid.flatMap((row, y) =>
          row.map((terrain, x) => (
            <GridCell key={`${x}-${y}`} terrain={terrain} />
          )),
        )}
      </div>
      <div className={styles.tokenLayer}>
        {nonDragons.map((e) => (
          <EntityToken key={e.id} entity={e} />
        ))}
        {agents.map((a) => (
          <AgentToken key={a.name} agent={a} />
        ))}
        {dragons.map((d) => (
          <DragonToken key={d.id} entity={d} />
        ))}
      </div>
      <DayNightOverlay timeOfDay={timeOfDay} />
      <WeatherOverlay weather={weather} />
    </div>
  );
}
