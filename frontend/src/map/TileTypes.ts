export enum TileType {
  GRASS = 0,
  FOREST = 1,
  STONE = 2,
  GOLD = 3,
  WATER = 4,
  DIRT = 5,
  BERRIES = 6,
}

export const TILE_COLORS: Record<TileType, number> = {
  [TileType.GRASS]: 0x4a7c3f,
  [TileType.FOREST]: 0x2d5a1e,
  [TileType.STONE]: 0x8a8a8a,
  [TileType.GOLD]: 0xd4a017,
  [TileType.WATER]: 0x2e6b9e,
  [TileType.DIRT]: 0x8b7355,
  [TileType.BERRIES]: 0xc45a9e,
};

export const TILE_WALKABLE: Record<TileType, boolean> = {
  [TileType.GRASS]: true,
  [TileType.FOREST]: true,
  [TileType.STONE]: true,
  [TileType.GOLD]: true,
  [TileType.WATER]: false,
  [TileType.DIRT]: true,
  [TileType.BERRIES]: true,
};

export const TILE_SIZE = 32;
