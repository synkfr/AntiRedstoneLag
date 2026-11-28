package org.ayosynk.antiRedstoneLag.util;

/**
 * Bit-packing helpers for chunk and block coordinates across all supported Minecraft world bounds.
 */
public final class BlockKeyCodec {

    private BlockKeyCodec() {
    }

    public static long packChunk(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    public static int unpackChunkX(long chunkKey) {
        return (int) (chunkKey >> 32);
    }

    public static int unpackChunkZ(long chunkKey) {
        return (int) chunkKey;
    }

    public static long packBlock(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    public static int unpackBlockX(long blockKey) {
        return (int) (blockKey >> 38);
    }

    public static int unpackBlockZ(long blockKey) {
        return (int) ((blockKey << 26) >> 38);
    }

    public static int unpackBlockY(long blockKey) {
        return (int) ((blockKey << 52) >> 52);
    }

    public static long chunkFromBlockKey(long blockKey) {
        return packChunk(unpackBlockX(blockKey) >> 4, unpackBlockZ(blockKey) >> 4);
    }
}
