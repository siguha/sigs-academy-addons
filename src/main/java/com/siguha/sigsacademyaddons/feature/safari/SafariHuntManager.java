package com.siguha.sigsacademyaddons.feature.safari;

import com.siguha.sigsacademyaddons.SigsAcademyAddons;
import com.siguha.sigsacademyaddons.data.HuntDataStore;
import com.siguha.sigsacademyaddons.handler.CatchDetector;
import com.siguha.sigsacademyaddons.handler.ScreenInterceptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// parses hunts from the npc screen, tracks progress, and correlates catches
public class SafariHuntManager {

    // lore parsing patterns
    private static final Pattern CAUGHT_PATTERN = Pattern.compile("Caught:\\s*(\\d+)/(\\d+)");
    private static final Pattern RESET_PATTERN = Pattern.compile("Resets in (.+)");
    private static final Pattern TIME_PARTS_PATTERN = Pattern.compile("(\\d+)h\\s*(\\d+)m\\s*(\\d+)s");
    private static final Pattern STAR_PATTERN = Pattern.compile("[\\u2B50\\u2605\\u2606*]+");

    private final HuntDataStore dataStore;
    private List<SafariHuntData> activeHunts = new ArrayList<>();

    // unattributed catches since last screen scrape (party full → pc)
    private int pendingUpdates = 0;

    // expiration check cooldown (~5 seconds = 100 ticks)
    private int expirationCheckCooldown = 0;

    public SafariHuntManager(HuntDataStore dataStore) {
        this.dataStore = dataStore;
        // load persisted hunts, remove expired ones
        this.activeHunts = dataStore.load();
        int before = activeHunts.size();
        activeHunts.removeIf(SafariHuntData::isResetExpired);
        int removed = before - activeHunts.size();
        if (removed > 0) {
            SigsAcademyAddons.LOGGER.info("[sig Safari] Removed {} expired hunts on load ({} remaining)",
                    removed, activeHunts.size());
            dataStore.save(activeHunts);
        } else if (!activeHunts.isEmpty()) {
            SigsAcademyAddons.LOGGER.info("[sig Safari] Loaded {} persisted hunts from disk", activeHunts.size());
        }
    }

    // removes expired hunts periodically (~every 5 seconds)
    public void tick() {
        if (activeHunts.isEmpty()) return;

        expirationCheckCooldown--;
        if (expirationCheckCooldown > 0) return;
        expirationCheckCooldown = 100;

        boolean anyExpired = activeHunts.stream().anyMatch(SafariHuntData::isResetExpired);
        if (anyExpired) {
            activeHunts.removeIf(SafariHuntData::isResetExpired);
            dataStore.save(activeHunts);
            SigsAcademyAddons.LOGGER.info("[sig Safari] Removed expired hunts — {} remaining", activeHunts.size());
        }
    }

    // called when the hunts npc screen is scraped, parses items into hunt data
    public void onHuntsScreenScraped(List<ScreenInterceptor.ScrapedHuntItem> scrapedItems) {
        List<SafariHuntData> parsedHunts = new ArrayList<>();

        for (ScreenInterceptor.ScrapedHuntItem item : scrapedItems) {
            try {
                SafariHuntData hunt = parseHuntItem(item);
                if (hunt != null) {
                    parsedHunts.add(hunt);
                    SigsAcademyAddons.LOGGER.debug("[sig Safari] Parsed hunt: {}", hunt);
                }
            } catch (Exception e) {
                SigsAcademyAddons.LOGGER.warn("[sig Safari] Failed to parse hunt item '{}': {}",
                        item.name(), e.getMessage());
            }
        }

        if (!parsedHunts.isEmpty()) {
            this.activeHunts = parsedHunts;
            this.pendingUpdates = 0; // fresh data from screen
            dataStore.save(activeHunts);
            SigsAcademyAddons.LOGGER.info("[sig Safari] Updated {} active hunts (pending updates cleared)", activeHunts.size());
        }
    }

    // tracks unattributed hunt progress messages until next screen scrape
    public void onHuntProgressUpdate() {
        pendingUpdates++;
        SigsAcademyAddons.LOGGER.info("[sig Safari] Hunt progress updated! ({} pending — open HUNTS NPC to refresh)",
                pendingUpdates);
    }

    // pending hunt progress updates since last screen scrape
    public int getPendingUpdates() {
        return pendingUpdates;
    }

    // matches a caught pokemon's types/egg groups against active hunts
    public void onPokemonCaught(CatchDetector.CaughtPokemonInfo catchInfo) {
        if (activeHunts.isEmpty()) {
            return;
        }

        SigsAcademyAddons.LOGGER.debug("[sig Safari] Correlating catch: {} (types={}, eggGroups={})",
                catchInfo.speciesName(), catchInfo.types(), catchInfo.eggGroups());

        // find hunts matching this pokemon's types or egg groups
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
            SigsAcademyAddons.LOGGER.debug("[sig Safari] No matching hunts for caught Pokemon: {}",
                    catchInfo.speciesName());
            return;
        }

        // increment matching hunts
        for (SafariHuntData hunt : matchingHunts) {
            hunt.incrementCaught();
            SigsAcademyAddons.LOGGER.debug("[sig Safari] Incremented hunt '{}' → {}",
                    hunt.getDisplayName(), hunt.getProgressString());
        }

        // catch attributed, decrement pending counter
        if (pendingUpdates > 0) {
            pendingUpdates--;
            SigsAcademyAddons.LOGGER.debug("[sig Safari] Catch attributed — pending updates: {}", pendingUpdates);
        }

        // persist changes
        dataStore.save(activeHunts);
    }

    // checks whether a caught pokemon matches a hunt's type or egg group targets
    private boolean doesCatchMatchHunt(CatchDetector.CaughtPokemonInfo catchInfo, SafariHuntData hunt) {
        switch (hunt.getCategory()) {
            case TYPE:
                // match any pokemon type against hunt targets
                for (String target : hunt.getTargets()) {
                    for (String pokemonType : catchInfo.types()) {
                        if (pokemonType.equalsIgnoreCase(target.trim())) {
                            return true;
                        }
                    }
                }
                return false;

            case EGG_GROUP:
                // normalized comparison for egg groups (e.g. "Water 2" vs "WATER2")
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

    // returns unmodifiable list of active hunts
    public List<SafariHuntData> getActiveHunts() {
        return Collections.unmodifiableList(activeHunts);
    }

    public boolean hasActiveHunts() {
        return !activeHunts.isEmpty();
    }

    public void clearHunts() {
        activeHunts.clear();
        dataStore.save(activeHunts);
    }

    // parses a scraped item into a safari hunt data object
    private SafariHuntData parseHuntItem(ScreenInterceptor.ScrapedHuntItem item) {
        String displayName = item.name();
        List<String> loreLines = item.loreLines();

        // parse caught/total from lore
        int caught = 0;
        int total = 0;
        String resetTimeText = "";
        List<String> rewards = new ArrayList<>();
        boolean inRewardsSection = false;

        for (String line : loreLines) {
            // strip formatting codes
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

        // parse display name to determine category and targets
        String cleanName = stripFormatting(displayName);

        int stars = countStars(cleanName);
        String nameWithoutStars = STAR_PATTERN.matcher(cleanName).replaceAll("").trim();

        // determine category and targets
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
            // "ice type" → "ice", "dark flying type" → ["dark", "flying"]
            String typePart = nameWithoutStars
                    .replaceAll("(?i)type", "")
                    .trim();
            targets = List.of(typePart.split("\\s+"));
        } else {
            category = SafariHuntData.HuntCategory.UNKNOWN;
            targets = List.of(nameWithoutStars);
        }

        // convert reset text to absolute timestamp (rounded down 5s for network delay)
        long resetEndTimeMs = parseResetTimeToAbsolute(resetTimeText);

        return new SafariHuntData(
                cleanName, category, targets,
                caught, total, resetTimeText, resetEndTimeMs, stars, rewards
        );
    }

    // parses "00h 17m 45s" into absolute timestamp, rounds down 5s for network delay
    private static long parseResetTimeToAbsolute(String resetTimeText) {
        if (resetTimeText == null || resetTimeText.isEmpty()) return 0;

        Matcher m = TIME_PARTS_PATTERN.matcher(resetTimeText);
        if (!m.find()) return 0;

        try {
            int hours = Integer.parseInt(m.group(1));
            int minutes = Integer.parseInt(m.group(2));
            int seconds = Integer.parseInt(m.group(3));

            long totalMs = ((hours * 3600L) + (minutes * 60L) + seconds) * 1000L;

            // round down 5s for network delay
            totalMs = Math.max(0, totalMs - 5000L);

            return System.currentTimeMillis() + totalMs;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // strips section-sign formatting, thai resource-pack chars, and star unicode
    private static String stripFormatting(String text) {
        if (text == null) return "";
        String stripped = text.replaceAll("\u00A7[0-9a-fk-or]", "");
        stripped = stripped.replaceAll("[\\u0E00-\\u0E7F]", "");
        stripped = stripped.replaceAll("[\\u2B50\\u2605\\u2606]", "");
        return stripped.trim();
    }

    // counts star chars (unicode stars + thai resource-pack mapped chars)
    private static int countStars(String text) {
        int count = 0;
        for (char c : text.toCharArray()) {
            if (c == '\u2B50' || c == '\u2605' || c == '\u2606' || c == '*') {
                count++;
            }
            if (c == '\u0E47') { // thai char mapped to star by resource pack
                count++;
            }
        }
        return count;
    }
}
