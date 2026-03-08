package com.siguha.sigsacademyaddons.feature.daycare;

public class DaycareState {

    public enum BreedingStage {
        EMPTY,
        ONE_POKEMON,
        BREEDING,
        EGG_READY,
        NEEDS_RESET,
        INCOMPATIBLE
    }

    public static class PenState {
        private final int penNumber;
        private boolean unlocked;
        private String pokemon1;
        private String pokemon2;
        private BreedingStage stage;
        private long estimatedEndTimeMs;
        private String inferredEggSpecies;
        private long lastStageChangeTimeMs;
        private boolean hadEggOnLastScrape = false;
        private boolean gender1Female = false;
        private boolean gender2Female = false;

        public PenState(int penNumber, boolean unlocked) {
            this.penNumber = penNumber;
            this.unlocked = unlocked;
            this.stage = BreedingStage.EMPTY;
            this.lastStageChangeTimeMs = System.currentTimeMillis();
        }

        public PenState(int penNumber, boolean unlocked, String pokemon1, String pokemon2,
                         BreedingStage stage, long estimatedEndTimeMs, String inferredEggSpecies,
                         long lastStageChangeTimeMs) {
            this.penNumber = penNumber;
            this.unlocked = unlocked;
            this.pokemon1 = pokemon1;
            this.pokemon2 = pokemon2;
            this.stage = stage;
            this.estimatedEndTimeMs = estimatedEndTimeMs;
            this.inferredEggSpecies = inferredEggSpecies;
            this.lastStageChangeTimeMs = lastStageChangeTimeMs;
        }

        public int getPenNumber() { return penNumber; }
        public boolean isUnlocked() { return unlocked; }
        public String getPokemon1() { return pokemon1; }
        public String getPokemon2() { return pokemon2; }
        public BreedingStage getStage() { return stage; }
        public long getEstimatedEndTimeMs() { return estimatedEndTimeMs; }
        public String getInferredEggSpecies() { return inferredEggSpecies; }

        public void setUnlocked(boolean unlocked) { this.unlocked = unlocked; }
        public void setStage(BreedingStage stage) {
            this.stage = stage;
            this.lastStageChangeTimeMs = System.currentTimeMillis();
        }
        public void setPokemon1(String pokemon1) { this.pokemon1 = pokemon1; }
        public void setPokemon2(String pokemon2) { this.pokemon2 = pokemon2; }
        public void setEstimatedEndTimeMs(long estimatedEndTimeMs) { this.estimatedEndTimeMs = estimatedEndTimeMs; }
        public void setInferredEggSpecies(String species) { this.inferredEggSpecies = species; }
        public long getLastStageChangeTimeMs() { return lastStageChangeTimeMs; }

        public boolean getHadEggOnLastScrape() { return hadEggOnLastScrape; }
        public void setHadEggOnLastScrape(boolean had) { this.hadEggOnLastScrape = had; }

        public boolean isGender1Female() { return gender1Female; }
        public boolean isGender2Female() { return gender2Female; }
        public void setGender1Female(boolean female) { this.gender1Female = female; }
        public void setGender2Female(boolean female) { this.gender2Female = female; }

        public void adjustTimestamps(long offsetMs) {
            if (estimatedEndTimeMs > 0) estimatedEndTimeMs += offsetMs;
            lastStageChangeTimeMs += offsetMs;
        }

        public boolean isBreeding() { return stage == BreedingStage.BREEDING; }

        public String inferEggSpecies() {
            if (pokemon1 == null || pokemon2 == null) return null;

            String p1 = pokemon1.toLowerCase();
            String p2 = pokemon2.toLowerCase();

            if (p1.contains("ditto") && !p2.contains("ditto")) {
                inferredEggSpecies = pokemon2;
            } else if (p2.contains("ditto") && !p1.contains("ditto")) {
                inferredEggSpecies = pokemon1;
            } else if (gender2Female) {
                inferredEggSpecies = pokemon2;
            } else if (gender1Female) {
                inferredEggSpecies = pokemon1;
            } else {
                inferredEggSpecies = pokemon1;
            }

            return inferredEggSpecies;
        }

        public long getRemainingMs() {
            if (!isBreeding() || estimatedEndTimeMs <= 0) return 0;

            return Math.max(0, estimatedEndTimeMs - System.currentTimeMillis());
        }

        public String getRemainingFormatted() {
            long remaining = getRemainingMs();

            if (remaining <= 0 && isBreeding()) return "0:00";
            if (remaining <= 0) return "";

            long totalSeconds = remaining / 1000;
            long minutes = totalSeconds / 60;
            long seconds = totalSeconds % 60;

            return "~" + String.format("%d:%02d", minutes, seconds);
        }

        public float getProgress() {
            if (!isBreeding() || estimatedEndTimeMs <= 0) return 0f;

            long totalDuration = DaycareManager.BREEDING_ESTIMATE_MS;
            long remaining = getRemainingMs();
            float elapsed = totalDuration - remaining;

            return Math.clamp(elapsed / totalDuration, 0.0f, 1.0f);
        }

        public String getDisplayName() {
            if (pokemon1 != null && pokemon2 != null) {
                return pokemon1 + " + " + pokemon2;
            }

            if (pokemon1 != null) return pokemon1;

            return "Empty";
        }
    }

    public static class ClaimedEgg {
        private final String species;
        private long claimedTimeMs;
        private long estimatedHatchTimeMs;
        private boolean completed;

        public ClaimedEgg(String species, long claimedTimeMs, long estimatedHatchTimeMs) {
            this.species = species;
            this.claimedTimeMs = claimedTimeMs;
            this.estimatedHatchTimeMs = estimatedHatchTimeMs;
            this.completed = false;
        }

        public String getSpecies() { return species; }
        public long getClaimedTimeMs() { return claimedTimeMs; }
        public long getEstimatedHatchTimeMs() { return estimatedHatchTimeMs; }
        public boolean isCompleted() { return completed; }
        public void setCompleted(boolean completed) { this.completed = completed; }

        public long getRemainingMs() {
            if (completed || estimatedHatchTimeMs <= 0) return 0;

            return Math.max(0, estimatedHatchTimeMs - System.currentTimeMillis());
        }

        public String getRemainingFormatted() {
            if (completed) return "Hatched!";

            long remaining = getRemainingMs();
            if (remaining <= 0) return "Waiting...";

            long totalSeconds = remaining / 1000;
            long minutes = totalSeconds / 60;
            long seconds = totalSeconds % 60;

            return "~" + String.format("%d:%02d", minutes, seconds);
        }

        public float getProgress() {
            if (completed) return 1.0f;

            long totalDuration = estimatedHatchTimeMs - claimedTimeMs;
            if (totalDuration <= 0) return 0f;

            long remaining = getRemainingMs();
            float elapsed = totalDuration - remaining;
            
            return Math.clamp(elapsed / totalDuration, 0.0f, 1.0f);
        }

        public void adjustTimestamps(long offsetMs) {
            claimedTimeMs += offsetMs;
            estimatedHatchTimeMs += offsetMs;
        }

        public String getDisplayLabel() {
            return species != null ? species : "Egg";
        }
    }
}
