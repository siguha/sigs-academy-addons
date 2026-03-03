package com.siguha.sigsacademyaddons.feature.daycare;

import com.siguha.sigsacademyaddons.SigsAcademyAddons;
import com.siguha.sigsacademyaddons.config.HudConfig;
import com.siguha.sigsacademyaddons.data.DaycareDataStore;
import com.siguha.sigsacademyaddons.data.EggCycleLookup;

import java.util.*;

public class DaycareManager {

    public static final long BREEDING_ESTIMATE_MS = 10 * 60 * 1000L;
    public static final long DEFAULT_HATCH_ESTIMATE_MS = 10 * 60 * 1000L; 

    private final DaycareDataStore dataStore;
    private final DaycareSoundPlayer soundPlayer;
    private final HudConfig hudConfig;

    private final Map<Integer, DaycareState.PenState> pens = new LinkedHashMap<>();
    private final List<DaycareState.ClaimedEgg> claimedEggs = new ArrayList<>();
    private final Map<Integer, String> penSpeciesMemory = new HashMap<>();
    private int eggsHatchedSinceMenuOpen = 0;
    private long lastDaycareMenuCloseTimeMs = 0;

    public DaycareManager(DaycareDataStore dataStore, DaycareSoundPlayer soundPlayer, HudConfig hudConfig) {
        this.dataStore = dataStore;
        this.soundPlayer = soundPlayer;
        this.hudConfig = hudConfig;
    }

    public void onServerJoined() {
        String address = SigsAcademyAddons.getCurrentServerAddress();
        int existingPens = pens.size();
        int existingEggs = claimedEggs.size();
        SigsAcademyAddons.LOGGER.info("[SAA Daycare] Server joined: {} — restoring state (had {} pens, {} eggs in memory)",
                address, existingPens, existingEggs);
        restoreState();
    }

    public void onServerDisconnected() {
        SigsAcademyAddons.LOGGER.info("[SAA Daycare] Server disconnected — persisting state");
        persistState();
        pens.clear();
        claimedEggs.clear();
        penSpeciesMemory.clear();
        eggsHatchedSinceMenuOpen = 0;
        lastDaycareMenuCloseTimeMs = 0;
    }

    public void onMainMenuScraped(List<ScrapedPenButton> scrapedButtons) {
        for (ScrapedPenButton button : scrapedButtons) {
            DaycareState.PenState existing = pens.get(button.penNumber());

            if (existing != null) {
                existing.setUnlocked(!button.locked());
            } else {
                DaycareState.PenState newPen = new DaycareState.PenState(button.penNumber(), !button.locked());
                pens.put(button.penNumber(), newPen);
            }
        }

        persistState();
    }

    public void onPenViewScraped(ScrapedPenData scraped) {
        DaycareState.PenState pen = pens.get(scraped.penNumber());

        if (pen == null) {
            pen = new DaycareState.PenState(scraped.penNumber(), true);
            pens.put(scraped.penNumber(), pen);
        }

        DaycareState.BreedingStage previousStage = pen.getStage();
        String previousP1 = pen.getPokemon1();
        String previousP2 = pen.getPokemon2();

        DaycareState.BreedingStage rawStage = scraped.stage();

        boolean samePokemon = Objects.equals(previousP1, scraped.pokemon1())
                && Objects.equals(previousP2, scraped.pokemon2());

        boolean eggWasClaimed = pen.getHadEggOnLastScrape() && !scraped.hasEgg()
                && rawStage == DaycareState.BreedingStage.BREEDING;

        pen.setHadEggOnLastScrape(scraped.hasEgg());

        DaycareState.BreedingStage finalStage;
        boolean startTimer = false;

        if (eggWasClaimed) {
            String eggSpecies = pen.getInferredEggSpecies();
            
            if (eggSpecies == null) {
                eggSpecies = penSpeciesMemory.getOrDefault(pen.getPenNumber(), "Unknown");
            }

            long now = System.currentTimeMillis();
            long hatchTimeMs = calculateHatchTimeMs(eggSpecies);
            claimedEggs.add(new DaycareState.ClaimedEgg(eggSpecies, now, now + hatchTimeMs));
            finalStage = DaycareState.BreedingStage.NEEDS_RESET;

        } else if (rawStage == DaycareState.BreedingStage.INCOMPATIBLE) {
            finalStage = DaycareState.BreedingStage.INCOMPATIBLE;

        } else if (rawStage == DaycareState.BreedingStage.EMPTY) {
            finalStage = DaycareState.BreedingStage.EMPTY;

        } else if (rawStage == DaycareState.BreedingStage.ONE_POKEMON) {
            finalStage = DaycareState.BreedingStage.ONE_POKEMON;

        } else {
            switch (previousStage) {
                case NEEDS_RESET -> {
                    if (!samePokemon) {
                        finalStage = DaycareState.BreedingStage.BREEDING;
                        startTimer = true;

                    } else if (scraped.serverBreedingProgress() > 0.01f) {
                        finalStage = DaycareState.BreedingStage.BREEDING;
                        startTimer = true;

                    } else {
                        long timeSinceMenuClose = System.currentTimeMillis() - lastDaycareMenuCloseTimeMs;

                        if (lastDaycareMenuCloseTimeMs > 0 && timeSinceMenuClose <= 60_000) {
                            finalStage = DaycareState.BreedingStage.BREEDING;
                            startTimer = true;
                            lastDaycareMenuCloseTimeMs = 0;

                        } else {
                            finalStage = DaycareState.BreedingStage.NEEDS_RESET;
                        }
                    }
                }
                case BREEDING, EGG_READY -> {
                    if (samePokemon) {
                        finalStage = DaycareState.BreedingStage.BREEDING;

                    } else {
                        finalStage = DaycareState.BreedingStage.BREEDING;
                        startTimer = true;

                    }
                }
                default -> {
                    finalStage = DaycareState.BreedingStage.BREEDING;
                    startTimer = true;
                }
            }
        }

        pen.setPokemon1(scraped.pokemon1());
        pen.setPokemon2(scraped.pokemon2());

        if (startTimer) {
            if (scraped.serverBreedingProgress() >= 0) {
                float sp = scraped.serverBreedingProgress();
                long remaining = (long) ((1.0f - sp) * BREEDING_ESTIMATE_MS);
                pen.setEstimatedEndTimeMs(System.currentTimeMillis() + remaining);

            } else {
                pen.setEstimatedEndTimeMs(System.currentTimeMillis() + BREEDING_ESTIMATE_MS);
            }
        }

        pen.setStage(finalStage);

        if (scraped.pokemon1() != null && scraped.pokemon2() != null) {
            String species = pen.inferEggSpecies();
            if (species != null) {
                penSpeciesMemory.put(pen.getPenNumber(), species);
            }
        }

        persistState();
    }

    public void onEggCreated() {
        SigsAcademyAddons.LOGGER.info("[SAA Daycare] Egg created — playing notification");
        soundPlayer.playEggCreatedSound();
    }

    private static final long HATCH_MATCH_TOLERANCE_MS = 2 * 60 * 1000L;

    public void onEggHatched() {
        SigsAcademyAddons.LOGGER.info("[SAA Daycare] Egg hatched event detected");

        long activeCount = claimedEggs.stream().filter(e -> !e.isCompleted()).count();

        DaycareState.ClaimedEgg closestEgg = null;
        for (DaycareState.ClaimedEgg egg : claimedEggs) {
            if (!egg.isCompleted() && egg.getRemainingMs() <= HATCH_MATCH_TOLERANCE_MS) {
                if (closestEgg == null || egg.getRemainingMs() < closestEgg.getRemainingMs()) {
                    closestEgg = egg;
                }
            }
        }

        if (closestEgg != null) {
            closestEgg.setCompleted(true);

        }

        eggsHatchedSinceMenuOpen++;
        soundPlayer.playEggHatchedSound();
        persistState();
    }

    public void onDaycareMenuOpened() {
        eggsHatchedSinceMenuOpen = 0;
        SigsAcademyAddons.LOGGER.debug("[SAA Daycare] Daycare menu opened");
    }

    public void onDaycareMenuClosed() {
        lastDaycareMenuCloseTimeMs = System.currentTimeMillis();
        SigsAcademyAddons.LOGGER.debug("[SAA Daycare] Daycare menu closed");
    }

    public void tick() {
        claimedEggs.removeIf(e -> e.isCompleted()
                && System.currentTimeMillis() - e.getEstimatedHatchTimeMs() > 5000);

        long now = System.currentTimeMillis();
        for (DaycareState.PenState pen : pens.values()) {
            if (pen.getStage() == DaycareState.BreedingStage.NEEDS_RESET) {
                long elapsed = now - pen.getLastStageChangeTimeMs();
                if (elapsed > 15 * 60 * 1000L) {
                    pen.setStage(DaycareState.BreedingStage.BREEDING);
                    pen.setEstimatedEndTimeMs(now + BREEDING_ESTIMATE_MS);
                    SigsAcademyAddons.LOGGER.info("[SAA Daycare] Pen {} NEEDS_RESET fallback timeout (15min) — reverting to BREEDING",
                            pen.getPenNumber());
                    persistState();
                }
            }
        }

    }

    public List<DaycareState.PenState> getDisplayPens() {
        return pens.values().stream()
                .filter(DaycareState.PenState::isUnlocked)
                .sorted(Comparator.comparingInt(DaycareState.PenState::getPenNumber))
                .toList();
    }

    public List<DaycareState.ClaimedEgg> getDisplayEggs() {
        int maxSlots = hudConfig.getDaycareEggsHatchingSlots();
        if (maxSlots <= 0) return List.of();

        return claimedEggs.stream()
                .filter(e -> !e.isCompleted())
                .sorted(Comparator.comparingLong(DaycareState.ClaimedEgg::getRemainingMs))
                .limit(maxSlots)
                .toList();
    }

    public int getTotalActiveEggs() {
        return (int) claimedEggs.stream().filter(e -> !e.isCompleted()).count();
    }

    public int getEggsHatchedSinceMenuOpen() {
        return eggsHatchedSinceMenuOpen;
    }

    public boolean hasActiveTimers() {
        boolean hasActivePen = pens.values().stream()
                .anyMatch(p -> p.getStage() == DaycareState.BreedingStage.BREEDING
                        || p.getStage() == DaycareState.BreedingStage.NEEDS_RESET
                        || p.getStage() == DaycareState.BreedingStage.INCOMPATIBLE);
        boolean hasActiveEggs = claimedEggs.stream().anyMatch(e -> !e.isCompleted());

        return hasActivePen || hasActiveEggs;
    }

    public boolean hasAnyContent() {
        boolean hasUnlockedPens = pens.values().stream().anyMatch(DaycareState.PenState::isUnlocked);
        boolean hasActiveEggs = claimedEggs.stream().anyMatch(e -> !e.isCompleted());

        return hasUnlockedPens || hasActiveEggs;
    }

    public void clearAll() {
        pens.clear();
        claimedEggs.clear();
        penSpeciesMemory.clear();
        eggsHatchedSinceMenuOpen = 0;
        dataStore.clear();
    }

    private static final long MS_PER_EGG_CYCLE = 20_000L;

    public static long calculateHatchTimeMs(String speciesName) {
        if (speciesName == null || speciesName.isEmpty()) {
            return DEFAULT_HATCH_ESTIMATE_MS;
        }

        int eggCycles = EggCycleLookup.getEggCycles(speciesName);

        if (eggCycles > 0) {
            return eggCycles * MS_PER_EGG_CYCLE;
        }

        return DEFAULT_HATCH_ESTIMATE_MS;
    }

    private void persistState() {
        dataStore.save(pens, claimedEggs, penSpeciesMemory);
    }

    private void restoreState() {
        pens.clear();
        claimedEggs.clear();
        penSpeciesMemory.clear();
        eggsHatchedSinceMenuOpen = 0;

        DaycareDataStore.LoadedData loaded = dataStore.load();
        pens.putAll(loaded.pens());
        claimedEggs.addAll(loaded.claimedEggs());
        penSpeciesMemory.putAll(loaded.penSpeciesMemory());
    }

    public record ScrapedPenButton(int penNumber, boolean locked) {}

    public record ScrapedPenData(int penNumber, String pokemon1, String pokemon2,
                                  DaycareState.BreedingStage stage,
                                  boolean hasEgg, float serverBreedingProgress) {}
}
