package com.siguha.sigsacademyaddons.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.siguha.sigsacademyaddons.SigsAcademyAddons;
import net.minecraft.client.Minecraft;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

// persists hud position, scale, and feature toggles to hud-config.json
public class HudConfig {

    private static final String CONFIG_FILE = "hud-config.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // hud anchor corner, offsets are relative to this
    public enum Anchor {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }

    // hud visual style: solid background or transparent overlay
    public enum HudStyle {
        SOLID,
        TRANSPARENT
    }

    private Anchor anchor = Anchor.TOP_RIGHT;
    private int offsetX = 5;
    private int offsetY = 5;
    private boolean safariTimerAlways = true;
    private float hudScale = 1.0f;
    private boolean safariQuestMonGlow = true;
    private boolean safariQuestMonTracers = true;
    private HudStyle hudStyle = HudStyle.SOLID;

    public HudConfig() {
        load();
    }

    public boolean isSafariTimerAlways() {
        return safariTimerAlways;
    }

    public void setSafariTimerAlways(boolean safariTimerAlways) {
        this.safariTimerAlways = safariTimerAlways;
        save();
    }

    public float getHudScale() {
        return hudScale;
    }

    public void setHudScale(float hudScale) {
        this.hudScale = Math.max(0.5f, Math.min(2.0f, hudScale));
        save();
    }

    public boolean isSafariQuestMonGlow() {
        return safariQuestMonGlow;
    }

    public void setSafariQuestMonGlow(boolean safariQuestMonGlow) {
        this.safariQuestMonGlow = safariQuestMonGlow;
        save();
    }

    public boolean isSafariQuestMonTracers() {
        return safariQuestMonTracers;
    }

    public void setSafariQuestMonTracers(boolean safariQuestMonTracers) {
        this.safariQuestMonTracers = safariQuestMonTracers;
        save();
    }

    public HudStyle getHudStyle() {
        return hudStyle;
    }

    public void setHudStyle(HudStyle hudStyle) {
        this.hudStyle = hudStyle;
        save();
    }

    public Anchor getAnchor() {
        return anchor;
    }

    public int getOffsetX() {
        return offsetX;
    }

    public int getOffsetY() {
        return offsetY;
    }

    // calculates panel x from anchor and offset
    public int getPanelX(int screenWidth, int panelWidth) {
        return switch (anchor) {
            case TOP_LEFT, BOTTOM_LEFT -> offsetX;
            case TOP_RIGHT, BOTTOM_RIGHT -> screenWidth - panelWidth - offsetX;
        };
    }

    // calculates panel y from anchor and offset
    public int getPanelY(int screenHeight, int panelHeight) {
        return switch (anchor) {
            case TOP_LEFT, TOP_RIGHT -> offsetY;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> screenHeight - panelHeight - offsetY;
        };
    }

    // sets position from absolute coords, auto-determines best anchor quadrant
    public void setPositionFromAbsolute(int panelX, int panelY, int panelWidth, int panelHeight,
                                         int screenWidth, int screenHeight) {
        int centerX = panelX + panelWidth / 2;
        int centerY = panelY + panelHeight / 2;

        boolean leftHalf = centerX < screenWidth / 2;
        boolean topHalf = centerY < screenHeight / 2;

        if (topHalf && leftHalf) {
            anchor = Anchor.TOP_LEFT;
            offsetX = panelX;
            offsetY = panelY;
        } else if (topHalf) {
            anchor = Anchor.TOP_RIGHT;
            offsetX = screenWidth - panelX - panelWidth;
            offsetY = panelY;
        } else if (leftHalf) {
            anchor = Anchor.BOTTOM_LEFT;
            offsetX = panelX;
            offsetY = screenHeight - panelY - panelHeight;
        } else {
            anchor = Anchor.BOTTOM_RIGHT;
            offsetX = screenWidth - panelX - panelWidth;
            offsetY = screenHeight - panelY - panelHeight;
        }

        // clamp to non-negative
        offsetX = Math.max(0, offsetX);
        offsetY = Math.max(0, offsetY);

        save();
    }

    public void save() {
        try {
            Path filePath = getConfigPath();
            Files.createDirectories(filePath.getParent());

            ConfigData data = new ConfigData(anchor.name(), offsetX, offsetY, safariTimerAlways, hudScale,
                    safariQuestMonGlow, safariQuestMonTracers, hudStyle.name());
            try (Writer writer = Files.newBufferedWriter(filePath)) {
                GSON.toJson(data, writer);
            }
            SigsAcademyAddons.LOGGER.debug("[HudConfig] Saved HUD position: anchor={}, offsetX={}, offsetY={}",
                    anchor, offsetX, offsetY);
        } catch (Exception e) {
            SigsAcademyAddons.LOGGER.warn("[HudConfig] Failed to save config", e);
        }
    }

    private void load() {
        try {
            Path filePath = getConfigPath();
            if (!Files.exists(filePath)) {
                return;
            }

            try (Reader reader = Files.newBufferedReader(filePath)) {
                ConfigData data = GSON.fromJson(reader, ConfigData.class);
                if (data != null) {
                    this.anchor = Anchor.valueOf(data.anchor);
                    this.offsetX = data.offsetX;
                    this.offsetY = data.offsetY;
                    this.safariTimerAlways = data.safariTimerAlways;
                    this.hudScale = data.hudScale > 0 ? data.hudScale : 1.0f;
                    // null = missing from old config, default true
                    this.safariQuestMonGlow = data.safariQuestMonGlow != null ? data.safariQuestMonGlow : true;
                    this.safariQuestMonTracers = data.safariQuestMonTracers != null ? data.safariQuestMonTracers : true;
                    this.hudStyle = data.hudStyle != null ? HudStyle.valueOf(data.hudStyle) : HudStyle.SOLID;
                    SigsAcademyAddons.LOGGER.info("[HudConfig] Loaded HUD position: anchor={}, offsetX={}, offsetY={}",
                            anchor, offsetX, offsetY);
                }
            }
        } catch (Exception e) {
            SigsAcademyAddons.LOGGER.warn("[HudConfig] Failed to load config, using defaults", e);
        }
    }

    private Path getConfigPath() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve(SigsAcademyAddons.CONFIG_DIR)
                .resolve(CONFIG_FILE);
    }

    private record ConfigData(String anchor, int offsetX, int offsetY, boolean safariTimerAlways, float hudScale,
                               Boolean safariQuestMonGlow, Boolean safariQuestMonTracers, String hudStyle) {
    }
}
