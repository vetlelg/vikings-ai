import { useState, useCallback, useMemo } from 'react';
import { useGameStore } from '../../store/gameStore';
import { useViewport } from '../../hooks/useViewport';
import { GridCell } from './GridCell';
import { AgentToken } from './AgentToken';
import { EntityToken } from './EntityToken';
import { WolfToken } from './WolfToken';
import { DragonToken } from './DragonToken';
import { DayNightOverlay } from './DayNightOverlay';
import { WeatherOverlay } from './WeatherOverlay';
import { MovementTrail } from './MovementTrail';
import { GridTooltip } from './GridTooltip';
import { AgentInspector } from './AgentInspector';
import { EventToasts } from './EventToasts';
import { VictoryOverlay } from './VictoryOverlay';
import styles from './GameGrid.module.css';

const CELL = 39; // cell-size + grid-gap

const roleTrailColors: Record<string, string> = {
  JARL: '#d4a843',
  WARRIOR: '#c0392b',
  FISHERMAN: '#3498db',
  SHIPBUILDER: '#8b6914',
  SKALD: '#9b59b6',
};

export function GameGrid() {
  const grid = useGameStore((s) => s.grid);
  const agents = useGameStore((s) => s.agents);
  const entities = useGameStore((s) => s.entities);
  const threats = useGameStore((s) => s.threats);
  const timeOfDay = useGameStore((s) => s.timeOfDay);
  const weather = useGameStore((s) => s.weather);
  const connected = useGameStore((s) => s.connected);
  const selectedAgent = useGameStore((s) => s.selectedAgent);
  const setSelectedAgent = useGameStore((s) => s.setSelectedAgent);
  const latestTaskByAgent = useGameStore((s) => s.latestTaskByAgent);
  const agentTrails = useGameStore((s) => s.agentTrails);

  const rows = grid.length;
  const cols = rows > 0 ? grid[0].length : 0;
  const gridPx = cols * CELL;
  const gridPy = rows * CELL;

  const { containerRef, transform, zoom, handlers } = useViewport(gridPx, gridPy);

  const [hoverCell, setHoverCell] = useState<{ x: number; y: number } | null>(null);
  const [tooltipPos, setTooltipPos] = useState({ x: 0, y: 0 });

  const handleGridMouseMove = useCallback((e: React.MouseEvent) => {
    // Convert screen coords to grid coords accounting for zoom/pan
    const wrapper = e.currentTarget as HTMLElement;
    const rect = wrapper.getBoundingClientRect();
    const mx = (e.clientX - rect.left) / zoom;
    const my = (e.clientY - rect.top) / zoom;
    const cellX = Math.floor(mx / CELL);
    const cellY = Math.floor(my / CELL);
    if (cellX >= 0 && cellX < cols && cellY >= 0 && cellY < rows) {
      setHoverCell({ x: cellX, y: cellY });
      setTooltipPos({ x: e.clientX, y: e.clientY });
    } else {
      setHoverCell(null);
    }
  }, [zoom, cols, rows]);

  const handleMouseLeave = useCallback(() => setHoverCell(null), []);

  const tooltipData = useMemo(() => {
    if (!hoverCell || grid.length === 0) return null;
    const terrain = grid[hoverCell.y]?.[hoverCell.x];
    if (!terrain) return null;
    return {
      terrain,
      agents: agents.filter(
        (a) => a.position.x === hoverCell.x && a.position.y === hoverCell.y,
      ),
      entities: entities.filter(
        (e) => e.position.x === hoverCell.x && e.position.y === hoverCell.y,
      ),
      threats: threats.filter(
        (t) => t.position.x === hoverCell.x && t.position.y === hoverCell.y,
      ),
    };
  }, [hoverCell, grid, agents, entities, threats]);

  if (grid.length === 0) {
    return (
      <div className={styles.viewport}>
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
  const wolves = entities.filter((e) => e.type === 'WOLF');
  const resources = entities.filter((e) => e.type === 'RESOURCE_NODE');

  return (
    <div
      className={styles.viewport}
      ref={containerRef}
      {...handlers}
    >
      <div
        className={styles.gridWrapper}
        style={{
          width: gridPx,
          height: gridPy,
          transform,
          transformOrigin: '0 0',
        }}
        onMouseMove={handleGridMouseMove}
        onMouseLeave={handleMouseLeave}
      >
        <div
          className={styles.grid}
          style={{
            gridTemplateColumns: `repeat(${cols}, var(--cell-size))`,
            gridTemplateRows: `repeat(${rows}, var(--cell-size))`,
          }}
        >
          {grid.flatMap((row, y) =>
            row.map((terrain, x) => (
              <GridCell key={`${x}-${y}`} terrain={terrain} />
            )),
          )}
        </div>
        <div className={styles.tokenLayer}>
          {agents.map((a) => (
            <MovementTrail
              key={`trail-${a.name}`}
              trail={agentTrails[a.name] || []}
              color={roleTrailColors[a.role] || '#888'}
            />
          ))}
          {resources.map((e) => (
            <EntityToken key={e.id} entity={e} />
          ))}
          {wolves.map((e) => (
            <WolfToken key={e.id} entity={e} />
          ))}
          {agents.map((a) => (
            <AgentToken
              key={a.name}
              agent={a}
              latestTask={latestTaskByAgent[a.name]}
              selected={selectedAgent === a.name}
              onClick={() => setSelectedAgent(selectedAgent === a.name ? null : a.name)}
            />
          ))}
          {dragons.map((d) => (
            <DragonToken key={d.id} entity={d} />
          ))}
        </div>
        <DayNightOverlay timeOfDay={timeOfDay} />
        <WeatherOverlay weather={weather} />
      </div>
      <AgentInspector />
      <EventToasts />
      <VictoryOverlay />
      {hoverCell && tooltipData && (
        <GridTooltip
          x={hoverCell.x}
          y={hoverCell.y}
          mouseX={tooltipPos.x}
          mouseY={tooltipPos.y}
          terrain={tooltipData.terrain}
          agents={tooltipData.agents}
          entities={tooltipData.entities}
          threats={tooltipData.threats}
        />
      )}
    </div>
  );
}
