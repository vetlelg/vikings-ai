import { useGameStore } from '../../store/gameStore';
import { TimberIcon, FishIcon, IronIcon, FursIcon } from '../shared/ResourceIcon';
import type { TimeOfDay } from '../../types/world';
import styles from './TopBar.module.css';

const timeIcons: Record<TimeOfDay, string> = {
  DAWN: '🌅',
  DAY: '☀️',
  DUSK: '🌇',
  NIGHT: '🌙',
};

const weatherLabels: Record<string, string> = {
  CLEAR: '☀ Clear',
  SNOW: '❄ Snow',
  STORM: '⛈ Storm',
};

function VoyageResource({ icon, current, target }: { icon: React.ReactNode; current: number; target: number }) {
  const pct = Math.min(100, (current / target) * 100);
  const done = current >= target;
  return (
    <div className={styles.voyageResource}>
      <span className={styles.voyageIcon}>{icon}</span>
      <div className={styles.voyageBar}>
        <div
          className={`${styles.voyageFill} ${done ? styles.voyageDone : ''}`}
          style={{ width: `${pct}%` }}
        />
      </div>
      <span className={`${styles.voyageCount} ${done ? styles.voyageCountDone : ''}`}>
        {current}/{target}
      </span>
    </div>
  );
}

export function TopBar() {
  const timeOfDay = useGameStore((s) => s.timeOfDay);
  const weather = useGameStore((s) => s.weather);
  const colonyResources = useGameStore((s) => s.colonyResources);
  const voyageGoal = useGameStore((s) => s.voyageGoal);
  const agents = useGameStore((s) => s.agents);
  const connected = useGameStore((s) => s.connected);
  const tick = useGameStore((s) => s.tick);

  return (
    <div className={styles.topBar}>
      <div className={styles.section}>
        <span className={styles.connectionDot + ' ' + (connected ? styles.connected : styles.disconnected)}
          title={connected ? 'Connected' : 'Disconnected'} />
        <div className={styles.timeDisplay}>
          <span className={styles.timeIcon}>{timeIcons[timeOfDay]}</span>
          {timeOfDay}
        </div>
        <span className={styles.weatherBadge}>{weatherLabels[weather] || weather}</span>
        <span className={styles.tick}>Tick {tick}</span>
      </div>

      <div className={styles.voyageSection}>
        <span className={styles.voyageLabel}>Longship</span>
        <VoyageResource icon={<TimberIcon />} current={colonyResources.timber} target={voyageGoal.timber} />
        <VoyageResource icon={<IronIcon />} current={colonyResources.iron} target={voyageGoal.iron} />
        <VoyageResource icon={<FursIcon />} current={colonyResources.furs} target={voyageGoal.furs} />
        <span className={styles.resource}><FishIcon />{colonyResources.fish}</span>
      </div>

      <div className={styles.agentStatus}>
        {agents.map((a) => (
          <span key={a.name} className={styles.agentDot} title={`${a.name} (${a.role}) HP:${a.health}`}>
            <span className={`${styles.dot} ${styles[a.status.toLowerCase()]}`} />
            <span>{a.name}</span>
          </span>
        ))}
      </div>
    </div>
  );
}
