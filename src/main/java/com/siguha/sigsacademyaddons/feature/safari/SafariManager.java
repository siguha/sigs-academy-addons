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

// tracks safari session timer and zone detection, persists to disk across relaunches
public class SafariManager {

    private static final String TIMER_FILE = "safari-timer.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private boolean inSafari = false;
    private long safariEndTimeMs = 0;
    private int safariDurationMinutes = 30; // actual duration from entry message

    // dimension tracking for safari zone detection
    private ResourceKey<Level> safariDimension = null;
    private boolean inSafariZone = false;

    public SafariManager() {
        restoreTimer();
    }

    // called when safari ticket entry message is detected in chat
    public void onSafariEntry(int durationMinutes) {
        this.inSafari = true;
        this.safariDurationMinutes = durationMinutes;
        this.safariEndTimeMs = System.currentTimeMillis() + (durationMinutes * 60L * 1000L);

        // record current dimension as safari dimension
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            this.safariDimension = client.player.level().dimension();
            this.inSafariZone = true;
            SigsAcademyAddons.LOGGER.info("[sig Safari] Session started: {} minutes, dimension={}",
                    durationMinutes, safariDimension.location());
        } else {
            SigsAcademyAddons.LOGGER.info("[sig Safari] Session started: {} minutes", durationMinutes);
        }

        persistTimer();
    }

    // updates safari state each client tick
    public void tick() {
        if (!inSafari) return;

        if (getRemainingMs() <= 0) {
            endSafari();
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        ResourceKey<Level> currentDim = client.player.level().dimension();

        // auto-detect safari dimension on first non-vanilla dimension
        if (safariDimension == null) {
            if (!currentDim.equals(Level.OVERWORLD)
                    && !currentDim.equals(Level.NETHER)
                    && !currentDim.equals(Level.END)) {
                safariDimension = currentDim;
                inSafariZone = true;
                persistTimer();
                SigsAcademyAddons.LOGGER.info("[sig Safari] Auto-detected safari dimension: {}",
                        safariDimension.location());
            }
            return;
        }

        boolean wasInZone = inSafariZone;
        inSafariZone = currentDim.equals(safariDimension);

        if (wasInZone && !inSafariZone) {
            SigsAcademyAddons.LOGGER.debug("[sig Safari] Player left safari zone (now in {})",
                    currentDim.location());
        } else if (!wasInZone && inSafariZone) {
            SigsAcademyAddons.LOGGER.debug("[sig Safari] Player re-entered safari zone");
        }
    }

    // ends the current safari session and clears persisted data
    public void endSafari() {
        if (inSafari) {
            inSafari = false;
            safariEndTimeMs = 0;
            safariDimension = null;
            inSafariZone = false;
            SigsAcademyAddons.LOGGER.info("[sig Safari] Session ended");
            clearPersistedTimer();
        }
    }

    public boolean isInSafari() {
        return inSafari;
    }

    // whether player is currently in the safari zone dimension
    public boolean isInSafariZone() {
        return safariDimension != null && inSafariZone;
    }

    public long getRemainingMs() {
        if (!inSafari || safariEndTimeMs <= 0) return 0;
        return Math.max(0, safariEndTimeMs - System.currentTimeMillis());
    }

    // returns remaining time formatted as "mm:ss"
    public String getRemainingTimeFormatted() {
        long remainingMs = getRemainingMs();
        if (remainingMs <= 0) return "0:00";

        long totalSeconds = remainingMs / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    // returns timer progress from 0.0 (just started) to 1.0 (expired)
    public float getTimerProgress() {
        long remaining = getRemainingMs();
        if (remaining <= 0) return 1.0f;
        long totalDuration = safariDurationMinutes * 60L * 1000L;
        float elapsed = totalDuration - remaining;
        return Math.clamp(elapsed / totalDuration, 0.0f, 1.0f);
    }

    // --- persistence ---

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
            SigsAcademyAddons.LOGGER.debug("[sig Safari] Timer persisted to disk (dimension={})", dimId);
        } catch (Exception e) {
            SigsAcademyAddons.LOGGER.warn("[sig Safari] Failed to persist timer", e);
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
                        SigsAcademyAddons.LOGGER.info("[sig Safari] Restored safari dimension: {}",
                                data.safariDimensionId);
                    } else {
                        SigsAcademyAddons.LOGGER.info("[sig Safari] No dimension in timer file — " +
                                "will auto-detect on first tick in a non-vanilla dimension");
                    }

                    long remainingSec = (data.endTimeMs - System.currentTimeMillis()) / 1000;
                    SigsAcademyAddons.LOGGER.info("[sig Safari] Restored timer: {}:{} remaining",
                            remainingSec / 60, String.format("%02d", remainingSec % 60));
                } else {
                    clearPersistedTimer();
                }
            }
        } catch (Exception e) {
            SigsAcademyAddons.LOGGER.warn("[sig Safari] Failed to restore timer", e);
        }
    }

    private void clearPersistedTimer() {
        try {
            Files.deleteIfExists(getTimerFilePath());
        } catch (Exception e) {
            SigsAcademyAddons.LOGGER.warn("[sig Safari] Failed to clear persisted timer", e);
        }
    }

    private Path getTimerFilePath() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve(SigsAcademyAddons.CONFIG_DIR)
                .resolve(TIMER_FILE);
    }

    // timer persistence data including duration and dimension for cross-relaunch support
    private record TimerData(String serverAddress, long endTimeMs, String safariDimensionId, int durationMinutes) {
    }
}
