package com.siguha.sigsacademyaddons.hud;

import com.siguha.sigsacademyaddons.config.HudConfig;
import com.siguha.sigsacademyaddons.feature.wondertrade.WondertradeManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class WondertradeHudRenderer implements HudPanel {

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

    @Override
    public String getPanelId() {
        return "wondertrade";
    }

    @Override
    public boolean shouldRender() {
        Minecraft client = Minecraft.getInstance();
        return hudConfig.isWtMenuEnabled() && client.player != null && !client.options.hideGui;
    }

    @Override
    public boolean hasVisibleContent() {
        return wondertradeManager.hasTimer();
    }

    @Override
    public int getContentWidth(Font font) {
        return hudConfig.isCompact() ? calculateCompactPanelWidth(font) : calculatePanelWidth(font);
    }

    @Override
    public int getContentHeight(Font font) {
        return hudConfig.isCompact() ? calculateCompactPanelHeight() : calculatePanelHeight(font);
    }

    @Override
    public int getContentHeight(Font font, int panelWidth) {
        if (hudConfig.isCompact()) return calculateCompactPanelHeightForWidth(font, panelWidth);
        return calculatePanelHeightForWidth(font, panelWidth);
    }

    @Override
    public void renderContent(GuiGraphics graphics, Font font, int panelWidth) {
        if (hudConfig.isCompact()) {
            renderCompact(graphics, font, panelWidth);
        } else {
            renderFull(graphics, font, panelWidth);
        }
    }

    @Override
    public void onHudRender(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!shouldRender()) return;
        if (hudConfig.isInGroup("wondertrade")) return;

        Minecraft client = Minecraft.getInstance();
        Font font = client.font;
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        float scale = hudConfig.getWtScale();

        double guiScale = client.getWindow().getGuiScale();
        float minScale = (float) (6.0 / (8.0 * guiScale));
        float maxScale = (float) (32.0 / (8.0 * guiScale));
        scale = Math.max(minScale, Math.min(maxScale, scale));

        boolean transparent = hudConfig.getHudStyle() == HudConfig.HudStyle.TRANSPARENT;

        int widthOvr = hudConfig.getWtWidthOverride();
        int panelWidth = widthOvr > 0 ? widthOvr : getContentWidth(font);
        int panelHeight = getContentHeight(font, panelWidth);

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

        renderContent(graphics, font, panelWidth);

        graphics.pose().popPose();
    }

    private void renderCompact(GuiGraphics graphics, Font font, int panelWidth) {
        int y = PADDING;
        String prefix = "WT Time:";

        String timerText;
        int timerColor;
        if (!wondertradeManager.hasTimer()) {
            timerText = "Not Set";
            timerColor = COLOR_TEXT_UNSET;
        } else if (wondertradeManager.isCooldownOver()) {
            timerText = "Ready!";
            timerColor = COLOR_TIMER;
        } else {
            timerText = wondertradeManager.getRemainingFormatted();
            timerColor = COLOR_TIMER;
        }
        HudTextUtil.renderStatLine(graphics, font, Component.literal(prefix), Component.literal(timerText),
                COLOR_HEADER, timerColor, y, panelWidth, PADDING, LINE_HEIGHT);
    }

    private void renderFull(GuiGraphics graphics, Font font, int panelWidth) {
        int currentY = PADDING;

        currentY = HudTextUtil.renderWrappedCentered(graphics, font, Component.translatable("interface.saa.wondertrade.helper"), panelWidth, currentY, COLOR_HEADER, LINE_HEIGHT);

        currentY += 2;
        graphics.fill(PADDING, currentY, panelWidth - PADDING, currentY + 1, 0xFF555555);
        currentY += SECTION_SPACING;

        if (!wondertradeManager.hasTimer()) {
            currentY = HudTextUtil.renderWrappedCentered(graphics, font, Component.translatable("interface.saa.wondertrade.unset"), panelWidth, currentY, COLOR_TEXT_UNSET, LINE_HEIGHT);

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
    }

    private int calculateCompactPanelWidth(Font font) {
        String prefix = "WT Time: ";
        String timerText;
        if (!wondertradeManager.hasTimer()) {
            timerText = "Not Set";
        } else if (wondertradeManager.isCooldownOver()) {
            timerText = "Ready!";
        } else {
            timerText = wondertradeManager.getRemainingFormatted();
        }
        return font.width(prefix) + font.width(timerText) + PADDING * 2;
    }

    private int calculateCompactPanelHeight() {
        return PADDING + LINE_HEIGHT + PADDING;
    }

    private int calculateCompactPanelHeightForWidth(Font font, int panelWidth) {
        String prefix = "WT Time:";
        String timerText;
        if (!wondertradeManager.hasTimer()) {
            timerText = "Not Set";
        } else if (wondertradeManager.isCooldownOver()) {
            timerText = "Ready!";
        } else {
            timerText = wondertradeManager.getRemainingFormatted();
        }
        return PADDING + HudTextUtil.statLineHeight(font, Component.literal(prefix), Component.literal(timerText), panelWidth, PADDING, LINE_HEIGHT) + PADDING;
    }

    private int calculatePanelWidth(Font font) {
        int maxWidth = PANEL_MIN_WIDTH;
        maxWidth = Math.max(maxWidth, font.width(Component.translatable("interface.saa.wondertrade.helper")) + PADDING * 2);
        maxWidth = Math.max(maxWidth, font.width(Component.translatable("interface.saa.wondertrade.unset")) + PADDING * 2);

        return maxWidth + PADDING * 2;
    }

    private int calculatePanelHeight(Font font) {
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

    private int calculatePanelHeightForWidth(Font font, int panelWidth) {
        int height = PADDING;
        height += HudTextUtil.wrappedCenteredHeight(font, Component.translatable("interface.saa.wondertrade.helper"), panelWidth, LINE_HEIGHT);
        height += 2 + SECTION_SPACING;

        if (!wondertradeManager.hasTimer()) {
            height += HudTextUtil.wrappedCenteredHeight(font, Component.translatable("interface.saa.wondertrade.unset"), panelWidth, LINE_HEIGHT);
        } else {
            height += LINE_HEIGHT;
            height += TIMER_BAR_HEIGHT;
        }

        height += PADDING;
        return height;
    }
}
