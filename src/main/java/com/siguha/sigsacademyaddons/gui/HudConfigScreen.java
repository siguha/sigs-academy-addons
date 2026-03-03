package com.siguha.sigsacademyaddons.gui;

import com.siguha.sigsacademyaddons.config.HudConfig;
import com.siguha.sigsacademyaddons.feature.daycare.DaycareManager;
import com.siguha.sigsacademyaddons.feature.daycare.DaycareState;
import com.siguha.sigsacademyaddons.feature.safari.SafariHuntData;
import com.siguha.sigsacademyaddons.feature.safari.SafariHuntManager;
import com.siguha.sigsacademyaddons.feature.safari.SafariManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class HudConfigScreen extends Screen {

    private static final int PADDING = 4;
    private static final int LINE_HEIGHT = 11;
    private static final int SECTION_SPACING = 6;
    private static final int TIMER_BAR_HEIGHT = 11;
    private static final int PANEL_MIN_WIDTH = 140;

    private static final int CORNER_HANDLE_SIZE = 6;
    private static final int CORNER_GRAB_RADIUS = 8;

    private static final float MIN_SCALE = 0.5f;
    private static final float MAX_SCALE = 2.0f;

    private static final int COLOR_BG = 0xAA000000;
    private static final int COLOR_HEADER = 0xFFFFAA00;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_HINT = 0xFFAAAAAA;
    private static final int COLOR_OUTLINE = 0xFFFFAA00;
    private static final int COLOR_OUTLINE_DAYCARE = 0xFF55FFFF;
    private static final int COLOR_OUTLINE_DRAG = 0xFF55FF55;
    private static final int COLOR_CORNER_HANDLE = 0xFFFFFFFF;
    private static final int COLOR_CORNER_HOVER = 0xFF55FF55;
    private static final int COLOR_QUEST_NUMBER = 0xFFFFFF55;
    private static final int COLOR_PROGRESS_COMPLETE = 0xFFFFAA00;
    private static final int COLOR_SECTION_HEADER = 0xFF55FFFF;
    private static final int COLOR_TEXT_INACTIVE = 0xFF777777;
    private static final int COLOR_EGG_READY = 0xFFFFD700;
    private static final int COLOR_TIMER_BAR_BG = 0xFF333333;

    private static final String NO_QUESTS_MESSAGE = "Please visit the Safari Hunt NPC and load your hunt menu.";

    private final HudConfig hudConfig;
    private final SafariManager safariManager;
    private final SafariHuntManager safariHuntManager;
    private final DaycareManager daycareManager;

    static class PanelState {
        String name;
        int unscaledWidth, unscaledHeight;
        float currentScale;
        int scaledWidth, scaledHeight;
        int panelX, panelY;

        PanelState(String name) {
            this.name = name;
        }

        void updateScaledDimensions() {
            scaledWidth = Math.round(unscaledWidth * currentScale);
            scaledHeight = Math.round(unscaledHeight * currentScale);
        }
    }

    private PanelState safariPanel;
    private PanelState daycarePanel;

    private enum DragMode { NONE, MOVE, RESIZE }
    private DragMode dragMode = DragMode.NONE;
    private PanelState activePanel = null;

    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    private int resizeAnchorX;
    private int resizeAnchorY;
    private boolean resizeFromLeft;
    private boolean resizeFromTop;

    public HudConfigScreen(HudConfig hudConfig, SafariManager safariManager,
                            SafariHuntManager safariHuntManager, DaycareManager daycareManager) {
        super(Component.literal("HUD Position"));
        this.hudConfig = hudConfig;
        this.safariManager = safariManager;
        this.safariHuntManager = safariHuntManager;
        this.daycareManager = daycareManager;
    }

    @Override
    protected void init() {
        super.init();

        // safari
        safariPanel = new PanelState("Safari");
        boolean showTimer = safariManager.isInSafari();
        boolean showHunts = safariHuntManager.hasActiveHunts();
        safariPanel.unscaledWidth = calculateSafariWidth(showTimer, showHunts);
        safariPanel.unscaledHeight = calculateSafariHeight(showTimer, showHunts);
        safariPanel.unscaledWidth = Math.max(safariPanel.unscaledWidth, 100);
        safariPanel.unscaledHeight = Math.max(safariPanel.unscaledHeight, 40);
        safariPanel.currentScale = hudConfig.getHudScale();
        safariPanel.updateScaledDimensions();
        safariPanel.panelX = hudConfig.getPanelX(this.width, safariPanel.scaledWidth);
        safariPanel.panelY = hudConfig.getPanelY(this.height, safariPanel.scaledHeight);

        // daycare
        daycarePanel = new PanelState("Daycare");
        daycarePanel.unscaledWidth = calculateDaycareWidth();
        daycarePanel.unscaledHeight = calculateDaycareHeight();
        daycarePanel.unscaledWidth = Math.max(daycarePanel.unscaledWidth, 100);
        daycarePanel.unscaledHeight = Math.max(daycarePanel.unscaledHeight, 40);
        daycarePanel.currentScale = hudConfig.getDaycareScale();
        daycarePanel.updateScaledDimensions();
        daycarePanel.panelX = hudConfig.getDaycarePanelX(this.width, daycarePanel.scaledWidth);
        daycarePanel.panelY = hudConfig.getDaycarePanelY(this.height, daycarePanel.scaledHeight);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.fill(0, 0, this.width, this.height, 0x88000000);
        String title = "Drag to reposition | Drag corners to resize";
        int titleWidth = this.font.width(title);
        graphics.drawString(this.font, title, (this.width - titleWidth) / 2, 10, COLOR_HEADER, true);

        String hint = "Press Escape to save and close";
        int hintWidth = this.font.width(hint);
        graphics.drawString(this.font, hint, (this.width - hintWidth) / 2, 22, COLOR_HINT, true);

        String scaleText = String.format("Safari: %.0f%% | Daycare: %.0f%%",
                safariPanel.currentScale * 100, daycarePanel.currentScale * 100);
        int scaleWidth = this.font.width(scaleText);
        graphics.drawString(this.font, scaleText, (this.width - scaleWidth) / 2, 34, COLOR_TEXT, true);

        // render safari panel
        renderPanel(graphics, safariPanel, mouseX, mouseY, true);

        // render daycare panel
        renderPanel(graphics, daycarePanel, mouseX, mouseY, false);
    }

    private void renderPanel(GuiGraphics graphics, PanelState panel, int mouseX, int mouseY, boolean isSafari) {
        graphics.pose().pushPose();
        graphics.pose().translate(panel.panelX, panel.panelY, 0);
        graphics.pose().scale(panel.currentScale, panel.currentScale, 1.0f);

        if (isSafari) {
            renderSafariPreview(graphics, panel);
        } else {
            renderDaycarePreview(graphics, panel);
        }

        graphics.pose().popPose();

        // outline
        int outlineColor;
        if (activePanel == panel && dragMode == DragMode.MOVE) {
            outlineColor = COLOR_OUTLINE_DRAG;
        } else if (activePanel == panel && dragMode == DragMode.RESIZE) {
            outlineColor = COLOR_CORNER_HOVER;
        } else {
            outlineColor = isSafari ? COLOR_OUTLINE : COLOR_OUTLINE_DAYCARE;
        }
        drawOutline(graphics, panel.panelX, panel.panelY, panel.scaledWidth, panel.scaledHeight, outlineColor);

        // label
        String label = panel.name;
        int labelWidth = this.font.width(label);
        int labelX = panel.panelX + (panel.scaledWidth - labelWidth) / 2;
        int labelY = panel.panelY - 10;
        graphics.drawString(this.font, label, labelX, labelY,
                isSafari ? COLOR_OUTLINE : COLOR_OUTLINE_DAYCARE, true);

        // corner handles
        drawCornerHandles(graphics, panel, mouseX, mouseY);
    }

    private void drawOutline(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x - 1, y - 1, x + w + 1, y, color);
        graphics.fill(x - 1, y + h, x + w + 1, y + h + 1, color);
        graphics.fill(x - 1, y - 1, x, y + h + 1, color);
        graphics.fill(x + w, y - 1, x + w + 1, y + h + 1, color);
    }

    private void drawCornerHandles(GuiGraphics graphics, PanelState panel, int mouseX, int mouseY) {
        int[][] corners = {
                {panel.panelX, panel.panelY},
                {panel.panelX + panel.scaledWidth, panel.panelY},
                {panel.panelX, panel.panelY + panel.scaledHeight},
                {panel.panelX + panel.scaledWidth, panel.panelY + panel.scaledHeight}
        };

        for (int[] corner : corners) {
            boolean hovered = isNearPoint(mouseX, mouseY, corner[0], corner[1], CORNER_GRAB_RADIUS);
            int color = (hovered || (activePanel == panel && dragMode == DragMode.RESIZE))
                    ? COLOR_CORNER_HOVER : COLOR_CORNER_HANDLE;
            int hs = CORNER_HANDLE_SIZE / 2;
            graphics.fill(corner[0] - hs, corner[1] - hs,
                    corner[0] + hs, corner[1] + hs, color);
        }
    }

    private void renderSafariPreview(GuiGraphics graphics, PanelState panel) {
        boolean transparent = hudConfig.getHudStyle() == HudConfig.HudStyle.TRANSPARENT;

        if (!transparent) {
            graphics.fill(0, 0, panel.unscaledWidth, panel.unscaledHeight, COLOR_BG);
        }

        int y = PADDING;
        boolean showTimer = safariManager.isInSafari();
        boolean showHunts = safariHuntManager.hasActiveHunts();

        if (!showTimer && !showHunts) {
            graphics.drawString(this.font, "SAA Safari Helper", PADDING, y, COLOR_HEADER, true);
            y += LINE_HEIGHT;

            int barWidth = panel.unscaledWidth - (PADDING * 2);
            graphics.fill(PADDING, y, PADDING + barWidth, y + TIMER_BAR_HEIGHT, COLOR_TIMER_BAR_BG);
            int filledWidth = (int) (barWidth * 0.82f);
            graphics.fill(PADDING, y, PADDING + filledWidth, y + TIMER_BAR_HEIGHT, 0xFF55FF55);
            String placeholderTimer = "24:31";
            int timerTextWidth = this.font.width(placeholderTimer);
            int timerTextX = PADDING + (barWidth - timerTextWidth) / 2;
            int timerTextY = y + (TIMER_BAR_HEIGHT - this.font.lineHeight) / 2 + 1;
            graphics.drawString(this.font, placeholderTimer, timerTextX, timerTextY, COLOR_TEXT, true);
            y += TIMER_BAR_HEIGHT;

            y += SECTION_SPACING;
            int maxTextWidth = panel.unscaledWidth - PADDING * 2;
            for (String line : wrapText(this.font, NO_QUESTS_MESSAGE, maxTextWidth)) {
                int lineWidth = this.font.width(line);
                graphics.drawString(this.font, line, (panel.unscaledWidth - lineWidth) / 2, y, COLOR_HINT, true);
                y += LINE_HEIGHT;
            }
            return;
        }

        if (showTimer) {
            String header = safariManager.isInSafariZone() ? "SAA Safari Helper" : "SAA Safari Helper (away)";
            graphics.drawString(this.font, header, PADDING, y, COLOR_HEADER, true);
            y += LINE_HEIGHT;

            String timerText = safariManager.getRemainingTimeFormatted();
            float remainingPercent = 1.0f - safariManager.getTimerProgress();

            int barWidth = panel.unscaledWidth - (PADDING * 2);
            graphics.fill(PADDING, y, PADDING + barWidth, y + TIMER_BAR_HEIGHT, COLOR_TIMER_BAR_BG);
            int filledWidth = (int) (barWidth * remainingPercent);

            if (filledWidth > 0) {
                graphics.fill(PADDING, y, PADDING + filledWidth, y + TIMER_BAR_HEIGHT, 0xFF55FF55);
            }

            int timerTextWidth = this.font.width(timerText);
            int timerTextX = PADDING + (barWidth - timerTextWidth) / 2;
            int timerTextY = y + (TIMER_BAR_HEIGHT - this.font.lineHeight) / 2 + 1;
            graphics.drawString(this.font, timerText, timerTextX, timerTextY, COLOR_TEXT, true);
            y += TIMER_BAR_HEIGHT;

            if (showHunts) {
                y += SECTION_SPACING;
                graphics.fill(PADDING, y, PADDING + barWidth, y + 1, 0xFF555555);
                y += SECTION_SPACING;

            } else {
                y += SECTION_SPACING;
                int maxTextWidth = panel.unscaledWidth - PADDING * 2;

                for (String line : wrapText(this.font, NO_QUESTS_MESSAGE, maxTextWidth)) {
                    int lineWidth = this.font.width(line);
                    graphics.drawString(this.font, line, (panel.unscaledWidth - lineWidth) / 2, y, COLOR_HINT, true);
                    y += LINE_HEIGHT;
                }
            }
        }

        if (showHunts) {
            graphics.drawString(this.font, "Active Hunts", PADDING, y, COLOR_HEADER, true);
            y += LINE_HEIGHT + 2;

            List<SafariHuntData> hunts = safariHuntManager.getActiveHunts();
            for (SafariHuntData hunt : hunts) {
                int nameX = PADDING;
                if (hunt.getStarRating() > 0) {
                    String stars = "*".repeat(hunt.getStarRating());
                    graphics.drawString(this.font, stars, nameX, y, 0xFFFFD700, true);
                    nameX += this.font.width(stars) + 2;
                }

                int nameColor = hunt.isComplete() ? COLOR_PROGRESS_COMPLETE : COLOR_TEXT;
                graphics.drawString(this.font, hunt.getDisplayName(), nameX, y, nameColor, true);

                String resetText = hunt.getResetCountdownFormatted();
                if (!resetText.isEmpty()) {
                    int resetColor = hunt.isResetExpired() ? 0xFFFF5555 : 0xFFFF8855;
                    int resetWidth = this.font.width(resetText);
                    graphics.drawString(this.font, resetText, panel.unscaledWidth - PADDING - resetWidth, y,
                            resetColor, true);
                }

                y += LINE_HEIGHT;

                int textX = PADDING + 4;
                String caughtStr = String.valueOf(hunt.getCaught());
                String slash = "/";
                String totalStr = String.valueOf(hunt.getTotal());

                int numberColor = hunt.isComplete() ? COLOR_PROGRESS_COMPLETE : COLOR_QUEST_NUMBER;
                int slashColor = hunt.isComplete() ? COLOR_PROGRESS_COMPLETE : COLOR_HINT;

                int px = textX;
                graphics.drawString(this.font, caughtStr, px, y, numberColor, true);
                px += this.font.width(caughtStr);
                graphics.drawString(this.font, slash, px, y, slashColor, true);
                px += this.font.width(slash);
                graphics.drawString(this.font, totalStr, px, y, numberColor, true);

                y += LINE_HEIGHT;
            }
        }
    }

    private void renderDaycarePreview(GuiGraphics graphics, PanelState panel) {
        boolean transparent = hudConfig.getHudStyle() == HudConfig.HudStyle.TRANSPARENT;

        if (!transparent) {
            graphics.fill(0, 0, panel.unscaledWidth, panel.unscaledHeight, COLOR_BG);
        }

        int y = PADDING;

        String dcHeader = "SAA Daycare Helper";
        int dcHeaderW = this.font.width(dcHeader);
        graphics.drawString(this.font, dcHeader, (panel.unscaledWidth - dcHeaderW) / 2, y, COLOR_HEADER, true);
        y += LINE_HEIGHT;

        List<DaycareState.PenState> displayPens = daycareManager.getDisplayPens();
        List<DaycareState.ClaimedEgg> displayEggs = daycareManager.getDisplayEggs();

        boolean hasLiveData = !displayPens.isEmpty() || !displayEggs.isEmpty();

        if (hasLiveData) {
            if (!displayPens.isEmpty()) {
                y += 2;
                graphics.fill(PADDING, y, panel.unscaledWidth - PADDING, y + 1, 0xFF555555);
                y += SECTION_SPACING;
                graphics.drawString(this.font, "Breeding", PADDING, y, COLOR_SECTION_HEADER, true);
                y += LINE_HEIGHT;

                for (DaycareState.PenState pen : displayPens) {
                    y = renderDaycarePenEntry(graphics, pen, y, panel.unscaledWidth);
                }
            }

            if (!displayEggs.isEmpty()) {
                y += 2;
                graphics.fill(PADDING, y, panel.unscaledWidth - PADDING, y + 1, 0xFF555555);
                y += SECTION_SPACING;
                graphics.drawString(this.font, "Hatching", PADDING, y, COLOR_SECTION_HEADER, true);
                y += LINE_HEIGHT;

                for (DaycareState.ClaimedEgg egg : displayEggs) {
                    y = renderDaycareEggEntry(graphics, egg, y, panel.unscaledWidth);
                }
            }
        } else {
            y += 2;
            graphics.fill(PADDING, y, panel.unscaledWidth - PADDING, y + 1, 0xFF555555);
            y += SECTION_SPACING;
            graphics.drawString(this.font, "Breeding", PADDING, y, COLOR_SECTION_HEADER, true);
            y += LINE_HEIGHT;

            graphics.drawString(this.font, "Pen 1: Eevee + Ditto", PADDING + 2, y, COLOR_TEXT, true);
            String timer1 = "~12:34";
            int tw1 = this.font.width(timer1);
            graphics.drawString(this.font, timer1, panel.unscaledWidth - PADDING - tw1, y, 0xFF55FF55, true);
            y += LINE_HEIGHT;

            int barX = PADDING + 2;
            int barWidth = panel.unscaledWidth - barX - PADDING;
            graphics.fill(barX, y, barX + barWidth, y + 6, COLOR_TIMER_BAR_BG);
            graphics.fill(barX, y, barX + (int)(barWidth * 0.4f), y + 6, 0xFF55FF55);
            y += 8;

            graphics.drawString(this.font, "Pen 2: Inactive", PADDING + 2, y, COLOR_TEXT_INACTIVE, true);
            y += LINE_HEIGHT;

            y += 2;
            graphics.fill(PADDING, y, panel.unscaledWidth - PADDING, y + 1, 0xFF555555);
            y += SECTION_SPACING;
            graphics.drawString(this.font, "Hatching", PADDING, y, COLOR_SECTION_HEADER, true);
            y += LINE_HEIGHT;

            graphics.drawString(this.font, "Eevee", PADDING + 2, y, COLOR_TEXT, true);
            String timer2 = "~7:45";
            int tw2 = this.font.width(timer2);
            graphics.drawString(this.font, timer2, panel.unscaledWidth - PADDING - tw2, y, 0xFF55FF55, true);
            y += LINE_HEIGHT;
            graphics.fill(barX, y, barX + barWidth, y + 6, COLOR_TIMER_BAR_BG);
            graphics.fill(barX, y, barX + (int)(barWidth * 0.25f), y + 6, 0xFF55FF55);
        }
    }

    private int renderDaycarePenEntry(GuiGraphics graphics, DaycareState.PenState pen, int y, int panelWidth) {
        DaycareState.BreedingStage stage = pen.getStage();
        String penLabel = "[PEN " + pen.getPenNumber() + "]";
        String species = pen.getInferredEggSpecies();

        if (species == null || species.isEmpty()) {
            species = pen.getPokemon1() != null ? pen.getPokemon1() : "Unknown";
        }

        switch (stage) {
            case EMPTY, ONE_POKEMON -> {
                graphics.drawString(this.font, penLabel, PADDING + 2, y, 0xFF88DDFF, true);
                int textX = PADDING + 2 + this.font.width(penLabel) + 4;
                graphics.drawString(this.font, "Inactive", textX, y, COLOR_TEXT_INACTIVE, true);
                y += LINE_HEIGHT;
            }

            case BREEDING -> {
                graphics.drawString(this.font, penLabel, PADDING + 2, y, 0xFF88DDFF, true);
                int speciesX = PADDING + 2 + this.font.width(penLabel) + 4;
                graphics.drawString(this.font, species, speciesX, y, COLOR_TEXT, true);
                String timerText = pen.getRemainingFormatted();

                if (!timerText.isEmpty()) {
                    int tw = this.font.width(timerText);
                    graphics.drawString(this.font, timerText, panelWidth - PADDING - tw, y, 0xFF55FF55, true);
                }

                y += LINE_HEIGHT;
                int barX = PADDING + 2;
                int barWidth = panelWidth - barX - PADDING;
                float progress = pen.getProgress();
                graphics.fill(barX, y, barX + barWidth, y + 6, COLOR_TIMER_BAR_BG);
                int filled = (int) (barWidth * progress);

                if (filled > 0) {
                    graphics.fill(barX, y, barX + filled, y + 6, 0xFF55FF55);
                }

                y += 8;
            }

            case EGG_READY -> {
                graphics.drawString(this.font, penLabel, PADDING + 2, y, 0xFF88DDFF, true);
                int speciesX = PADDING + 2 + this.font.width(penLabel) + 4;
                graphics.drawString(this.font, species, speciesX, y, COLOR_EGG_READY, true);
                String readyText = "Egg Ready!";
                int readyWidth = this.font.width(readyText);
                graphics.drawString(this.font, readyText, panelWidth - PADDING - readyWidth, y, COLOR_EGG_READY, true);
                y += LINE_HEIGHT;
            }

            case NEEDS_RESET -> {
                graphics.drawString(this.font, penLabel, PADDING + 2, y, 0xFF88DDFF, true);
                int speciesX = PADDING + 2 + this.font.width(penLabel) + 4;
                graphics.drawString(this.font, species, speciesX, y, COLOR_TEXT, true);
                String resetText = "Parents Need Reset!";
                int resetWidth = this.font.width(resetText);
                graphics.drawString(this.font, resetText, panelWidth - PADDING - resetWidth, y, 0xFFFF8800, true);
                y += LINE_HEIGHT;
                int barX = PADDING + 2;
                int barWidth = panelWidth - barX - PADDING;
                graphics.fill(barX, y, barX + barWidth, y + 6, 0xFFFF8800);
                y += 8;
            }

            case INCOMPATIBLE -> {
                graphics.drawString(this.font, penLabel, PADDING + 2, y, 0xFF88DDFF, true);
                int textX = PADDING + 2 + this.font.width(penLabel) + 4;
                graphics.drawString(this.font, "Incompatible Setup", textX, y, 0xFFFF3333, true);
                y += LINE_HEIGHT;
                int barX = PADDING + 2;
                int barWidth = panelWidth - barX - PADDING;
                graphics.fill(barX, y, barX + barWidth, y + 6, 0xFFFF3333);
                y += 8;
            }
        }
        return y;
    }

    private int renderDaycareEggEntry(GuiGraphics graphics, DaycareState.ClaimedEgg egg, int y, int panelWidth) {
        String label = egg.getDisplayLabel();
        graphics.drawString(this.font, label, PADDING + 2, y, COLOR_TEXT, true);
        String timerText = egg.getRemainingFormatted();
        int tw = this.font.width(timerText);
        graphics.drawString(this.font, timerText, panelWidth - PADDING - tw, y, 0xFF55FF55, true);
        y += LINE_HEIGHT;

        int barX = PADDING + 2;
        int barWidth = panelWidth - barX - PADDING;
        float progress = egg.getProgress();
        graphics.fill(barX, y, barX + barWidth, y + 6, COLOR_TIMER_BAR_BG);
        int filled = (int) (barWidth * progress);
        if (filled > 0) {
            graphics.fill(barX, y, barX + filled, y + 6, 0xFF55FF55);
        }
        y += 8;
        return y;
    }

    private int calculateSafariWidth(boolean showTimer, boolean showHunts) {
        int maxWidth = PANEL_MIN_WIDTH;

        if (showHunts) {
            for (SafariHuntData hunt : safariHuntManager.getActiveHunts()) {
                int nameWidth = this.font.width(hunt.getDisplayName()) + (hunt.getStarRating() * 5) + PADDING * 2;
                String resetText = hunt.getResetCountdownFormatted();
                if (!resetText.isEmpty()) {
                    nameWidth += this.font.width(resetText) + 8;
                }
                maxWidth = Math.max(maxWidth, nameWidth);
            }
        }

        if (showTimer) {
            String header = safariManager.isInSafariZone() ? "SAA Safari Helper" : "SAA Safari Helper (away)";
            maxWidth = Math.max(maxWidth, this.font.width(header) + PADDING * 2);
        }

        return maxWidth + PADDING * 2;
    }

    private int calculateSafariHeight(boolean showTimer, boolean showHunts) {
        int height = PADDING;

        if (!showTimer && !showHunts) {
            int panelWidth = calculateSafariWidth(showTimer, showHunts);
            int messageLines = wrapText(this.font, NO_QUESTS_MESSAGE, Math.max(panelWidth - PADDING * 2, 1)).size();
            return height + LINE_HEIGHT + TIMER_BAR_HEIGHT + SECTION_SPACING + LINE_HEIGHT * messageLines + PADDING;
        }

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
            int panelWidth = calculateSafariWidth(showTimer, showHunts);
            int messageLines = wrapText(this.font, NO_QUESTS_MESSAGE, Math.max(panelWidth - PADDING * 2, 1)).size();
            height += SECTION_SPACING + LINE_HEIGHT * messageLines;
        }

        height += PADDING;
        return height;
    }

    private int calculateDaycareWidth() {
        int maxWidth = PANEL_MIN_WIDTH;
        maxWidth = Math.max(maxWidth, this.font.width("SAA Daycare Helper") + PADDING * 2);

        for (DaycareState.PenState pen : daycareManager.getDisplayPens()) {
            String penLabel = "[PEN " + pen.getPenNumber() + "]";
            String species = pen.getInferredEggSpecies();
            if (species == null) species = pen.getPokemon1() != null ? pen.getPokemon1() : "Unknown";

            int lineWidth;
            if (pen.getStage() == DaycareState.BreedingStage.EMPTY || pen.getStage() == DaycareState.BreedingStage.ONE_POKEMON) {
                lineWidth = this.font.width(penLabel) + 4 + this.font.width("Inactive");
            } else if (pen.getStage() == DaycareState.BreedingStage.NEEDS_RESET) {
                lineWidth = this.font.width(penLabel) + 4 + this.font.width(species) + 8 + this.font.width("Parents Need Reset!");
            } else if (pen.getStage() == DaycareState.BreedingStage.INCOMPATIBLE) {
                lineWidth = this.font.width(penLabel) + 4 + this.font.width("Incompatible Setup");
            } else if (pen.getStage() == DaycareState.BreedingStage.EGG_READY) {
                lineWidth = this.font.width(penLabel) + 4 + this.font.width(species) + 8 + this.font.width("Egg Ready!");
            } else {
                lineWidth = this.font.width(penLabel) + 4 + this.font.width(species) + 8 + this.font.width(pen.getRemainingFormatted());
            }
            maxWidth = Math.max(maxWidth, lineWidth + PADDING * 2 + 4);
        }

        maxWidth = Math.max(maxWidth, this.font.width("[PEN 1] Eevee") + this.font.width("~12:34") + PADDING * 2 + 8);

        return maxWidth + PADDING * 2;
    }

    private int calculateDaycareHeight() {
        int height = PADDING;
        height += LINE_HEIGHT;

        List<DaycareState.PenState> pens = daycareManager.getDisplayPens();
        List<DaycareState.ClaimedEgg> eggs = daycareManager.getDisplayEggs();

        if (!pens.isEmpty() || !eggs.isEmpty()) {
            if (!pens.isEmpty()) {
                height += 2 + SECTION_SPACING + LINE_HEIGHT;
                for (DaycareState.PenState pen : pens) {
                    if (pen.getStage() == DaycareState.BreedingStage.BREEDING
                            || pen.getStage() == DaycareState.BreedingStage.NEEDS_RESET
                            || pen.getStage() == DaycareState.BreedingStage.INCOMPATIBLE) {
                        height += LINE_HEIGHT + 8;
                    } else {
                        height += LINE_HEIGHT;
                    }
                }
            }
            if (!eggs.isEmpty()) {
                height += 2 + SECTION_SPACING + LINE_HEIGHT;
                height += eggs.size() * (LINE_HEIGHT + 8);
            }
        } else {
            height += 2 + SECTION_SPACING + LINE_HEIGHT;
            height += LINE_HEIGHT + 8;
            height += LINE_HEIGHT;
            height += 2 + SECTION_SPACING + LINE_HEIGHT;
            height += LINE_HEIGHT + 8;
        }

        height += PADDING;
        return height;
    }


    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int mx = (int) mouseX;
        int my = (int) mouseY;

        CornerHit safariCorner = getCornerHit(safariPanel, mx, my);
        CornerHit daycareCorner = getCornerHit(daycarePanel, mx, my);

        if (safariCorner != null) {
            startResize(safariPanel, safariCorner);
            return true;
        }
        if (daycareCorner != null) {
            startResize(daycarePanel, daycareCorner);
            return true;
        }

        if (isInsidePanel(safariPanel, mx, my)) {
            startMove(safariPanel, mx, my);
            return true;
        }
        if (isInsidePanel(daycarePanel, mx, my)) {
            startMove(daycarePanel, mx, my);
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void startResize(PanelState panel, CornerHit corner) {
        dragMode = DragMode.RESIZE;
        activePanel = panel;
        resizeFromLeft = corner.fromLeft;
        resizeFromTop = corner.fromTop;
        resizeAnchorX = corner.fromLeft ? (panel.panelX + panel.scaledWidth) : panel.panelX;
        resizeAnchorY = corner.fromTop ? (panel.panelY + panel.scaledHeight) : panel.panelY;
    }

    private void startMove(PanelState panel, int mx, int my) {
        dragMode = DragMode.MOVE;
        activePanel = panel;
        dragOffsetX = mx - panel.panelX;
        dragOffsetY = my - panel.panelY;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragMode != DragMode.NONE) {
            dragMode = DragMode.NONE;
            activePanel = null;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button != 0 || activePanel == null) return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);

        int mx = (int) mouseX;
        int my = (int) mouseY;

        if (dragMode == DragMode.MOVE) {
            activePanel.panelX = Math.clamp(mx - dragOffsetX, 0,
                    Math.max(0, this.width - activePanel.scaledWidth));
            activePanel.panelY = Math.clamp(my - dragOffsetY, 0,
                    Math.max(0, this.height - activePanel.scaledHeight));
            return true;
        }

        if (dragMode == DragMode.RESIZE) {
            float distX;
            if (resizeFromLeft) {
                distX = resizeAnchorX - mx;
            } else {
                distX = mx - resizeAnchorX;
            }

            float newScale = distX / activePanel.unscaledWidth;

            float maxFitScale = Math.min(
                    (float) this.width / activePanel.unscaledWidth,
                    (float) this.height / activePanel.unscaledHeight
            );
            newScale = Math.clamp(newScale, MIN_SCALE, Math.min(MAX_SCALE, maxFitScale));

            activePanel.currentScale = newScale;
            activePanel.updateScaledDimensions();

            if (resizeFromLeft) {
                activePanel.panelX = resizeAnchorX - activePanel.scaledWidth;
            } else {
                activePanel.panelX = resizeAnchorX;
            }
            if (resizeFromTop) {
                activePanel.panelY = resizeAnchorY - activePanel.scaledHeight;
            } else {
                activePanel.panelY = resizeAnchorY;
            }

            activePanel.panelX = Math.clamp(activePanel.panelX, 0,
                    Math.max(0, this.width - activePanel.scaledWidth));
            activePanel.panelY = Math.clamp(activePanel.panelY, 0,
                    Math.max(0, this.height - activePanel.scaledHeight));

            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public void onClose() {
        // save safari panel
        hudConfig.setHudScale(safariPanel.currentScale);
        hudConfig.setPositionFromAbsolute(safariPanel.panelX, safariPanel.panelY,
                safariPanel.scaledWidth, safariPanel.scaledHeight, this.width, this.height);

        // save daycare panel
        hudConfig.setDaycareScale(daycarePanel.currentScale);
        hudConfig.setDaycarePositionFromAbsolute(daycarePanel.panelX, daycarePanel.panelY,
                daycarePanel.scaledWidth, daycarePanel.scaledHeight, this.width, this.height);

        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
    
    private record CornerHit(boolean fromLeft, boolean fromTop) {}

    private CornerHit getCornerHit(PanelState panel, int mx, int my) {
        if (isNearPoint(mx, my, panel.panelX, panel.panelY, CORNER_GRAB_RADIUS)) {
            return new CornerHit(true, true);
        }
        if (isNearPoint(mx, my, panel.panelX + panel.scaledWidth, panel.panelY, CORNER_GRAB_RADIUS)) {
            return new CornerHit(false, true);
        }
        if (isNearPoint(mx, my, panel.panelX, panel.panelY + panel.scaledHeight, CORNER_GRAB_RADIUS)) {
            return new CornerHit(true, false);
        }
        if (isNearPoint(mx, my, panel.panelX + panel.scaledWidth, panel.panelY + panel.scaledHeight, CORNER_GRAB_RADIUS)) {
            return new CornerHit(false, false);
        }
        return null;
    }

    private boolean isInsidePanel(PanelState panel, int mx, int my) {
        return mx >= panel.panelX && mx <= panel.panelX + panel.scaledWidth
                && my >= panel.panelY && my <= panel.panelY + panel.scaledHeight;
    }

    private static boolean isNearPoint(int mx, int my, int px, int py, int radius) {
        return Math.abs(mx - px) <= radius && Math.abs(my - py) <= radius;
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
