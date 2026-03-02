package com.siguha.sigsacademyaddons;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SigsAcademyAddons implements ModInitializer {
    public static final String MOD_ID = "sigs-academy-addons";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final String CONFIG_DIR = "config/sigs-academy-addons";

    @Override
    public void onInitialize() {
        LOGGER.info("[Sigs Academy Addons] Common initialization complete.");
    }

    public static String getCurrentServerAddress() {
        Minecraft client = Minecraft.getInstance();
        if (client.getCurrentServer() != null) {
            return client.getCurrentServer().ip;
        }
        return "singleplayer";
    }

    public static String normalizeForComparison(String text) {
        if (text == null) return "";
        return text.toLowerCase().replaceAll("[\\s\\-_]", "");
    }
}
