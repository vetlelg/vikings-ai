import type { AgentRole } from '../../types/world';

const iconStyle = { width: 22, height: 22 };

export function JarlIcon() {
  return (
    <svg viewBox="0 0 24 24" style={iconStyle}>
      <path d="M12 2L15 8H9L12 2Z" fill="#d4a843" />
      <path d="M5 8L8 14H4L5 8Z" fill="#d4a843" />
      <path d="M19 8L20 14H16L19 8Z" fill="#d4a843" />
      <rect x="7" y="14" width="10" height="6" rx="1" fill="#d4a843" />
    </svg>
  );
}

export function WarriorIcon() {
  return (
    <svg viewBox="0 0 24 24" style={iconStyle}>
      <path d="M12 2L14 10H10L12 2Z" fill="#c0392b" />
      <rect x="11" y="10" width="2" height="8" fill="#c0392b" />
      <rect x="8" y="16" width="8" height="2" rx="1" fill="#c0392b" />
    </svg>
  );
}

export function FishermanIcon() {
  return (
    <svg viewBox="0 0 24 24" style={iconStyle}>
      <ellipse cx="12" cy="12" rx="7" ry="4" fill="#3498db" />
      <path d="M19 12L23 8V16L19 12Z" fill="#3498db" />
      <circle cx="9" cy="11" r="1" fill="#0d0f14" />
    </svg>
  );
}

export function ShipbuilderIcon() {
  return (
    <svg viewBox="0 0 24 24" style={iconStyle}>
      <rect x="10" y="3" width="4" height="14" rx="1" fill="#8b6914" />
      <rect x="6" y="14" width="12" height="4" rx="1" fill="#8b6914" />
    </svg>
  );
}

export function SkaldIcon() {
  return (
    <svg viewBox="0 0 24 24" style={iconStyle}>
      <path d="M8 4C8 4 8 18 8 20" stroke="#9b59b6" strokeWidth="2" fill="none" />
      <path d="M8 4C8 4 16 4 16 8C16 12 8 12 8 12" stroke="#9b59b6" strokeWidth="2" fill="none" />
      <path d="M8 12C8 12 14 12 14 15C14 18 8 18 8 18" stroke="#9b59b6" strokeWidth="2" fill="none" />
    </svg>
  );
}

export function AgentRoleIcon({ role }: { role: AgentRole }) {
  switch (role) {
    case 'JARL': return <JarlIcon />;
    case 'WARRIOR': return <WarriorIcon />;
    case 'FISHERMAN': return <FishermanIcon />;
    case 'SHIPBUILDER': return <ShipbuilderIcon />;
    case 'SKALD': return <SkaldIcon />;
  }
}
