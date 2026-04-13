import type { ActionType } from '../../types/world';
import styles from './ActionBubble.module.css';

const actionEmoji: Record<ActionType, string> = {
  MOVE: '🚶',
  GATHER: '⛏',
  FIGHT: '⚔️',
  BUILD: '🏗️',
  PATROL: '👁️',
  FLEE: '🏃',
  SPEAK: '💬',
  IDLE: '💤',
};

interface Props {
  action: ActionType;
  reasoning?: string;
}

export function ActionBubble({ action, reasoning }: Props) {
  const emoji = actionEmoji[action] || '❓';

  return (
    <div className={styles.bubble}>
      <span className={styles.emoji}>{emoji}</span>
      {reasoning && <span className={styles.text}>{reasoning}</span>}
    </div>
  );
}
