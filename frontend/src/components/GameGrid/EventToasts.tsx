import { useEffect, useCallback } from 'react';
import { useGameStore } from '../../store/gameStore';
import type { Toast } from '../../types/world';
import styles from './EventToasts.module.css';

const eventIcons: Record<string, string> = {
  DRAGON_SIGHTED: '🐉',
  DRAGON_DEFEATED: '🗡️',
  AGENT_DIED: '💀',
  RAID_INCOMING: '⚔️',
  WOLF_SPOTTED: '🐺',
};

function ToastItem({ toast, onRemove }: { toast: Toast; onRemove: (id: string) => void }) {
  useEffect(() => {
    const timer = setTimeout(() => onRemove(toast.id), 4000);
    return () => clearTimeout(timer);
  }, [toast.id, onRemove]);

  return (
    <div className={`${styles.toast} ${styles[toast.severity]}`}>
      <span className={styles.icon}>{eventIcons[toast.eventType] || '⚠'}</span>
      <span className={styles.text}>{toast.text}</span>
    </div>
  );
}

export function EventToasts() {
  const toasts = useGameStore((s) => s.toasts);
  const removeToast = useGameStore((s) => s.removeToast);

  const handleRemove = useCallback(
    (id: string) => removeToast(id),
    [removeToast],
  );

  if (toasts.length === 0) return null;

  return (
    <div className={styles.container}>
      {toasts.map((t) => (
        <ToastItem key={t.id} toast={t} onRemove={handleRemove} />
      ))}
    </div>
  );
}
