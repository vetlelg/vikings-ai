import { TileType } from './TileTypes';

export const MAP_WIDTH = 64;
export const MAP_HEIGHT = 64;

const G = TileType.GRASS;
const F = TileType.FOREST;
const S = TileType.STONE;
const O = TileType.GOLD;
const W = TileType.WATER;
const D = TileType.DIRT;
const B = TileType.BERRIES;

function createMap(): TileType[][] {
  const map: TileType[][] = [];
  for (let y = 0; y < MAP_HEIGHT; y++) {
    map[y] = [];
    for (let x = 0; x < MAP_WIDTH; x++) {
      map[y][x] = G;
    }
  }

  function set(x: number, y: number, tile: TileType) {
    if (x >= 0 && x < MAP_WIDTH && y >= 0 && y < MAP_HEIGHT) {
      map[y][x] = tile;
    }
  }

  function fillRect(x1: number, y1: number, w: number, h: number, tile: TileType) {
    for (let y = y1; y < y1 + h; y++) {
      for (let x = x1; x < x1 + w; x++) {
        set(x, y, tile);
      }
    }
  }

  function mirrorSet(x: number, y: number, tile: TileType) {
    set(x, y, tile);
    set(MAP_WIDTH - 1 - x, MAP_HEIGHT - 1 - y, tile);
  }

  function mirrorRect(x1: number, y1: number, w: number, h: number, tile: TileType) {
    for (let y = y1; y < y1 + h; y++) {
      for (let x = x1; x < x1 + w; x++) {
        mirrorSet(x, y, tile);
      }
    }
  }

  // --- Base areas (top-left and bottom-right, mirrored) ---
  mirrorRect(4, 4, 7, 7, D);

  // --- Berry bushes near each base (food) ---
  mirrorRect(3, 14, 3, 2, B);
  mirrorRect(14, 3, 2, 3, B);
  mirrorRect(10, 10, 2, 2, B);

  // --- Forest clusters (wood) ---
  // Near bases
  mirrorRect(0, 18, 4, 5, F);
  mirrorRect(18, 0, 5, 4, F);
  mirrorRect(12, 10, 4, 4, F);
  mirrorRect(2, 12, 3, 3, F);

  // Mid-map forests
  mirrorRect(8, 24, 5, 4, F);
  mirrorRect(24, 8, 4, 5, F);
  mirrorRect(16, 18, 3, 3, F);
  mirrorRect(18, 16, 3, 3, F);

  // Central forest patches
  mirrorRect(27, 22, 4, 3, F);
  mirrorRect(22, 27, 3, 4, F);

  // Outer edge forests
  mirrorRect(0, 30, 3, 4, F);
  mirrorRect(0, 40, 4, 3, F);

  // --- Stone deposits ---
  mirrorRect(16, 20, 2, 2, S);
  mirrorRect(20, 16, 2, 2, S);
  mirrorRect(10, 16, 2, 2, S);

  // --- Gold deposits (more contested, toward center) ---
  mirrorRect(24, 24, 2, 2, O);
  mirrorRect(30, 18, 2, 2, O);
  mirrorRect(18, 30, 2, 2, O);

  // --- Water features ---
  // Central lake
  fillRect(29, 29, 6, 6, W);
  fillRect(28, 30, 1, 4, W);
  fillRect(35, 30, 1, 4, W);
  fillRect(30, 28, 4, 1, W);
  fillRect(30, 35, 4, 1, W);

  // Ponds near edges
  mirrorRect(0, 28, 2, 4, W);
  mirrorRect(28, 0, 4, 2, W);

  // Small pond mid-map
  mirrorRect(20, 12, 2, 2, W);

  // --- Dirt paths connecting bases toward center ---
  for (let i = 11; i < 28; i++) {
    mirrorSet(i, i, D);
    mirrorSet(i + 1, i, D);
    mirrorSet(i, i + 1, D);
  }

  // Side paths
  for (let i = 11; i < 22; i++) {
    mirrorSet(i, 7, D);
    mirrorSet(7, i, D);
  }

  return map;
}

export const MAP_DATA = createMap();

export interface FactionStart {
  townCenterX: number;
  townCenterY: number;
  workerPositions: { x: number; y: number }[];
}

export const FACTION_STARTS: [FactionStart, FactionStart] = [
  {
    townCenterX: 6,
    townCenterY: 6,
    workerPositions: [
      { x: 5, y: 9 },
      { x: 7, y: 9 },
      { x: 9, y: 7 },
    ],
  },
  {
    townCenterX: MAP_WIDTH - 7,
    townCenterY: MAP_HEIGHT - 7,
    workerPositions: [
      { x: MAP_WIDTH - 6, y: MAP_HEIGHT - 10 },
      { x: MAP_WIDTH - 8, y: MAP_HEIGHT - 10 },
      { x: MAP_WIDTH - 10, y: MAP_HEIGHT - 8 },
    ],
  },
];
