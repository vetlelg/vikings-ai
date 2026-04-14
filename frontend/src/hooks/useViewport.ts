import { useRef, useState, useCallback, useEffect } from 'react';

const MIN_ZOOM = 0.3;
const MAX_ZOOM = 2.0;
const ZOOM_STEP = 0.1;
const DRAG_THRESHOLD = 3; // pixels before a mousedown becomes a drag

interface ViewportState {
  zoom: number;
  panX: number;
  panY: number;
}

export function useViewport(contentWidth: number, contentHeight: number) {
  const containerElRef = useRef<HTMLDivElement | null>(null);
  const [state, setState] = useState<ViewportState>({ zoom: 1, panX: 0, panY: 0 });
  const stateRef = useRef(state);
  stateRef.current = state;

  const dragging = useRef(false);
  const dragStarted = useRef(false);
  const lastMouse = useRef({ x: 0, y: 0 });
  const startMouse = useRef({ x: 0, y: 0 });
  const initialized = useRef(false);
  const wheelCleanup = useRef<(() => void) | null>(null);

  // Fit-to-viewport on first load
  useEffect(() => {
    if (initialized.current || contentWidth === 0 || !containerElRef.current) return;
    const rect = containerElRef.current.getBoundingClientRect();
    const scaleX = rect.width / contentWidth;
    const scaleY = rect.height / contentHeight;
    const fitZoom = Math.min(scaleX, scaleY, 1) * 0.95;
    const clampedZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, fitZoom));
    const panX = (rect.width - contentWidth * clampedZoom) / 2;
    const panY = (rect.height - contentHeight * clampedZoom) / 2;
    setState({ zoom: clampedZoom, panX, panY });
    initialized.current = true;
  }, [contentWidth, contentHeight]);

  // Callback ref: attaches native wheel listener when element mounts
  const containerRef = useCallback((el: HTMLDivElement | null) => {
    // Clean up previous listener
    if (wheelCleanup.current) {
      wheelCleanup.current();
      wheelCleanup.current = null;
    }
    containerElRef.current = el;
    if (!el) return;

    const onWheel = (e: WheelEvent) => {
      e.preventDefault();
      const prev = stateRef.current;
      const direction = e.deltaY < 0 ? 1 : -1;
      const newZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, prev.zoom + direction * ZOOM_STEP));
      const rect = el.getBoundingClientRect();
      const mx = e.clientX - rect.left;
      const my = e.clientY - rect.top;
      const scale = newZoom / prev.zoom;
      const panX = mx - scale * (mx - prev.panX);
      const panY = my - scale * (my - prev.panY);
      setState({ zoom: newZoom, panX, panY });
    };

    el.addEventListener('wheel', onWheel, { passive: false });
    wheelCleanup.current = () => el.removeEventListener('wheel', onWheel);
  }, []);

  const handleMouseDown = useCallback((e: React.MouseEvent) => {
    if (e.button !== 0 && e.button !== 1) return;
    dragging.current = true;
    dragStarted.current = false;
    lastMouse.current = { x: e.clientX, y: e.clientY };
    startMouse.current = { x: e.clientX, y: e.clientY };
  }, []);

  const handleMouseMove = useCallback((e: React.MouseEvent) => {
    if (!dragging.current) return;
    const dx = e.clientX - lastMouse.current.x;
    const dy = e.clientY - lastMouse.current.y;
    lastMouse.current = { x: e.clientX, y: e.clientY };

    // Only start panning after the mouse has moved past the threshold
    if (!dragStarted.current) {
      const totalDx = Math.abs(e.clientX - startMouse.current.x);
      const totalDy = Math.abs(e.clientY - startMouse.current.y);
      if (totalDx < DRAG_THRESHOLD && totalDy < DRAG_THRESHOLD) return;
      dragStarted.current = true;
    }

    setState((prev) => ({
      ...prev,
      panX: prev.panX + dx,
      panY: prev.panY + dy,
    }));
  }, []);

  const handleMouseUp = useCallback(() => {
    dragging.current = false;
  }, []);

  // Release drag if mouse leaves window
  useEffect(() => {
    const onUp = () => { dragging.current = false; };
    window.addEventListener('mouseup', onUp);
    return () => window.removeEventListener('mouseup', onUp);
  }, []);

  const transform = `translate(${state.panX}px, ${state.panY}px) scale(${state.zoom})`;

  return {
    containerRef,
    transform,
    zoom: state.zoom,
    handlers: {
      onMouseDown: handleMouseDown,
      onMouseMove: handleMouseMove,
      onMouseUp: handleMouseUp,
    },
  };
}
