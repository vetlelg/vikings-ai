import { useEffect, useRef, useCallback } from 'react';
import { useGameStore } from '../store/gameStore';
import type { WSMessage, WorldState, AgentTask, WorldEvent, SagaLogEntry, WorldCommand } from '../types/world';

const WS_URL = import.meta.env.VITE_WS_URL || 'ws://localhost:8080/ws';

export function useGameSocket() {
  const wsRef = useRef<WebSocket | null>(null);
  const reconnectTimer = useRef<ReturnType<typeof setTimeout>>();
  const attemptRef = useRef(0);

  const applyWorldState = useGameStore((s) => s.applyWorldState);
  const addAgentTask = useGameStore((s) => s.addAgentTask);
  const addWorldEvent = useGameStore((s) => s.addWorldEvent);
  const addSagaEntry = useGameStore((s) => s.addSagaEntry);
  const setConnected = useGameStore((s) => s.setConnected);

  const connect = useCallback(() => {
    // Close any existing connection before opening a new one
    // (prevents duplicates under React StrictMode)
    if (wsRef.current) {
      wsRef.current.onclose = null;
      wsRef.current.close();
      wsRef.current = null;
    }

    try {
      const ws = new WebSocket(WS_URL);

      ws.onopen = () => {
        console.log('WebSocket connected');
        setConnected(true);
        attemptRef.current = 0;
      };

      ws.onmessage = (event) => {
        try {
          const msg: WSMessage = JSON.parse(event.data);
          switch (msg.topic) {
            case 'world-state':
              applyWorldState(msg.payload as WorldState);
              break;
            case 'agent-tasks':
              addAgentTask(msg.payload as AgentTask);
              break;
            case 'world-events':
              addWorldEvent(msg.payload as WorldEvent);
              break;
            case 'saga-log':
              addSagaEntry(msg.payload as SagaLogEntry);
              break;
          }
        } catch (e) {
          console.warn('Failed to parse WebSocket message:', e);
        }
      };

      ws.onclose = () => {
        setConnected(false);
        wsRef.current = null;
        // Reconnect with backoff
        const delay = Math.min(1000 * Math.pow(2, attemptRef.current), 10000);
        attemptRef.current++;
        reconnectTimer.current = setTimeout(connect, delay);
      };

      ws.onerror = () => {
        // onclose will fire after this
      };

      wsRef.current = ws;
    } catch (e) {
      console.warn('WebSocket connection failed:', e);
      const delay = Math.min(1000 * Math.pow(2, attemptRef.current), 10000);
      attemptRef.current++;
      reconnectTimer.current = setTimeout(connect, delay);
    }
  }, [applyWorldState, addAgentTask, addWorldEvent, addSagaEntry, setConnected]);

  useEffect(() => {
    connect();
    return () => {
      clearTimeout(reconnectTimer.current);
      wsRef.current?.close();
    };
  }, [connect]);

  const sendCommand = useCallback(
    (cmd: WorldCommand) => {
      if (wsRef.current?.readyState === WebSocket.OPEN) {
        wsRef.current.send(JSON.stringify(cmd));
      }
    },
    [],
  );

  return { sendCommand };
}
