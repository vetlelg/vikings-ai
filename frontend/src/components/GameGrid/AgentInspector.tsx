import { useGameStore } from '../../store/gameStore';
import { AgentRoleIcon } from '../shared/AgentIcons';
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

export function AgentInspector() {
  const selectedAgent = useGameStore((s) => s.selectedAgent);
  const agents = useGameStore((s) => s.agents);
  const latestActionByAgent = useGameStore((s) => s.latestActionByAgent);
  const setSelectedAgent = useGameStore((s) => s.setSelectedAgent);

  if (!selectedAgent) return null;

  const agent = agents.find((a) => a.name === selectedAgent);
  if (!agent) return null;

  const lastAction = latestActionByAgent[agent.name];
  const invEntries = Object.entries(agent.inventory).filter(([, v]) => v > 0);

  return (
    <div className={styles.inspector} onMouseMove={(e) => e.stopPropagation()}>
      <button className={styles.close} onClick={() => setSelectedAgent(null)}>×</button>

      <div className={styles.header}>
        <div className={styles.icon} style={{ borderColor: roleColors[agent.role] }}>
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

      {lastAction && (
        <div className={styles.section}>
          <div className={styles.label}>Last Action</div>
          <div className={styles.action}>
            {lastAction.action}
            {lastAction.direction ? ` ${lastAction.direction}` : ''}
          </div>
          <div className={styles.reasoning}>"{lastAction.reasoning}"</div>
        </div>
      )}
    </div>
  );
}
