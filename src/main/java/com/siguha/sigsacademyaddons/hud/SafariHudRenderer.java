package com.siguha.sigsacademyaddons.hud;

import com.siguha.sigsacademyaddons.config.HudConfig;
import com.siguha.sigsacademyaddons.feature.safari.HuntEntityTracker;
import com.siguha.sigsacademyaddons.feature.safari.SafariHuntData;
import com.siguha.sigsacademyaddons.feature.safari.SafariHuntManager;
import com.siguha.sigsacademyaddons.feature.safari.SafariManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class SafariHudRenderer implements HudPanel {

    private static final int PADDING = 4;
    private static final int LINE_HEIGHT = 11;
    private static final int SECTION_SPACING = 6;
    private static final int PROGRESS_BAR_HEIGHT = 3;
    private static final int TIMER_BAR_HEIGHT = 11;
    private static final int PANEL_MIN_WIDTH = 140;

    private static final int COLOR_BG = 0xAA000000;
    private static final int COLOR_HEADER = 0xFFFFAA00;
    private static final int COLOR_TEXT_PRIMARY = 0xFFFFFFFF;
    private static final int COLOR_TEXT_SECONDARY = 0xFFAAAAAA;
    private static final int COLOR_TIMER_SAFE = 0xFF55FF55;
    private static final int COLOR_TIMER_WARN = 0xFFFFFF55;
    private static final int COLOR_TIMER_DANGER = 0xFFFF5555;
    private static final int COLOR_PROGRESS_BG = 0xFF333333;
    private static final int COLOR_PROGRESS_FILL = 0xFF55FF55;
    private static final int COLOR_PROGRESS_COMPLETE = 0xFFFFAA00;
    private static final int COLOR_STAR = 0xFFFFD700;
    private static final int COLOR_RESET_TIMER = 0xFFFF8855;
    private static final int COLOR_QUEST_NUMBER = 0xFFFFFF55;

    private static final String NO_QUESTS_MESSAGE = "Please visit the Safari Hunt NPC and load your hunt menu.";

    private final SafariManager safariManager;
    private final SafariHuntManager safariHuntManager;
    private final HudConfig hudConfig;

    public SafariHudRenderer(SafariManager safariManager, SafariHuntManager safariHuntManager, HudConfig hudConfig) {
        this.safariManager = safariManager;
        this.safariHuntManager = safariHuntManager;
        this.hudConfig = hudConfig;
    }

    @Override
    public String getPanelId() {
        return "safari";
    }

    private boolean[] computeVisibility() {
        boolean inSafariZone = safariManager.isInSafariZone();
        boolean showOutsideZone = hudConfig.isSafariTimerAlways();
        boolean hasActiveTimer = safariManager.isInSafari();
        boolean showTimer = hasActiveTimer && (showOutsideZone || inSafariZone);
        boolean showHunts = safariHuntManager.hasActiveHunts() && (showOutsideZone || inSafariZone);
        return new boolean[]{showTimer, showHunts};
    }

    @Override
    public boolean shouldRender() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) {
            return false;
        }

        if (!hudConfig.isSafariMenuEnabled()) {
            return false;
        }

        boolean[] vis = computeVisibility();
        return vis[0] || vis[1];
    }

    @Override
    public boolean hasVisibleContent() {
        return shouldRender();
    }

    @Override
    public int getContentWidth(Font font) {
        boolean[] vis = computeVisibility();
        boolean showTimer = vis[0];
        boolean compact = hudConfig.isCompact();

        if (compact) {
            return calculateCompactPanelWidth(font, showTimer);
        } else {
            return calculatePanelWidth(font, showTimer);
        }
    }

    @Override
    public int getContentHeight(Font font) {
        boolean[] vis = computeVisibility();
        boolean showTimer = vis[0];
        boolean showHunts = vis[1];
        boolean compact = hudConfig.isCompact();

        int panelWidth = compact
                ? calculateCompactPanelWidth(font, showTimer)
                : calculatePanelWidth(font, showTimer);

        if (compact) {
            return calculateCompactPanelHeight(font, panelWidth, showTimer, showHunts);
        } else {
            return calculatePanelHeight(font, panelWidth, showTimer, showHunts);
        }
    }

    @Override
    public int getContentHeight(Font font, int panelWidth) {
        boolean[] vis = computeVisibility();
        boolean showTimer = vis[0];
        boolean showHunts = vis[1];
        boolean compact = hudConfig.isCompact();

        if (compact) {
            return calculateCompactPanelHeight(font, panelWidth, showTimer, showHunts);
        } else {
            return calculatePanelHeight(font, panelWidth, showTimer, showHunts);
        }
    }

    @Override
    public void renderContent(GuiGraphics graphics, Font font, int panelWidth) {
        boolean[] vis = computeVisibility();
        boolean showTimer = vis[0];
        boolean showHunts = vis[1];
        boolean compact = hudConfig.isCompact();

        if (compact) {
            renderCompact(graphics, font, panelWidth, showTimer, showHunts);
        } else {
            renderFull(graphics, font, panelWidth, showTimer, showHunts);
        }
    }

    @Override
    public void onHudRender(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!shouldRender()) {
            return;
        }

        if (hudConfig.isInGroup("safari")) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        Font font = client.font;
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        float scale = hudConfig.getHudScale();

        double guiScale = client.getWindow().getGuiScale();
        float minScale = (float) (6.0 / (8.0 * guiScale));
        float maxScale = (float) (32.0 / (8.0 * guiScale));
        scale = Math.max(minScale, Math.min(maxScale, scale));

        boolean transparent = hudConfig.getHudStyle() == HudConfig.HudStyle.TRANSPARENT;

        boolean[] vis = computeVisibility();
        boolean showTimer = vis[0];
        boolean showHunts = vis[1];
        boolean compact = hudConfig.isCompact();

        int widthOvr = hudConfig.getHudWidthOverride();
        int panelWidth;
        if (widthOvr > 0) {
            panelWidth = widthOvr;
        } else {
            panelWidth = compact
                    ? calculateCompactPanelWidth(font, showTimer)
                    : calculatePanelWidth(font, showTimer);
        }
        int panelHeight = compact
                ? calculateCompactPanelHeight(font, panelWidth, showTimer, showHunts)
                : calculatePanelHeight(font, panelWidth, showTimer, showHunts);

        int scaledWidth = Math.round(panelWidth * scale);
        int scaledHeight = Math.round(panelHeight * scale);

        int panelX = hudConfig.getPanelX(screenWidth, scaledWidth);
        int panelY = hudConfig.getPanelY(screenHeight, scaledHeight);

        graphics.pose().pushPose();
        graphics.pose().translate(panelX, panelY, 0);
        graphics.pose().scale(scale, scale, 1.0f);

        if (!transparent) {
            graphics.fill(0, 0, panelWidth, panelHeight, COLOR_BG);
        }

        renderContent(graphics, font, panelWidth);

        graphics.pose().popPose();
    }

    private void renderCompact(GuiGraphics graphics, Font font, int panelWidth,
                                boolean showTimer, boolean showHunts) {
        int currentY = PADDING;

        if (showTimer) {
            currentY = renderCompactTimerLine(graphics, font, currentY, panelWidth);
        }

        if (showHunts) {
            renderCompactHuntSection(graphics, font, currentY, panelWidth);
        } else if (showTimer) {
            graphics.drawString(font, "No hunts loaded", PADDING, currentY, COLOR_TEXT_SECONDARY, true);
        }
    }

    private int renderCompactTimerLine(GuiGraphics graphics, Font font, int startY, int panelWidth) {
        int y = startY;
        String prefix = safariManager.isInSafariZone() ? "Safari:" : "Safari (away):";
        String timerText = safariManager.getRemainingTimeFormatted();
        float progress = safariManager.getTimerProgress();
        int timerColor = getTimerColor(progress);
        return HudTextUtil.renderStatLine(graphics, font, prefix, timerText,
                COLOR_HEADER, timerColor, y, panelWidth, PADDING, LINE_HEIGHT);
    }

    private void renderCompactHuntSection(GuiGraphics graphics, Font font, int startY, int panelWidth) {
        int y = startY;
        List<SafariHuntData> hunts = safariHuntManager.getActiveHunts();

        for (int i = 0; i < hunts.size(); i++) {
            SafariHuntData hunt = hunts.get(i);

            int nameColor;
            if (hunt.isComplete()) {
                nameColor = COLOR_PROGRESS_COMPLETE;
            } else if (hunt.getCategory() != SafariHuntData.HuntCategory.UNKNOWN) {
                nameColor = HuntEntityTracker.getSlotColor(i) | 0xFF000000;
            } else {
                nameColor = COLOR_TEXT_PRIMARY;
            }

            String nameAndCount = hunt.getDisplayName() + " [" + hunt.getCaught() + "/" + hunt.getTotal() + "]";
            String resetText = hunt.getResetCountdownFormatted();

            if (!resetText.isEmpty()) {
                String timerSuffix = " - " + resetText;
                int resetColor = hunt.isResetExpired() ? COLOR_TIMER_DANGER : COLOR_RESET_TIMER;
                y = HudTextUtil.renderStatLine(graphics, font, nameAndCount, timerSuffix,
                        nameColor, resetColor, y, panelWidth, PADDING, LINE_HEIGHT);
            } else {
                graphics.drawString(font, nameAndCount, PADDING, y, nameColor, true);
                y += LINE_HEIGHT;
            }
        }
    }

    private int calculateCompactPanelWidth(Font font, boolean showTimer) {
        int maxWidth = PANEL_MIN_WIDTH;

        if (showTimer) {
            String prefix = safariManager.isInSafariZone() ? "Safari: " : "Safari (away): ";
            maxWidth = Math.max(maxWidth,
                    font.width(prefix) + font.width(safariManager.getRemainingTimeFormatted()) + PADDING * 2);
        }

        List<SafariHuntData> hunts = safariHuntManager.getActiveHunts();
        for (SafariHuntData hunt : hunts) {
            int lineWidth = font.width(hunt.getDisplayName()) + 4
                    + font.width("[" + hunt.getCaught() + "/" + hunt.getTotal() + "]");
            String resetText = hunt.getResetCountdownFormatted();
            if (!resetText.isEmpty()) {
                lineWidth += font.width(" - " + resetText);
            }
            maxWidth = Math.max(maxWidth, lineWidth + PADDING * 2);
        }

        if (hunts.isEmpty() && showTimer) {
            maxWidth = Math.max(maxWidth, font.width("No hunts loaded") + PADDING * 2);
        }

        return maxWidth + PADDING * 2;
    }

    private int calculateCompactPanelHeight(Font font, int panelWidth, boolean showTimer, boolean showHunts) {
        int height = PADDING;
        if (showTimer) {
            String prefix = safariManager.isInSafariZone() ? "Safari:" : "Safari (away):";
            String timerText = safariManager.getRemainingTimeFormatted();
            height += HudTextUtil.statLineHeight(font, prefix, timerText, panelWidth, PADDING, LINE_HEIGHT);
        }
        if (showHunts) {
            List<SafariHuntData> hunts = safariHuntManager.getActiveHunts();
            for (SafariHuntData hunt : hunts) {
                String nameAndCount = hunt.getDisplayName() + " [" + hunt.getCaught() + "/" + hunt.getTotal() + "]";
                String resetText = hunt.getResetCountdownFormatted();
                if (!resetText.isEmpty()) {
                    height += HudTextUtil.statLineHeight(font, nameAndCount, " - " + resetText, panelWidth, PADDING, LINE_HEIGHT);
                } else {
                    height += LINE_HEIGHT;
                }
            }
        } else if (showTimer) {
            height += LINE_HEIGHT;
        }
        height += PADDING;
        return height;
    }

    private void renderFull(GuiGraphics graphics, Font font, int panelWidth,
                             boolean showTimer, boolean showHunts) {
        int currentY = PADDING;

        if (showTimer) {
            currentY = renderTimerSection(graphics, font, currentY, panelWidth);

            if (showHunts) {
                currentY += SECTION_SPACING;
                graphics.fill(PADDING, currentY,
                        panelWidth - PADDING, currentY + 1, 0xFF555555);
                currentY += SECTION_SPACING;
            }
        }

        if (showHunts) {
            renderHuntSection(graphics, font, currentY, panelWidth);

        } else if (showTimer) {
            renderNoQuestsMessage(graphics, font, currentY, panelWidth);

        }
    }

    private int renderTimerSection(GuiGraphics graphics, Font font, int startY, int panelWidth) {
        int y = startY;
        String header = "SAA Safari Helper";

        if (!safariManager.isInSafariZone()) {
            header = "SAA Safari Helper (away)";
        }

        y = HudTextUtil.renderWrappedCentered(graphics, font, header, panelWidth, y, COLOR_HEADER, LINE_HEIGHT);

        String timerText = safariManager.getRemainingTimeFormatted();
        float progress = safariManager.getTimerProgress();
        int timerColor = getTimerColor(progress);
        float remainingPercent = 1.0f - progress;

        int barX = PADDING;
        int barWidth = panelWidth - (PADDING * 2);

        graphics.fill(barX, y, barX + barWidth, y + TIMER_BAR_HEIGHT, COLOR_PROGRESS_BG);
        int filledWidth = (int) (barWidth * remainingPercent);

        if (filledWidth > 0) {
            graphics.fill(barX, y, barX + filledWidth, y + TIMER_BAR_HEIGHT, timerColor);
        }

        int textWidth = font.width(timerText);
        int textX = barX + (barWidth - textWidth) / 2;
        int textY = y + (TIMER_BAR_HEIGHT - font.lineHeight) / 2 + 1;
        graphics.drawString(font, timerText, textX, textY, COLOR_TEXT_PRIMARY, true);

        y += TIMER_BAR_HEIGHT;

        return y;
    }

    private void renderNoQuestsMessage(GuiGraphics graphics, Font font, int startY, int panelWidth) {
        int y = startY + SECTION_SPACING;
        int maxTextWidth = panelWidth - PADDING * 2;
        List<String> lines = wrapText(font, NO_QUESTS_MESSAGE, maxTextWidth);

        for (String line : lines) {
            int lineWidth = font.width(line);
            graphics.drawString(font, line, (panelWidth - lineWidth) / 2, y, COLOR_TEXT_SECONDARY, true);
            y += LINE_HEIGHT;
        }
    }

    private void renderHuntSection(GuiGraphics graphics, Font font, int startY, int panelWidth) {
        int y = startY;

        graphics.drawString(font, Component.translatable("text.saa.active_hunts"), PADDING, y, COLOR_HEADER, true);
        y += LINE_HEIGHT + 2;

        List<SafariHuntData> hunts = safariHuntManager.getActiveHunts();
        boolean showHighlightIndicator = hudConfig.isSafariQuestMonGlow();

        for (int huntIndex = 0; huntIndex < hunts.size(); huntIndex++) {
            SafariHuntData hunt = hunts.get(huntIndex);
            int nameX = PADDING;

            if (showHighlightIndicator && !hunt.isComplete()
                    && hunt.getCategory() != SafariHuntData.HuntCategory.UNKNOWN) {
                int slotColor = HuntEntityTracker.getSlotColor(huntIndex) | 0xFF000000;
                int iconSize = 5;
                int iconY = y + (font.lineHeight - iconSize) / 2;
                graphics.fill(nameX, iconY, nameX + iconSize, iconY + iconSize, slotColor);
                nameX += 8;
            }

            if (hunt.getStarRating() > 0) {
                String stars = "*".repeat(hunt.getStarRating());
                graphics.drawString(font, stars, nameX, y, COLOR_STAR, true);
                nameX += font.width(stars) + 2;
            }

            int nameColor = hunt.isComplete() ? COLOR_PROGRESS_COMPLETE : COLOR_TEXT_PRIMARY;
            String resetText = hunt.getResetCountdownFormatted();

            if (!resetText.isEmpty()) {
                int nameW = font.width(hunt.getDisplayName());
                int resetW = font.width(resetText);
                int available = panelWidth - nameX - PADDING;
                int resetColor = hunt.isResetExpired() ? COLOR_TIMER_DANGER : COLOR_RESET_TIMER;

                if (nameW + 8 + resetW <= available) {
                    graphics.drawString(font, hunt.getDisplayName(), nameX, y, nameColor, true);
                    graphics.drawString(font, resetText, panelWidth - PADDING - resetW, y, resetColor, true);
                    y += LINE_HEIGHT;
                } else {
                    graphics.drawString(font, hunt.getDisplayName(), nameX, y, nameColor, true);
                    y += LINE_HEIGHT;
                    graphics.drawString(font, resetText, panelWidth - PADDING - resetW, y, resetColor, true);
                    y += LINE_HEIGHT;
                }
            } else {
                graphics.drawString(font, hunt.getDisplayName(), nameX, y, nameColor, true);
                y += LINE_HEIGHT;
            }

            int textX = PADDING + 4;
            String caughtStr = String.valueOf(hunt.getCaught());
            String slash = "/";
            String totalStr = String.valueOf(hunt.getTotal());

            int numberColor = hunt.isComplete() ? COLOR_PROGRESS_COMPLETE : COLOR_QUEST_NUMBER;
            int slashColor = hunt.isComplete() ? COLOR_PROGRESS_COMPLETE : COLOR_TEXT_SECONDARY;

            int px = textX;
            graphics.drawString(font, caughtStr, px, y, numberColor, true);
            px += font.width(caughtStr);
            graphics.drawString(font, slash, px, y, slashColor, true);
            px += font.width(slash);
            graphics.drawString(font, totalStr, px, y, numberColor, true);
            px += font.width(totalStr);

            int barX = px + 6;
            int barWidth = panelWidth - barX - PADDING;
            if (barWidth > 10) {
                float huntProgress = hunt.getTotal() > 0
                        ? (float) hunt.getCaught() / hunt.getTotal()
                        : 0f;
                int barY = y + 3;
                graphics.fill(barX, barY, barX + barWidth, barY + PROGRESS_BAR_HEIGHT, COLOR_PROGRESS_BG);
                int filled = (int) (barWidth * huntProgress);

                if (filled > 0) {
                    int fillColor = hunt.isComplete() ? COLOR_PROGRESS_COMPLETE : COLOR_PROGRESS_FILL;
                    graphics.fill(barX, barY, barX + filled, barY + PROGRESS_BAR_HEIGHT, fillColor);
                }
            }

            y += LINE_HEIGHT;
        }
    }

    private int calculatePanelWidth(Font font, boolean showTimer) {
        int maxWidth = PANEL_MIN_WIDTH;

        for (SafariHuntData hunt : safariHuntManager.getActiveHunts()) {
            int nameWidth = font.width(hunt.getDisplayName()) + (hunt.getStarRating() * 5) + PADDING * 2;
            String resetText = hunt.getResetCountdownFormatted();
            if (!resetText.isEmpty()) {
                nameWidth += font.width(resetText) + 8;
            }
            maxWidth = Math.max(maxWidth, nameWidth);
        }

        if (showTimer) {
            String header = safariManager.isInSafariZone() ? "SAA Safari Helper" : "SAA Safari Helper (away)";
            maxWidth = Math.max(maxWidth, font.width(header) + PADDING * 2);
        }

        return maxWidth + PADDING * 2;
    }

    private int calculatePanelHeight(Font font, int panelWidth, boolean showTimer, boolean showHunts) {
        int height = PADDING;

        if (showTimer) {
            String header = safariManager.isInSafariZone() ? "SAA Safari Helper" : "SAA Safari Helper (away)";
            height += HudTextUtil.wrappedCenteredHeight(font, header, panelWidth, LINE_HEIGHT);
            height += TIMER_BAR_HEIGHT;
        }

        if (showTimer && showHunts) {
            height += SECTION_SPACING * 2 + 1;
        }

        if (showHunts) {
            height += LINE_HEIGHT + 2;
            List<SafariHuntData> hunts = safariHuntManager.getActiveHunts();
            for (SafariHuntData hunt : hunts) {
                String resetText = hunt.getResetCountdownFormatted();
                if (!resetText.isEmpty()) {
                    int nameW = font.width(hunt.getDisplayName());
                    int resetW = font.width(resetText);
                    int available = panelWidth - PADDING * 2;
                    if (nameW + 8 + resetW <= available) {
                        height += LINE_HEIGHT;
                    } else {
                        height += LINE_HEIGHT * 2;
                    }
                } else {
                    height += LINE_HEIGHT;
                }
                height += LINE_HEIGHT;
            }

        } else if (showTimer) {
            int maxTextWidth = panelWidth - PADDING * 2;
            int lineCount = wrapText(font, NO_QUESTS_MESSAGE, maxTextWidth).size();
            height += SECTION_SPACING + LINE_HEIGHT * lineCount;
        }

        height += PADDING;
        return height;
    }

    private int getTimerColor(float progress) {
        if (progress >= 0.9f) return COLOR_TIMER_DANGER;
        if (progress >= 0.75f) return COLOR_TIMER_WARN;
        return COLOR_TIMER_SAFE;
    }

    private static List<String> wrapText(Font font, String text, int maxWidth) {
        return HudTextUtil.wrapText(font, text, maxWidth);
    }
}
