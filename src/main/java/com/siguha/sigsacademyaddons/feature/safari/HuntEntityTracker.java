package com.siguha.sigsacademyaddons.feature.safari;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.siguha.sigsacademyaddons.SigsAcademyAddons;
import com.siguha.sigsacademyaddons.config.HudConfig;
import com.siguha.sigsacademyaddons.data.EggGroupLookup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.*;

// scans nearby pokemon entities and caches hunt matches for glow/tracer rendering
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

    // volatile for thread-safe access from render thread: entityId to 0xRRGGBB
    private volatile Map<Integer, Integer> matchedEntities = Collections.emptyMap();

    public HuntEntityTracker(SafariManager safariManager,
                             SafariHuntManager safariHuntManager,
                             HudConfig hudConfig) {
        this.safariManager = safariManager;
        this.safariHuntManager = safariHuntManager;
        this.hudConfig = hudConfig;
    }

    // scans for matching entities every ~1 second
    public void tick() {
        tickCounter++;
        if (tickCounter % SCAN_INTERVAL_TICKS != 0) return;

        // skip if both glow and tracers are disabled
        if (!hudConfig.isSafariQuestMonGlow() && !hudConfig.isSafariQuestMonTracers()) {
            if (!matchedEntities.isEmpty()) {
                matchedEntities = Collections.emptyMap();
            }
            return;
        }

        // skip if not in safari zone
        if (!safariManager.isInSafariZone()) {
            if (!matchedEntities.isEmpty()) {
                matchedEntities = Collections.emptyMap();
            }
            return;
        }

        // skip if no active hunts
        if (!safariHuntManager.hasActiveHunts()) {
            if (!matchedEntities.isEmpty()) {
                matchedEntities = Collections.emptyMap();
            }
            return;
        }

        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        Player player = client.player;
        if (level == null || player == null) return;

        // collect incomplete hunts with their slot indices
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
            }
            return;
        }

        // scan nearby pokemon entities
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

                // extract types
                Set<String> types = new HashSet<>();
                try {
                    types.add(species.getPrimaryType().getName());
                    if (species.getSecondaryType() != null) {
                        types.add(species.getSecondaryType().getName());
                    }
                } catch (Exception e) {
                    // type data unavailable
                }

                // egg groups via client-side json fallback
                Set<String> eggGroups = EggGroupLookup.getEggGroups(speciesName);

                // match against hunts (lowest index wins for color for mons of multiple types)
                for (IndexedHunt ih : incompleteHunts) {
                    if (doesEntityMatchHunt(types, eggGroups, ih.hunt)) {
                        int color = SLOT_COLORS[ih.index % SLOT_COLORS.length];
                        newMatches.put(pokemonEntity.getId(), color);
                        break;
                    }
                }
            } catch (Exception e) {
                // skip entities still spawning or with inaccessible data
            }
        }

        matchedEntities = newMatches;
    }

    // checks if entity types/egg groups match a hunt (mirrors SafariHuntManager logic)
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

    // whether entity is matched (called from glow mixin on render thread)
    public boolean isMatched(int entityId) {
        return matchedEntities.containsKey(entityId);
    }

    // returns color for matched entity or -1 (called from color mixin on render thread)
    public int getColor(int entityId) {
        Integer color = matchedEntities.get(entityId);
        return color != null ? color : -1;
    }

    public Map<Integer, Integer> getMatchedEntities() {
        return matchedEntities;
    }

    // returns color for a quest slot index (used by hud renderer for indicators)
    public static int getSlotColor(int slotIndex) {
        return SLOT_COLORS[slotIndex % SLOT_COLORS.length];
    }

    // pairs a hunt with its slot index for color assignment
    private record IndexedHunt(int index, SafariHuntData hunt) {
    }
}
