package com.siguha.sigsacademyaddons.hud;

import com.siguha.sigsacademyaddons.config.HudConfig;
import com.siguha.sigsacademyaddons.feature.wondertrade.WondertradeManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class WondertradeHudRenderer {

    private static final int PADDING = 4;
    private static final int LINE_HEIGHT = 11;
    private static final int SECTION_SPACING = 6;
    private static final int TIMER_BAR_HEIGHT = 6;
    private static final int PANEL_MIN_WIDTH = 140;

    private static final int COLOR_BG = 0xAA000000;
    private static final int COLOR_HEADER = 0xFFFFAA00;
    private static final int COLOR_TIMER = 0xFF55FF55;
    private static final int COLOR_PROGRESS_BG = 0xFF333333;
    private static final int COLOR_TEXT_UNSET = 0xFF777777;

    private final WondertradeManager wondertradeManager;
    private final HudConfig hudConfig;

    public WondertradeHudRenderer(WondertradeManager wondertradeManager, HudConfig hudConfig) {
        this.wondertradeManager = wondertradeManager;
        this.hudConfig = hudConfig;
    }

    public void onHudRender(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) return;
        if (!hudConfig.isWtMenuEnabled()) return;

        Font font = client.font;
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        float scale = hudConfig.getWtScale();
        boolean transparent = hudConfig.getHudStyle() == HudConfig.HudStyle.TRANSPARENT;

        int panelWidth = calculatePanelWidth(font);
        int panelHeight = calculatePanelHeight(font);

        int scaledWidth = Math.round(panelWidth * scale);
        int scaledHeight = Math.round(panelHeight * scale);

        int panelX = hudConfig.getWtPanelX(screenWidth, scaledWidth);
        int panelY = hudConfig.getWtPanelY(screenHeight, scaledHeight);

        graphics.pose().pushPose();
        graphics.pose().translate(panelX, panelY, 0);
        graphics.pose().scale(scale, scale, 1.0f);

        if (!transparent) {
            graphics.fill(0, 0, panelWidth, panelHeight, COLOR_BG);
        }

        int currentY = PADDING;

        String header = "SAA Wondertrade Helper";
        int headerWidth = font.width(header);
        graphics.drawString(font, header, (panelWidth - headerWidth) / 2, currentY, COLOR_HEADER, true);
        currentY += LINE_HEIGHT;

        currentY += 2;
        graphics.fill(PADDING, currentY, panelWidth - PADDING, currentY + 1, 0xFF555555);
        currentY += SECTION_SPACING;

        if (!wondertradeManager.hasTimer()) {
            String unsetText = "Please use WT once to set menu.";
            int unsetWidth = font.width(unsetText);
            graphics.drawString(font, unsetText, (panelWidth - unsetWidth) / 2, currentY, COLOR_TEXT_UNSET, true);

        } else if (wondertradeManager.isCooldownOver()) {
            String doneText = "Cooldown Over!";
            int doneWidth = font.width(doneText);
            graphics.drawString(font, doneText, (panelWidth - doneWidth) / 2, currentY, COLOR_TIMER, true);
            currentY += LINE_HEIGHT;

            int barX = PADDING + 2;
            int barWidth = panelWidth - barX - PADDING;
            graphics.fill(barX, currentY, barX + barWidth, currentY + TIMER_BAR_HEIGHT, COLOR_TIMER);

        } else {
            String timerText = wondertradeManager.getRemainingFormatted();
            int timerWidth = font.width(timerText);
            graphics.drawString(font, timerText, (panelWidth - timerWidth) / 2, currentY, COLOR_TIMER, true);
            currentY += LINE_HEIGHT;

            int barX = PADDING + 2;
            int barWidth = panelWidth - barX - PADDING;
            float progress = wondertradeManager.getProgress();

            graphics.fill(barX, currentY, barX + barWidth, currentY + TIMER_BAR_HEIGHT, COLOR_PROGRESS_BG);
            int filled = (int) (barWidth * progress);

            if (filled > 0) {
                graphics.fill(barX, currentY, barX + filled, currentY + TIMER_BAR_HEIGHT, COLOR_TIMER);
            }
        }

        graphics.pose().popPose();
    }

    public int calculatePanelWidth(Font font) {
        int maxWidth = PANEL_MIN_WIDTH;
        maxWidth = Math.max(maxWidth, font.width("SAA Wondertrade Helper") + PADDING * 2);
        maxWidth = Math.max(maxWidth, font.width("Please use WT once to set menu.") + PADDING * 2);

        return maxWidth + PADDING * 2;
    }

    public int calculatePanelHeight(Font font) {
        int height = PADDING;
        height += LINE_HEIGHT;
        height += 2 + SECTION_SPACING;

        if (!wondertradeManager.hasTimer()) {
            height += LINE_HEIGHT;
        } else {
            height += LINE_HEIGHT;
            height += TIMER_BAR_HEIGHT;
        }

        height += PADDING;
        return height;
    }
}
