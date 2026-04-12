import type { WorldCommand } from '../../types/world';
import styles from './CommandPanel.module.css';

interface Props {
  sendCommand: (cmd: WorldCommand) => void;
}

export function CommandPanel({ sendCommand }: Props) {
  return (
    <div className={styles.commandBar}>
      <button
        className={styles.commandButton}
        onClick={() => sendCommand({ command: 'spawn_dragon' })}
      >
        Summon Dragon
      </button>
      <button
        className={styles.commandButton}
        onClick={() => sendCommand({ command: 'start_winter' })}
      >
        Winter Comes
      </button>
      <button
        className={styles.commandButton}
        onClick={() => sendCommand({ command: 'rival_raid' })}
      >
        Rival Raid
      </button>
    </div>
  );
}
