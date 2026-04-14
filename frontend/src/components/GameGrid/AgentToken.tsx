import { memo } from 'react';
import type { AgentSnapshot, AgentTask } from '../../types/world';
import { AgentRoleIcon } from '../shared/AgentIcons';
import { ActionBubble } from './ActionBubble';
import styles from './AgentToken.module.css';

const CELL = 39; // cell-size + grid-gap

interface Props {
  agent: AgentSnapshot;
  latestTask?: AgentTask;
  selected?: boolean;
  onClick?: () => void;
}

export const AgentToken = memo(
  function AgentToken({ agent, latestTask, selected, onClick }: Props) {
    const x = agent.position.x * CELL;
    const y = agent.position.y * CELL;

    const fighting = agent.currentAction === 'FIGHT';
    const gathering = agent.currentAction === 'GATHER';
    const lowHealth = agent.health < 40 && agent.status !== 'DEAD';
    const classes = [
      styles.token,
      styles[agent.role],
      agent.status === 'THINKING' ? styles.thinking : '',
      agent.status === 'DEAD' ? styles.dead : '',
      selected ? styles.selected : '',
      fighting ? styles.fighting : '',
      gathering ? styles.gathering : '',
      lowHealth ? styles.lowHealth : '',
    ].filter(Boolean).join(' ');

    const healthPct = Math.max(0, agent.health);
    const healthColor = healthPct > 60 ? '#2ecc71' : healthPct > 30 ? '#e8a627' : '#c0392b';

    // Show the current action (from engine), with task reasoning
    const action = agent.currentAction;
    const reasoning = agent.currentTaskReasoning ?? latestTask?.reasoning;

    return (
      <div
        className={classes}
        style={{ transform: `translate(${x}px, ${y}px)` }}
        onClick={onClick}
      >
        <span className={gathering ? styles.gatherIcon : undefined}>
          <AgentRoleIcon role={agent.role} />
        </span>
        {agent.status !== 'DEAD' && (
          <div className={styles.healthBar}>
            <div
              className={styles.healthFill}
              style={{ width: `${healthPct}%`, backgroundColor: healthColor }}
            />
          </div>
        )}
        <span className={styles.nameTag}>{agent.name}</span>
        {action && (
          <ActionBubble
            key={`${agent.name}-${agent.currentTaskType ?? ''}`}
            action={action}
            reasoning={reasoning}
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
    prev.agent.currentAction === next.agent.currentAction &&
    prev.agent.currentTaskType === next.agent.currentTaskType &&
    prev.selected === next.selected &&
    prev.latestTask?.tick === next.latestTask?.tick,
);
