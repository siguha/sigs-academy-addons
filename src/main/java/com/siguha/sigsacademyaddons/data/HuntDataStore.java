package com.siguha.sigsacademyaddons.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.siguha.sigsacademyaddons.SigsAcademyAddons;
import com.siguha.sigsacademyaddons.feature.safari.SafariHuntData;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// json persistence for safari hunt data, keyed by server address
public class HuntDataStore {

    private static final String HUNTS_FILE = "hunts.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type HUNT_LIST_TYPE = new TypeToken<List<HuntDataEntry>>() {}.getType();

    public void save(List<SafariHuntData> hunts) {
        try {
            Path filePath = getFilePath();
            Files.createDirectories(filePath.getParent());

            String serverAddress = SigsAcademyAddons.getCurrentServerAddress();
            List<HuntDataEntry> entries = hunts.stream()
                    .map(h -> new HuntDataEntry(
                            serverAddress,
                            h.getDisplayName(),
                            h.getCategory().name(),
                            h.getTargets(),
                            h.getCaught(),
                            h.getTotal(),
                            h.getResetTimeText(),
                            h.getResetEndTimeMs(),
                            h.getStarRating(),
                            h.getRewards()
                    ))
                    .toList();

            try (Writer writer = Files.newBufferedWriter(filePath)) {
                GSON.toJson(entries, writer);
            }
        } catch (IOException e) {
            SigsAcademyAddons.LOGGER.error("[SAA HuntDataStore] failed to save hunt data", e);
        }
    }

    public List<SafariHuntData> load() {
        try {
            Path filePath = getFilePath();
            if (!Files.exists(filePath)) {
                return new ArrayList<>();
            }

            String serverAddress = SigsAcademyAddons.getCurrentServerAddress();

            try (Reader reader = Files.newBufferedReader(filePath)) {
                List<HuntDataEntry> entries = GSON.fromJson(reader, HUNT_LIST_TYPE);
                if (entries == null) {
                    return new ArrayList<>();
                }

                return entries.stream()
                        .filter(e -> serverAddress.equals(e.serverAddress))
                        .map(e -> new SafariHuntData(
                                e.displayName,
                                SafariHuntData.HuntCategory.valueOf(e.category),
                                e.targets,
                                e.caught,
                                e.total,
                                e.resetTimeText,
                                e.resetEndTimeMs,
                                e.starRating,
                                e.rewards
                        ))
                        .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
            }
        } catch (Exception e) {
            SigsAcademyAddons.LOGGER.error("[SAA HuntDataStore] failed to load hunt data", e);
            return new ArrayList<>();
        }
    }

    private Path getFilePath() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve(SigsAcademyAddons.CONFIG_DIR)
                .resolve(HUNTS_FILE);
    }

    private record HuntDataEntry(
            String serverAddress,
            String displayName,
            String category,
            List<String> targets,
            int caught,
            int total,
            String resetTimeText,
            long resetEndTimeMs,
            int starRating,
            List<String> rewards
    ) {
    }
}
