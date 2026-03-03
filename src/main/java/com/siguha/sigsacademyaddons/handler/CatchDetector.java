package com.siguha.sigsacademyaddons.handler;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.storage.ClientBox;
import com.cobblemon.mod.common.client.storage.ClientPC;
import com.cobblemon.mod.common.client.storage.ClientParty;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.siguha.sigsacademyaddons.SigsAcademyAddons;
import com.siguha.sigsacademyaddons.data.EggGroupLookup;
import net.minecraft.client.Minecraft;

import java.util.*;
import java.util.function.Consumer;

public class CatchDetector {

    private final Set<UUID> knownPartyUuids = new HashSet<>();
    private boolean partyInitialized = false;

    private Set<UUID> knownPcUuids = null;
    private boolean pcScanRequested = false;
    private int pcScanDelayTicks = 0;
    private static final int PC_SCAN_DELAY = 10;
    private boolean pcAvailable = false;

    private Consumer<CaughtPokemonInfo> onCatchListener;
    private int ticksSinceLastLog = 0;
    private static final int DEBUG_LOG_INTERVAL = 600;

    public void setOnCatchListener(Consumer<CaughtPokemonInfo> listener) {
        this.onCatchListener = listener;
    }

    public void requestPcScan() {
        pcScanRequested = true;
        pcScanDelayTicks = 0;
    }

    public void tick(Minecraft client) {
        if (client.player == null) {
            if (partyInitialized) {
                knownPartyUuids.clear();
                knownPcUuids = null;
                partyInitialized = false;
                pcAvailable = false;
                pcScanRequested = false;
            }
            return;
        }

        tickPartyMonitoring(client);

        if (pcScanRequested) {
            pcScanDelayTicks++;
            if (pcScanDelayTicks >= PC_SCAN_DELAY) {
                pcScanRequested = false;
                performPcScan();
            }
        }
    }

    private void tickPartyMonitoring(Minecraft client) {
        try {
            ClientParty party = CobblemonClient.INSTANCE.getStorage().getParty();

            if (party == null) {
                if (ticksSinceLastLog >= DEBUG_LOG_INTERVAL) {
                    ticksSinceLastLog = 0;
                }
                ticksSinceLastLog++;
                return;
            }

            Set<UUID> currentUuids = new HashSet<>();
            for (int slot = 0; slot < 6; slot++) {
                Pokemon pokemon = party.get(slot);
                if (pokemon != null) {
                    currentUuids.add(pokemon.getUuid());
                }
            }

            if (!partyInitialized) {
                knownPartyUuids.addAll(currentUuids);
                partyInitialized = true;
                initializePcSnapshot();
                return;
            }

            ticksSinceLastLog++;
            if (ticksSinceLastLog >= DEBUG_LOG_INTERVAL) {
                SigsAcademyAddons.LOGGER.debug("[SAA CatchDetector] monitoring party",
                        currentUuids.size(),
                        pcAvailable ? (knownPcUuids != null ? knownPcUuids.size() + " Pokemon" : "pending") : "unavailable");
                ticksSinceLastLog = 0;
            }

            for (UUID uuid : currentUuids) {
                if (!knownPartyUuids.contains(uuid)) {
                    Pokemon pokemon = party.findByUUID(uuid);
                    if (pokemon != null) {
                        onNewPokemonDetected(pokemon);
                    }
                }
            }

            knownPartyUuids.clear();
            knownPartyUuids.addAll(currentUuids);

        } catch (Exception e) {
            if (ticksSinceLastLog >= DEBUG_LOG_INTERVAL) {
                SigsAcademyAddons.LOGGER.error("[SAA CatchDetector] error during monitoring: {}", e.getMessage());
                ticksSinceLastLog = 0;
            } 
            ticksSinceLastLog++;
        }
    }

    private void initializePcSnapshot() {
        try {
            Map<UUID, ClientPC> pcStores = CobblemonClient.INSTANCE.getStorage().getPcStores();
            if (pcStores == null || pcStores.isEmpty()) {
                return;
            }

            knownPcUuids = collectAllPcUuids(pcStores);
            pcAvailable = true;
        } catch (Exception e) {
            SigsAcademyAddons.LOGGER.warn("[SAA CatchDetector] Failed to initialize PC snapshot: {}", e.getMessage());
        }
    }

    private void performPcScan() {
        try {
            Map<UUID, ClientPC> pcStores = CobblemonClient.INSTANCE.getStorage().getPcStores();
            if (pcStores == null || pcStores.isEmpty()) {
                return;
            }

            Set<UUID> currentPcUuids = collectAllPcUuids(pcStores);

            if (knownPcUuids == null) {
                knownPcUuids = currentPcUuids;
                pcAvailable = true;

                return;
            }

            List<UUID> newUuids = new ArrayList<>();
            for (UUID uuid : currentPcUuids) {
                if (!knownPcUuids.contains(uuid)) {
                    newUuids.add(uuid);
                }
            }

            if (newUuids.isEmpty()) {
                SigsAcademyAddons.LOGGER.debug("[SAA CatchDetector] PC scan complete — no new Pokemon found");
            } else {
                SigsAcademyAddons.LOGGER.debug("[SAA CatchDetector] PC scan found {} new Pokemon", newUuids.size());
                for (UUID uuid : newUuids) {
                    Pokemon pokemon = findPokemonInPc(pcStores, uuid);
                    if (pokemon != null) {
                        SigsAcademyAddons.LOGGER.info("[SAA CatchDetector] New Pokemon detected in PC");
                        onNewPokemonDetected(pokemon);
                    }
                }
            }

            knownPcUuids = currentPcUuids;
            pcAvailable = true;

        } catch (Exception e) {
            SigsAcademyAddons.LOGGER.error("[SAA CatchDetector] PC scan failed: {}", e.getMessage());
        }
    }

    private Set<UUID> collectAllPcUuids(Map<UUID, ClientPC> pcStores) {
        Set<UUID> uuids = new HashSet<>();
        for (ClientPC clientPC : pcStores.values()) {
            for (ClientBox box : clientPC.getBoxes()) {
                for (Pokemon pokemon : box) {
                    if (pokemon != null) {
                        uuids.add(pokemon.getUuid());
                    }
                }
            }
        }
        return uuids;
    }

    private Pokemon findPokemonInPc(Map<UUID, ClientPC> pcStores, UUID pokemonUuid) {
        for (ClientPC clientPC : pcStores.values()) {
            Pokemon found = clientPC.findByUUID(pokemonUuid);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private void onNewPokemonDetected(Pokemon pokemon) {
        Species species = pokemon.getSpecies();
        String speciesName = species.getName();
        Set<String> types = new HashSet<>();
        try {
            types.add(species.getPrimaryType().getName());

            if (species.getSecondaryType() != null) {
                types.add(species.getSecondaryType().getName());
            }
        } catch (Exception e) {
            SigsAcademyAddons.LOGGER.warn("[SAA CatchDetector] Failed to get types for {}: {}", speciesName, e.getMessage());
        }

        Set<String> eggGroups = new HashSet<>();
        try {
            if (!species.getEggGroups().isEmpty()) {
                for (var eggGroup : species.getEggGroups()) {
                    eggGroups.add(eggGroup.name());

                }
            } else {
                Species registrySpecies = PokemonSpecies.INSTANCE.getByName(speciesName.toLowerCase());

                if (registrySpecies != null && !registrySpecies.getEggGroups().isEmpty()) {
                    for (var eggGroup : registrySpecies.getEggGroups()) {
                        eggGroups.add(eggGroup.name());

                    }
                }
            }
        } catch (Exception e) {
            SigsAcademyAddons.LOGGER.warn("[SAA CatchDetector] Failed to get egg groups via API for {}: {}", speciesName, e.getMessage());
        }

        if (eggGroups.isEmpty()) {
            Set<String> jsonEggGroups = EggGroupLookup.getEggGroups(speciesName);
            if (!jsonEggGroups.isEmpty()) {
                for (String eg : jsonEggGroups) {
                    eggGroups.add(eg.toUpperCase());
                }

            }
        }

        CaughtPokemonInfo info = new CaughtPokemonInfo(speciesName, types, eggGroups);

        if (onCatchListener != null) {
            onCatchListener.accept(info);
        }
    }

    public record CaughtPokemonInfo(
            String speciesName,
            Set<String> types,
            Set<String> eggGroups
    ) {
    }
}
