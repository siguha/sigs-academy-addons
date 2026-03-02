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

// detects catches by monitoring party uuids (continuous) and pc storage (triggered)
public class CatchDetector {

    // party monitoring
    private final Set<UUID> knownPartyUuids = new HashSet<>();
    private boolean partyInitialized = false;

    // pc monitoring (triggered by hunt progress messages)
    private Set<UUID> knownPcUuids = null;
    private boolean pcScanRequested = false;
    private int pcScanDelayTicks = 0;
    private static final int PC_SCAN_DELAY = 10; // 0.5s delay for storage sync
    private boolean pcAvailable = false;

    private Consumer<CaughtPokemonInfo> onCatchListener;
    private int ticksSinceLastLog = 0;
    private static final int DEBUG_LOG_INTERVAL = 600;

    public void setOnCatchListener(Consumer<CaughtPokemonInfo> listener) {
        this.onCatchListener = listener;
    }

    // requests a delayed pc scan after hunt progress message
    public void requestPcScan() {
        pcScanRequested = true;
        pcScanDelayTicks = 0;
        SigsAcademyAddons.LOGGER.debug("[sig CatchDetector] PC scan requested — will scan in {} ticks", PC_SCAN_DELAY);
    }

    // checks for party changes and processes pending pc scans each tick
    public void tick(Minecraft client) {
        if (client.player == null) {
            if (partyInitialized) {
                knownPartyUuids.clear();
                knownPcUuids = null;
                partyInitialized = false;
                pcAvailable = false;
                pcScanRequested = false;
                SigsAcademyAddons.LOGGER.debug("[sig CatchDetector] Reset — player left world");
            }
            return;
        }

        tickPartyMonitoring(client);

        // pc scan (triggered, delayed)
        if (pcScanRequested) {
            pcScanDelayTicks++;
            if (pcScanDelayTicks >= PC_SCAN_DELAY) {
                pcScanRequested = false;
                performPcScan();
            }
        }
    }

    // monitors party for new pokemon additions each tick
    private void tickPartyMonitoring(Minecraft client) {
        try {
            ClientParty party = CobblemonClient.INSTANCE.getStorage().getParty();

            if (party == null) {
                if (ticksSinceLastLog >= DEBUG_LOG_INTERVAL) {
                    SigsAcademyAddons.LOGGER.warn("[sig CatchDetector] party null");
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
                // first tick — snapshot only, no events
                knownPartyUuids.addAll(currentUuids);
                partyInitialized = true;
                SigsAcademyAddons.LOGGER.debug("[sig CatchDetector] party init", currentUuids.size());
                initializePcSnapshot();
                return;
            }

            // periodic debug log
            ticksSinceLastLog++;
            if (ticksSinceLastLog >= DEBUG_LOG_INTERVAL) {
                SigsAcademyAddons.LOGGER.debug("[sig CatchDetector] monitoring party",
                        currentUuids.size(),
                        pcAvailable ? (knownPcUuids != null ? knownPcUuids.size() + " Pokemon" : "pending") : "unavailable");
                ticksSinceLastLog = 0;
            }

            // detect new pokemon in party
            for (UUID uuid : currentUuids) {
                if (!knownPartyUuids.contains(uuid)) {
                    Pokemon pokemon = party.findByUUID(uuid);
                    if (pokemon != null) {
                        SigsAcademyAddons.LOGGER.info("[sig CatchDetector] new pokemon in party");
                        onNewPokemonDetected(pokemon);
                    }
                }
            }

            // update snapshot
            knownPartyUuids.clear();
            knownPartyUuids.addAll(currentUuids);

        } catch (Exception e) {
            if (ticksSinceLastLog >= DEBUG_LOG_INTERVAL) {
                SigsAcademyAddons.LOGGER.error("[sig CatchDetector] error during monitoring: {}", e.getMessage());
                ticksSinceLastLog = 0;
            } 
            ticksSinceLastLog++;
        }
    }

    // takes initial pc uuid snapshot on first load
    private void initializePcSnapshot() {
        try {
            Map<UUID, ClientPC> pcStores = CobblemonClient.INSTANCE.getStorage().getPcStores();
            if (pcStores == null || pcStores.isEmpty()) {
                SigsAcademyAddons.LOGGER.debug("[sig CatchDetector] pc not yet avail");
                return;
            }

            knownPcUuids = collectAllPcUuids(pcStores);
            pcAvailable = true;
            SigsAcademyAddons.LOGGER.debug("[sig CatchDetector] pc snapshot init", knownPcUuids.size());
        } catch (Exception e) {
            SigsAcademyAddons.LOGGER.warn("[sig CatchDetector] Failed to initialize PC snapshot: {}", e.getMessage());
        }
    }

    // scans pc boxes for new pokemon not in our snapshot
    private void performPcScan() {
        try {
            Map<UUID, ClientPC> pcStores = CobblemonClient.INSTANCE.getStorage().getPcStores();
            if (pcStores == null || pcStores.isEmpty()) {
                SigsAcademyAddons.LOGGER.warn("[sig CatchDetector] PC scan failed — PC stores unavailable");
                return;
            }

            Set<UUID> currentPcUuids = collectAllPcUuids(pcStores);

            if (knownPcUuids == null) {
                // first pc access — initialize snapshot only
                knownPcUuids = currentPcUuids;
                pcAvailable = true;
                SigsAcademyAddons.LOGGER.debug("[sig CatchDetector] PC snapshot initialized during scan ({} Pokemon)",
                        currentPcUuids.size());
                return;
            }

            // find new pokemon in pc
            List<UUID> newUuids = new ArrayList<>();
            for (UUID uuid : currentPcUuids) {
                if (!knownPcUuids.contains(uuid)) {
                    newUuids.add(uuid);
                }
            }

            if (newUuids.isEmpty()) {
                SigsAcademyAddons.LOGGER.debug("[sig CatchDetector] PC scan complete — no new Pokemon found");
            } else {
                SigsAcademyAddons.LOGGER.debug("[sig CatchDetector] PC scan found {} new Pokemon", newUuids.size());
                for (UUID uuid : newUuids) {
                    Pokemon pokemon = findPokemonInPc(pcStores, uuid);
                    if (pokemon != null) {
                        SigsAcademyAddons.LOGGER.info("[sig CatchDetector] New Pokemon detected in PC");
                        onNewPokemonDetected(pokemon);
                    }
                }
            }

            // update pc snapshot
            knownPcUuids = currentPcUuids;
            pcAvailable = true;

        } catch (Exception e) {
            SigsAcademyAddons.LOGGER.error("[sig CatchDetector] PC scan failed: {}", e.getMessage());
        }
    }

    // collects all pokemon uuids across all pc stores and boxes
    private Set<UUID> collectAllPcUuids(Map<UUID, ClientPC> pcStores) {
        Set<UUID> uuids = new HashSet<>();
        for (ClientPC clientPC : pcStores.values()) {
            for (ClientBox box : clientPC.getBoxes()) {
                for (Pokemon pokemon : box) {
                    if (pokemon != null) { // iterator can yield null for empty slots
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

    // extracts species info and notifies listeners
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
            SigsAcademyAddons.LOGGER.warn("[sig CatchDetector] Failed to get types for {}: {}", speciesName, e.getMessage());
        }

        // egg groups: try api → registry → species json fallback
        Set<String> eggGroups = new HashSet<>();
        try {
            if (!species.getEggGroups().isEmpty()) {
                for (var eggGroup : species.getEggGroups()) {
                    eggGroups.add(eggGroup.name());
                }
            } else {
                // fallback: global registry
                Species registrySpecies = PokemonSpecies.INSTANCE.getByName(speciesName.toLowerCase());
                if (registrySpecies != null && !registrySpecies.getEggGroups().isEmpty()) {
                    for (var eggGroup : registrySpecies.getEggGroups()) {
                        eggGroups.add(eggGroup.name());
                    }
                    SigsAcademyAddons.LOGGER.debug("[sig CatchDetector] Egg groups from registry for {}: {}", speciesName, eggGroups);
                }
            }
        } catch (Exception e) {
            SigsAcademyAddons.LOGGER.warn("[sig CatchDetector] Failed to get egg groups via API for {}: {}", speciesName, e.getMessage());
        }

        // fallback: species json files (cobblemon doesn't sync egg groups to client)
        if (eggGroups.isEmpty()) {
            Set<String> jsonEggGroups = EggGroupLookup.getEggGroups(speciesName);
            if (!jsonEggGroups.isEmpty()) {
                // convert lowercase json names to uppercase enum convention
                for (String eg : jsonEggGroups) {
                    eggGroups.add(eg.toUpperCase());
                }
                SigsAcademyAddons.LOGGER.debug("[sig CatchDetector] Egg groups from species JSON for {}: {}", speciesName, eggGroups);
            } else {
                SigsAcademyAddons.LOGGER.debug("[sig CatchDetector] No egg groups found for {} via any source", speciesName);
            }
        }

        CaughtPokemonInfo info = new CaughtPokemonInfo(speciesName, types, eggGroups);
        SigsAcademyAddons.LOGGER.info("[sig CatchDetector] Caught: {} | Types: {} | EggGroups: {}",
                speciesName, types, eggGroups);

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
