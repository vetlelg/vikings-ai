import { memo } from 'react';
import type { EntitySnapshot } from '../../types/world';

const CELL = 39;

const entitySymbols: Record<string, { symbol: string; color: string }> = {
  WOLF: { symbol: '🐺', color: '#888' },
  RESOURCE_NODE: { symbol: '◆', color: '#d4a843' },
};

const subtypeSymbols: Record<string, { symbol: string; color: string }> = {
  timber: { symbol: '🪵', color: '#8b6914' },
  fish: { symbol: '🐟', color: '#3498db' },
  iron: { symbol: '⛏', color: '#7f8c8d' },
  furs: { symbol: '🦊', color: '#8b5e3c' },
};

interface Props {
  entity: EntitySnapshot;
}

export const EntityToken = memo(function EntityToken({ entity }: Props) {
  const x = entity.position.x * CELL;
  const y = entity.position.y * CELL;

  const display = entity.subtype
    ? subtypeSymbols[entity.subtype] || entitySymbols[entity.type]
    : entitySymbols[entity.type] || { symbol: '?', color: '#fff' };

  return (
    <div
      style={{
        position: 'absolute',
        width: 'var(--cell-size)',
        height: 'var(--cell-size)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        transform: `translate(${x}px, ${y}px)`,
        transition: 'transform var(--transition-glide)',
        zIndex: entity.type === 'WOLF' ? 8 : 5,
        fontSize: entity.type === 'WOLF' ? 18 : 14,
        pointerEvents: 'none',
      }}
      title={entity.subtype || entity.type}
    >
      {display.symbol}
    </div>
  );
});
