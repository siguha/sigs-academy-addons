package com.siguha.sigsacademyaddons.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.siguha.sigsacademyaddons.SigsAcademyAddons;
import net.minecraft.client.Minecraft;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HudConfig {

    private static final String CONFIG_FILE = "hud-config.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public enum Anchor {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }

    public enum HudStyle {
        SOLID,
        TRANSPARENT
    }

    public enum HudLayout {
        FULL,
        COMPACT
    }

    private Anchor anchor = Anchor.TOP_RIGHT;
    private int offsetX = 5;
    private int offsetY = 5;
    private int refScreenWidth = 0;
    private boolean safariTimerAlways = true;
    private float hudScale = 1.0f;
    private boolean safariQuestMonGlow = true;
    private HudStyle hudStyle = HudStyle.SOLID;
    private HudLayout hudLayout = HudLayout.FULL;
    private boolean safariMenuEnabled = true;

    private boolean daycareMenuEnabled = true;
    private boolean daycareSoundsEnabled = true;
    private Anchor daycareAnchor = Anchor.TOP_LEFT;
    private int daycareOffsetX = 5;
    private int daycareOffsetY = 5;
    private int daycareRefScreenWidth = 0;
    private float daycareScale = 1.0f;
    private int daycareEggsHatchingSlots = 5;
    private float manualHatchMultiplier = 0f;

    private boolean wtMenuEnabled = true;
    private boolean wtShowChatReminders = true;
    private boolean wtSoundsEnabled = true;
    private Anchor wtAnchor = Anchor.BOTTOM_RIGHT;
    private int wtOffsetX = 5;
    private int wtOffsetY = 5;
    private int wtRefScreenWidth = 0;
    private float wtScale = 1.0f;

    private List<String> joinedGroup = new ArrayList<>();
    private float groupScale = 1.0f;
    private Anchor groupAnchor = Anchor.TOP_LEFT;
    private int groupOffsetX = 5;
    private int groupOffsetY = 5;
    private int groupRefScreenWidth = 0;

    private boolean daycareBabyGuards = true;
    private int daycareIvPercentLower = 60;
    private int daycareIvPercentUpper = 80;

    private boolean driflootAlertsEnabled = true;

    private boolean suppressInRaids = true;
    private boolean suppressInHideouts = false;
    private boolean suppressInDungeons = false;
    private boolean suppressInBattles = true;
    private boolean hudHidden = false;

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

    public HudStyle getHudStyle() {
        return hudStyle;
    }

    public void setHudStyle(HudStyle hudStyle) {
        this.hudStyle = hudStyle;
        save();
    }

    public HudLayout getHudLayout() {
        return hudLayout;
    }

    public void setHudLayout(HudLayout hudLayout) {
        this.hudLayout = hudLayout;
        save();
    }

    public boolean isCompact() {
        return hudLayout == HudLayout.COMPACT;
    }

    public boolean isSafariMenuEnabled() {
        return safariMenuEnabled;
    }

    public void setSafariMenuEnabled(boolean safariMenuEnabled) {
        this.safariMenuEnabled = safariMenuEnabled;
        save();
    }

    public boolean isDaycareMenuEnabled() {
        return daycareMenuEnabled;
    }

    public void setDaycareMenuEnabled(boolean daycareMenuEnabled) {
        this.daycareMenuEnabled = daycareMenuEnabled;
        save();
    }

    public boolean isDaycareSoundsEnabled() {
        return daycareSoundsEnabled;
    }

    public void setDaycareSoundsEnabled(boolean daycareSoundsEnabled) {
        this.daycareSoundsEnabled = daycareSoundsEnabled;
        save();
    }

    public boolean isWtMenuEnabled() {
        return wtMenuEnabled;
    }

    public void setWtMenuEnabled(boolean wtMenuEnabled) {
        this.wtMenuEnabled = wtMenuEnabled;
        save();
    }

    public boolean isWtShowChatReminders() {
        return wtShowChatReminders;
    }

    public void setWtShowChatReminders(boolean wtShowChatReminders) {
        this.wtShowChatReminders = wtShowChatReminders;
        save();
    }

    public boolean isWtSoundsEnabled() {
        return wtSoundsEnabled;
    }

    public void setWtSoundsEnabled(boolean wtSoundsEnabled) {
        this.wtSoundsEnabled = wtSoundsEnabled;
        save();
    }

    public Anchor getAnchor() {
        return anchor;
    }

    public int getPanelX(int screenWidth, int panelWidth) {
        if (refScreenWidth > 0) {
            int refPanelX = switch (anchor) {
                case TOP_LEFT, BOTTOM_LEFT -> offsetX;
                case TOP_RIGHT, BOTTOM_RIGHT -> refScreenWidth - panelWidth - offsetX;
            };
            return (screenWidth - refScreenWidth) / 2 + refPanelX;
        }
        return switch (anchor) {
            case TOP_LEFT, BOTTOM_LEFT -> offsetX;
            case TOP_RIGHT, BOTTOM_RIGHT -> screenWidth - panelWidth - offsetX;
        };
    }

    public int getPanelY(int screenHeight, int panelHeight) {
        return switch (anchor) {
            case TOP_LEFT, TOP_RIGHT -> offsetY;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> screenHeight - offsetY;
        };
    }

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
            offsetY = screenHeight - panelY;
        } else {
            anchor = Anchor.BOTTOM_RIGHT;
            offsetX = screenWidth - panelX - panelWidth;
            offsetY = screenHeight - panelY;
        }

        offsetX = Math.max(0, offsetX);
        offsetY = Math.max(0, offsetY);
        this.refScreenWidth = screenWidth;

        save();
    }

    public Anchor getDaycareAnchor() {
        return daycareAnchor;
    }

    public int getDaycarePanelX(int screenWidth, int panelWidth) {
        if (daycareRefScreenWidth > 0) {
            int refPanelX = switch (daycareAnchor) {
                case TOP_LEFT, BOTTOM_LEFT -> daycareOffsetX;
                case TOP_RIGHT, BOTTOM_RIGHT -> daycareRefScreenWidth - panelWidth - daycareOffsetX;
            };
            return (screenWidth - daycareRefScreenWidth) / 2 + refPanelX;
        }
        return switch (daycareAnchor) {
            case TOP_LEFT, BOTTOM_LEFT -> daycareOffsetX;
            case TOP_RIGHT, BOTTOM_RIGHT -> screenWidth - panelWidth - daycareOffsetX;
        };
    }

    public int getDaycarePanelY(int screenHeight, int panelHeight) {
        return switch (daycareAnchor) {
            case TOP_LEFT, TOP_RIGHT -> daycareOffsetY;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> screenHeight - daycareOffsetY;
        };
    }

    public float getDaycareScale() {
        return daycareScale;
    }

    public void setDaycareScale(float daycareScale) {
        this.daycareScale = Math.max(0.5f, Math.min(2.0f, daycareScale));
        save();
    }

    public int getDaycareEggsHatchingSlots() {
        return daycareEggsHatchingSlots;
    }

    public void setDaycareEggsHatchingSlots(int daycareEggsHatchingSlots) {
        this.daycareEggsHatchingSlots = Math.max(0, Math.min(5, daycareEggsHatchingSlots));
        save();
    }

    public float getManualHatchMultiplier() {
        return manualHatchMultiplier;
    }

    public void setManualHatchMultiplier(float manualHatchMultiplier) {
        this.manualHatchMultiplier = manualHatchMultiplier;
        save();
    }

    public void setDaycarePositionFromAbsolute(int panelX, int panelY, int panelWidth, int panelHeight,
                                                int screenWidth, int screenHeight) {
        int centerX = panelX + panelWidth / 2;
        int centerY = panelY + panelHeight / 2;

        boolean leftHalf = centerX < screenWidth / 2;
        boolean topHalf = centerY < screenHeight / 2;

        if (topHalf && leftHalf) {
            daycareAnchor = Anchor.TOP_LEFT;
            daycareOffsetX = panelX;
            daycareOffsetY = panelY;
        } else if (topHalf) {
            daycareAnchor = Anchor.TOP_RIGHT;
            daycareOffsetX = screenWidth - panelX - panelWidth;
            daycareOffsetY = panelY;
        } else if (leftHalf) {
            daycareAnchor = Anchor.BOTTOM_LEFT;
            daycareOffsetX = panelX;
            daycareOffsetY = screenHeight - panelY;
        } else {
            daycareAnchor = Anchor.BOTTOM_RIGHT;
            daycareOffsetX = screenWidth - panelX - panelWidth;
            daycareOffsetY = screenHeight - panelY;
        }

        daycareOffsetX = Math.max(0, daycareOffsetX);
        daycareOffsetY = Math.max(0, daycareOffsetY);
        this.daycareRefScreenWidth = screenWidth;

        save();
    }

    public Anchor getWtAnchor() {
        return wtAnchor;
    }

    public int getWtPanelX(int screenWidth, int panelWidth) {
        if (wtRefScreenWidth > 0) {
            int refPanelX = switch (wtAnchor) {
                case TOP_LEFT, BOTTOM_LEFT -> wtOffsetX;
                case TOP_RIGHT, BOTTOM_RIGHT -> wtRefScreenWidth - panelWidth - wtOffsetX;
            };
            return (screenWidth - wtRefScreenWidth) / 2 + refPanelX;
        }
        return switch (wtAnchor) {
            case TOP_LEFT, BOTTOM_LEFT -> wtOffsetX;
            case TOP_RIGHT, BOTTOM_RIGHT -> screenWidth - panelWidth - wtOffsetX;
        };
    }

    public int getWtPanelY(int screenHeight, int panelHeight) {
        return switch (wtAnchor) {
            case TOP_LEFT, TOP_RIGHT -> wtOffsetY;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> screenHeight - wtOffsetY;
        };
    }

    public float getWtScale() {
        return wtScale;
    }

    public void setWtScale(float wtScale) {
        this.wtScale = Math.max(0.5f, Math.min(2.0f, wtScale));
        save();
    }

    public void setWtPositionFromAbsolute(int panelX, int panelY, int panelWidth, int panelHeight,
                                           int screenWidth, int screenHeight) {
        int centerX = panelX + panelWidth / 2;
        int centerY = panelY + panelHeight / 2;

        boolean leftHalf = centerX < screenWidth / 2;
        boolean topHalf = centerY < screenHeight / 2;

        if (topHalf && leftHalf) {
            wtAnchor = Anchor.TOP_LEFT;
            wtOffsetX = panelX;
            wtOffsetY = panelY;
        } else if (topHalf) {
            wtAnchor = Anchor.TOP_RIGHT;
            wtOffsetX = screenWidth - panelX - panelWidth;
            wtOffsetY = panelY;
        } else if (leftHalf) {
            wtAnchor = Anchor.BOTTOM_LEFT;
            wtOffsetX = panelX;
            wtOffsetY = screenHeight - panelY;
        } else {
            wtAnchor = Anchor.BOTTOM_RIGHT;
            wtOffsetX = screenWidth - panelX - panelWidth;
            wtOffsetY = screenHeight - panelY;
        }

        wtOffsetX = Math.max(0, wtOffsetX);
        wtOffsetY = Math.max(0, wtOffsetY);
        this.wtRefScreenWidth = screenWidth;

        save();
    }

    public List<String> getJoinedGroup() {
        return Collections.unmodifiableList(joinedGroup);
    }

    public void setJoinedGroup(List<String> group) {
        this.joinedGroup = group != null ? new ArrayList<>(group) : new ArrayList<>();
        save();
    }

    public boolean isInGroup(String panelId) {
        return joinedGroup.contains(panelId);
    }

    public float getGroupScale() {
        return groupScale;
    }

    public void setGroupScale(float groupScale) {
        this.groupScale = Math.max(0.5f, Math.min(2.0f, groupScale));
        save();
    }

    public int getGroupPanelX(int screenWidth, int scaledWidth) {
        if (groupRefScreenWidth > 0) {
            return (screenWidth - groupRefScreenWidth) / 2 + groupOffsetX;
        }
        return groupOffsetX;
    }

    public int getGroupPanelY(int screenHeight, int scaledHeight) {
        return switch (groupAnchor) {
            case TOP_LEFT, TOP_RIGHT -> groupOffsetY;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> screenHeight - groupOffsetY;
        };
    }

    public boolean isDaycareBabyGuards() {
        return daycareBabyGuards;
    }

    public void setDaycareBabyGuards(boolean daycareBabyGuards) {
        this.daycareBabyGuards = daycareBabyGuards;
        save();
    }

    public int getDaycareIvPercentLower() {
        return daycareIvPercentLower;
    }

    public void setDaycareIvPercentLower(int value) {
        this.daycareIvPercentLower = Math.max(0, Math.min(100, value));
        save();
    }

    public int getDaycareIvPercentUpper() {
        return daycareIvPercentUpper;
    }

    public void setDaycareIvPercentUpper(int value) {
        this.daycareIvPercentUpper = Math.max(0, Math.min(100, value));
        save();
    }

    public boolean isDriflootAlertsEnabled() {
        return driflootAlertsEnabled;
    }

    public void setDriflootAlertsEnabled(boolean driflootAlertsEnabled) {
        this.driflootAlertsEnabled = driflootAlertsEnabled;
        save();
    }

    public boolean isSuppressInRaids() { return suppressInRaids; }

    public void setSuppressInRaids(boolean suppressInRaids) {
        this.suppressInRaids = suppressInRaids;
        save();
    }

    public boolean isSuppressInHideouts() { return suppressInHideouts; }

    public void setSuppressInHideouts(boolean suppressInHideouts) {
        this.suppressInHideouts = suppressInHideouts;
        save();
    }

    public boolean isSuppressInDungeons() { return suppressInDungeons; }

    public void setSuppressInDungeons(boolean suppressInDungeons) {
        this.suppressInDungeons = suppressInDungeons;
        save();
    }

    public boolean isSuppressInBattles() { return suppressInBattles; }

    public void setSuppressInBattles(boolean suppressInBattles) {
        this.suppressInBattles = suppressInBattles;
        save();
    }

    public boolean isHudHidden() { return hudHidden; }

    public void setHudHidden(boolean hudHidden) {
        this.hudHidden = hudHidden;
        save();
    }

    public void setGroupPositionFromAbsolute(int panelX, int panelY, int panelWidth, int panelHeight,
                                              int screenWidth, int screenHeight) {
        int centerY = panelY + panelHeight / 2;
        boolean topHalf = centerY < screenHeight / 2;

        groupOffsetX = panelX;
        this.groupRefScreenWidth = screenWidth;

        if (topHalf) {
            groupAnchor = Anchor.TOP_LEFT;
            groupOffsetY = panelY;
        } else {
            groupAnchor = Anchor.BOTTOM_LEFT;
            groupOffsetY = screenHeight - panelY;
        }

        groupOffsetX = Math.max(0, groupOffsetX);
        groupOffsetY = Math.max(0, groupOffsetY);

        save();
    }

    public void save() {
        try {
            Path filePath = getConfigPath();
            Files.createDirectories(filePath.getParent());

            ConfigData data = new ConfigData(
                    anchor.name(), offsetX, offsetY, safariTimerAlways, hudScale,
                    safariQuestMonGlow, hudStyle.name(), safariMenuEnabled,
                    daycareMenuEnabled, daycareSoundsEnabled,
                    daycareAnchor.name(), daycareOffsetX, daycareOffsetY,
                    daycareScale, daycareEggsHatchingSlots,
                    wtMenuEnabled, wtShowChatReminders, wtSoundsEnabled,
                    wtAnchor.name(), wtOffsetX, wtOffsetY, wtScale,
                    hudLayout.name(),
                    refScreenWidth, daycareRefScreenWidth, wtRefScreenWidth,
                    joinedGroup.isEmpty() ? null : new ArrayList<>(joinedGroup),
                    groupScale, groupAnchor.name(), groupOffsetX, groupOffsetY,
                    groupRefScreenWidth,
                    daycareBabyGuards,
                    driflootAlertsEnabled,
                    suppressInRaids, suppressInHideouts, suppressInDungeons,
                    suppressInBattles, hudHidden,
                    daycareIvPercentLower, daycareIvPercentUpper);
            try (Writer writer = Files.newBufferedWriter(filePath)) {
                GSON.toJson(data, writer);
            }
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
                    this.safariTimerAlways = data.safariTimerAlways;
                    this.hudScale = data.hudScale > 0 ? data.hudScale : 1.0f;
                    this.safariQuestMonGlow = data.safariQuestMonGlow != null ? data.safariQuestMonGlow : true;
                    this.hudStyle = data.hudStyle != null ? HudStyle.valueOf(data.hudStyle) : HudStyle.SOLID;
                    this.safariMenuEnabled = data.safariMenuEnabled != null ? data.safariMenuEnabled : true;
                    this.hudLayout = data.hudLayout != null ? HudLayout.valueOf(data.hudLayout) : HudLayout.FULL;

                    this.offsetX = data.offsetX;
                    this.offsetY = data.offsetY;
                    this.refScreenWidth = data.refScreenWidth != null ? data.refScreenWidth : 0;

                    this.daycareMenuEnabled = data.daycareMenuEnabled != null ? data.daycareMenuEnabled : true;
                    this.daycareSoundsEnabled = data.daycareSoundsEnabled != null ? data.daycareSoundsEnabled : true;
                    this.daycareAnchor = data.daycareAnchor != null ? Anchor.valueOf(data.daycareAnchor) : Anchor.TOP_LEFT;
                    this.daycareOffsetX = data.daycareOffsetX != null ? data.daycareOffsetX : 5;
                    this.daycareOffsetY = data.daycareOffsetY != null ? data.daycareOffsetY : 5;
                    this.daycareRefScreenWidth = data.daycareRefScreenWidth != null ? data.daycareRefScreenWidth : 0;
                    this.daycareScale = data.daycareScale != null && data.daycareScale > 0 ? data.daycareScale : 1.0f;
                    this.daycareEggsHatchingSlots = data.daycareEggsHatchingSlots != null ? data.daycareEggsHatchingSlots : 5;

                    this.wtMenuEnabled = data.wtMenuEnabled != null ? data.wtMenuEnabled : true;
                    this.wtShowChatReminders = data.wtShowChatReminders != null ? data.wtShowChatReminders : true;
                    this.wtSoundsEnabled = data.wtSoundsEnabled != null ? data.wtSoundsEnabled : true;
                    this.wtAnchor = data.wtAnchor != null ? Anchor.valueOf(data.wtAnchor) : Anchor.BOTTOM_RIGHT;
                    this.wtOffsetX = data.wtOffsetX != null ? data.wtOffsetX : 5;
                    this.wtOffsetY = data.wtOffsetY != null ? data.wtOffsetY : 5;
                    this.wtRefScreenWidth = data.wtRefScreenWidth != null ? data.wtRefScreenWidth : 0;
                    this.wtScale = data.wtScale != null && data.wtScale > 0 ? data.wtScale : 1.0f;

                    this.joinedGroup = data.joinedGroup != null ? new ArrayList<>(data.joinedGroup) : new ArrayList<>();
                    this.groupScale = data.groupScale != null && data.groupScale > 0 ? data.groupScale : 1.0f;
                    this.groupAnchor = data.groupAnchor != null ? Anchor.valueOf(data.groupAnchor) : Anchor.TOP_LEFT;
                    this.groupOffsetX = data.groupOffsetX != null ? data.groupOffsetX : 5;
                    this.groupOffsetY = data.groupOffsetY != null ? data.groupOffsetY : 5;
                    this.groupRefScreenWidth = data.groupRefScreenWidth != null ? data.groupRefScreenWidth : 0;

                    this.daycareBabyGuards = data.daycareBabyGuards != null ? data.daycareBabyGuards : true;

                    this.driflootAlertsEnabled = data.driflootAlertsEnabled != null ? data.driflootAlertsEnabled : true;

                    this.suppressInRaids = data.suppressInRaids != null ? data.suppressInRaids : true;
                    this.suppressInHideouts = data.suppressInHideouts != null ? data.suppressInHideouts : false;
                    this.suppressInDungeons = data.suppressInDungeons != null ? data.suppressInDungeons : false;
                    this.suppressInBattles = data.suppressInBattles != null ? data.suppressInBattles : true;
                    this.hudHidden = data.hudHidden != null ? data.hudHidden : false;
                    this.daycareIvPercentLower = data.daycareIvPercentLower != null
                            ? Math.max(0, Math.min(100, data.daycareIvPercentLower)) : 60;
                    this.daycareIvPercentUpper = data.daycareIvPercentUpper != null
                            ? Math.max(0, Math.min(100, data.daycareIvPercentUpper)) : 80;
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

    private record ConfigData(
            String anchor, int offsetX, int offsetY, boolean safariTimerAlways, float hudScale,
            Boolean safariQuestMonGlow, String hudStyle, Boolean safariMenuEnabled,
            Boolean daycareMenuEnabled, Boolean daycareSoundsEnabled,
            String daycareAnchor, Integer daycareOffsetX, Integer daycareOffsetY,
            Float daycareScale, Integer daycareEggsHatchingSlots,
            Boolean wtMenuEnabled, Boolean wtShowChatReminders, Boolean wtSoundsEnabled,
            String wtAnchor, Integer wtOffsetX, Integer wtOffsetY, Float wtScale,
            String hudLayout,
            Integer refScreenWidth, Integer daycareRefScreenWidth, Integer wtRefScreenWidth,
            List<String> joinedGroup, Float groupScale, String groupAnchor,
            Integer groupOffsetX, Integer groupOffsetY, Integer groupRefScreenWidth,
            Boolean daycareBabyGuards,
            Boolean driflootAlertsEnabled,
            Boolean suppressInRaids, Boolean suppressInHideouts, Boolean suppressInDungeons,
            Boolean suppressInBattles, Boolean hudHidden,
            Integer daycareIvPercentLower, Integer daycareIvPercentUpper) {
    }
}
