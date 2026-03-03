package com.siguha.sigsacademyaddons.feature.safari;

import java.util.List;

public class SafariHuntData {

    public enum HuntCategory {
        TYPE,
        EGG_GROUP,
        UNKNOWN
    }

    private final String displayName;
    private final HuntCategory category;
    private final List<String> targets;
    private int caught;
    private int total;
    private String resetTimeText;
    private long resetEndTimeMs;
    private int starRating;
    private final List<String> rewards;

    public SafariHuntData(String displayName, HuntCategory category, List<String> targets,
                          int caught, int total, String resetTimeText, long resetEndTimeMs,
                          int starRating, List<String> rewards) {
        this.displayName = displayName;
        this.category = category;
        this.targets = targets;
        this.caught = caught;
        this.total = total;
        this.resetTimeText = resetTimeText;
        this.resetEndTimeMs = resetEndTimeMs;
        this.starRating = starRating;
        this.rewards = rewards;
    }

    public String getDisplayName() {
        return displayName;
    }

    public HuntCategory getCategory() {
        return category;
    }

    public List<String> getTargets() {
        return targets;
    }

    public int getCaught() {
        return caught;
    }

    public void setCaught(int caught) {
        this.caught = caught;
    }

    public void incrementCaught() {
        if (caught < total) {
            caught++;
        }
    }

    public int getTotal() {
        return total;
    }

    public String getResetTimeText() {
        return resetTimeText;
    }

    public long getResetEndTimeMs() {
        return resetEndTimeMs;
    }

    public int getStarRating() {
        return starRating;
    }

    public List<String> getRewards() {
        return rewards;
    }

    public boolean isComplete() {
        return caught >= total;
    }

    public String getProgressString() {
        return caught + "/" + total;
    }

    public long getResetRemainingMs() {
        if (resetEndTimeMs <= 0) return 0;
        return Math.max(0, resetEndTimeMs - System.currentTimeMillis());
    }

    public String getResetCountdownFormatted() {
        long remainingMs = getResetRemainingMs();
        if (remainingMs <= 0) return "";

        long totalSeconds = remainingMs / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }

    public boolean isResetExpired() {
        return resetEndTimeMs > 0 && System.currentTimeMillis() >= resetEndTimeMs;
    }

    @Override
    public String toString() {
        return displayName + " [" + category + "] " + getProgressString();
    }
}
