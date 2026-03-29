package com.siguha.sigsacademyaddons.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.siguha.sigsacademyaddons.SigsAcademyAddons;
import net.minecraft.client.Minecraft;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;

public class CardGradingDataStore {

    private static final String CARD_GRADING_FILE = "card-grading-state.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type DATA_TYPE = new TypeToken<CardGradingData>() {}.getType();

    public void save(long requestStartTimeMs, long readyTimeMs) {
        try {
            Path filePath = getFilePath();
            Files.createDirectories(filePath.getParent());

            String serverAddress = SigsAcademyAddons.getCurrentServerAddress();
            CardGradingData data = new CardGradingData(serverAddress, requestStartTimeMs, readyTimeMs);

            try (Writer writer = Files.newBufferedWriter(filePath)) {
                GSON.toJson(data, writer);
            }
        } catch (Exception e) {
            SigsAcademyAddons.LOGGER.error("[SAA CardGradingDataStore] failed to save grading data", e);
        }
    }

    public LoadedData load() {
        try {
            Path filePath = getFilePath();
            if (!Files.exists(filePath)) {
                return LoadedData.EMPTY;
            }

            String serverAddress = SigsAcademyAddons.getCurrentServerAddress();

            try (Reader reader = Files.newBufferedReader(filePath)) {
                CardGradingData data = GSON.fromJson(reader, DATA_TYPE);
                if (data == null || !serverAddress.equals(data.serverAddress)) {
                    return LoadedData.EMPTY;
                }
                return new LoadedData(data.requestStartTimeMs, data.readyTimeMs);
            }
        } catch (Exception e) {
            SigsAcademyAddons.LOGGER.error("[SAA CardGradingDataStore] failed to load grading data", e);
            return LoadedData.EMPTY;
        }
    }

    public void clear() {
        try {
            Files.deleteIfExists(getFilePath());
        } catch (Exception e) {
            SigsAcademyAddons.LOGGER.warn("[SAA CardGradingDataStore] failed to clear grading data", e);
        }
    }

    private Path getFilePath() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve(SigsAcademyAddons.CONFIG_DIR)
                .resolve(CARD_GRADING_FILE);
    }

    public record LoadedData(long requestStartTimeMs, long readyTimeMs) {
        public static final LoadedData EMPTY = new LoadedData(-1, -1);

        public boolean hasTimer() {
            return requestStartTimeMs > 0 && readyTimeMs > 0;
        }
    }

    private record CardGradingData(String serverAddress, long requestStartTimeMs, long readyTimeMs) {}
}
