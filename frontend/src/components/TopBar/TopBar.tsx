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

export function TopBar() {
  const timeOfDay = useGameStore((s) => s.timeOfDay);
  const weather = useGameStore((s) => s.weather);
  const colonyResources = useGameStore((s) => s.colonyResources);
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

      <div className={styles.section}>
        <span className={styles.resource}><TimberIcon />{colonyResources.timber}</span>
        <span className={styles.resource}><FishIcon />{colonyResources.fish}</span>
        <span className={styles.resource}><IronIcon />{colonyResources.iron}</span>
        <span className={styles.resource}><FursIcon />{colonyResources.furs}</span>
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
