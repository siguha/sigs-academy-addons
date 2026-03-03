package com.siguha.sigsacademyaddons.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.siguha.sigsacademyaddons.SigsAcademyAddons;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

// reads egg groups from cobblemon species json in the mod jar
public class EggGroupLookup {

    private static final String[] GENERATION_FOLDERS = {
            "generation1", "generation2", "generation3", "generation4",
            "generation5", "generation6", "generation7", "generation7b",
            "generation8", "generation8a", "generation9"
    };

    private static final String SPECIES_DATA_PREFIX = "data/cobblemon/species/";

    private static final Map<String, Set<String>> cache = new HashMap<>();

    public static Set<String> getEggGroups(String speciesName) {
        if (speciesName == null || speciesName.isEmpty()) {
            return Set.of();
        }

        String key = speciesName.toLowerCase();

        // check cache first
        if (cache.containsKey(key)) {
            return cache.get(key);
        }

        // load from species json
        Set<String> eggGroups = loadFromSpeciesData(key);
        cache.put(key, eggGroups);

        if (!eggGroups.isEmpty()) {
            SigsAcademyAddons.LOGGER.debug("[SAA EggGroupLookup] loaded egg groups for {}: {}", speciesName, eggGroups);
        } else {
            SigsAcademyAddons.LOGGER.debug("[SAA EggGroupLookup] no egg groups found for {} in species data", speciesName);
        }

        return eggGroups;
    }

    // searches generation folders on classpath for species json
    private static Set<String> loadFromSpeciesData(String speciesNameLower) {
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

                JsonArray eggGroupsArray = json.getAsJsonArray("eggGroups");
                if (eggGroupsArray == null || eggGroupsArray.isEmpty()) {
                    return Set.of();
                }

                Set<String> eggGroups = new HashSet<>();
                for (JsonElement elem : eggGroupsArray) {
                    eggGroups.add(elem.getAsString().toLowerCase());
                }
                return eggGroups;

            } catch (Exception e) {
                SigsAcademyAddons.LOGGER.debug("[SAA EggGroupLookup] error reading {}: {}", path, e.getMessage());
            }
        }

        return Set.of();
    }

    public static void clearCache() {
        cache.clear();
    }
}
