package com.siguha.sigsacademyaddons.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.siguha.sigsacademyaddons.SigsAcademyAddons;
import com.siguha.sigsacademyaddons.feature.daycare.DaycareState;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class DaycareDataStore {

    private static final String DAYCARE_FILE = "daycare-state.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type DATA_TYPE = new TypeToken<DaycareData>() {}.getType();

    private long disconnectTimeMs = 0;

    public void save(Map<Integer, DaycareState.PenState> pens,
                     List<DaycareState.ClaimedEgg> claimedEggs,
                     Map<Integer, String> penSpeciesMemory) {
        try {
            Path filePath = getFilePath();
            Files.createDirectories(filePath.getParent());

            String serverAddress = SigsAcademyAddons.getCurrentServerAddress();

            List<PenEntry> penEntries = pens.values().stream()
                    .map(p -> new PenEntry(p.getPenNumber(), p.isUnlocked(),
                            p.getPokemon1(), p.getPokemon2(),
                            p.getStage().name(), p.getEstimatedEndTimeMs(),
                            p.getInferredEggSpecies(),
                            p.getLastStageChangeTimeMs()))
                    .toList();

            List<ClaimedEggEntry> eggEntries = claimedEggs.stream()
                    .filter(e -> !e.isCompleted())
                    .map(e -> new ClaimedEggEntry(e.getSpecies(),
                            e.getClaimedTimeMs(), e.getEstimatedHatchTimeMs()))
                    .toList();

            Map<Integer, String> speciesMemoryCopy = new HashMap<>(penSpeciesMemory);

            DaycareData data = new DaycareData(serverAddress, penEntries, eggEntries, speciesMemoryCopy, disconnectTimeMs);

            try (Writer writer = Files.newBufferedWriter(filePath)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            SigsAcademyAddons.LOGGER.error("[SAA DaycareDataStore] failed to save daycare data", e);
        }
    }

    public record LoadedData(Map<Integer, DaycareState.PenState> pens,
                              List<DaycareState.ClaimedEgg> claimedEggs,
                              Map<Integer, String> penSpeciesMemory,
                              long disconnectTimeMs) {}

    public LoadedData load() {
        try {
            Path filePath = getFilePath();
            if (!Files.exists(filePath)) {
                return emptyData();
            }

            String serverAddress = SigsAcademyAddons.getCurrentServerAddress();

            try (Reader reader = Files.newBufferedReader(filePath)) {
                DaycareData data = GSON.fromJson(reader, DATA_TYPE);
                if (data == null || !serverAddress.equals(data.serverAddress)) {
                    return emptyData();
                }

                Map<Integer, DaycareState.PenState> pens = new LinkedHashMap<>();
                if (data.pens != null) {
                    for (PenEntry entry : data.pens) {
                        DaycareState.BreedingStage stage;
                        try {
                            stage = DaycareState.BreedingStage.valueOf(entry.stage);
                        } catch (IllegalArgumentException e) {
                            stage = DaycareState.BreedingStage.EMPTY;
                        }

                        if (stage == DaycareState.BreedingStage.EGG_READY) {
                            stage = DaycareState.BreedingStage.BREEDING;
                        }

                        DaycareState.PenState pen = new DaycareState.PenState(
                                entry.penNumber, entry.unlocked,
                                entry.pokemon1, entry.pokemon2,
                                stage, entry.estimatedEndTimeMs, entry.inferredEggSpecies,
                                entry.lastStageChangeTimeMs);
                        pens.put(entry.penNumber, pen);
                    }
                }

                List<DaycareState.ClaimedEgg> eggs = new ArrayList<>();
                if (data.claimedEggs != null) {
                    for (ClaimedEggEntry entry : data.claimedEggs) {
                        eggs.add(new DaycareState.ClaimedEgg(
                                entry.species, entry.claimedTimeMs, entry.estimatedHatchTimeMs));
                    }
                }

                Map<Integer, String> speciesMemory = data.penSpeciesMemory != null
                        ? new HashMap<>(data.penSpeciesMemory) : new HashMap<>();

                long loadedDisconnectTime = data.disconnectTimeMs != null ? data.disconnectTimeMs : 0;
                return new LoadedData(pens, eggs, speciesMemory, loadedDisconnectTime);
            }
        } catch (Exception e) {
            SigsAcademyAddons.LOGGER.error("[SAA DaycareDataStore] failed to load daycare data", e);
            return emptyData();
        }
    }

    public void clear() {
        try {
            Files.deleteIfExists(getFilePath());
        } catch (Exception e) {
            SigsAcademyAddons.LOGGER.warn("[SAA DaycareDataStore] failed to clear daycare data", e);
        }
    }

    public void setDisconnectTimeMs(long ms) { this.disconnectTimeMs = ms; }

    private LoadedData emptyData() {
        return new LoadedData(new LinkedHashMap<>(), new ArrayList<>(), new HashMap<>(), 0);
    }

    private Path getFilePath() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve(SigsAcademyAddons.CONFIG_DIR)
                .resolve(DAYCARE_FILE);
    }

    private record DaycareData(String serverAddress, List<PenEntry> pens,
                                List<ClaimedEggEntry> claimedEggs,
                                Map<Integer, String> penSpeciesMemory,
                                Long disconnectTimeMs) {}
    private record PenEntry(int penNumber, boolean unlocked,
                            String pokemon1, String pokemon2,
                            String stage, long estimatedEndTimeMs,
                            String inferredEggSpecies,
                            long lastStageChangeTimeMs) {}
    private record ClaimedEggEntry(String species, long claimedTimeMs,
                                    long estimatedHatchTimeMs) {}
}
