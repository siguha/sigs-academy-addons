package com.siguha.sigsacademyaddons.feature.dungeon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DungeonEntityScanner {

    private static final int SCAN_INTERVAL_TICKS = 20;
    private static final double SCAN_RADIUS = 96.0;

    public enum DungeonEntityType { STAKE, POKELOOT }

    public record ScannedEntity(int entityId, DungeonEntityType type, BlockPos pos) {}

    private int tickCounter = 0;
    private volatile List<ScannedEntity> scannedEntities = Collections.emptyList();

    public void tick() {
        tickCounter++;
        if (tickCounter % SCAN_INTERVAL_TICKS != 0) return;

        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        Player player = client.player;
        if (level == null || player == null) return;

        AABB scanBox = player.getBoundingBox().inflate(SCAN_RADIUS);
        List<ScannedEntity> results = new ArrayList<>();

        for (ArmorStand stand : level.getEntitiesOfClass(ArmorStand.class, scanBox)) {
            try {
                if (stand.getCustomName() == null) continue;
                String name = stand.getCustomName().getString();

                if (name.contains("_stake")) {
                    results.add(new ScannedEntity(stand.getId(), DungeonEntityType.STAKE, stand.blockPosition()));
                } else if (name.contains("oasislayout")) {
                    results.add(new ScannedEntity(stand.getId(), DungeonEntityType.POKELOOT, stand.blockPosition()));
                }
            } catch (Exception ignored) {}
        }

        scannedEntities = results;
    }

    public List<ScannedEntity> getScannedEntities() {
        return scannedEntities;
    }

    public void clear() {
        scannedEntities = Collections.emptyList();
        tickCounter = 0;
    }
}
