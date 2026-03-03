package com.siguha.sigsacademyaddons.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.siguha.sigsacademyaddons.SigsAcademyAddons;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

// reads egg cycle directly from bundled cobblemon species files since it doesn't package the actual egg cycle in the species data
public class EggCycleLookup {

    private static final String[] GENERATION_FOLDERS = {
            "generation1", "generation2", "generation3", "generation4",
            "generation5", "generation6", "generation7", "generation7b",
            "generation8", "generation8a", "generation9"
    };

    private static final String SPECIES_DATA_PREFIX = "data/cobblemon/species/";

    private static final Map<String, Integer> cache = new HashMap<>();

    public static int getEggCycles(String speciesName) {
        if (speciesName == null || speciesName.isEmpty()) {
            return -1;
        }

        String key = speciesName.toLowerCase().trim();

        if (cache.containsKey(key)) {
            return cache.get(key);
        }

        int eggCycles = loadFromSpeciesData(key);
        cache.put(key, eggCycles);

        if (eggCycles > 0) {
            SigsAcademyAddons.LOGGER.debug("[SAA EggCycleLookup] loaded eggCycles for {}: {}", speciesName, eggCycles);
        } else {
            SigsAcademyAddons.LOGGER.warn("[SAA EggCycleLookup] no eggCycles found for {}", speciesName);
        }

        return eggCycles;
    }

    private static int loadFromSpeciesData(String speciesNameLower) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();

        for (String genFolder : GENERATION_FOLDERS) {
            String path = SPECIES_DATA_PREFIX + genFolder + "/" + speciesNameLower + ".json";

            try (InputStream is = cl.getResourceAsStream(path)) {
                if (is == null) {
                    continue;
                }

                JsonObject json = JsonParser.parseReader(
                        new InputStreamReader(is, StandardCharsets.UTF_8)
                ).getAsJsonObject();

                if (json.has("eggCycles")) {
                    return json.get("eggCycles").getAsInt();
                }

                return -1;

            } catch (Exception e) {
                SigsAcademyAddons.LOGGER.debug("[SAA EggCycleLookup] error reading {}: {}", path, e.getMessage());
            }
        }

        return -1;
    }

    public static void clearCache() {
        cache.clear();
    }
}
