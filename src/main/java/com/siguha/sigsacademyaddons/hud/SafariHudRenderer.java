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

import java.util.ArrayList;
import java.util.List;

// renders safari hud overlay with countdown timer and hunt tracker
public class SafariHudRenderer {

    // layout constants
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

    public void onHudRender(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) {
            return;
        }

        boolean inSafariZone = safariManager.isInSafariZone();
        boolean showOutsideZone = hudConfig.isSafariTimerAlways();

        boolean hasActiveTimer = safariManager.isInSafari();
        boolean showTimer = hasActiveTimer && (showOutsideZone || inSafariZone);
        boolean showHunts = safariHuntManager.hasActiveHunts() && (showOutsideZone || inSafariZone);

        if (!showTimer && !showHunts) {
            return;
        }

        Font font = client.font;
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        float scale = hudConfig.getHudScale();
        boolean transparent = hudConfig.getHudStyle() == HudConfig.HudStyle.TRANSPARENT;

        int panelWidth = calculatePanelWidth(font, showTimer, showHunts);
        int panelHeight = calculatePanelHeight(font, panelWidth, showTimer, showHunts);

        int scaledWidth = Math.round(panelWidth * scale);
        int scaledHeight = Math.round(panelHeight * scale);

        int panelX = hudConfig.getPanelX(screenWidth, scaledWidth);
        int panelY = hudConfig.getPanelY(screenHeight, scaledHeight);

        graphics.pose().pushPose();
        graphics.pose().translate(panelX, panelY, 0);
        graphics.pose().scale(scale, scale, 1.0f);

        // background for solid 
        if (!transparent) {
            graphics.fill(0, 0, panelWidth, panelHeight, COLOR_BG);
        }

        int currentY = PADDING;

        // timer section
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

        graphics.pose().popPose();
    }

    private int renderTimerSection(GuiGraphics graphics, Font font, int startY, int panelWidth) {
        int y = startY;
        String header = "SAA Safari Helper";
        if (!safariManager.isInSafariZone()) {
            header = "SAA Safari Helper (away)";
        }
        graphics.drawString(font, header, PADDING, y, COLOR_HEADER, true);
        y += LINE_HEIGHT;

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

        graphics.drawString(font, "Active Hunts", PADDING, y, COLOR_HEADER, true);
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
            graphics.drawString(font, hunt.getDisplayName(), nameX, y, nameColor, true);

            String resetText = hunt.getResetCountdownFormatted();
            if (!resetText.isEmpty()) {
                int resetColor = hunt.isResetExpired() ? COLOR_TIMER_DANGER : COLOR_RESET_TIMER;
                int resetWidth = font.width(resetText);
                graphics.drawString(font, resetText, panelWidth - PADDING - resetWidth, y,
                        resetColor, true);
            }

            y += LINE_HEIGHT;

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

            // mini progress bar
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

    private int calculatePanelWidth(Font font, boolean showTimer, boolean showHunts) {
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
            height += LINE_HEIGHT;
            height += TIMER_BAR_HEIGHT;
        }

        if (showTimer && showHunts) {
            height += SECTION_SPACING * 2 + 1;
        }

        if (showHunts) {
            height += LINE_HEIGHT + 2;
            height += safariHuntManager.getActiveHunts().size() * (LINE_HEIGHT * 2);
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
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder();

        for (String word : words) {
            if (current.isEmpty()) {
                current.append(word);
            } else {
                String test = current + " " + word;
                if (font.width(test) > maxWidth) {
                    lines.add(current.toString());
                    current = new StringBuilder(word);
                } else {
                    current.append(" ").append(word);
                }
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }
}
