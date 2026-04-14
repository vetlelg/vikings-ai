import { useMemo } from 'react';
import { useGameStore } from '../../store/gameStore';
import { AgentRoleIcon } from '../shared/AgentIcons';
import type { Position, TerrainType } from '../../types/world';
import styles from './AgentInspector.module.css';

const roleColors: Record<string, string> = {
  JARL: '#d4a843',
  WARRIOR: '#c0392b',
  FISHERMAN: '#3498db',
  SHIPBUILDER: '#8b6914',
  SKALD: '#9b59b6',
};

const resourceEmojis: Record<string, string> = {
  TIMBER: '🪵', timber: '🪵',
  FISH: '🐟', fish: '🐟',
  IRON: '⛏', iron: '⛏',
  FURS: '🦊', furs: '🦊',
};

const terrainColors: Record<TerrainType, string> = {
  GRASS: '#3a5a2c',
  FOREST: '#1e3a1a',
  WATER: '#1a3a5c',
  MOUNTAIN: '#4a4a52',
  BEACH: '#c4a44a',
  VILLAGE: '#6b4c2a',
};

const MINIMAP_SIZE = 100;

function MiniMap({ trail, current, color }: { trail: Position[]; current: Position; color: string }) {
  const grid = useGameStore((s) => s.grid);

  const canvas = useMemo(() => {
    if (grid.length === 0) return null;
    const rows = grid.length;
    const cols = grid[0].length;
    const cellW = MINIMAP_SIZE / cols;
    const cellH = MINIMAP_SIZE / rows;

    // Build terrain pixels
    const terrainRects: React.ReactNode[] = [];
    for (let y = 0; y < rows; y++) {
      for (let x = 0; x < cols; x++) {
        terrainRects.push(
          <rect
            key={`${x}-${y}`}
            x={x * cellW}
            y={y * cellH}
            width={cellW + 0.5}
            height={cellH + 0.5}
            fill={terrainColors[grid[y][x]]}
          />
        );
      }
    }

    // Build trail path
    const allPoints = [...trail, current];
    let pathD = '';
    if (allPoints.length > 1) {
      pathD = allPoints.map((p, i) => {
        const px = (p.x + 0.5) * cellW;
        const py = (p.y + 0.5) * cellH;
        return `${i === 0 ? 'M' : 'L'}${px},${py}`;
      }).join(' ');
    }

    const dotX = (current.x + 0.5) * cellW;
    const dotY = (current.y + 0.5) * cellH;

    return (
      <svg width={MINIMAP_SIZE} height={MINIMAP_SIZE} className={styles.minimapSvg}>
        {terrainRects}
        {pathD && (
          <path d={pathD} fill="none" stroke={color} strokeWidth={1.2} opacity={0.7} strokeLinejoin="round" />
        )}
        <circle cx={dotX} cy={dotY} r={2.5} fill={color} stroke="#fff" strokeWidth={0.5} />
      </svg>
    );
  }, [grid, trail, current, color]);

  return <div className={styles.minimap}>{canvas}</div>;
}

export function AgentInspector() {
  const selectedAgent = useGameStore((s) => s.selectedAgent);
  const agents = useGameStore((s) => s.agents);
  const latestTaskByAgent = useGameStore((s) => s.latestTaskByAgent);
  const agentFullTrails = useGameStore((s) => s.agentFullTrails);
  const setSelectedAgent = useGameStore((s) => s.setSelectedAgent);

  if (!selectedAgent) return null;

  const agent = agents.find((a) => a.name === selectedAgent);
  if (!agent) return null;

  const lastTask = latestTaskByAgent[agent.name];
  const invEntries = Object.entries(agent.inventory).filter(([, v]) => v > 0);
  const depositEntries = Object.entries(agent.totalDeposited).filter(([, v]) => v > 0);
  const trail = agentFullTrails[agent.name] || [];
  const color = roleColors[agent.role] || '#888';

  return (
    <div className={styles.inspector} onMouseMove={(e) => e.stopPropagation()}>
      <button className={styles.close} onClick={() => setSelectedAgent(null)}>×</button>

      <div className={styles.header}>
        <div className={styles.icon} style={{ borderColor: color }}>
          <AgentRoleIcon role={agent.role} />
        </div>
        <div className={styles.headerText}>
          <div className={styles.name}>{agent.name}</div>
          <div className={styles.role}>{agent.role}</div>
        </div>
        <div className={`${styles.status} ${styles[agent.status]}`}>
          {agent.status}
        </div>
      </div>

      <div className={styles.section}>
        <div className={styles.label}>Health</div>
        <div className={styles.healthRow}>
          <div className={styles.healthBar}>
            <div
              className={styles.healthFill}
              style={{
                width: `${agent.health}%`,
                background: agent.health > 50 ? '#2ecc71' : agent.health > 25 ? '#e67e22' : '#c0392b',
              }}
            />
          </div>
          <span className={styles.healthText}>{agent.health}</span>
        </div>
      </div>

      <div className={styles.statsRow}>
        <div className={styles.stat} title="Kills">
          <span className={styles.statIcon}>&#9876;</span>
          <span className={styles.statValue}>{agent.kills}</span>
        </div>
        <div className={styles.stat} title="Deaths">
          <span className={styles.statIcon}>&#9760;</span>
          <span className={styles.statValue}>{agent.deaths}</span>
        </div>
        {depositEntries.map(([resource, count]) => (
          <div key={resource} className={styles.stat} title={`${resource} deposited`}>
            <span className={styles.statIcon}>{resourceEmojis[resource] || '◆'}</span>
            <span className={styles.statValue}>{count}</span>
          </div>
        ))}
      </div>

      <div className={styles.section}>
        <div className={styles.label}>Position</div>
        <div className={styles.mono}>({agent.position.x}, {agent.position.y})</div>
      </div>

      <div className={styles.section}>
        <div className={styles.label}>Inventory</div>
        {invEntries.length === 0 ? (
          <span className={styles.empty}>Empty</span>
        ) : (
          <div className={styles.inventory}>
            {invEntries.map(([resource, count]) => (
              <div key={resource} className={styles.inventoryItem}>
                {resourceEmojis[resource] || '◆'} {count}
              </div>
            ))}
          </div>
        )}
      </div>

      <div className={styles.section}>
        <div className={styles.label}>Current Task</div>
        <div className={styles.action}>
          {agent.currentTaskType ?? lastTask?.taskType ?? 'None'}
          {lastTask?.targetResourceType ? ` (${lastTask.targetResourceType})` : ''}
        </div>
        <div className={styles.reasoning}>"{agent.currentTaskReasoning ?? lastTask?.reasoning ?? ''}"</div>
        {agent.currentAction && (
          <div className={styles.mono} style={{ fontSize: 10, marginTop: 2 }}>
            Doing: {agent.currentAction}{agent.currentDirection ? ` ${agent.currentDirection}` : ''}
          </div>
        )}
      </div>

      <div className={styles.section}>
        <div className={styles.label}>Trail</div>
        <MiniMap trail={trail} current={agent.position} color={color} />
      </div>
    </div>
  );
}
