import type { ActionType } from '../../types/world';
import styles from './ActionBubble.module.css';

const actionDisplay: Record<ActionType, { emoji: string; label: string }> = {
  MOVE: { emoji: '🚶', label: 'Moving' },
  GATHER: { emoji: '⛏', label: 'Gathering' },
  FIGHT: { emoji: '⚔️', label: 'Fighting!' },
  BUILD: { emoji: '🏗️', label: 'Building' },
  PATROL: { emoji: '👁️', label: 'Patrolling' },
  FLEE: { emoji: '🏃', label: 'Fleeing!' },
  SPEAK: { emoji: '💬', label: 'Speaking' },
  IDLE: { emoji: '💤', label: 'Idle' },
};

interface Props {
  action: ActionType;
  direction?: string;
}

export function ActionBubble({ action, direction }: Props) {
  const display = actionDisplay[action] || { emoji: '❓', label: action };
  const text = direction ? `${display.label} ${direction}` : display.label;

  return (
    <div className={styles.bubble}>
      <span className={styles.emoji}>{display.emoji}</span>
      <span className={styles.text}>{text}</span>
    </div>
  );
}
