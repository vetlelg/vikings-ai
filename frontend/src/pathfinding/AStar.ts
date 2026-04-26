import { MAP_WIDTH, MAP_HEIGHT, MAP_DATA } from '../map/MapData';
import { TILE_WALKABLE } from '../map/TileTypes';

interface Node {
  x: number;
  y: number;
  g: number;
  h: number;
  f: number;
  parent: Node | null;
}

function heuristic(ax: number, ay: number, bx: number, by: number): number {
  return Math.abs(ax - bx) + Math.abs(ay - by);
}

const NEIGHBORS = [
  { dx: 0, dy: -1 },
  { dx: 1, dy: 0 },
  { dx: 0, dy: 1 },
  { dx: -1, dy: 0 },
  { dx: 1, dy: -1 },
  { dx: 1, dy: 1 },
  { dx: -1, dy: 1 },
  { dx: -1, dy: -1 },
];

export function isWalkable(x: number, y: number, occupiedTiles?: Set<string>): boolean {
  if (x < 0 || x >= MAP_WIDTH || y < 0 || y >= MAP_HEIGHT) return false;
  if (!TILE_WALKABLE[MAP_DATA[y][x]]) return false;
  if (occupiedTiles && occupiedTiles.has(`${x},${y}`)) return false;
  return true;
}

export function findPath(
  startX: number,
  startY: number,
  endX: number,
  endY: number,
  occupiedTiles?: Set<string>,
): { x: number; y: number }[] | null {
  if (!isWalkable(endX, endY, occupiedTiles)) return null;
  if (startX === endX && startY === endY) return [];

  const open: Node[] = [];
  const closed = new Set<string>();

  const startNode: Node = {
    x: startX,
    y: startY,
    g: 0,
    h: heuristic(startX, startY, endX, endY),
    f: 0,
    parent: null,
  };
  startNode.f = startNode.g + startNode.h;
  open.push(startNode);

  let iterations = 0;
  const maxIterations = 2000;

  while (open.length > 0 && iterations < maxIterations) {
    iterations++;

    let lowestIdx = 0;
    for (let i = 1; i < open.length; i++) {
      if (open[i].f < open[lowestIdx].f) lowestIdx = i;
    }
    const current = open.splice(lowestIdx, 1)[0];

    if (current.x === endX && current.y === endY) {
      const path: { x: number; y: number }[] = [];
      let node: Node | null = current;
      while (node && (node.x !== startX || node.y !== startY)) {
        path.unshift({ x: node.x, y: node.y });
        node = node.parent;
      }
      return path;
    }

    const key = `${current.x},${current.y}`;
    if (closed.has(key)) continue;
    closed.add(key);

    for (const { dx, dy } of NEIGHBORS) {
      const nx = current.x + dx;
      const ny = current.y + dy;
      const nKey = `${nx},${ny}`;

      if (!isWalkable(nx, ny) || closed.has(nKey)) continue;

      // Prevent diagonal movement through walls
      if (dx !== 0 && dy !== 0) {
        if (!isWalkable(current.x + dx, current.y) || !isWalkable(current.x, current.y + dy)) {
          continue;
        }
      }

      const moveCost = dx !== 0 && dy !== 0 ? 1.414 : 1;
      const g = current.g + moveCost;
      const h = heuristic(nx, ny, endX, endY);
      const f = g + h;

      const existingIdx = open.findIndex((n) => n.x === nx && n.y === ny);
      if (existingIdx >= 0 && open[existingIdx].g <= g) continue;

      const neighbor: Node = { x: nx, y: ny, g, h, f, parent: current };
      if (existingIdx >= 0) {
        open[existingIdx] = neighbor;
      } else {
        open.push(neighbor);
      }
    }
  }

  return null;
}
