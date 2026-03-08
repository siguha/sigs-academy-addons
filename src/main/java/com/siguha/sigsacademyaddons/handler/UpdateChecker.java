package com.siguha.sigsacademyaddons.handler;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.siguha.sigsacademyaddons.SigsAcademyAddons;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class UpdateChecker {

    private static final String GITHUB_API_URL =
            "https://api.github.com/repos/siguha/sigs-academy-addons/releases/latest";
    private static final String CURSEFORGE_URL =
            "https://www.curseforge.com/minecraft/mc-mods/sigs-academy-addons";

    private static volatile boolean checked = false;

    public static void checkForUpdatesAsync() {
        if (checked) return;
        checked = true;

        Thread thread = new Thread(() -> {
            try {
                String currentVersion = FabricLoader.getInstance()
                        .getModContainer(SigsAcademyAddons.MOD_ID)
                        .map(c -> c.getMetadata().getVersion().getFriendlyString())
                        .orElse(null);
                if (currentVersion == null) return;

                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(GITHUB_API_URL))
                        .header("Accept", "application/vnd.github.v3+json")
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) return;

                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                String latestTag = json.get("tag_name").getAsString();

                if (isNewerVersion(normalizeVersion(latestTag), normalizeVersion(currentVersion))) {
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> {
                        if (mc.player != null) {
                            mc.player.sendSystemMessage(buildUpdateMessage(latestTag));
                        }
                    });
                }
            } catch (Exception e) {
                SigsAcademyAddons.LOGGER.debug("[SAA] Update check failed: {}", e.getMessage());
            }
        }, "SAA-UpdateChecker");
        thread.setDaemon(true);
        thread.start();
    }

    public static void resetForNewSession() {
        checked = false;
    }

    private static String normalizeVersion(String version) {
        return version.replaceAll("^[vVbB]", "").trim();
    }

    private static boolean isNewerVersion(String latest, String current) {
        String[] latestParts = latest.split("\\.");
        String[] currentParts = current.split("\\.");
        int length = Math.max(latestParts.length, currentParts.length);
        for (int i = 0; i < length; i++) {
            int l = i < latestParts.length ? parseIntSafe(latestParts[i]) : 0;
            int c = i < currentParts.length ? parseIntSafe(currentParts[i]) : 0;
            if (l > c) return true;
            if (l < c) return false;
        }
        return false;
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static Component buildUpdateMessage(String latestVersion) {
        MutableComponent msg = Component.empty();
        msg.append(Component.literal("SAA has a more recent version available! ")
                .withStyle(ChatFormatting.YELLOW));
        msg.append(Component.literal("(" + latestVersion + ")")
                .withStyle(ChatFormatting.GOLD));
        msg.append(Component.literal("\nTo stay up-to-date and take advantage of our newest features, consider upgrading!")
                .withStyle(ChatFormatting.YELLOW));
        msg.append(Component.literal("\n"));
        msg.append(Component.literal("[Download on CurseForge]")
                .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.GREEN)
                        .withBold(true)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, CURSEFORGE_URL))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Click to open CurseForge page")))));
        return msg;
    }
}
