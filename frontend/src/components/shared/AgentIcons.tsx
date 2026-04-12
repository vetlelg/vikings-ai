import type { AgentRole } from '../../types/world';

const SIZE = 32;
const style = { width: SIZE, height: SIZE };

// Bjorn — Jarl: gold crown, fur cloak, commanding stance
export function JarlIcon() {
  return (
    <svg viewBox="0 0 32 32" style={style}>
      {/* Body / tunic */}
      <path d="M10 18 L12 28 L20 28 L22 18 Z" fill="#5a4320" />
      {/* Fur cloak */}
      <path d="M9 17 Q16 20 23 17 L22 22 Q16 24 10 22 Z" fill="#8b7040" />
      {/* Head */}
      <circle cx="16" cy="13" r="5" fill="#e8c89a" />
      {/* Beard */}
      <path d="M12 15 Q16 21 20 15" fill="#a07030" />
      {/* Helmet */}
      <path d="M11 12 Q16 6 21 12 Z" fill="#c0c0c0" />
      <rect x="15" y="7" width="2" height="3" rx="1" fill="#d4a843" />
      {/* Crown points */}
      <path d="M12 10 L10 7 L13 9 M20 10 L22 7 L19 9" stroke="#d4a843" strokeWidth="1.2" fill="none" />
      {/* Eyes */}
      <circle cx="14" cy="13" r="0.8" fill="#2a1a0a" />
      <circle cx="18" cy="13" r="0.8" fill="#2a1a0a" />
    </svg>
  );
}

// Astrid — Warrior: red shield, sword, fierce
export function WarriorIcon() {
  return (
    <svg viewBox="0 0 32 32" style={style}>
      {/* Body / armor */}
      <path d="M11 18 L12 28 L20 28 L21 18 Z" fill="#4a2020" />
      {/* Chainmail chest */}
      <path d="M11 17 Q16 19 21 17 L21 21 Q16 23 11 21 Z" fill="#707070" />
      {/* Head */}
      <circle cx="16" cy="13" r="4.5" fill="#e8c89a" />
      {/* Hair (braids) */}
      <path d="M11 13 Q10 18 9 22" stroke="#c44" strokeWidth="1.5" fill="none" />
      <path d="M21 13 Q22 18 23 22" stroke="#c44" strokeWidth="1.5" fill="none" />
      {/* Helmet */}
      <path d="M11.5 12 Q16 7 20.5 12 Z" fill="#808080" />
      {/* Horns */}
      <path d="M12 11 Q9 6 7 5" stroke="#e8d8b0" strokeWidth="1.5" fill="none" strokeLinecap="round" />
      <path d="M20 11 Q23 6 25 5" stroke="#e8d8b0" strokeWidth="1.5" fill="none" strokeLinecap="round" />
      {/* Eyes */}
      <circle cx="14.5" cy="13" r="0.8" fill="#2a1a0a" />
      <circle cx="17.5" cy="13" r="0.8" fill="#2a1a0a" />
      {/* Sword (right side) */}
      <line x1="23" y1="14" x2="27" y2="8" stroke="#c0c0c0" strokeWidth="1.5" strokeLinecap="round" />
      <line x1="22" y1="16" x2="24" y2="16" stroke="#8b6914" strokeWidth="1.5" />
    </svg>
  );
}

// Erik — Fisherman: blue tunic, fishing rod
export function FishermanIcon() {
  return (
    <svg viewBox="0 0 32 32" style={style}>
      {/* Body / tunic */}
      <path d="M11 18 L12 28 L20 28 L21 18 Z" fill="#2a4a6a" />
      {/* Vest */}
      <path d="M11 17 Q16 19 21 17 L21 21 Q16 22 11 21 Z" fill="#3a6a9a" />
      {/* Head */}
      <circle cx="16" cy="13" r="4.5" fill="#e8c89a" />
      {/* Beard (short) */}
      <path d="M13 15 Q16 18 19 15" fill="#8a7050" />
      {/* Hat / wool cap */}
      <path d="M11 12 Q16 7 21 12 Q16 10 11 12 Z" fill="#3498db" />
      <ellipse cx="16" cy="12" rx="5.5" ry="1.5" fill="#2a80b8" />
      {/* Eyes */}
      <circle cx="14.5" cy="13" r="0.8" fill="#2a1a0a" />
      <circle cx="17.5" cy="13" r="0.8" fill="#2a1a0a" />
      {/* Fishing rod */}
      <line x1="6" y1="10" x2="6" y2="24" stroke="#8b6914" strokeWidth="1.2" />
      <line x1="6" y1="10" x2="3" y2="14" stroke="#aaa" strokeWidth="0.7" strokeDasharray="1,1" />
    </svg>
  );
}

// Ingrid — Shipbuilder: brown tunic, hammer/axe
export function ShipbuilderIcon() {
  return (
    <svg viewBox="0 0 32 32" style={style}>
      {/* Body / tunic */}
      <path d="M11 18 L12 28 L20 28 L21 18 Z" fill="#5a3a1a" />
      {/* Apron */}
      <path d="M12 19 L12 26 L20 26 L20 19 Z" fill="#8b6914" opacity="0.6" />
      {/* Leather vest */}
      <path d="M11 17 Q16 19 21 17 L21 21 Q16 22 11 21 Z" fill="#6b4c2a" />
      {/* Head */}
      <circle cx="16" cy="13" r="4.5" fill="#e8c89a" />
      {/* Hair (braided up) */}
      <path d="M12 10 Q16 7 20 10" stroke="#a07030" strokeWidth="2" fill="none" />
      {/* Headband */}
      <path d="M11 12 Q16 10.5 21 12" stroke="#8b6914" strokeWidth="1.2" fill="none" />
      {/* Eyes */}
      <circle cx="14.5" cy="13" r="0.8" fill="#2a1a0a" />
      <circle cx="17.5" cy="13" r="0.8" fill="#2a1a0a" />
      {/* Hammer */}
      <line x1="24" y1="12" x2="24" y2="22" stroke="#6b4c2a" strokeWidth="1.3" />
      <rect x="22" y="10" width="5" height="3.5" rx="0.5" fill="#808080" />
    </svg>
  );
}

// Sigurd — Skald: purple robe, scroll/lyre
export function SkaldIcon() {
  return (
    <svg viewBox="0 0 32 32" style={style}>
      {/* Body / robe */}
      <path d="M10 18 L11 28 L21 28 L22 18 Z" fill="#4a2060" />
      {/* Robe collar */}
      <path d="M11 17 Q16 19 21 17 L21 20 Q16 22 11 20 Z" fill="#6a3a8a" />
      {/* Head */}
      <circle cx="16" cy="13" r="4.5" fill="#e8c89a" />
      {/* Long hair */}
      <path d="M11 11 Q10 16 10 20" stroke="#555" strokeWidth="1.5" fill="none" />
      <path d="M21 11 Q22 16 22 20" stroke="#555" strokeWidth="1.5" fill="none" />
      {/* Hood/cap */}
      <path d="M11.5 12 Q16 6.5 20.5 12 Z" fill="#5a3080" />
      {/* Eyes */}
      <circle cx="14.5" cy="13" r="0.8" fill="#2a1a0a" />
      <circle cx="17.5" cy="13" r="0.8" fill="#2a1a0a" />
      {/* Lyre */}
      <path d="M5 14 Q5 10 8 10 Q8 14 5 14 Z" stroke="#d4a843" strokeWidth="0.8" fill="none" />
      <line x1="5.5" y1="11" x2="5.5" y2="13.5" stroke="#d4a843" strokeWidth="0.5" />
      <line x1="7" y1="11" x2="7" y2="13.5" stroke="#d4a843" strokeWidth="0.5" />
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
