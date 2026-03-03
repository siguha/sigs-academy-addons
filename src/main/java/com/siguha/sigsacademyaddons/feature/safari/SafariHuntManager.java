package com.siguha.sigsacademyaddons.feature.safari;

import com.siguha.sigsacademyaddons.SigsAcademyAddons;
import com.siguha.sigsacademyaddons.data.HuntDataStore;
import com.siguha.sigsacademyaddons.handler.CatchDetector;
import com.siguha.sigsacademyaddons.handler.ScreenInterceptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SafariHuntManager {

    private static final Pattern CAUGHT_PATTERN = Pattern.compile("Caught:\\s*(\\d+)/(\\d+)");
    private static final Pattern RESET_PATTERN = Pattern.compile("Resets in (.+)");
    private static final Pattern TIME_PARTS_PATTERN = Pattern.compile("(\\d+)h\\s*(\\d+)m\\s*(\\d+)s");
    private static final Pattern STAR_PATTERN = Pattern.compile("[\\u2B50\\u2605\\u2606*]+");

    private final HuntDataStore dataStore;
    private List<SafariHuntData> activeHunts = new ArrayList<>();

    private int pendingUpdates = 0;

    private int expirationCheckCooldown = 0;

    public SafariHuntManager(HuntDataStore dataStore) {
        this.dataStore = dataStore;
        this.activeHunts = dataStore.load();
        int before = activeHunts.size();
        activeHunts.removeIf(SafariHuntData::isResetExpired);
        int removed = before - activeHunts.size();
        if (removed > 0) {
            dataStore.save(activeHunts);

        }
    }

    public void tick() {
        if (activeHunts.isEmpty()) return;

        expirationCheckCooldown--;
        if (expirationCheckCooldown > 0) return;
        expirationCheckCooldown = 100;

        int before = activeHunts.size();
        activeHunts.removeIf(SafariHuntData::isResetExpired);

        if (activeHunts.size() < before) {
            dataStore.save(activeHunts);
        }
    }

    public void onHuntsScreenScraped(List<ScreenInterceptor.ScrapedHuntItem> scrapedItems) {
        List<SafariHuntData> parsedHunts = new ArrayList<>();

        for (ScreenInterceptor.ScrapedHuntItem item : scrapedItems) {
            try {
                SafariHuntData hunt = parseHuntItem(item);

                if (hunt != null) {
                    parsedHunts.add(hunt);
                }

            } catch (Exception e) {
                SigsAcademyAddons.LOGGER.warn("[SAA Safari] Failed to parse hunt item '{}': {}",
                        item.name(), e.getMessage());
            }
        }

        if (!parsedHunts.isEmpty()) {
            this.activeHunts = parsedHunts;
            this.pendingUpdates = 0;
            dataStore.save(activeHunts);
        }
    }

    public void onHuntProgressUpdate() {
        pendingUpdates++;
    }

    public int getPendingUpdates() {
        return pendingUpdates;
    }

    public void onPokemonCaught(CatchDetector.CaughtPokemonInfo catchInfo) {
        if (activeHunts.isEmpty()) {
            return;
        }

        List<SafariHuntData> matchingHunts = new ArrayList<>();

        for (SafariHuntData hunt : activeHunts) {
            if (hunt.isComplete()) {
                continue;
            }

            if (doesCatchMatchHunt(catchInfo, hunt)) {
                matchingHunts.add(hunt);
            }
        }

        if (matchingHunts.isEmpty()) {
            return;
        }

        for (SafariHuntData hunt : matchingHunts) {
            hunt.incrementCaught();
        }

        if (pendingUpdates > 0) {
            pendingUpdates--;
        }

        dataStore.save(activeHunts);
    }

    private boolean doesCatchMatchHunt(CatchDetector.CaughtPokemonInfo catchInfo, SafariHuntData hunt) {
        switch (hunt.getCategory()) {
            case TYPE:
                for (String target : hunt.getTargets()) {
                    for (String pokemonType : catchInfo.types()) {
                        if (pokemonType.equalsIgnoreCase(target.trim())) {
                            return true;
                        }
                    }
                }
                return false;

            case EGG_GROUP:
                for (String target : hunt.getTargets()) {
                    String normalizedTarget = SigsAcademyAddons.normalizeForComparison(target);

                    for (String pokemonEggGroup : catchInfo.eggGroups()) {
                        String normalizedEggGroup = SigsAcademyAddons.normalizeForComparison(pokemonEggGroup);

                        if (normalizedEggGroup.equals(normalizedTarget)) {

                            return true;
                        }
                    }
                }
                return false;

            case UNKNOWN:
                return false;

            default:
                return false;
        }
    }

    public List<SafariHuntData> getActiveHunts() {
        List<SafariHuntData> sorted = new ArrayList<>(activeHunts);
        sorted.sort(Comparator.comparingInt(h -> h.getCategory().ordinal()));

        return Collections.unmodifiableList(sorted);
    }

    public boolean hasActiveHunts() {
        return !activeHunts.isEmpty();
    }

    public void clearHunts() {
        activeHunts.clear();
        dataStore.save(activeHunts);
    }

    private SafariHuntData parseHuntItem(ScreenInterceptor.ScrapedHuntItem item) {
        String displayName = item.name();
        List<String> loreLines = item.loreLines();

        int caught = 0;
        int total = 0;
        String resetTimeText = "";
        List<String> rewards = new ArrayList<>();
        boolean inRewardsSection = false;

        for (String line : loreLines) {
            String cleanLine = stripFormatting(line);

            Matcher caughtMatcher = CAUGHT_PATTERN.matcher(cleanLine);
            if (caughtMatcher.find()) {
                caught = Integer.parseInt(caughtMatcher.group(1));
                total = Integer.parseInt(caughtMatcher.group(2));
                continue;
            }

            Matcher resetMatcher = RESET_PATTERN.matcher(cleanLine);
            if (resetMatcher.find()) {
                resetTimeText = resetMatcher.group(1).trim();
                continue;
            }

            if (cleanLine.contains("Rewards:")) {
                inRewardsSection = true;
                continue;
            }

            if (inRewardsSection && !cleanLine.isBlank()) {
                rewards.add(cleanLine.trim());
            }
        }

        String cleanName = stripFormatting(displayName);

        int stars = countStars(cleanName);
        String nameWithoutStars = STAR_PATTERN.matcher(cleanName).replaceAll("").trim();

        SafariHuntData.HuntCategory category;
        List<String> targets;

        if (nameWithoutStars.toLowerCase().contains("egg group")) {
            category = SafariHuntData.HuntCategory.EGG_GROUP;
            String eggGroupName = nameWithoutStars
                    .replaceAll("(?i)egg\\s*group", "")
                    .trim();
            targets = List.of(eggGroupName);

        } else if (nameWithoutStars.toLowerCase().contains("type")) {
            category = SafariHuntData.HuntCategory.TYPE;
            String typePart = nameWithoutStars
                    .replaceAll("(?i)type", "")
                    .trim();
            targets = List.of(typePart.split("\\s+"));

        } else {
            category = SafariHuntData.HuntCategory.UNKNOWN;
            targets = List.of(nameWithoutStars);
        }

        long resetEndTimeMs = parseResetTimeToAbsolute(resetTimeText);

        return new SafariHuntData(
                cleanName, category, targets,
                caught, total, resetTimeText, resetEndTimeMs, stars, rewards
        );
    }

    private static long parseResetTimeToAbsolute(String resetTimeText) {
        if (resetTimeText == null || resetTimeText.isEmpty()) return 0;

        Matcher m = TIME_PARTS_PATTERN.matcher(resetTimeText);
        if (!m.find()) return 0;

        try {
            int hours = Integer.parseInt(m.group(1));
            int minutes = Integer.parseInt(m.group(2));
            int seconds = Integer.parseInt(m.group(3));

            long totalMs = ((hours * 3600L) + (minutes * 60L) + seconds) * 1000L;

            totalMs = Math.max(0, totalMs - 5000L);

            return System.currentTimeMillis() + totalMs;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String stripFormatting(String text) {
        if (text == null) return "";
        String stripped = text.replaceAll("\u00A7[0-9a-fk-or]", "");
        stripped = stripped.replaceAll("[\\u0E00-\\u0E7F]", "");
        stripped = stripped.replaceAll("[\\u2B50\\u2605\\u2606]", "");
        
        return stripped.trim();
    }

    private static int countStars(String text) {
        int count = 0;
        for (char c : text.toCharArray()) {
            if (c == '\u2B50' || c == '\u2605' || c == '\u2606' || c == '*') {
                count++;
            }
            if (c == '\u0E47') {
                count++;
            }
        }
        return count;
    }
}
