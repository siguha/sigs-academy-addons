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

import java.util.List;

// renders safari hud overlay with countdown timer and hunt tracker
public class SafariHudRenderer {

    // layout constants (unscaled, scale applied via matrix transform)
    private static final int PADDING = 4;
    private static final int LINE_HEIGHT = 11;
    private static final int SECTION_SPACING = 6;
    private static final int PROGRESS_BAR_HEIGHT = 3;
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

        // hud visibility based on config abd safari zone detection
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

        int panelWidth = calculatePanelWidth(font, showTimer);
        int panelHeight = calculatePanelHeight(showTimer, showHunts);

        int scaledWidth = Math.round(panelWidth * scale);
        int scaledHeight = Math.round(panelHeight * scale);

        int panelX = hudConfig.getPanelX(screenWidth, scaledWidth);
        int panelY = hudConfig.getPanelY(screenHeight, scaledHeight);

        graphics.pose().pushPose();
        graphics.pose().translate(panelX, panelY, 0);
        graphics.pose().scale(scale, scale, 1.0f);

        graphics.fill(0, 0, panelWidth, panelHeight, COLOR_BG);

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

        // hunt tracker section
        if (showHunts) {
            renderHuntSection(graphics, font, currentY, panelWidth);
        }

        graphics.pose().popPose();
    }

    private int renderTimerSection(GuiGraphics graphics, Font font, int startY, int panelWidth) {
        int y = startY;
        String header = "Safari Zone";
        if (!safariManager.isInSafariZone()) {
            header = "Safari Zone (away)";
        }
        graphics.drawString(font, header, PADDING, y, COLOR_HEADER, true);
        y += LINE_HEIGHT;

        String timerText = safariManager.getRemainingTimeFormatted();
        float progress = safariManager.getTimerProgress();
        int timerColor = getTimerColor(progress);

        graphics.drawString(font, timerText, PADDING, y, timerColor, true);
        y += LINE_HEIGHT;

        // progress bar
        int barX = PADDING;
        int barWidth = panelWidth - (PADDING * 2);
        float remainingPercent = 1.0f - progress;

        graphics.fill(barX, y, barX + barWidth, y + PROGRESS_BAR_HEIGHT, COLOR_PROGRESS_BG);
        int filledWidth = (int) (barWidth * remainingPercent);
        if (filledWidth > 0) {
            graphics.fill(barX, y, barX + filledWidth, y + PROGRESS_BAR_HEIGHT, timerColor);
        }
        y += PROGRESS_BAR_HEIGHT;

        return y;
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
                graphics.fill(nameX, y + 2, nameX + 5, y + 7, slotColor);
                nameX += 8;
            }

            if (hunt.getStarRating() > 0) {
                String stars = "*".repeat(hunt.getStarRating());
                graphics.drawString(font, stars, nameX, y, COLOR_STAR, true);
                nameX += font.width(stars) + 2;
            }

            int nameColor = hunt.isComplete() ? COLOR_PROGRESS_COMPLETE : COLOR_TEXT_PRIMARY;
            graphics.drawString(font, hunt.getDisplayName(), nameX, y, nameColor, true);

            // reset countdown (right-aligned)
            String resetText = hunt.getResetCountdownFormatted();
            if (!resetText.isEmpty()) {
                int resetColor = hunt.isResetExpired() ? COLOR_TIMER_DANGER : COLOR_RESET_TIMER;
                int resetWidth = font.width(resetText);
                graphics.drawString(font, resetText, panelWidth - PADDING - resetWidth, y,
                        resetColor, true);
            }

            y += LINE_HEIGHT;

            // progress text + mini bar
            String progressText = hunt.getProgressString();
            int progressColor = hunt.isComplete() ? COLOR_PROGRESS_COMPLETE : COLOR_TEXT_SECONDARY;
            int textX = PADDING + 4;
            graphics.drawString(font, progressText, textX, y, progressColor, true);

            // mini progress bar
            int barX = textX + font.width(progressText) + 6;
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
            String header = safariManager.isInSafariZone() ? "Safari Zone" : "Safari Zone (away)";
            maxWidth = Math.max(maxWidth, font.width(header) + PADDING * 2);
        }

        return maxWidth + PADDING * 2;
    }

    private int calculatePanelHeight(boolean showTimer, boolean showHunts) {
        int height = PADDING;

        if (showTimer) {
            height += LINE_HEIGHT;
            height += LINE_HEIGHT;
            height += PROGRESS_BAR_HEIGHT;
        }

        if (showTimer && showHunts) {
            height += SECTION_SPACING * 2 + 1;
        }

        if (showHunts) {
            height += LINE_HEIGHT + 2;
            height += safariHuntManager.getActiveHunts().size() * (LINE_HEIGHT * 2);
        }

        height += PADDING;
        return height;
    }

    private int getTimerColor(float progress) {
        if (progress >= 0.9f) return COLOR_TIMER_DANGER;
        if (progress >= 0.75f) return COLOR_TIMER_WARN;
        return COLOR_TIMER_SAFE;
    }
}
