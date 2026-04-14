import type { TerrainType, AgentSnapshot, EntitySnapshot, ThreatSnapshot } from '../../types/world';
import styles from './GridTooltip.module.css';

interface Props {
  x: number;
  y: number;
  mouseX: number;
  mouseY: number;
  terrain: TerrainType;
  agents: AgentSnapshot[];
  entities: EntitySnapshot[];
  threats: ThreatSnapshot[];
}

const terrainLabels: Record<TerrainType, string> = {
  GRASS: '🌿 Grassland',
  FOREST: '🌲 Forest',
  WATER: '🌊 Fjord',
  MOUNTAIN: '⛰️ Mountain',
  BEACH: '🏖️ Beach',
  VILLAGE: '🏘️ Village',
};

const subtypeLabels: Record<string, string> = {
  tree: '🌲 Tree',
  mine: '⛏ Mine',
  fishing_spot: '🐟 Fishing Spot',
  hunting_ground: '🦊 Hunting Ground',
};

export function GridTooltip({ x, y, mouseX, mouseY, terrain, agents, entities, threats }: Props) {
  const hasContent = agents.length > 0 || entities.length > 0 || threats.length > 0;

  return (
    <div
      className={styles.tooltip}
      style={{ left: mouseX + 14, top: mouseY + 14 }}
    >
      <div className={styles.header}>
        <span className={styles.terrain}>{terrainLabels[terrain]}</span>
        <span className={styles.coords}>({x}, {y})</span>
      </div>
      {hasContent && <div className={styles.divider} />}
      {agents.map((a) => (
        <div key={a.name} className={styles.agent}>
          <span className={styles.agentName}>{a.name}</span>
          <span className={styles.agentRole}>{a.role}</span>
          <span className={styles.agentHp}>HP {a.health}</span>
        </div>
      ))}
      {entities.map((e) => (
        <div key={e.id} className={styles.entity}>
          {e.type === 'WOLF' ? '🐺 Wolf' :
           e.type === 'DRAGON' ? '🐉 Dragon' :
           `${subtypeLabels[e.subtype || ''] || `◆ ${e.subtype || 'Resource'}`}${
             e.remaining != null && e.capacity ? ` (${e.remaining}/${e.capacity})` : ''
           }`}
        </div>
      ))}
      {threats.map((t) => (
        <div key={t.id} className={`${styles.threat} ${styles[t.severity]}`}>
          ⚠ {t.type} ({t.severity})
        </div>
      ))}
    </div>
  );
}
