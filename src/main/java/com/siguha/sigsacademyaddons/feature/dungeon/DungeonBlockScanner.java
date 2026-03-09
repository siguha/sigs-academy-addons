package com.siguha.sigsacademyaddons.feature.dungeon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DungeonBlockScanner {

    private static final int SCAN_INTERVAL_TICKS = 100;
    private static final int SCAN_RADIUS = 96;

    private static final String GILDED_CHEST_ID = "cobblemon:gilded_chest";
    private static final String GIMMIGHOUL_CHEST_ID = "cobblemon:gimmighoul_chest";

    public record ScannedBlock(BlockPos pos) {}

    private int tickCounter = 0;
    private volatile List<ScannedBlock> scannedBlocks = Collections.emptyList();

    public void tick() {
        tickCounter++;
        if (tickCounter % SCAN_INTERVAL_TICKS != 0) return;

        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        Player player = client.player;
        if (level == null || player == null) return;

        BlockPos center = player.blockPosition();
        int chunkMinX = (center.getX() - SCAN_RADIUS) >> 4;
        int chunkMaxX = (center.getX() + SCAN_RADIUS) >> 4;
        int chunkMinZ = (center.getZ() - SCAN_RADIUS) >> 4;
        int chunkMaxZ = (center.getZ() + SCAN_RADIUS) >> 4;

        List<ScannedBlock> results = new ArrayList<>();

        for (int cx = chunkMinX; cx <= chunkMaxX; cx++) {
            for (int cz = chunkMinZ; cz <= chunkMaxZ; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) continue;

                chunk.getBlockEntities().forEach((pos, be) -> {
                    String id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
                    if (GILDED_CHEST_ID.equals(id) || GIMMIGHOUL_CHEST_ID.equals(id)) {
                        results.add(new ScannedBlock(pos));
                    }
                });
            }
        }

        scannedBlocks = results;
    }

    public List<ScannedBlock> getScannedBlocks() {
        return scannedBlocks;
    }

    public void clear() {
        scannedBlocks = Collections.emptyList();
        tickCounter = 0;
    }
}
