package com.siguha.sigsacademyaddons.feature.safari;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.siguha.sigsacademyaddons.SigsAcademyAddons;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class SafariManager {

    private static final String TIMER_FILE = "safari-timer.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private boolean inSafari = false;
    private long safariEndTimeMs = 0;
    private int safariDurationMinutes = 30;

    private ResourceKey<Level> safariDimension = null;
    private boolean inSafariZone = false;

    public SafariManager() {
        restoreTimer();
    }

    public void onSafariEntry(int durationMinutes) {
        this.inSafari = true;
        this.safariDurationMinutes = durationMinutes;
        this.safariEndTimeMs = System.currentTimeMillis() + (durationMinutes * 60L * 1000L);

        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            this.safariDimension = client.player.level().dimension();
            this.inSafariZone = true;
        }

        persistTimer();
    }

    public void tick() {
        if (!inSafari) return;

        if (getRemainingMs() <= 0) {
            endSafari();

            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        ResourceKey<Level> currentDim = client.player.level().dimension();

        if (safariDimension == null) {
            if (!currentDim.equals(Level.OVERWORLD)
                    && !currentDim.equals(Level.NETHER)
                    && !currentDim.equals(Level.END)) {
                safariDimension = currentDim;
                inSafariZone = true;
                persistTimer();
            }

            return;
        }

        inSafariZone = currentDim.equals(safariDimension);
    }

    public void endSafari() {
        if (inSafari) {
            inSafari = false;
            safariEndTimeMs = 0;
            safariDimension = null;
            inSafariZone = false;

            clearPersistedTimer();
        }
    }

    public boolean isInSafari() {
        return inSafari;
    }

    public boolean isInSafariZone() {
        return safariDimension != null && inSafariZone;
    }

    public long getRemainingMs() {
        if (!inSafari || safariEndTimeMs <= 0) return 0;

        return Math.max(0, safariEndTimeMs - System.currentTimeMillis());
    }

    public String getRemainingTimeFormatted() {
        long remainingMs = getRemainingMs();
        if (remainingMs <= 0) return "0:00";

        long totalSeconds = remainingMs / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;

        return String.format("%d:%02d", minutes, seconds);
    }

    public float getTimerProgress() {
        long remaining = getRemainingMs();
        if (remaining <= 0) return 1.0f;
        long totalDuration = safariDurationMinutes * 60L * 1000L;
        float elapsed = totalDuration - remaining;

        return Math.clamp(elapsed / totalDuration, 0.0f, 1.0f);
    }

    private void persistTimer() {
        try {
            Path filePath = getTimerFilePath();
            Files.createDirectories(filePath.getParent());

            String serverAddress = SigsAcademyAddons.getCurrentServerAddress();
            String dimId = safariDimension != null ? safariDimension.location().toString() : null;
            TimerData data = new TimerData(serverAddress, safariEndTimeMs, dimId, safariDurationMinutes);

            try (Writer writer = Files.newBufferedWriter(filePath)) {
                GSON.toJson(data, writer);
            }
        } catch (Exception e) {
            SigsAcademyAddons.LOGGER.warn("[SAA Safari] Failed to persist timer", e);
        }
    }

    private void restoreTimer() {
        try {
            Path filePath = getTimerFilePath();
            if (!Files.exists(filePath)) return;

            try (Reader reader = Files.newBufferedReader(filePath)) {
                TimerData data = GSON.fromJson(reader, TimerData.class);
                if (data != null && data.endTimeMs > System.currentTimeMillis()) {
                    this.safariEndTimeMs = data.endTimeMs;
                    this.inSafari = true;
                    this.safariDurationMinutes = data.durationMinutes > 0 ? data.durationMinutes : 30;

                    if (data.safariDimensionId != null && !data.safariDimensionId.isEmpty()) {
                        this.safariDimension = ResourceKey.create(
                                Registries.DIMENSION,
                                ResourceLocation.parse(data.safariDimensionId)
                        );
                    }
                } else {
                    clearPersistedTimer();
                }
            }
        } catch (Exception e) {
            SigsAcademyAddons.LOGGER.warn("[SAA Safari] Failed to restore timer", e);
        }
    }

    private void clearPersistedTimer() {
        try {
            Files.deleteIfExists(getTimerFilePath());
        } catch (Exception e) {
            SigsAcademyAddons.LOGGER.warn("[SAA Safari] Failed to clear persisted timer", e);
        }
    }

    private Path getTimerFilePath() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve(SigsAcademyAddons.CONFIG_DIR)
                .resolve(TIMER_FILE);
    }

    private record TimerData(String serverAddress, long endTimeMs, String safariDimensionId, int durationMinutes) {
    }
}
