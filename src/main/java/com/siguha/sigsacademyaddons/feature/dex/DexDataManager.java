package com.siguha.sigsacademyaddons.feature.dex;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.siguha.sigsacademyaddons.SigsAcademyAddons;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class DexDataManager {

    public record DexEntry(String id, String name, int dexnum, String primaryType, String secondaryType, int spawnCount) {}

    public record DexDetail(
            String id,
            String name,
            int dexnum,
            String primaryType,
            String secondaryType,
            Double maleRatio,
            List<String> abilities,
            List<String> eggGroups,
            List<String> normalImagePaths,
            Map<String, Integer> baseStats,
                Map<String, Integer> evYield,
            int catchRate,
            String experienceGroup,
            int moveCount,
            List<String> evolutions,
            int spawnPoolCount,
                List<String> spawnSummaries,
            boolean implemented
    ) {}

    private static final ResourceLocation DEX_INDEX = ResourceLocation.fromNamespaceAndPath(
            "sigsacademyaddons", "dex/dex.json");

    private final Map<String, DexEntry> entriesById = new LinkedHashMap<>();
    private final Map<String, DexDetail> detailsById = new LinkedHashMap<>();

    private boolean loaded = false;
    private String loadError = null;

    public void ensureLoaded() {
        if (loaded || loadError != null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.getResourceManager() == null) {
            loadError = "Resource manager unavailable";
            return;
        }

        Optional<Resource> resource = mc.getResourceManager().getResource(DEX_INDEX);
        if (resource.isEmpty()) {
            loadError = "Missing dex/dex.json";
            return;
        }

        try (Reader reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonArray()) {
                loadError = "dex.json format invalid";
                return;
            }

            JsonArray array = root.getAsJsonArray();
            for (JsonElement el : array) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject obj = el.getAsJsonObject();
                String id = asString(obj, "id");
                if (id.isEmpty()) {
                    continue;
                }

                DexEntry entry = new DexEntry(
                        id,
                        asString(obj, "name"),
                        asInt(obj, "dexnum", -1),
                        asString(obj, "primaryType"),
                        asString(obj, "secondaryType"),
                        asInt(obj, "spawnCount", 0)
                );
                entriesById.put(id, entry);
            }

            loaded = true;
        } catch (Exception e) {
            loadError = "Failed to load dex index";
            SigsAcademyAddons.LOGGER.warn("[SAA Dex] Failed to load dex index", e);
        }
    }

    public boolean isReady() {
        return loaded;
    }

    public String getLoadError() {
        return loadError;
    }

    public List<DexEntry> search(String query, int limit) {
        ensureLoaded();
        if (!loaded) {
            return Collections.emptyList();
        }

        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<DexEntry> matches = new ArrayList<>();

        for (DexEntry entry : entriesById.values()) {
            if (q.isEmpty()) {
                matches.add(entry);
                continue;
            }

            String dexStr = entry.dexnum() > 0 ? String.valueOf(entry.dexnum()) : "";
            if (entry.name().toLowerCase(Locale.ROOT).contains(q)
                    || entry.id().toLowerCase(Locale.ROOT).contains(q)
                    || dexStr.equals(q)) {
                matches.add(entry);
            }
        }

        matches.sort(Comparator.comparingInt(DexEntry::dexnum).thenComparing(DexEntry::name));
        if (limit > 0 && matches.size() > limit) {
            return new ArrayList<>(matches.subList(0, limit));
        }
        return matches;
    }

    public DexDetail getDetail(String id) {
        ensureLoaded();
        if (!loaded || id == null || id.isBlank()) {
            return null;
        }

        DexDetail cached = detailsById.get(id);
        if (cached != null) {
            return cached;
        }

        ResourceLocation path = ResourceLocation.fromNamespaceAndPath(
                "sigsacademyaddons", "dex/mons/" + id + ".json");

        Minecraft mc = Minecraft.getInstance();
        Optional<Resource> resource = mc.getResourceManager().getResource(path);
        if (resource.isEmpty()) {
            return null;
        }

        try (Reader reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                return null;
            }

            JsonObject obj = root.getAsJsonObject();
            Map<String, Integer> baseStats = parseBaseStats(obj.getAsJsonObject("baseStats"));
                Map<String, Integer> evYield = parseEvYield(obj.getAsJsonObject("evYield"));
            List<String> abilities = parseStringArray(obj.getAsJsonArray("abilities"));
            List<String> eggGroups = parseStringArray(obj.getAsJsonArray("eggGroups"));
            List<String> normalImagePaths = parseNormalImages(obj.getAsJsonObject("images"));
            List<String> evolutions = parseEvolutions(obj.getAsJsonArray("evolutions"));
            int spawnPoolCount = parseSpawnCount(obj.get("spawns"));
                List<String> spawnSummaries = parseSpawnSummaries(obj.get("spawns"));
            int moveCount = obj.has("moves") && obj.get("moves").isJsonArray()
                    ? obj.getAsJsonArray("moves").size() : 0;

            DexDetail detail = new DexDetail(
                    asString(obj, "id"),
                    asString(obj, "name"),
                    asInt(obj, "dexnum", -1),
                    asString(obj, "primaryType"),
                    asString(obj, "secondaryType"),
                    obj.has("maleRatio") && obj.get("maleRatio").isJsonPrimitive()
                            ? obj.get("maleRatio").getAsDouble() : null,
                    abilities,
                    eggGroups,
                        normalImagePaths,
                    baseStats,
                        evYield,
                    asInt(obj, "catchRate", -1),
                    asString(obj, "experienceGroup"),
                    moveCount,
                    evolutions,
                    spawnPoolCount,
                        spawnSummaries,
                    obj.has("implemented") && obj.get("implemented").isJsonPrimitive()
                            && obj.get("implemented").getAsBoolean()
            );

            detailsById.put(id, detail);
            return detail;
        } catch (IOException e) {
            SigsAcademyAddons.LOGGER.warn("[SAA Dex] Failed to load mon detail {}", id, e);
            return null;
        }
    }

    private static Map<String, Integer> parseBaseStats(JsonObject statsObj) {
        if (statsObj == null) {
            return Collections.emptyMap();
        }

        Map<String, Integer> stats = new LinkedHashMap<>();
        addStat(statsObj, stats, "hp", "HP");
        addStat(statsObj, stats, "attack", "Atk");
        addStat(statsObj, stats, "defence", "Def");
        addStat(statsObj, stats, "special_attack", "SpA");
        addStat(statsObj, stats, "special_defence", "SpD");
        addStat(statsObj, stats, "speed", "Spe");
        return stats;
    }

    private static void addStat(JsonObject src, Map<String, Integer> out, String key, String label) {
        if (src.has(key) && src.get(key).isJsonPrimitive()) {
            out.put(label, src.get(key).getAsInt());
        }
    }

    private static List<String> parseStringArray(JsonArray array) {
        if (array == null) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<>();
        for (JsonElement el : array) {
            if (el.isJsonPrimitive()) {
                values.add(el.getAsString());
            }
        }
        return values;
    }

    private static List<String> parseEvolutions(JsonArray array) {
        if (array == null) {
            return Collections.emptyList();
        }

        List<String> values = new ArrayList<>();
        for (JsonElement el : array) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject evo = el.getAsJsonObject();
            String result = asString(evo, "result");
            if (result.isEmpty()) {
                continue;
            }
            String variant = asString(evo, "variant");
            values.add(variant.isEmpty() ? result : (result + " (" + variant + ")"));
        }
        return values;
    }

    private static List<String> parseNormalImages(JsonObject imagesObj) {
        if (imagesObj == null || !imagesObj.has("normal") || !imagesObj.get("normal").isJsonArray()) {
            return Collections.emptyList();
        }

        List<String> paths = new ArrayList<>();
        JsonArray normal = imagesObj.getAsJsonArray("normal");
        for (JsonElement element : normal) {
            if (!element.isJsonPrimitive()) {
                continue;
            }

            String raw = element.getAsString();
            if (raw.isBlank()) {
                continue;
            }

            String normalized = raw.replace('\\', '/');
            if (normalized.startsWith("out/")) {
                normalized = "dex/" + normalized.substring("out/".length());
            }
            if (!normalized.startsWith("dex/")) {
                normalized = "dex/" + normalized;
            }
            paths.add(normalized);
        }

        return paths;
    }

    private static Map<String, Integer> parseEvYield(JsonObject evObj) {
        if (evObj == null) {
            return Collections.emptyMap();
        }

        Map<String, Integer> ev = new LinkedHashMap<>();
        addEv(evObj, ev, "hp", "HP");
        addEv(evObj, ev, "attack", "Atk");
        addEv(evObj, ev, "defence", "Def");
        addEv(evObj, ev, "special_attack", "SpA");
        addEv(evObj, ev, "special_defence", "SpD");
        addEv(evObj, ev, "speed", "Spe");
        return ev;
    }

    private static void addEv(JsonObject src, Map<String, Integer> out, String key, String label) {
        if (src.has(key) && src.get(key).isJsonPrimitive()) {
            int value = src.get(key).getAsInt();
            if (value > 0) {
                out.put(label, value);
            }
        }
    }

    private static List<String> parseSpawnSummaries(JsonElement spawnsElement) {
        if (spawnsElement == null || !spawnsElement.isJsonArray()) {
            return Collections.emptyList();
        }

        List<String> summaries = new ArrayList<>();
        JsonArray groups = spawnsElement.getAsJsonArray();
        for (JsonElement groupElement : groups) {
            if (!groupElement.isJsonArray()) {
                continue;
            }

            JsonArray entries = groupElement.getAsJsonArray();
            for (JsonElement entryElement : entries) {
                if (!entryElement.isJsonObject()) {
                    continue;
                }

                JsonObject entry = entryElement.getAsJsonObject();
                String rarity = asString(entry, "rarity");
                String levels = asString(entry, "levels");
                String biome = parseBiomeTag(entry.getAsJsonObject("biomeTags"));

                StringBuilder sb = new StringBuilder();
                if (!rarity.isEmpty()) {
                    sb.append(capitalizeWords(rarity.replace('-', ' ')));
                }
                if (!levels.isEmpty()) {
                    if (!sb.isEmpty()) sb.append(" | ");
                    sb.append("Lv ").append(levels);
                }
                if (!biome.isEmpty()) {
                    if (!sb.isEmpty()) sb.append(" | ");
                    sb.append(biome);
                }

                if (!sb.isEmpty()) {
                    summaries.add(sb.toString());
                }
                if (summaries.size() >= 8) {
                    return summaries;
                }
            }
        }

        return summaries;
    }

    private static String parseBiomeTag(JsonObject biomeTags) {
        if (biomeTags == null || !biomeTags.has("include") || !biomeTags.get("include").isJsonArray()) {
            return "";
        }

        JsonArray include = biomeTags.getAsJsonArray("include");
        List<String> tags = new ArrayList<>();
        for (JsonElement tagElement : include) {
            if (!tagElement.isJsonPrimitive()) {
                continue;
            }
            String raw = tagElement.getAsString();
            String clean = raw
                    .replace("#cobblemon:is_", "")
                    .replace("#minecraft:is_", "")
                    .replace("#", "")
                    .replace('_', ' ');
            tags.add(capitalizeWords(clean));
            if (tags.size() >= 2) {
                break;
            }
        }

        return String.join(", ", tags);
    }

    private static String capitalizeWords(String input) {
        if (input.isEmpty()) {
            return input;
        }

        String[] parts = input.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
        }
        return sb.toString();
    }

    private static int parseSpawnCount(JsonElement spawnsElement) {
        if (spawnsElement == null || !spawnsElement.isJsonArray()) {
            return 0;
        }
        int count = 0;
        JsonArray outer = spawnsElement.getAsJsonArray();
        for (JsonElement group : outer) {
            if (!group.isJsonArray()) {
                continue;
            }
            count += group.getAsJsonArray().size();
        }
        return count;
    }

    private static String asString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || !obj.get(key).isJsonPrimitive()) {
            return "";
        }
        return obj.get(key).getAsString();
    }

    private static int asInt(JsonObject obj, String key, int fallback) {
        if (obj == null || !obj.has(key) || !obj.get(key).isJsonPrimitive()) {
            return fallback;
        }
        try {
            return obj.get(key).getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
