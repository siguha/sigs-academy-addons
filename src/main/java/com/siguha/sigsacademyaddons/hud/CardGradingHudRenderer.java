package com.siguha.sigsacademyaddons.hud;

import com.siguha.sigsacademyaddons.config.HudConfig;
import com.siguha.sigsacademyaddons.feature.cardgrading.CardGradingManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class CardGradingHudRenderer implements HudPanel {

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

    private final CardGradingManager cardGradingManager;
    private final HudConfig hudConfig;

    public CardGradingHudRenderer(CardGradingManager cardGradingManager, HudConfig hudConfig) {
        this.cardGradingManager = cardGradingManager;
        this.hudConfig = hudConfig;
    }

    @Override
    public String getPanelId() {
        return "grading";
    }

    @Override
    public boolean shouldRender() {
        Minecraft client = Minecraft.getInstance();
        return hudConfig.isCardGradingMenuEnabled() && client.player != null && !client.options.hideGui;
    }

    @Override
    public boolean hasVisibleContent() {
        return true;
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
        if (hudConfig.isCompact()) {
            return calculateCompactPanelHeightForWidth(font, panelWidth);
        }
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
        if (hudConfig.isInGroup("grading")) return;

        Minecraft client = Minecraft.getInstance();
        Font font = client.font;
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        float scale = hudConfig.getCardGradingScale();

        double guiScale = client.getWindow().getGuiScale();
        float minScale = (float) (6.0 / (8.0 * guiScale));
        float maxScale = (float) (32.0 / (8.0 * guiScale));
        scale = Math.max(minScale, Math.min(maxScale, scale));

        boolean transparent = hudConfig.getHudStyle() == HudConfig.HudStyle.TRANSPARENT;

        int widthOvr = hudConfig.getCardGradingWidthOverride();
        int panelWidth = widthOvr > 0 ? widthOvr : getContentWidth(font);
        int panelHeight = getContentHeight(font, panelWidth);

        int scaledWidth = Math.round(panelWidth * scale);
        int scaledHeight = Math.round(panelHeight * scale);

        int panelX = hudConfig.getCardGradingPanelX(screenWidth, scaledWidth);
        int panelY = hudConfig.getCardGradingPanelY(screenHeight, scaledHeight);

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
        String timerText = cardGradingManager.isReadyToClaim()
                ? "Ready!"
                : cardGradingManager.hasTimer() ? cardGradingManager.getRemainingFormatted() : "Not Set";
        int timerColor = cardGradingManager.hasTimer() ? COLOR_TIMER : COLOR_TEXT_UNSET;

        HudTextUtil.renderStatLine(graphics, font, "Grading Time:", timerText,
                COLOR_HEADER, timerColor, y, panelWidth, PADDING, LINE_HEIGHT);
    }

    private void renderFull(GuiGraphics graphics, Font font, int panelWidth) {
        int y = PADDING;

        y = HudTextUtil.renderWrappedCentered(graphics, font, "SAA Card Grading",
                panelWidth, y, COLOR_HEADER, LINE_HEIGHT);

        y += 2;
        graphics.fill(PADDING, y, panelWidth - PADDING, y + 1, 0xFF555555);
        y += SECTION_SPACING;

        String timerText;
        if (!cardGradingManager.hasTimer()) {
            String unsetText = "Please use grading once to set menu.";
            HudTextUtil.renderWrappedCentered(graphics, font, unsetText, panelWidth, y, COLOR_TEXT_UNSET, LINE_HEIGHT);
            return;
        } else if (cardGradingManager.isReadyToClaim()) {
            timerText = "Ready to Claim!";
        } else {
            timerText = cardGradingManager.getRemainingFormatted();
        }

        int timerColor = cardGradingManager.hasTimer() ? COLOR_TIMER : COLOR_TEXT_UNSET;
        int timerWidth = font.width(timerText);
        graphics.drawString(font, timerText, (panelWidth - timerWidth) / 2, y, timerColor, true);
        y += LINE_HEIGHT;

        int barX = PADDING + 2;
        int barWidth = panelWidth - barX - PADDING;
        graphics.fill(barX, y, barX + barWidth, y + TIMER_BAR_HEIGHT, COLOR_PROGRESS_BG);

        int filled = cardGradingManager.isReadyToClaim()
                ? barWidth
                : (int) (barWidth * cardGradingManager.getProgress());
        if (filled > 0) {
            graphics.fill(barX, y, barX + filled, y + TIMER_BAR_HEIGHT, COLOR_TIMER);
        }
    }

    private int calculateCompactPanelWidth(Font font) {
        String timerText;
        if (!cardGradingManager.hasTimer()) {
            timerText = "Not Set";
        } else if (cardGradingManager.isReadyToClaim()) {
            timerText = "Ready!";
        } else {
            timerText = cardGradingManager.getRemainingFormatted();
        }
        return font.width("Grading Time:") + 8 + font.width(timerText) + PADDING * 2 + 2;
    }

    private int calculateCompactPanelHeight() {
        return PADDING + LINE_HEIGHT + PADDING;
    }

    private int calculateCompactPanelHeightForWidth(Font font, int panelWidth) {
        String timerText;
        if (!cardGradingManager.hasTimer()) {
            timerText = "Not Set";
        } else if (cardGradingManager.isReadyToClaim()) {
            timerText = "Ready!";
        } else {
            timerText = cardGradingManager.getRemainingFormatted();
        }
        return PADDING + HudTextUtil.statLineHeight(font, "Grading Time:", timerText, panelWidth, PADDING, LINE_HEIGHT) + PADDING;
    }

    private int calculatePanelWidth(Font font) {
        int maxWidth = PANEL_MIN_WIDTH;
        maxWidth = Math.max(maxWidth, font.width("SAA Card Grading") + PADDING * 2);
        maxWidth = Math.max(maxWidth, font.width("Please use grading once to set menu.") + PADDING * 2);
        maxWidth = Math.max(maxWidth, font.width("Ready to Claim!") + PADDING * 2);
        return maxWidth + PADDING * 2;
    }

    private int calculatePanelHeight(Font font) {
        int height = PADDING;
        height += LINE_HEIGHT;
        height += 2 + SECTION_SPACING;
        if (!cardGradingManager.hasTimer()) {
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
        height += HudTextUtil.wrappedCenteredHeight(font, "SAA Card Grading", panelWidth, LINE_HEIGHT);
        height += 2 + SECTION_SPACING;
        if (!cardGradingManager.hasTimer()) {
            height += HudTextUtil.wrappedCenteredHeight(font, "Please use grading once to set menu.", panelWidth, LINE_HEIGHT);
        } else {
            height += HudTextUtil.wrappedCenteredHeight(font, "Ready to Claim!", panelWidth, LINE_HEIGHT);
            height += TIMER_BAR_HEIGHT;
        }
        height += PADDING;
        return height;
    }
}
