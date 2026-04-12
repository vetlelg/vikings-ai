import { SagaLog } from './SagaLog';
import { EventLog } from './EventLog';

export function SidePanel() {
  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 12,
        minHeight: 0,
        overflow: 'hidden',
      }}
    >
      <SagaLog />
      <EventLog />
    </div>
  );
}
