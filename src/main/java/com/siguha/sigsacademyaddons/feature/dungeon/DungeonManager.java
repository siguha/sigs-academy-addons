package com.siguha.sigsacademyaddons.feature.dungeon;

import com.siguha.sigsacademyaddons.SigsAcademyAddons;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Set;

public class DungeonManager {

    private static final ResourceKey<Level> DUNGEON_DIM = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("dungeons", "dungeons")
    );
    private static final int SPAWN_CAPTURE_DELAY = 5;

    private boolean inDungeon = false;
    private boolean wasInDungeon = false;
    private boolean spawnCaptured = false;
    private int spawnCaptureDelayTicks = 0;
    private final Set<BlockPos> openedChests = new HashSet<>();

    private final DungeonEntityScanner entityScanner = new DungeonEntityScanner();
    private final DungeonBlockScanner blockScanner = new DungeonBlockScanner();
    private final DungeonWorldRenderer worldRenderer = new DungeonWorldRenderer(this);

    public void tick() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            if (inDungeon) exitDungeon();
            return;
        }

        inDungeon = DUNGEON_DIM.equals(client.player.level().dimension());

        if (inDungeon && !wasInDungeon) {
            enterDungeon();
        } else if (!inDungeon && wasInDungeon) {
            exitDungeon();
        }
        wasInDungeon = inDungeon;

        if (!inDungeon) return;

        if (!spawnCaptured) {
            if (spawnCaptureDelayTicks > 0) {
                spawnCaptureDelayTicks--;
            } else {
                spawnCaptured = true;
                SigsAcademyAddons.LOGGER.info("[SAA Dungeon] Spawn captured, scanners active.");
            }
            return;
        }

        entityScanner.tick();
        blockScanner.tick();
    }

    private void enterDungeon() {
        spawnCaptureDelayTicks = SPAWN_CAPTURE_DELAY;
        spawnCaptured = false;
        SigsAcademyAddons.LOGGER.info("[SAA Dungeon] Entered dungeon dimension.");
    }

    private void exitDungeon() {
        inDungeon = false;
        spawnCaptured = false;
        entityScanner.clear();
        blockScanner.clear();
        openedChests.clear();
        SigsAcademyAddons.LOGGER.info("[SAA Dungeon] Left dungeon dimension, cleared state.");
    }

    public boolean isInDungeon() { return inDungeon; }
    public boolean isSpawnCaptured() { return spawnCaptured; }
    public DungeonEntityScanner getEntityScanner() { return entityScanner; }
    public DungeonBlockScanner getBlockScanner() { return blockScanner; }
    public DungeonWorldRenderer getWorldRenderer() { return worldRenderer; }

    public void markChestOpened(BlockPos pos) { openedChests.add(pos); }
    public boolean isChestOpened(BlockPos pos) { return openedChests.contains(pos); }
}
