import { memo } from 'react';
import type { AgentSnapshot, AgentAction } from '../../types/world';
import { AgentRoleIcon } from '../shared/AgentIcons';
import { ActionBubble } from './ActionBubble';
import styles from './AgentToken.module.css';

const CELL = 39; // cell-size + grid-gap

interface Props {
  agent: AgentSnapshot;
  latestAction?: AgentAction;
  selected?: boolean;
  onClick?: () => void;
}

export const AgentToken = memo(
  function AgentToken({ agent, latestAction, selected, onClick }: Props) {
    const x = agent.position.x * CELL;
    const y = agent.position.y * CELL;

    const classes = [
      styles.token,
      styles[agent.role],
      agent.status === 'THINKING' ? styles.thinking : '',
      agent.status === 'DEAD' ? styles.dead : '',
      selected ? styles.selected : '',
    ].filter(Boolean).join(' ');

    return (
      <div
        className={classes}
        style={{ transform: `translate(${x}px, ${y}px)` }}
        onClick={onClick}
      >
        <AgentRoleIcon role={agent.role} />
        <span className={styles.nameTag}>{agent.name}</span>
        {latestAction && (
          <ActionBubble
            key={`${agent.name}-${latestAction.tick}`}
            action={latestAction.action}
            direction={latestAction.direction}
          />
        )}
      </div>
    );
  },
  (prev, next) =>
    prev.agent.position.x === next.agent.position.x &&
    prev.agent.position.y === next.agent.position.y &&
    prev.agent.status === next.agent.status &&
    prev.agent.health === next.agent.health &&
    prev.selected === next.selected &&
    prev.latestAction?.tick === next.latestAction?.tick,
);
