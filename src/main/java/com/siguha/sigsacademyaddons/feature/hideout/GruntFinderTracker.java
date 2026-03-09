package com.siguha.sigsacademyaddons.feature.hideout;

import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.siguha.sigsacademyaddons.config.HudConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GruntFinderTracker {

    private static final int SCAN_INTERVAL_TICKS = 40;
    private static final double SCAN_RADIUS = 350.0;
    public static final int GRUNT_COLOR = 0xFF5555;

    private static final ResourceKey<Level> HIDEOUT_DIM = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("hideouts", "hideouts")
    );

    private final HudConfig hudConfig;
    private int tickCounter = 0;
    private volatile Set<Integer> matchedEntities = Collections.emptySet();

    public GruntFinderTracker(HudConfig hudConfig) {
        this.hudConfig = hudConfig;
    }

    public void tick() {
        tickCounter++;
        if (tickCounter % SCAN_INTERVAL_TICKS != 0) return;

        if (!hudConfig.isGruntFinderEnabled()) {
            if (!matchedEntities.isEmpty()) {
                matchedEntities = Collections.emptySet();
            }
            return;
        }

        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        Player player = client.player;
        if (level == null || player == null) return;

        if (!HIDEOUT_DIM.equals(level.dimension())) {
            if (!matchedEntities.isEmpty()) {
                matchedEntities = Collections.emptySet();
            }
            return;
        }

        AABB scanBox = player.getBoundingBox().inflate(SCAN_RADIUS);
        List<NPCEntity> nearbyNpcs = level.getEntitiesOfClass(NPCEntity.class, scanBox);

        Set<Integer> newMatches = new HashSet<>();

        for (NPCEntity npc : nearbyNpcs) {
            try {
                Component customName = npc.getCustomName();
                if (customName == null) continue;

                String name = customName.getString();
                if (name.contains("Grunt")) {
                    newMatches.add(npc.getId());
                }
            } catch (Exception ignored) {
            }
        }

        matchedEntities = newMatches;
    }

    public boolean isMatched(int entityId) {
        return matchedEntities.contains(entityId);
    }

    public Set<Integer> getMatchedEntities() {
        return matchedEntities;
    }
}
