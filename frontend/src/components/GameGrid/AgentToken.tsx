import { memo } from 'react';
import type { AgentSnapshot } from '../../types/world';
import { AgentRoleIcon } from '../shared/AgentIcons';
import styles from './AgentToken.module.css';

const CELL = 39; // cell-size + grid-gap

interface Props {
  agent: AgentSnapshot;
}

export const AgentToken = memo(
  function AgentToken({ agent }: Props) {
    const x = agent.position.x * CELL;
    const y = agent.position.y * CELL;

    const classes = [
      styles.token,
      styles[agent.role],
      agent.status === 'THINKING' ? styles.thinking : '',
      agent.status === 'DEAD' ? styles.dead : '',
    ].filter(Boolean).join(' ');

    return (
      <div
        className={classes}
        style={{ transform: `translate(${x}px, ${y}px)` }}
        title={`${agent.name} (${agent.role}) HP:${agent.health}`}
      >
        <span className={styles.ring} />
        <AgentRoleIcon role={agent.role} />
        <span className={styles.nameTag}>{agent.name}</span>
      </div>
    );
  },
  (prev, next) =>
    prev.agent.position.x === next.agent.position.x &&
    prev.agent.position.y === next.agent.position.y &&
    prev.agent.status === next.agent.status &&
    prev.agent.health === next.agent.health,
);
