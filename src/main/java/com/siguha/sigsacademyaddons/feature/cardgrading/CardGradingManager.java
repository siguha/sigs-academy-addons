package com.siguha.sigsacademyaddons.feature.cardgrading;

import com.siguha.sigsacademyaddons.SigsAcademyAddons;
import com.siguha.sigsacademyaddons.data.CardGradingDataStore;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;

public class CardGradingManager {

    public static final long DEFAULT_GRADING_DURATION_MS = 60L * 60L * 1000L;
    private static final long COMPLETION_TOLERANCE_MS = 5_000L;

    private final CardGradingDataStore dataStore;
    private final CardGradingSoundPlayer soundPlayer;

    private long requestStartTimeMs = -1;
    private long readyTimeMs = -1;
    private boolean academyTimerSeenThisSession = false;
    private boolean notifiedReady = false;

    private static AcademyAccessor academyAccessor;
    private static boolean academyAccessorResolved = false;

    public CardGradingManager(CardGradingDataStore dataStore, CardGradingSoundPlayer soundPlayer) {
        this.dataStore = dataStore;
        this.soundPlayer = soundPlayer;
    }

    public boolean hasTimer() {
        return requestStartTimeMs > 0 && readyTimeMs > 0;
    }

    public boolean isReadyToClaim() {
        return hasTimer() && getRemainingMs() == 0;
    }

    public long getRemainingMs() {
        if (!hasTimer()) {
            return 0;
        }
        return Math.max(0, readyTimeMs - System.currentTimeMillis());
    }

    public String getRemainingFormatted() {
        if (!hasTimer()) {
            return "0:00";
        }
        if (isReadyToClaim()) {
            return "Ready!";
        }

        long totalSeconds = getRemainingMs() / 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format("%d:%02d", minutes, seconds);
    }

    public float getProgress() {
        if (!hasTimer()) {
            return 0f;
        }

        long totalDuration = readyTimeMs - requestStartTimeMs;
        if (totalDuration <= 0) {
            return 1.0f;
        }

        float elapsed = totalDuration - getRemainingMs();
        return Math.clamp(elapsed / totalDuration, 0.0f, 1.0f);
    }

    public void onServerJoined() {
        restoreState();
        academyTimerSeenThisSession = false;
    }

    public void onServerDisconnected() {
        clearInMemory();
        academyTimerSeenThisSession = false;
        soundPlayer.clearQueue();
    }

    public void playTestSound() {
        soundPlayer.playTestSound();
    }

    public void tick() {
        soundPlayer.tick();

        AcademySnapshot snapshot = readAcademySnapshot();
        if (snapshot != null) {
            if (!snapshot.hasData()) {
                if (academyTimerSeenThisSession && hasTimer()) {
                    clearAll();
                }
            } else {
                academyTimerSeenThisSession = true;
                syncTimer(snapshot.completionTimeMs());
            }
        }

        notifyIfReady();
    }

    public void clearAll() {
        clearInMemory();
        soundPlayer.clearQueue();
        dataStore.clear();
    }

    private void persistState() {
        if (!hasTimer()) {
            dataStore.clear();
            return;
        }
        dataStore.save(requestStartTimeMs, readyTimeMs);
    }

    private void restoreState() {
        clearInMemory();

        CardGradingDataStore.LoadedData loaded = dataStore.load();
        if (!loaded.hasTimer()) {
            return;
        }

        readyTimeMs = loaded.readyTimeMs();
        requestStartTimeMs = loaded.requestStartTimeMs() > 0 && loaded.requestStartTimeMs() <= readyTimeMs
                ? loaded.requestStartTimeMs()
                : readyTimeMs - DEFAULT_GRADING_DURATION_MS;
        notifiedReady = isReadyToClaim();
    }

    private void clearInMemory() {
        requestStartTimeMs = -1;
        readyTimeMs = -1;
        notifiedReady = false;
    }

    private void syncTimer(long completionTimeMs) {
        boolean completionChanged = Math.abs(readyTimeMs - completionTimeMs) > COMPLETION_TOLERANCE_MS;
        boolean missingStart = requestStartTimeMs <= 0 || requestStartTimeMs > completionTimeMs;
        if (!completionChanged && !missingStart) {
            return;
        }

        readyTimeMs = completionTimeMs;
        requestStartTimeMs = completionTimeMs - DEFAULT_GRADING_DURATION_MS;
        notifiedReady = getRemainingMs() == 0;
        persistState();
    }

    private void notifyIfReady() {
        if (!hasTimer() || notifiedReady || getRemainingMs() > 0) {
            return;
        }

        notifiedReady = true;
        soundPlayer.playReadySound();
    }

    private AcademySnapshot readAcademySnapshot() {
        AcademyAccessor accessor = getAcademyAccessor();
        if (accessor == null) {
            return null;
        }

        try {
            Object clientData = accessor.clientField.get(null);
            if (clientData == null) {
                return AcademySnapshot.EMPTY;
            }

            Instant completionTime = (Instant) accessor.getCompletionTime.invoke(clientData);
            if (completionTime == null) {
                return AcademySnapshot.EMPTY;
            }

            return new AcademySnapshot(completionTime.toEpochMilli());
        } catch (Exception e) {
            SigsAcademyAddons.LOGGER.debug("[SAA CardGrading] Failed to read Academy grading sync", e);
            return null;
        }
    }

    private static AcademyAccessor getAcademyAccessor() {
        if (academyAccessorResolved) {
            return academyAccessor;
        }

        academyAccessorResolved = true;

        try {
            Class<?> gradingDataClass = Class.forName("abeshutt.staracademy.card.CardGradingData");
            Field clientField = gradingDataClass.getDeclaredField("CLIENT");
            clientField.setAccessible(true);

            Method getCompletionTime = gradingDataClass.getDeclaredMethod("getCompletionTime");
            getCompletionTime.setAccessible(true);

            academyAccessor = new AcademyAccessor(clientField, getCompletionTime);
        } catch (Exception e) {
            academyAccessor = null;
        }

        return academyAccessor;
    }

    private record AcademyAccessor(Field clientField, Method getCompletionTime) {}

    private record AcademySnapshot(long completionTimeMs) {
        private static final AcademySnapshot EMPTY = new AcademySnapshot(-1);

        private boolean hasData() {
            return completionTimeMs > 0;
        }
    }
}
