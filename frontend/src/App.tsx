import { useGameSocket } from './hooks/useGameSocket';
import { TopBar } from './components/TopBar/TopBar';
import { GameGrid } from './components/GameGrid/GameGrid';
import { SidePanel } from './components/SidePanel/SidePanel';
import { CommandPanel } from './components/CommandPanel/CommandPanel';
import styles from './App.module.css';

export default function App() {
  const { sendCommand } = useGameSocket();

  return (
    <div className={styles.app}>
      <TopBar />
      <main className={styles.main}>
        <div className={styles.gridArea}>
          <GameGrid />
        </div>
        <SidePanel />
      </main>
      <CommandPanel sendCommand={sendCommand} />
    </div>
  );
}
