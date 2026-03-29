package com.siguha.sigsacademyaddons.hud;

import com.siguha.sigsacademyaddons.config.HudConfig;
import com.siguha.sigsacademyaddons.feature.cardstats.CardStatsManager;
import com.siguha.sigsacademyaddons.feature.cardstats.CardStatsManager.StatEntry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

public class CardStatsHudRenderer implements HudPanel {

    private static final int PADDING = 4;
    private static final int LINE_HEIGHT = 11;
    private static final int SECTION_SPACING = 6;
    private static final int PANEL_MIN_WIDTH = 120;

    private static final int COLOR_BG = 0xAA000000;
    private static final int COLOR_HEADER = 0xFFFFAA00;
    private static final int COLOR_SECTION_HEADER = 0xFF55FFFF;
    private static final int COLOR_STAT_NAME = 0xFFFFFFFF;
    private static final int COLOR_STAT_VALUE = 0xFF55FF55;

    private final CardStatsManager cardStatsManager;
    private final HudConfig hudConfig;

    public CardStatsHudRenderer(CardStatsManager cardStatsManager, HudConfig hudConfig) {
        this.cardStatsManager = cardStatsManager;
        this.hudConfig = hudConfig;
    }

    @Override
    public String getPanelId() {
        return "cardstats";
    }

    @Override
    public boolean shouldRender() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) return false;
        if (!hudConfig.isCardStatsMenuEnabled()) return false;
        return cardStatsManager.hasCardAlbum() && cardStatsManager.hasAnyStats();
    }

    @Override
    public boolean hasVisibleContent() {
        return shouldRender() && hudConfig.isCardStatsDisplayAlways();
    }

    public boolean canRenderInInventory() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return false;
        if (!hudConfig.isCardStatsMenuEnabled()) return false;
        if (!hudConfig.isCardStatsDisplayInInventory()) return false;
        return cardStatsManager.hasCardAlbum() && cardStatsManager.hasAnyStats();
    }

    @Override
    public int getContentWidth(Font font) {
        if (hudConfig.isCompact()) {
            return calculateCompactWidth(font);
        }
        return calculateFullWidth(font);
    }

    @Override
    public int getContentHeight(Font font) {
        if (hudConfig.isCompact()) {
            return calculateCompactHeight();
        }
        return calculateFullHeight();
    }

    @Override
    public int getContentHeight(Font font, int panelWidth) {
        if (hudConfig.isCompact()) {
            return calculateCompactHeightForWidth(font, panelWidth);
        }
        return calculateFullHeightForWidth(font, panelWidth);
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
        if (!hudConfig.isCardStatsDisplayAlways()) return;
        if (hudConfig.isInGroup("cardstats")) return;

        Minecraft client = Minecraft.getInstance();
        Font font = client.font;
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        float scale = hudConfig.getCardStatsScale();

        double guiScale = client.getWindow().getGuiScale();
        float minScale = (float) (6.0 / (8.0 * guiScale));
        float maxScale = (float) (32.0 / (8.0 * guiScale));
        scale = Math.max(minScale, Math.min(maxScale, scale));

        boolean transparent = hudConfig.getHudStyle() == HudConfig.HudStyle.TRANSPARENT;

        int widthOvr = hudConfig.getCardStatsWidthOverride();
        int panelWidth = widthOvr > 0 ? widthOvr : getContentWidth(font);
        int panelHeight = getContentHeight(font, panelWidth);

        int scaledWidth = Math.round(panelWidth * scale);
        int scaledHeight = Math.round(panelHeight * scale);

        int panelX = hudConfig.getCardStatsPanelX(screenWidth, scaledWidth);
        int panelY = hudConfig.getCardStatsPanelY(screenHeight, scaledHeight);

        graphics.pose().pushPose();
        graphics.pose().translate(panelX, panelY, 0);
        graphics.pose().scale(scale, scale, 1.0f);

        if (!transparent) {
            graphics.fill(0, 0, panelWidth, panelHeight, COLOR_BG);
        }

        renderContent(graphics, font, panelWidth);

        graphics.pose().popPose();
    }

    private void renderFull(GuiGraphics graphics, Font font, int panelWidth) {
        int y = PADDING;

        Component header = Component.translatable("interface.saa.text.saa_stats");
        y = HudTextUtil.renderWrappedCentered(graphics, font, header, panelWidth, y, COLOR_HEADER, LINE_HEIGHT);

        List<StatEntry> playerStats = cardStatsManager.getPlayerStats();
        List<StatEntry> cardStatsList = cardStatsManager.getCardStats();

        if (!playerStats.isEmpty()) {
            y += 2;
            graphics.fill(PADDING, y, panelWidth - PADDING, y + 1, 0xFF555555);
            y += SECTION_SPACING;

            graphics.drawString(font, Component.translatable("interface.saa.text.player"), PADDING, y, COLOR_SECTION_HEADER, true);
            y += LINE_HEIGHT;

            for (StatEntry entry : playerStats) {
                y = renderStatLine(graphics, font, y, panelWidth, entry);
            }
        }

        if (!cardStatsList.isEmpty()) {
            y += 2;
            graphics.fill(PADDING, y, panelWidth - PADDING, y + 1, 0xFF555555);
            y += SECTION_SPACING;

            graphics.drawString(font, Component.translatable("interface.saa.text.cards"), PADDING, y, COLOR_SECTION_HEADER, true);
            y += LINE_HEIGHT;

            for (StatEntry entry : cardStatsList) {
                y = renderStatLine(graphics, font, y, panelWidth, entry);
            }
        }
    }

    private void renderCompact(GuiGraphics graphics, Font font, int panelWidth) {
        int y = PADDING;

        Component title = Component.translatable("interface.saa.text.stats");
        graphics.drawString(font, title, PADDING, y, COLOR_HEADER, true);
        y += LINE_HEIGHT;

        List<StatEntry> playerStats = cardStatsManager.getPlayerStats();
        List<StatEntry> cardStatsList = cardStatsManager.getCardStats();

        if (!playerStats.isEmpty()) {
            graphics.drawString(font, Component.translatable("interface.saa.text.player"), PADDING, y, COLOR_SECTION_HEADER, true);
            y += LINE_HEIGHT;

            for (StatEntry entry : playerStats) {
                y = renderStatLine(graphics, font, y, panelWidth, entry);
            }
        }

        if (!cardStatsList.isEmpty()) {
            graphics.drawString(font, Component.translatable("interface.saa.text.cards"), PADDING, y, COLOR_SECTION_HEADER, true);
            y += LINE_HEIGHT;

            for (StatEntry entry : cardStatsList) {
                y = renderStatLine(graphics, font, y, panelWidth, entry);
            }
        }
    }

    private int renderStatLine(GuiGraphics graphics, Font font, int y, int panelWidth, StatEntry entry) {
        String valueStr = CardStatsManager.formatValue(entry);
        return HudTextUtil.renderStatLine(graphics, font,
                Component.literal(entry.displayName()), Component.literal(valueStr),
                COLOR_STAT_NAME, COLOR_STAT_VALUE,
                y, panelWidth, PADDING, LINE_HEIGHT);
    }

    private int calculateFullWidth(Font font) {
        int maxWidth = PANEL_MIN_WIDTH;

        List<StatEntry> playerStats = cardStatsManager.getPlayerStats();
        List<StatEntry> cardStatsList = cardStatsManager.getCardStats();

        for (StatEntry entry : playerStats) {
            int lineWidth = font.width(entry.displayName()) + 8 + font.width(CardStatsManager.formatValue(entry));
            maxWidth = Math.max(maxWidth, lineWidth + PADDING * 2 + 2);
        }

        for (StatEntry entry : cardStatsList) {
            int lineWidth = font.width(entry.displayName()) + 8 + font.width(CardStatsManager.formatValue(entry));
            maxWidth = Math.max(maxWidth, lineWidth + PADDING * 2 + 2);
        }

        Component header = Component.translatable("interface.saa.text.saa_stats");
        maxWidth = Math.max(maxWidth, font.width(header) + PADDING * 2);
        maxWidth = Math.max(maxWidth, font.width(Component.translatable("interface.saa.text.player")) + PADDING * 2);
        maxWidth = Math.max(maxWidth, font.width(Component.translatable("interface.saa.text.cards")) + PADDING * 2);

        return maxWidth;
    }

    private int calculateFullHeight() {
        List<StatEntry> playerStats = cardStatsManager.getPlayerStats();
        List<StatEntry> cardStatsList = cardStatsManager.getCardStats();

        int height = PADDING;
        height += LINE_HEIGHT;

        if (!playerStats.isEmpty()) {
            height += 2 + SECTION_SPACING;
            height += LINE_HEIGHT;
            height += playerStats.size() * LINE_HEIGHT;
        }

        if (!cardStatsList.isEmpty()) {
            height += 2 + SECTION_SPACING;
            height += LINE_HEIGHT;
            height += cardStatsList.size() * LINE_HEIGHT;
        }

        height += PADDING;
        return height;
    }

    private int calculateCompactWidth(Font font) {
        int maxWidth = PANEL_MIN_WIDTH;

        List<StatEntry> playerStats = cardStatsManager.getPlayerStats();
        List<StatEntry> cardStatsList = cardStatsManager.getCardStats();

        maxWidth = Math.max(maxWidth, font.width(Component.translatable("interface.saa.text.stats")) + PADDING * 2);
        maxWidth = Math.max(maxWidth, font.width(Component.translatable("interface.saa.text.player")) + PADDING * 2);
        maxWidth = Math.max(maxWidth, font.width(Component.translatable("interface.saa.text.cards")) + PADDING * 2);

        for (StatEntry entry : playerStats) {
            int lineWidth = font.width(entry.displayName()) + 8 + font.width(CardStatsManager.formatValue(entry));
            maxWidth = Math.max(maxWidth, lineWidth + PADDING * 2 + 2);
        }

        for (StatEntry entry : cardStatsList) {
            int lineWidth = font.width(entry.displayName()) + 8 + font.width(CardStatsManager.formatValue(entry));
            maxWidth = Math.max(maxWidth, lineWidth + PADDING * 2 + 2);
        }

        return maxWidth;
    }

    private int calculateCompactHeight() {
        List<StatEntry> playerStats = cardStatsManager.getPlayerStats();
        List<StatEntry> cardStatsList = cardStatsManager.getCardStats();

        int height = PADDING;
        height += LINE_HEIGHT;
        if (!playerStats.isEmpty()) {
            height += LINE_HEIGHT;
            height += playerStats.size() * LINE_HEIGHT;
        }
        if (!cardStatsList.isEmpty()) {
            height += LINE_HEIGHT;
            height += cardStatsList.size() * LINE_HEIGHT;
        }
        height += PADDING;
        return height;
    }

    private int calculateFullHeightForWidth(Font font, int panelWidth) {
        List<StatEntry> playerStats = cardStatsManager.getPlayerStats();
        List<StatEntry> cardStatsList = cardStatsManager.getCardStats();

        int height = PADDING;
        height += HudTextUtil.wrappedCenteredHeight(font, Component.translatable("interface.saa.text.saa_stats"), panelWidth, LINE_HEIGHT);

        if (!playerStats.isEmpty()) {
            height += 2 + SECTION_SPACING;
            height += LINE_HEIGHT;
            for (StatEntry entry : playerStats) {
                height += HudTextUtil.statLineHeight(font, Component.literal(entry.displayName()),
                        Component.literal(CardStatsManager.formatValue(entry)), panelWidth, PADDING, LINE_HEIGHT);
            }
        }

        if (!cardStatsList.isEmpty()) {
            height += 2 + SECTION_SPACING;
            height += LINE_HEIGHT;
            for (StatEntry entry : cardStatsList) {
                height += HudTextUtil.statLineHeight(font, Component.literal(entry.displayName()),
                        Component.literal(CardStatsManager.formatValue(entry)), panelWidth, PADDING, LINE_HEIGHT);
            }
        }

        height += PADDING;
        return height;
    }

    private int calculateCompactHeightForWidth(Font font, int panelWidth) {
        List<StatEntry> playerStats = cardStatsManager.getPlayerStats();
        List<StatEntry> cardStatsList = cardStatsManager.getCardStats();

        int height = PADDING;
        height += LINE_HEIGHT;
        if (!playerStats.isEmpty()) {
            height += LINE_HEIGHT;
            for (StatEntry entry : playerStats) {
                height += HudTextUtil.statLineHeight(font, Component.literal(entry.displayName()),
                        Component.literal(CardStatsManager.formatValue(entry)), panelWidth, PADDING, LINE_HEIGHT);
            }
        }
        if (!cardStatsList.isEmpty()) {
            height += LINE_HEIGHT;
            for (StatEntry entry : cardStatsList) {
                height += HudTextUtil.statLineHeight(font, Component.literal(entry.displayName()),
                        Component.literal(CardStatsManager.formatValue(entry)), panelWidth, PADDING, LINE_HEIGHT);
            }
        }
        height += PADDING;
        return height;
    }

    public void renderInInventory(GuiGraphics graphics, int invLeftPos, int invTopPos, int invImageHeight) {
        if (!canRenderInInventory()) return;

        Minecraft client = Minecraft.getInstance();
        Font font = client.font;

        int panelWidth = getContentWidth(font);
        int panelHeight = getContentHeight(font);

        int gap = 4;
        int panelX = invLeftPos - panelWidth - gap;
        int panelY = invTopPos + (invImageHeight - panelHeight) / 2;

        panelX = Math.max(0, panelX);
        panelY = Math.max(0, panelY);

        graphics.pose().pushPose();
        graphics.pose().translate(panelX, panelY, 0);

        renderContent(graphics, font, panelWidth);

        graphics.pose().popPose();
    }
}
