package com.siguha.sigsacademyaddons.feature.safari;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.siguha.sigsacademyaddons.SigsAcademyAddons;
import com.siguha.sigsacademyaddons.config.HudConfig;
import com.siguha.sigsacademyaddons.data.EggGroupLookup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class HuntEntityTracker {

    private static final int SCAN_INTERVAL_TICKS = 20;
    private static final double SCAN_RADIUS = 64.0;

    public static final int[] SLOT_COLORS = {
            0xFF5555, 0x5555FF, 0x55FF55, 0xFFFF55, 0x55FFFF, 0xFF55FF
    };

    private final SafariManager safariManager;
    private final SafariHuntManager safariHuntManager;
    private final HudConfig hudConfig;

    private int tickCounter = 0;

    private volatile Map<Integer, Integer> matchedEntities = Collections.emptyMap();
    private volatile Set<Integer> visibleEntities = Collections.emptySet();

    public HuntEntityTracker(SafariManager safariManager,
                             SafariHuntManager safariHuntManager,
                             HudConfig hudConfig) {
        this.safariManager = safariManager;
        this.safariHuntManager = safariHuntManager;
        this.hudConfig = hudConfig;
    }

    public void tick() {
        tickCounter++;
        if (tickCounter % SCAN_INTERVAL_TICKS != 0) return;

        if (!hudConfig.isSafariQuestMonGlow() && !hudConfig.isSafariQuestMonTracers()) {
            if (!matchedEntities.isEmpty()) {
                matchedEntities = Collections.emptyMap();
                visibleEntities = Collections.emptySet();
            }

            return;
        }

        if (!safariManager.isInSafariZone()) {
            if (!matchedEntities.isEmpty()) {
                matchedEntities = Collections.emptyMap();
                visibleEntities = Collections.emptySet();
            }

            return;
        }

        if (!safariHuntManager.hasActiveHunts()) {
            if (!matchedEntities.isEmpty()) {
                matchedEntities = Collections.emptyMap();
                visibleEntities = Collections.emptySet();
            }

            return;
        }

        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        Player player = client.player;
        if (level == null || player == null) return;

        List<SafariHuntData> allHunts = safariHuntManager.getActiveHunts();
        List<IndexedHunt> incompleteHunts = new ArrayList<>();

        for (int i = 0; i < allHunts.size(); i++) {
            SafariHuntData hunt = allHunts.get(i);
            if (!hunt.isComplete() && hunt.getCategory() != SafariHuntData.HuntCategory.UNKNOWN) {
                incompleteHunts.add(new IndexedHunt(i, hunt));
            }
        }

        if (incompleteHunts.isEmpty()) {
            if (!matchedEntities.isEmpty()) {
                matchedEntities = Collections.emptyMap();
                visibleEntities = Collections.emptySet();
            }

            return;
        }

        AABB scanBox = player.getBoundingBox().inflate(SCAN_RADIUS);
        List<PokemonEntity> nearbyPokemon = level.getEntitiesOfClass(PokemonEntity.class, scanBox);

        Map<Integer, Integer> newMatches = new HashMap<>();

        for (PokemonEntity pokemonEntity : nearbyPokemon) {
            try {
                Pokemon pokemon = pokemonEntity.getPokemon();
                if (pokemon == null) continue;

                Species species = pokemon.getSpecies();
                if (species == null) continue;

                String speciesName = species.getName();

                Set<String> types = new HashSet<>();
                try {
                    types.add(species.getPrimaryType().getName());
                    if (species.getSecondaryType() != null) {
                        types.add(species.getSecondaryType().getName());
                    }
                } catch (Exception e) {
                }

                Set<String> eggGroups = EggGroupLookup.getEggGroups(speciesName);

                for (IndexedHunt ih : incompleteHunts) {
                    if (doesEntityMatchHunt(types, eggGroups, ih.hunt)) {
                        int color = SLOT_COLORS[ih.index % SLOT_COLORS.length];
                        newMatches.put(pokemonEntity.getId(), color);

                        break;
                    }
                }
            } catch (Exception e) {
            }
        }

        matchedEntities = newMatches;

        Set<Integer> newVisible = new HashSet<>();
        Vec3 playerEye = player.getEyePosition(1.0f);

        for (int entityId : newMatches.keySet()) {
            Entity entity = level.getEntity(entityId);

            if (entity == null) continue;

            ClipContext ctx = new ClipContext(playerEye, entity.getEyePosition(1.0f),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player);

            if (level.clip(ctx).getType() == HitResult.Type.MISS) {
                newVisible.add(entityId);
            }
        }
        visibleEntities = newVisible;
    }

    private boolean doesEntityMatchHunt(Set<String> types, Set<String> eggGroups, SafariHuntData hunt) {
        switch (hunt.getCategory()) {
            case TYPE:
                for (String target : hunt.getTargets()) {
                    for (String pokemonType : types) {
                        if (pokemonType.equalsIgnoreCase(target.trim())) {
                            return true;
                        }
                    }
                }
                return false;

            case EGG_GROUP:
                for (String target : hunt.getTargets()) {
                    String normalizedTarget = SigsAcademyAddons.normalizeForComparison(target);
                    for (String pokemonEggGroup : eggGroups) {
                        String normalizedEggGroup = SigsAcademyAddons.normalizeForComparison(pokemonEggGroup);
                        if (normalizedEggGroup.equals(normalizedTarget)) {
                            return true;
                        }
                    }
                }
                return false;

            default:
                return false;
        }
    }

    public boolean isMatched(int entityId) {
        return matchedEntities.containsKey(entityId);
    }

    public boolean hasLineOfSight(int entityId) {
        return visibleEntities.contains(entityId);
    }

    public int getColor(int entityId) {
        Integer color = matchedEntities.get(entityId);
        return color != null ? color : -1;
    }

    public Map<Integer, Integer> getMatchedEntities() {
        return matchedEntities;
    }

    public static int getSlotColor(int slotIndex) {
        return SLOT_COLORS[slotIndex % SLOT_COLORS.length];
    }

    private record IndexedHunt(int index, SafariHuntData hunt) {
    }
}
