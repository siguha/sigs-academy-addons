package com.siguha.sigsacademyaddons.gui;

import com.siguha.sigsacademyaddons.config.HudConfig;
import com.siguha.sigsacademyaddons.feature.cardstats.CardStatsManager;
import com.siguha.sigsacademyaddons.feature.daycare.DaycareManager;
import com.siguha.sigsacademyaddons.feature.safari.SafariHuntManager;
import com.siguha.sigsacademyaddons.feature.safari.SafariManager;
import com.siguha.sigsacademyaddons.feature.wondertrade.WondertradeManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
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
    private static final int COLOR_PEN_LABEL = 0xFF88DDFF;
    private static final int COLOR_TIMER_GREEN = 0xFF55FF55;

    private static final int UNJOIN_BUTTON_SIZE = 7;

    private static final String NO_QUESTS_MESSAGE = "Please visit the Safari Hunt NPC and load your hunt menu.";

    private static final int COLOR_OUTLINE_WT = 0xFF55FF55;
    private static final int COLOR_OUTLINE_CARDSTATS = 0xFFFFCC00;
    private static final int COLOR_JOIN_HIGHLIGHT = 0x4455FF55;
    private static final int COLOR_UNJOIN_X = 0xFFFF5555;
    private static final int COLOR_OUTLINE_GROUP = 0xFFFFAA00;
    private static final int BUTTON_PADDING_X = 6;
    private static final int BUTTON_PADDING_Y = 3;
    private static final int BUTTON_SPACING = 4;
    private static final int COLOR_BUTTON_BG = 0xCC000000;
    private static final int COLOR_BUTTON_BG_HOVER = 0xCC333333;
    private static final int COLOR_BUTTON_BORDER = 0xFFFFAA00;
    private static final int COLOR_BUTTON_TEXT = 0xFFFFFFFF;

    private static final int SAFARI_MAX_HUNTS = 6;
    private static final String[] PLACEHOLDER_PEN_SPECIES = {"Eevee", "Ralts", "Magikarp", "Ditto", "Pichu"};
    private static final String[] PLACEHOLDER_PEN_TIMERS = {"~12:34", "~8:22", "~15:01", "~3:45", "~6:10"};
    private static final float[] PLACEHOLDER_PEN_PROGRESS = {0.4f, 0.65f, 0.2f, 0.85f, 0.55f};
    private static final String[] PLACEHOLDER_EGG_NAMES = {"Eevee", "Charmander", "Bulbasaur", "Squirtle", "Pikachu"};
    private static final String[] PLACEHOLDER_EGG_TIMERS = {"~7:45", "~5:12", "~3:30", "~1:15", "~0:42"};
    private static final float[] PLACEHOLDER_EGG_PROGRESS = {0.25f, 0.45f, 0.6f, 0.8f, 0.92f};

    private final HudConfig hudConfig;
    private final SafariManager safariManager;
    private final SafariHuntManager safariHuntManager;
    private final DaycareManager daycareManager;
    private final WondertradeManager wondertradeManager;
    private final CardStatsManager cardStatsManager;

    static class PanelState {
        String panelId;
        String name;
        int unscaledWidth, unscaledHeight;
        float currentScale;
        int scaledWidth, scaledHeight;
        int panelX, panelY;

        PanelState(String panelId, String name) {
            this.panelId = panelId;
            this.name = name;
        }

        void updateScaledDimensions() {
            scaledWidth = Math.round(unscaledWidth * currentScale);
            scaledHeight = Math.round(unscaledHeight * currentScale);
        }
    }

    static class GroupState {
        List<PanelState> members = new ArrayList<>();
        float currentScale;
        int panelX, panelY;
        int maxUnscaledWidth;
        int totalUnscaledHeight;
        int scaledWidth, scaledHeight;

        void recalculate() {
            maxUnscaledWidth = 0;
            totalUnscaledHeight = 0;
            for (int i = 0; i < members.size(); i++) {
                maxUnscaledWidth = Math.max(maxUnscaledWidth, members.get(i).unscaledWidth);
                totalUnscaledHeight += members.get(i).unscaledHeight;
            }
            scaledWidth = Math.round(maxUnscaledWidth * currentScale);
            scaledHeight = Math.round(totalUnscaledHeight * currentScale);
        }
    }

    private PanelState safariPanel;
    private PanelState daycarePanel;
    private PanelState wtPanel;
    private PanelState cardStatsPanel;
    private GroupState groupState;

    private enum DragMode { NONE, MOVE, RESIZE }
    private DragMode dragMode = DragMode.NONE;
    private PanelState activePanel = null;
    private boolean draggingGroup = false;

    private int previousWidth = -1;
    private int previousHeight = -1;

    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    private int resizeAnchorX;
    private int resizeAnchorY;
    private boolean resizeFromLeft;
    private boolean resizeFromTop;

    private boolean suppressionMenuExpanded = false;

    public HudConfigScreen(HudConfig hudConfig, SafariManager safariManager,
                            SafariHuntManager safariHuntManager, DaycareManager daycareManager,
                            WondertradeManager wondertradeManager, CardStatsManager cardStatsManager) {
        super(Component.literal("HUD Position"));
        this.hudConfig = hudConfig;
        this.safariManager = safariManager;
        this.safariHuntManager = safariHuntManager;
        this.daycareManager = daycareManager;
        this.wondertradeManager = wondertradeManager;
        this.cardStatsManager = cardStatsManager;
    }

    @Override
    protected void init() {
        super.init();

        if (previousWidth > 0 && safariPanel != null) {
            int deltaX = (this.width - previousWidth) / 2;
            int deltaY = (this.height - previousHeight) / 2;

            for (PanelState panel : new PanelState[]{safariPanel, daycarePanel, wtPanel, cardStatsPanel}) {
                if (!isInGroup(panel)) {
                    panel.panelX = Math.clamp(panel.panelX + deltaX, 0,
                            Math.max(0, this.width - panel.scaledWidth));
                    panel.panelY = Math.clamp(panel.panelY + deltaY, 0,
                            Math.max(0, this.height - panel.scaledHeight));
                }
            }

            if (groupState != null) {
                groupState.panelX = Math.clamp(groupState.panelX + deltaX, 0,
                        Math.max(0, this.width - groupState.scaledWidth));
                groupState.panelY = Math.clamp(groupState.panelY + deltaY, 0,
                        Math.max(0, this.height - groupState.scaledHeight));
            }

            previousWidth = this.width;
            previousHeight = this.height;
            return;
        }

        boolean compact = hudConfig.isCompact();

        safariPanel = new PanelState("safari", "Safari");
        safariPanel.unscaledWidth = placeholderSafariWidth(compact);
        safariPanel.unscaledHeight = placeholderSafariHeight(compact);
        safariPanel.currentScale = hudConfig.getHudScale();
        safariPanel.updateScaledDimensions();
        safariPanel.panelX = hudConfig.getPanelX(this.width, safariPanel.scaledWidth);
        safariPanel.panelY = hudConfig.getPanelY(this.height, safariPanel.scaledHeight);

        daycarePanel = new PanelState("daycare", "Daycare");
        daycarePanel.unscaledWidth = placeholderDaycareWidth(compact);
        daycarePanel.unscaledHeight = placeholderDaycareHeight(compact);
        daycarePanel.currentScale = hudConfig.getDaycareScale();
        daycarePanel.updateScaledDimensions();
        daycarePanel.panelX = hudConfig.getDaycarePanelX(this.width, daycarePanel.scaledWidth);
        daycarePanel.panelY = hudConfig.getDaycarePanelY(this.height, daycarePanel.scaledHeight);

        wtPanel = new PanelState("wondertrade", "Wondertrade");
        wtPanel.unscaledWidth = placeholderWtWidth(compact);
        wtPanel.unscaledHeight = placeholderWtHeight(compact);
        wtPanel.currentScale = hudConfig.getWtScale();
        wtPanel.updateScaledDimensions();
        wtPanel.panelX = hudConfig.getWtPanelX(this.width, wtPanel.scaledWidth);
        wtPanel.panelY = hudConfig.getWtPanelY(this.height, wtPanel.scaledHeight);

        cardStatsPanel = new PanelState("cardstats", "Card Stats");
        cardStatsPanel.unscaledWidth = placeholderCardStatsWidth(compact);
        cardStatsPanel.unscaledHeight = placeholderCardStatsHeight(compact);
        cardStatsPanel.currentScale = hudConfig.getCardStatsScale();
        cardStatsPanel.updateScaledDimensions();
        cardStatsPanel.panelX = hudConfig.getCardStatsPanelX(this.width, cardStatsPanel.scaledWidth);
        cardStatsPanel.panelY = hudConfig.getCardStatsPanelY(this.height, cardStatsPanel.scaledHeight);

        groupState = null;
        List<String> groupOrder = hudConfig.getJoinedGroup();
        if (groupOrder.size() >= 2) {
            groupState = new GroupState();
            groupState.currentScale = hudConfig.getGroupScale();
            for (String id : groupOrder) {
                PanelState panel = getPanelById(id);
                if (panel != null) {
                    groupState.members.add(panel);
                }
            }
            if (groupState.members.size() >= 2) {
                groupState.recalculate();
                groupState.panelX = hudConfig.getGroupPanelX(this.width, groupState.scaledWidth);
                groupState.panelY = hudConfig.getGroupPanelY(this.height, groupState.scaledHeight);
            } else {
                groupState = null;
            }
        }

        previousWidth = this.width;
        previousHeight = this.height;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.fill(0, 0, this.width, this.height, 0x88000000);
        String title = "Drag to reposition | Drag corners to resize | Overlap to join";
        int titleWidth = this.font.width(title);
        graphics.drawString(this.font, title, (this.width - titleWidth) / 2, 10, COLOR_HEADER, true);

        String hint = "Press Escape to save and close";
        int hintWidth = this.font.width(hint);
        graphics.drawString(this.font, hint, (this.width - hintWidth) / 2, 22, COLOR_HINT, true);

        renderButtons(graphics, mouseX, mouseY);

        if (!isInGroup(safariPanel)) {
            renderPanel(graphics, safariPanel, mouseX, mouseY, COLOR_OUTLINE);
        }
        if (!isInGroup(daycarePanel)) {
            renderPanel(graphics, daycarePanel, mouseX, mouseY, COLOR_OUTLINE_DAYCARE);
        }
        if (!isInGroup(wtPanel)) {
            renderPanel(graphics, wtPanel, mouseX, mouseY, COLOR_OUTLINE_WT);
        }
        if (!isInGroup(cardStatsPanel)) {
            renderPanel(graphics, cardStatsPanel, mouseX, mouseY, COLOR_OUTLINE_CARDSTATS);
        }

        if (groupState != null && groupState.members.size() >= 2) {
            renderGroup(graphics, mouseX, mouseY);
        }

        if (dragMode == DragMode.MOVE && activePanel != null && !draggingGroup) {
            renderJoinHint(graphics, activePanel);
        }
    }

    private void renderButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        int bx = 8;
        int by = 8;

        String resetPosText = "Reset Positions";
        int resetPosW = this.font.width(resetPosText) + BUTTON_PADDING_X * 2;
        int buttonH = this.font.lineHeight + BUTTON_PADDING_Y * 2;
        boolean resetPosHovered = mouseX >= bx && mouseX <= bx + resetPosW
                && mouseY >= by && mouseY <= by + buttonH;
        graphics.fill(bx, by, bx + resetPosW, by + buttonH,
                resetPosHovered ? COLOR_BUTTON_BG_HOVER : COLOR_BUTTON_BG);
        drawOutline(graphics, bx, by, resetPosW, buttonH, COLOR_BUTTON_BORDER);
        graphics.drawString(this.font, resetPosText, bx + BUTTON_PADDING_X,
                by + BUTTON_PADDING_Y, COLOR_BUTTON_TEXT, true);

        by += buttonH + BUTTON_SPACING;

        String resetScaleText = "Reset Scale";
        int resetScaleW = this.font.width(resetScaleText) + BUTTON_PADDING_X * 2;
        boolean resetScaleHovered = mouseX >= bx && mouseX <= bx + resetScaleW
                && mouseY >= by && mouseY <= by + buttonH;
        graphics.fill(bx, by, bx + resetScaleW, by + buttonH,
                resetScaleHovered ? COLOR_BUTTON_BG_HOVER : COLOR_BUTTON_BG);
        drawOutline(graphics, bx, by, resetScaleW, buttonH, COLOR_BUTTON_BORDER);
        graphics.drawString(this.font, resetScaleText, bx + BUTTON_PADDING_X,
                by + BUTTON_PADDING_Y, COLOR_BUTTON_TEXT, true);

        by += buttonH + BUTTON_SPACING;

        String suppressText = "Suppression Rules " + (suppressionMenuExpanded ? "\u25BC" : "\u25B6");
        int suppressW = this.font.width(suppressText) + BUTTON_PADDING_X * 2;
        boolean suppressHovered = mouseX >= bx && mouseX <= bx + suppressW
                && mouseY >= by && mouseY <= by + buttonH;
        graphics.fill(bx, by, bx + suppressW, by + buttonH,
                suppressHovered ? COLOR_BUTTON_BG_HOVER : COLOR_BUTTON_BG);
        drawOutline(graphics, bx, by, suppressW, buttonH, COLOR_BUTTON_BORDER);
        graphics.drawString(this.font, suppressText, bx + BUTTON_PADDING_X,
                by + BUTTON_PADDING_Y, COLOR_BUTTON_TEXT, true);

        if (suppressionMenuExpanded) {
            by += buttonH + BUTTON_SPACING;
            int childX = bx + 4;

            String[] labels = {"Raids", "Hideouts", "Dungeons", "Battles", "Hide HUD"};
            boolean[] values = {
                    hudConfig.isSuppressInRaids(), hudConfig.isSuppressInHideouts(),
                    hudConfig.isSuppressInDungeons(), hudConfig.isSuppressInBattles(),
                    hudConfig.isHudHidden()
            };

            for (int i = 0; i < labels.length; i++) {
                boolean enabled = values[i];
                String label = labels[i] + ": " + (enabled ? "ON" : "OFF");
                int labelColor = enabled ? 0xFF55FF55 : 0xFFFF5555;
                int labelW = this.font.width(label) + BUTTON_PADDING_X * 2;
                boolean hovered = mouseX >= childX && mouseX <= childX + labelW
                        && mouseY >= by && mouseY <= by + buttonH;
                graphics.fill(childX, by, childX + labelW, by + buttonH,
                        hovered ? COLOR_BUTTON_BG_HOVER : COLOR_BUTTON_BG);
                drawOutline(graphics, childX, by, labelW, buttonH, 0xFF888888);
                graphics.drawString(this.font, label, childX + BUTTON_PADDING_X,
                        by + BUTTON_PADDING_Y, labelColor, true);
                by += buttonH + BUTTON_SPACING;
            }
        }
    }

    private void renderPanel(GuiGraphics graphics, PanelState panel, int mouseX, int mouseY, int panelColor) {
        graphics.pose().pushPose();
        graphics.pose().translate(panel.panelX, panel.panelY, 0);
        graphics.pose().scale(panel.currentScale, panel.currentScale, 1.0f);

        if (panel == safariPanel) {
            renderSafariPreview(graphics, panel);
        } else if (panel == daycarePanel) {
            renderDaycarePreview(graphics, panel);
        } else if (panel == wtPanel) {
            renderWtPreview(graphics, panel);
        } else if (panel == cardStatsPanel) {
            renderCardStatsPreview(graphics, panel);
        }

        graphics.pose().popPose();

        int outlineColor;
        if (activePanel == panel && dragMode == DragMode.MOVE) {
            outlineColor = COLOR_OUTLINE_DRAG;
        } else if (activePanel == panel && dragMode == DragMode.RESIZE) {
            outlineColor = COLOR_CORNER_HOVER;
        } else {
            outlineColor = panelColor;
        }
        drawOutline(graphics, panel.panelX, panel.panelY, panel.scaledWidth, panel.scaledHeight, outlineColor);

        String label;
        if (activePanel == panel && dragMode == DragMode.RESIZE) {
            label = panel.name + " - Scale: " + String.format("%.0f%%", panel.currentScale * 100);
        } else {
            label = panel.name;
        }
        int labelWidth = this.font.width(label);
        int labelX = panel.panelX + (panel.scaledWidth - labelWidth) / 2;
        int labelY = panel.panelY - 10;
        graphics.drawString(this.font, label, labelX, labelY, panelColor, true);

        drawCornerHandles(graphics, panel, mouseX, mouseY, panelColor);
    }

    private void drawOutline(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x - 1, y - 1, x + w + 1, y, color);
        graphics.fill(x - 1, y + h, x + w + 1, y + h + 1, color);
        graphics.fill(x - 1, y - 1, x, y + h + 1, color);
        graphics.fill(x + w, y - 1, x + w + 1, y + h + 1, color);
    }

    private void drawCornerHandles(GuiGraphics graphics, PanelState panel, int mouseX, int mouseY, int panelColor) {
        boolean anyCornerHovered = false;
        int[][] corners = {
                {panel.panelX, panel.panelY},
                {panel.panelX + panel.scaledWidth, panel.panelY},
                {panel.panelX, panel.panelY + panel.scaledHeight},
                {panel.panelX + panel.scaledWidth, panel.panelY + panel.scaledHeight}
        };
        for (int[] corner : corners) {
            if (isNearPoint(mouseX, mouseY, corner[0], corner[1], CORNER_GRAB_RADIUS)) {
                anyCornerHovered = true;
                break;
            }
        }
        int color = (anyCornerHovered || (activePanel == panel && dragMode == DragMode.RESIZE))
                ? COLOR_CORNER_HOVER : panelColor;
        drawResizeIcon(graphics, panel.panelX - 1, panel.panelY - 1, color);
    }

    private void renderGroup(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean transparent = hudConfig.getHudStyle() == HudConfig.HudStyle.TRANSPARENT;

        groupState.recalculate();

        graphics.pose().pushPose();
        graphics.pose().translate(groupState.panelX, groupState.panelY, 0);
        graphics.pose().scale(groupState.currentScale, groupState.currentScale, 1.0f);

        if (!transparent) {
            graphics.fill(0, 0, groupState.maxUnscaledWidth, groupState.totalUnscaledHeight, COLOR_BG);
        }

        int currentY = 0;
        for (int i = 0; i < groupState.members.size(); i++) {
            PanelState member = groupState.members.get(i);

            int savedWidth = member.unscaledWidth;
            member.unscaledWidth = groupState.maxUnscaledWidth;

            graphics.pose().pushPose();
            graphics.pose().translate(0, currentY, 0);
            renderPreviewContent(graphics, member);
            graphics.pose().popPose();

            member.unscaledWidth = savedWidth;

            currentY += member.unscaledHeight;
        }

        graphics.pose().popPose();

        int outlineColor;
        if (draggingGroup && dragMode == DragMode.MOVE) {
            outlineColor = COLOR_OUTLINE_DRAG;
        } else if (draggingGroup && dragMode == DragMode.RESIZE) {
            outlineColor = COLOR_CORNER_HOVER;
        } else {
            outlineColor = COLOR_OUTLINE_GROUP;
        }
        drawOutline(graphics, groupState.panelX, groupState.panelY,
                groupState.scaledWidth, groupState.scaledHeight, outlineColor);

        StringBuilder labelBuilder = new StringBuilder();
        for (int i = 0; i < groupState.members.size(); i++) {
            if (i > 0) labelBuilder.append(" + ");
            labelBuilder.append(groupState.members.get(i).name);
        }
        String label;
        if (draggingGroup && dragMode == DragMode.RESIZE) {
            label = labelBuilder + " - Scale: " + String.format("%.0f%%", groupState.currentScale * 100);
        } else {
            label = labelBuilder.toString();
        }
        int labelWidth = this.font.width(label);
        int labelX = groupState.panelX + (groupState.scaledWidth - labelWidth) / 2;
        int labelY = groupState.panelY - 10;
        graphics.drawString(this.font, label, labelX, labelY, COLOR_OUTLINE_GROUP, true);

        drawGroupCornerHandles(graphics, mouseX, mouseY);

        if (isInsideGroup(mouseX, mouseY) && dragMode == DragMode.NONE) {
            renderUnjoinButtons(graphics, mouseX, mouseY);
        }
    }

    private void renderPreviewContent(GuiGraphics graphics, PanelState panel) {
        if (panel == safariPanel) {
            if (hudConfig.isCompact()) renderCompactSafariPreview(graphics, panel);
            else renderFullSafariPreview(graphics, panel);
        } else if (panel == daycarePanel) {
            if (hudConfig.isCompact()) renderCompactDaycarePreview(graphics, panel);
            else renderFullDaycarePreview(graphics, panel);
        } else if (panel == wtPanel) {
            if (hudConfig.isCompact()) renderCompactWtPreview(graphics, panel);
            else renderFullWtPreview(graphics, panel);
        } else if (panel == cardStatsPanel) {
            if (hudConfig.isCompact()) renderCompactCardStatsPreview(graphics, panel);
            else renderFullCardStatsPreview(graphics, panel);
        }
    }

    private void renderUnjoinButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        float scale = groupState.currentScale;
        int currentY = 0;

        for (int i = 0; i < groupState.members.size(); i++) {
            currentY += groupState.members.get(i).unscaledHeight;

            int buttonCenterX = groupState.panelX + groupState.scaledWidth / 2;
            int buttonCenterY = groupState.panelY + Math.round(currentY * scale);

            graphics.fill(groupState.panelX, buttonCenterY,
                    groupState.panelX + groupState.scaledWidth, buttonCenterY + 1, COLOR_OUTLINE_GROUP);

            int hs = UNJOIN_BUTTON_SIZE / 2;
            int bx = buttonCenterX - hs;
            int by = buttonCenterY - hs;

            boolean xHovered = mouseX >= bx - 1 && mouseX <= bx + UNJOIN_BUTTON_SIZE + 1
                    && mouseY >= by - 1 && mouseY <= by + UNJOIN_BUTTON_SIZE + 1;

            int xColor = xHovered ? 0xFFFFFFFF : COLOR_UNJOIN_X;
            String xChar = "x";
            int xWidth = this.font.width(xChar);
            int textX = buttonCenterX - xWidth / 2;
            int textY = buttonCenterY - this.font.lineHeight / 2 + 1;
            graphics.drawString(this.font, xChar, textX, textY, xColor, true);

            int arrowX = groupState.panelX + groupState.scaledWidth - 12;
            if (i > 0) {
                int ay = buttonCenterY - 7;
                boolean upHovered = mouseX >= arrowX - 1 && mouseX <= arrowX + 8
                        && mouseY >= ay - 1 && mouseY <= ay + 5;
                drawUpArrow(graphics, arrowX, ay, upHovered ? 0xFFFFFFFF : COLOR_UNJOIN_X);
            }
            if (i < groupState.members.size() - 1) {
                int ay = buttonCenterY + 3;
                boolean downHovered = mouseX >= arrowX - 1 && mouseX <= arrowX + 8
                        && mouseY >= ay - 1 && mouseY <= ay + 5;
                drawDownArrow(graphics, arrowX, ay, downHovered ? 0xFFFFFFFF : COLOR_UNJOIN_X);
            }

        }
    }

    private void renderJoinHint(GuiGraphics graphics, PanelState dragged) {
        if (groupState != null) {
            int overlap = getOverlapArea(
                    dragged.panelX, dragged.panelY, dragged.scaledWidth, dragged.scaledHeight,
                    groupState.panelX, groupState.panelY, groupState.scaledWidth, groupState.scaledHeight);
            int droppedArea = dragged.scaledWidth * dragged.scaledHeight;
            if (droppedArea > 0 && (float) overlap / droppedArea > 0.3f) {
                graphics.fill(groupState.panelX, groupState.panelY,
                        groupState.panelX + groupState.scaledWidth,
                        groupState.panelY + groupState.scaledHeight,
                        COLOR_JOIN_HIGHLIGHT);
                return;
            }
        }

        PanelState[] ungrouped = getUngroupedPanels();
        for (PanelState target : ungrouped) {
            if (target == dragged) continue;
            int overlap = getOverlapArea(
                    dragged.panelX, dragged.panelY, dragged.scaledWidth, dragged.scaledHeight,
                    target.panelX, target.panelY, target.scaledWidth, target.scaledHeight);
            int smallerArea = Math.min(
                    dragged.scaledWidth * dragged.scaledHeight,
                    target.scaledWidth * target.scaledHeight);
            if (smallerArea > 0 && (float) overlap / smallerArea > 0.3f) {
                graphics.fill(target.panelX, target.panelY,
                        target.panelX + target.scaledWidth,
                        target.panelY + target.scaledHeight,
                        COLOR_JOIN_HIGHLIGHT);
                return;
            }
        }
    }

    private void drawGroupCornerHandles(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean anyCornerHovered = false;
        int[][] corners = {
                {groupState.panelX, groupState.panelY},
                {groupState.panelX + groupState.scaledWidth, groupState.panelY},
                {groupState.panelX, groupState.panelY + groupState.scaledHeight},
                {groupState.panelX + groupState.scaledWidth, groupState.panelY + groupState.scaledHeight}
        };
        for (int[] corner : corners) {
            if (isNearPoint(mouseX, mouseY, corner[0], corner[1], CORNER_GRAB_RADIUS)) {
                anyCornerHovered = true;
                break;
            }
        }
        int color = (anyCornerHovered || (draggingGroup && dragMode == DragMode.RESIZE))
                ? COLOR_CORNER_HOVER : COLOR_OUTLINE_GROUP;
        drawResizeIcon(graphics, groupState.panelX - 1, groupState.panelY - 1, color);
    }

    private void renderSafariPreview(GuiGraphics graphics, PanelState panel) {
        boolean transparent = hudConfig.getHudStyle() == HudConfig.HudStyle.TRANSPARENT;

        if (!transparent) {
            graphics.fill(0, 0, panel.unscaledWidth, panel.unscaledHeight, COLOR_BG);
        }

        if (hudConfig.isCompact()) {
            renderCompactSafariPreview(graphics, panel);
        } else {
            renderFullSafariPreview(graphics, panel);
        }
    }

    private void renderCompactSafariPreview(GuiGraphics graphics, PanelState panel) {
        int y = PADDING;

        String prefix = "Safari: ";
        graphics.drawString(this.font, prefix, PADDING, y, COLOR_HEADER, true);
        graphics.drawString(this.font, "24:31", PADDING + this.font.width(prefix), y, 0xFF55FF55, true);
        y += LINE_HEIGHT;

        String[][] placeholderHunts = {
                {"Dragon Type", "[0/30]", "- 1h 4m"},
                {"Fire Type", "[5/20]", "- 2h 15m"},
                {"Water Type", "[12/25]", "- 0h 42m"},
                {"Grass Type", "[3/15]", "- 3h 20m"},
                {"Electric Type", "[8/10]", "- 0h 18m"},
                {"Psychic Type", "[20/20]", ""}
        };
        int[] slotColors = {0xFFFF5555, 0xFF5555FF, 0xFF55FF55, 0xFFFFFF55, 0xFF55FFFF, 0xFFFF55FF};

        for (int i = 0; i < placeholderHunts.length; i++) {
            int px = PADDING;
            int nameColor = i == 5 ? COLOR_PROGRESS_COMPLETE : slotColors[i];
            graphics.drawString(this.font, placeholderHunts[i][0], px, y, nameColor, true);
            px += this.font.width(placeholderHunts[i][0]) + 4;
            int countColor = i == 5 ? COLOR_PROGRESS_COMPLETE : COLOR_HINT;
            graphics.drawString(this.font, placeholderHunts[i][1], px, y, countColor, true);
            px += this.font.width(placeholderHunts[i][1]);
            if (!placeholderHunts[i][2].isEmpty()) {
                graphics.drawString(this.font, " " + placeholderHunts[i][2], px, y, 0xFFFF8855, true);
            }
            y += LINE_HEIGHT;
        }
    }

    private void renderFullSafariPreview(GuiGraphics graphics, PanelState panel) {
        int y = PADDING;

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
        graphics.fill(PADDING, y, PADDING + barWidth, y + 1, 0xFF555555);
        y += SECTION_SPACING;

        graphics.drawString(this.font, "Active Hunts", PADDING, y, COLOR_HEADER, true);
        y += LINE_HEIGHT + 2;

        String[][] placeholderHunts = {
                {"Dragon Type", "0", "30", "1h 4m"},
                {"Fire Type", "5", "20", "2h 15m"},
                {"Water Type", "12", "25", "0h 42m"},
                {"Grass Type", "3", "15", "3h 20m"},
                {"Electric Type", "8", "10", "0h 18m"},
                {"Psychic Type", "20", "20", ""}
        };

        for (int i = 0; i < placeholderHunts.length; i++) {
            boolean complete = i == 5;
            int nameColor = complete ? COLOR_PROGRESS_COMPLETE : COLOR_TEXT;
            graphics.drawString(this.font, placeholderHunts[i][0], PADDING, y, nameColor, true);

            if (!placeholderHunts[i][3].isEmpty()) {
                int resetWidth = this.font.width(placeholderHunts[i][3]);
                graphics.drawString(this.font, placeholderHunts[i][3], panel.unscaledWidth - PADDING - resetWidth, y,
                        0xFFFF8855, true);
            }
            y += LINE_HEIGHT;

            int numberColor = complete ? COLOR_PROGRESS_COMPLETE : COLOR_QUEST_NUMBER;
            int slashColor = complete ? COLOR_PROGRESS_COMPLETE : COLOR_HINT;
            int px = PADDING + 4;
            graphics.drawString(this.font, placeholderHunts[i][1], px, y, numberColor, true);
            px += this.font.width(placeholderHunts[i][1]);
            graphics.drawString(this.font, "/", px, y, slashColor, true);
            px += this.font.width("/");
            graphics.drawString(this.font, placeholderHunts[i][2], px, y, numberColor, true);
            y += LINE_HEIGHT;
        }
    }

    private void renderDaycarePreview(GuiGraphics graphics, PanelState panel) {
        boolean transparent = hudConfig.getHudStyle() == HudConfig.HudStyle.TRANSPARENT;

        if (!transparent) {
            graphics.fill(0, 0, panel.unscaledWidth, panel.unscaledHeight, COLOR_BG);
        }

        if (hudConfig.isCompact()) {
            renderCompactDaycarePreview(graphics, panel);
        } else {
            renderFullDaycarePreview(graphics, panel);
        }
    }

    private void renderCompactDaycarePreview(GuiGraphics graphics, PanelState panel) {
        int y = PADDING;
        int penCount = getPlaceholderPenCount();
        int maxEggs = hudConfig.getDaycareEggsHatchingSlots();

        graphics.drawString(this.font, "Daycare", PADDING, y, COLOR_HEADER, true);
        y += LINE_HEIGHT;

        graphics.drawString(this.font, "Breeding", PADDING, y, COLOR_SECTION_HEADER, true);
        y += LINE_HEIGHT;

        for (int i = 0; i < penCount; i++) {
            String penLabel = "[PEN " + (i + 1) + "]";
            graphics.drawString(this.font, penLabel, PADDING + 2, y, COLOR_PEN_LABEL, true);
            int textX = PADDING + 2 + this.font.width(penLabel) + 4;

            String species = PLACEHOLDER_PEN_SPECIES[i % PLACEHOLDER_PEN_SPECIES.length];
            String timer = PLACEHOLDER_PEN_TIMERS[i % PLACEHOLDER_PEN_TIMERS.length];
            graphics.drawString(this.font, species, textX, y, COLOR_TEXT, true);
            int tw = this.font.width(timer);
            graphics.drawString(this.font, timer, panel.unscaledWidth - PADDING - tw, y, COLOR_TIMER_GREEN, true);
            y += LINE_HEIGHT;
        }

        if (maxEggs > 0) {
            graphics.drawString(this.font, "Hatching", PADDING, y, COLOR_SECTION_HEADER, true);
            y += LINE_HEIGHT;

            for (int i = 0; i < maxEggs; i++) {
                String eggName = PLACEHOLDER_EGG_NAMES[i % PLACEHOLDER_EGG_NAMES.length];
                String eggTimer = PLACEHOLDER_EGG_TIMERS[i % PLACEHOLDER_EGG_TIMERS.length];
                graphics.drawString(this.font, eggName, PADDING + 2, y, COLOR_TEXT, true);
                int tw = this.font.width(eggTimer);
                graphics.drawString(this.font, eggTimer, panel.unscaledWidth - PADDING - tw, y, COLOR_TIMER_GREEN, true);
                y += LINE_HEIGHT;
            }

            graphics.drawString(this.font, "(+1 more)", PADDING + 2, y, COLOR_HINT, true);
        }
    }

    private void renderFullDaycarePreview(GuiGraphics graphics, PanelState panel) {
        int y = PADDING;
        int penCount = getPlaceholderPenCount();
        int maxEggs = hudConfig.getDaycareEggsHatchingSlots();

        String dcHeader = "SAA Daycare Helper";
        int dcHeaderW = this.font.width(dcHeader);
        graphics.drawString(this.font, dcHeader, (panel.unscaledWidth - dcHeaderW) / 2, y, COLOR_HEADER, true);
        y += LINE_HEIGHT;

        int barX = PADDING + 2;
        int barWidth = panel.unscaledWidth - barX - PADDING;

        y += 2;
        graphics.fill(PADDING, y, panel.unscaledWidth - PADDING, y + 1, 0xFF555555);
        y += SECTION_SPACING;
        graphics.drawString(this.font, "Breeding", PADDING, y, COLOR_SECTION_HEADER, true);
        y += LINE_HEIGHT;

        for (int i = 0; i < penCount; i++) {
            String penLabel = "[PEN " + (i + 1) + "]";
            String species = PLACEHOLDER_PEN_SPECIES[i % PLACEHOLDER_PEN_SPECIES.length];
            String timer = PLACEHOLDER_PEN_TIMERS[i % PLACEHOLDER_PEN_TIMERS.length];
            float progress = PLACEHOLDER_PEN_PROGRESS[i % PLACEHOLDER_PEN_PROGRESS.length];

            graphics.drawString(this.font, penLabel, PADDING + 2, y, COLOR_PEN_LABEL, true);
            int speciesX = PADDING + 2 + this.font.width(penLabel) + 4;
            graphics.drawString(this.font, species, speciesX, y, COLOR_TEXT, true);
            int tw = this.font.width(timer);
            graphics.drawString(this.font, timer, panel.unscaledWidth - PADDING - tw, y, COLOR_TIMER_GREEN, true);
            y += LINE_HEIGHT;
            graphics.fill(barX, y, barX + barWidth, y + 6, COLOR_TIMER_BAR_BG);
            graphics.fill(barX, y, barX + (int)(barWidth * progress), y + 6, COLOR_TIMER_GREEN);
            y += 8;
        }

        if (maxEggs > 0) {
            y += 2;
            graphics.fill(PADDING, y, panel.unscaledWidth - PADDING, y + 1, 0xFF555555);
            y += SECTION_SPACING;
            graphics.drawString(this.font, "Hatching", PADDING, y, COLOR_SECTION_HEADER, true);
            y += LINE_HEIGHT;

            for (int i = 0; i < maxEggs; i++) {
                String eggName = PLACEHOLDER_EGG_NAMES[i % PLACEHOLDER_EGG_NAMES.length];
                String eggTimer = PLACEHOLDER_EGG_TIMERS[i % PLACEHOLDER_EGG_TIMERS.length];
                float progress = PLACEHOLDER_EGG_PROGRESS[i % PLACEHOLDER_EGG_PROGRESS.length];

                graphics.drawString(this.font, eggName, PADDING + 2, y, COLOR_TEXT, true);
                int tw = this.font.width(eggTimer);
                graphics.drawString(this.font, eggTimer, panel.unscaledWidth - PADDING - tw, y, COLOR_TIMER_GREEN, true);
                y += LINE_HEIGHT;

                graphics.fill(barX, y, barX + barWidth, y + 6, COLOR_TIMER_BAR_BG);
                graphics.fill(barX, y, barX + (int)(barWidth * progress), y + 6, COLOR_TIMER_GREEN);
                y += 8;
            }

            graphics.drawString(this.font, "(+1 more)", PADDING + 2, y, COLOR_HINT, true);
        }
    }

    private void renderWtPreview(GuiGraphics graphics, PanelState panel) {
        boolean transparent = hudConfig.getHudStyle() == HudConfig.HudStyle.TRANSPARENT;

        if (!transparent) {
            graphics.fill(0, 0, panel.unscaledWidth, panel.unscaledHeight, COLOR_BG);
        }

        if (hudConfig.isCompact()) {
            renderCompactWtPreview(graphics, panel);
        } else {
            renderFullWtPreview(graphics, panel);
        }
    }

    private void renderCompactWtPreview(GuiGraphics graphics, PanelState panel) {
        int y = PADDING;
        String prefix = "WT Time: ";
        graphics.drawString(this.font, prefix, PADDING, y, COLOR_HEADER, true);
        int textX = PADDING + this.font.width(prefix);
        graphics.drawString(this.font, "42:15", textX, y, 0xFF55FF55, true);
    }

    private void renderFullWtPreview(GuiGraphics graphics, PanelState panel) {
        int y = PADDING;

        String header = "SAA Wondertrade Helper";
        int headerW = this.font.width(header);
        graphics.drawString(this.font, header, (panel.unscaledWidth - headerW) / 2, y, COLOR_HEADER, true);
        y += LINE_HEIGHT;

        y += 2;
        graphics.fill(PADDING, y, panel.unscaledWidth - PADDING, y + 1, 0xFF555555);
        y += SECTION_SPACING;

        String timerText = "42:15";
        int timerW = this.font.width(timerText);
        graphics.drawString(this.font, timerText, (panel.unscaledWidth - timerW) / 2, y, 0xFF55FF55, true);
        y += LINE_HEIGHT;

        int barX = PADDING + 2;
        int barWidth = panel.unscaledWidth - barX - PADDING;
        graphics.fill(barX, y, barX + barWidth, y + 6, COLOR_TIMER_BAR_BG);
        graphics.fill(barX, y, barX + (int) (barWidth * 0.3f), y + 6, 0xFF55FF55);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int mx = (int) mouseX;
        int my = (int) mouseY;

        int buttonClickResult = getButtonClick(mx, my);
        if (buttonClickResult == 0) {
            resetPositions();
            return true;
        } else if (buttonClickResult == 1) {
            resetScales();
            return true;
        } else if (buttonClickResult == 2) {
            suppressionMenuExpanded = !suppressionMenuExpanded;
            return true;
        } else if (buttonClickResult == 3) {
            hudConfig.setSuppressInRaids(!hudConfig.isSuppressInRaids());
            return true;
        } else if (buttonClickResult == 4) {
            hudConfig.setSuppressInHideouts(!hudConfig.isSuppressInHideouts());
            return true;
        } else if (buttonClickResult == 5) {
            hudConfig.setSuppressInDungeons(!hudConfig.isSuppressInDungeons());
            return true;
        } else if (buttonClickResult == 6) {
            hudConfig.setSuppressInBattles(!hudConfig.isSuppressInBattles());
            return true;
        } else if (buttonClickResult == 7) {
            hudConfig.setHudHidden(!hudConfig.isHudHidden());
            return true;
        }

        if (groupState != null && isInsideGroup(mx, my) && dragMode == DragMode.NONE) {
            int[] arrowHit = getReorderArrowHit(mx, my);
            if (arrowHit != null) {
                int idx = arrowHit[0];
                int swapIdx = idx + arrowHit[1];
                if (swapIdx >= 0 && swapIdx < groupState.members.size()) {
                    PanelState temp = groupState.members.get(idx);
                    groupState.members.set(idx, groupState.members.get(swapIdx));
                    groupState.members.set(swapIdx, temp);
                    groupState.recalculate();
                }
                return true;
            }
            int clickedUnjoinIndex = getUnjoinButtonIndex(mx, my);
            if (clickedUnjoinIndex >= 0) {
                unjoinPanel(clickedUnjoinIndex);
                return true;
            }
        }

        if (groupState != null) {
            CornerHit groupCorner = getGroupCornerHit(mx, my);
            if (groupCorner != null) {
                startGroupResize(groupCorner);
                return true;
            }
            if (isInsideGroup(mx, my)) {
                startGroupMove(mx, my);
                return true;
            }
        }

        PanelState[] ungrouped = getUngroupedPanels();
        for (PanelState panel : ungrouped) {
            CornerHit corner = getCornerHit(panel, mx, my);
            if (corner != null) {
                startResize(panel, corner);
                return true;
            }
        }
        for (PanelState panel : ungrouped) {
            if (isInsidePanel(panel, mx, my)) {
                startMove(panel, mx, my);
                return true;
            }
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
        draggingGroup = false;
        dragOffsetX = mx - panel.panelX;
        dragOffsetY = my - panel.panelY;
    }

    private void startGroupMove(int mx, int my) {
        dragMode = DragMode.MOVE;
        draggingGroup = true;
        activePanel = null;
        dragOffsetX = mx - groupState.panelX;
        dragOffsetY = my - groupState.panelY;
    }

    private void startGroupResize(CornerHit corner) {
        dragMode = DragMode.RESIZE;
        draggingGroup = true;
        activePanel = null;
        resizeFromLeft = corner.fromLeft;
        resizeFromTop = corner.fromTop;
        resizeAnchorX = corner.fromLeft ? (groupState.panelX + groupState.scaledWidth) : groupState.panelX;
        resizeAnchorY = corner.fromTop ? (groupState.panelY + groupState.scaledHeight) : groupState.panelY;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragMode != DragMode.NONE) {
            if (dragMode == DragMode.MOVE && activePanel != null && !draggingGroup) {
                checkJoinOnRelease(activePanel);
            }
            dragMode = DragMode.NONE;
            activePanel = null;
            draggingGroup = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button != 0) return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);

        int mx = (int) mouseX;
        int my = (int) mouseY;

        if (dragMode == DragMode.MOVE && draggingGroup && groupState != null) {
            groupState.panelX = Math.clamp(mx - dragOffsetX, 0,
                    Math.max(0, this.width - groupState.scaledWidth));
            groupState.panelY = Math.clamp(my - dragOffsetY, 0,
                    Math.max(0, this.height - groupState.scaledHeight));
            return true;
        }

        if (dragMode == DragMode.RESIZE && draggingGroup && groupState != null) {
            float distX = resizeFromLeft ? resizeAnchorX - mx : mx - resizeAnchorX;
            float newScale = distX / groupState.maxUnscaledWidth;
            float maxFitScale = Math.min(
                    (float) this.width / groupState.maxUnscaledWidth,
                    (float) this.height / groupState.totalUnscaledHeight);
            newScale = Math.clamp(newScale, MIN_SCALE, Math.min(MAX_SCALE, maxFitScale));

            groupState.currentScale = newScale;
            groupState.scaledWidth = Math.round(groupState.maxUnscaledWidth * newScale);
            groupState.scaledHeight = Math.round(groupState.totalUnscaledHeight * newScale);

            groupState.panelX = resizeFromLeft ? resizeAnchorX - groupState.scaledWidth : resizeAnchorX;
            groupState.panelY = resizeFromTop ? resizeAnchorY - groupState.scaledHeight : resizeAnchorY;

            groupState.panelX = Math.clamp(groupState.panelX, 0,
                    Math.max(0, this.width - groupState.scaledWidth));
            groupState.panelY = Math.clamp(groupState.panelY, 0,
                    Math.max(0, this.height - groupState.scaledHeight));
            return true;
        }

        if (dragMode == DragMode.MOVE && activePanel != null) {
            activePanel.panelX = Math.clamp(mx - dragOffsetX, 0,
                    Math.max(0, this.width - activePanel.scaledWidth));
            activePanel.panelY = Math.clamp(my - dragOffsetY, 0,
                    Math.max(0, this.height - activePanel.scaledHeight));
            return true;
        }

        if (dragMode == DragMode.RESIZE && activePanel != null) {
            float distX = resizeFromLeft ? resizeAnchorX - mx : mx - resizeAnchorX;
            float newScale = distX / activePanel.unscaledWidth;
            float maxFitScale = Math.min(
                    (float) this.width / activePanel.unscaledWidth,
                    (float) this.height / activePanel.unscaledHeight);
            newScale = Math.clamp(newScale, MIN_SCALE, Math.min(MAX_SCALE, maxFitScale));

            activePanel.currentScale = newScale;
            activePanel.updateScaledDimensions();

            activePanel.panelX = resizeFromLeft ? resizeAnchorX - activePanel.scaledWidth : resizeAnchorX;
            activePanel.panelY = resizeFromTop ? resizeAnchorY - activePanel.scaledHeight : resizeAnchorY;

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
        if (!isInGroup(safariPanel)) {
            hudConfig.setHudScale(safariPanel.currentScale);
            hudConfig.setPositionFromAbsolute(safariPanel.panelX, safariPanel.panelY,
                    safariPanel.scaledWidth, safariPanel.scaledHeight, this.width, this.height);
        }

        if (!isInGroup(daycarePanel)) {
            hudConfig.setDaycareScale(daycarePanel.currentScale);
            hudConfig.setDaycarePositionFromAbsolute(daycarePanel.panelX, daycarePanel.panelY,
                    daycarePanel.scaledWidth, daycarePanel.scaledHeight, this.width, this.height);
        }

        if (!isInGroup(wtPanel)) {
            hudConfig.setWtScale(wtPanel.currentScale);
            hudConfig.setWtPositionFromAbsolute(wtPanel.panelX, wtPanel.panelY,
                    wtPanel.scaledWidth, wtPanel.scaledHeight, this.width, this.height);
        }

        if (!isInGroup(cardStatsPanel)) {
            hudConfig.setCardStatsScale(cardStatsPanel.currentScale);
            hudConfig.setCardStatsPositionFromAbsolute(cardStatsPanel.panelX, cardStatsPanel.panelY,
                    cardStatsPanel.scaledWidth, cardStatsPanel.scaledHeight, this.width, this.height);
        }

        if (groupState != null && groupState.members.size() >= 2) {
            List<String> groupOrder = new ArrayList<>();
            for (PanelState member : groupState.members) {
                groupOrder.add(member.panelId);
            }
            hudConfig.setJoinedGroup(groupOrder);
            hudConfig.setGroupScale(groupState.currentScale);
            hudConfig.setGroupPositionFromAbsolute(groupState.panelX, groupState.panelY,
                    groupState.scaledWidth, groupState.scaledHeight, this.width, this.height);
        } else {
            hudConfig.setJoinedGroup(Collections.emptyList());
        }

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

    private boolean isInsideGroup(int mx, int my) {
        return groupState != null
                && mx >= groupState.panelX && mx <= groupState.panelX + groupState.scaledWidth
                && my >= groupState.panelY && my <= groupState.panelY + groupState.scaledHeight;
    }

    private boolean isInGroup(PanelState panel) {
        return groupState != null && groupState.members.contains(panel);
    }

    private PanelState getPanelById(String id) {
        return switch (id) {
            case "safari" -> safariPanel;
            case "daycare" -> daycarePanel;
            case "wondertrade" -> wtPanel;
            case "cardstats" -> cardStatsPanel;
            default -> null;
        };
    }

    private PanelState[] getUngroupedPanels() {
        List<PanelState> result = new ArrayList<>();
        if (!isInGroup(safariPanel)) result.add(safariPanel);
        if (!isInGroup(daycarePanel)) result.add(daycarePanel);
        if (!isInGroup(wtPanel)) result.add(wtPanel);
        if (!isInGroup(cardStatsPanel)) result.add(cardStatsPanel);
        return result.toArray(new PanelState[0]);
    }

    private CornerHit getGroupCornerHit(int mx, int my) {
        if (isNearPoint(mx, my, groupState.panelX, groupState.panelY, CORNER_GRAB_RADIUS))
            return new CornerHit(true, true);
        if (isNearPoint(mx, my, groupState.panelX + groupState.scaledWidth, groupState.panelY, CORNER_GRAB_RADIUS))
            return new CornerHit(false, true);
        if (isNearPoint(mx, my, groupState.panelX, groupState.panelY + groupState.scaledHeight, CORNER_GRAB_RADIUS))
            return new CornerHit(true, false);
        if (isNearPoint(mx, my, groupState.panelX + groupState.scaledWidth, groupState.panelY + groupState.scaledHeight, CORNER_GRAB_RADIUS))
            return new CornerHit(false, false);
        return null;
    }

    private int getOverlapArea(int x1, int y1, int w1, int h1, int x2, int y2, int w2, int h2) {
        int overlapX = Math.max(0, Math.min(x1 + w1, x2 + w2) - Math.max(x1, x2));
        int overlapY = Math.max(0, Math.min(y1 + h1, y2 + h2) - Math.max(y1, y2));
        return overlapX * overlapY;
    }

    private int getUnjoinButtonIndex(int mx, int my) {
        float scale = groupState.currentScale;
        int currentY = 0;

        for (int i = 0; i < groupState.members.size(); i++) {
            currentY += groupState.members.get(i).unscaledHeight;

            int buttonCenterX = groupState.panelX + groupState.scaledWidth / 2;
            int buttonCenterY = groupState.panelY + Math.round(currentY * scale);

            int hs = UNJOIN_BUTTON_SIZE / 2 + 1;
            if (Math.abs(mx - buttonCenterX) <= hs && Math.abs(my - buttonCenterY) <= hs) {
                return i;
            }
        }
        return -1;
    }

    private int[] getReorderArrowHit(int mx, int my) {
        float scale = groupState.currentScale;
        int currentY = 0;

        for (int i = 0; i < groupState.members.size(); i++) {
            currentY += groupState.members.get(i).unscaledHeight;

            int buttonCenterY = groupState.panelY + Math.round(currentY * scale);
            int arrowX = groupState.panelX + groupState.scaledWidth - 12;

            if (i > 0) {
                int ay = buttonCenterY - 7;
                if (mx >= arrowX - 1 && mx <= arrowX + 8 && my >= ay - 1 && my <= ay + 5) {
                    return new int[]{i, -1};
                }
            }
            if (i < groupState.members.size() - 1) {
                int ay = buttonCenterY + 3;
                if (mx >= arrowX - 1 && mx <= arrowX + 8 && my >= ay - 1 && my <= ay + 5) {
                    return new int[]{i, 1};
                }
            }
        }
        return null;
    }

    private void checkJoinOnRelease(PanelState dropped) {
        if (groupState != null) {
            int overlap = getOverlapArea(
                    dropped.panelX, dropped.panelY, dropped.scaledWidth, dropped.scaledHeight,
                    groupState.panelX, groupState.panelY, groupState.scaledWidth, groupState.scaledHeight);
            int droppedArea = dropped.scaledWidth * dropped.scaledHeight;
            if (droppedArea > 0 && (float) overlap / droppedArea > 0.3f) {
                addToGroup(dropped);
                return;
            }
        }

        PanelState[] ungrouped = getUngroupedPanels();
        for (PanelState target : ungrouped) {
            if (target == dropped) continue;
            int overlap = getOverlapArea(
                    dropped.panelX, dropped.panelY, dropped.scaledWidth, dropped.scaledHeight,
                    target.panelX, target.panelY, target.scaledWidth, target.scaledHeight);
            int smallerArea = Math.min(
                    dropped.scaledWidth * dropped.scaledHeight,
                    target.scaledWidth * target.scaledHeight);
            if (smallerArea > 0 && (float) overlap / smallerArea > 0.3f) {
                joinPanels(dropped, target);
                return;
            }
        }
    }

    private void joinPanels(PanelState dropped, PanelState target) {
        boolean compact = hudConfig.isCompact();

        int droppedCenterY = dropped.panelY + dropped.scaledHeight / 2;
        int targetCenterY = target.panelY + target.scaledHeight / 2;

        groupState = new GroupState();
        groupState.currentScale = target.currentScale;

        if (droppedCenterY < targetCenterY) {
            groupState.members.add(dropped);
            groupState.members.add(target);
        } else {
            groupState.members.add(target);
            groupState.members.add(dropped);
        }

        dropped.currentScale = target.currentScale;
        dropped.updateScaledDimensions();
        target.updateScaledDimensions();

        groupState.recalculate();
        groupState.panelX = target.panelX;
        groupState.panelY = target.panelY;

        groupState.panelX = Math.clamp(groupState.panelX, 0, Math.max(0, this.width - groupState.scaledWidth));
        groupState.panelY = Math.clamp(groupState.panelY, 0, Math.max(0, this.height - groupState.scaledHeight));
    }

    private void addToGroup(PanelState dropped) {
        boolean compact = hudConfig.isCompact();

        dropped.currentScale = groupState.currentScale;
        dropped.updateScaledDimensions();

        int droppedCenterY = dropped.panelY + dropped.scaledHeight / 2;
        int groupCenterY = groupState.panelY + groupState.scaledHeight / 2;

        if (droppedCenterY < groupCenterY) {
            groupState.members.add(0, dropped);
        } else {
            groupState.members.add(dropped);
        }

        groupState.recalculate();

        groupState.panelX = Math.clamp(groupState.panelX, 0, Math.max(0, this.width - groupState.scaledWidth));
        groupState.panelY = Math.clamp(groupState.panelY, 0, Math.max(0, this.height - groupState.scaledHeight));
    }

    private void unjoinPanel(int memberIndex) {
        boolean compact = hudConfig.isCompact();
        PanelState removed = groupState.members.remove(memberIndex);
        float prevGroupScale = groupState.currentScale;
        int prevGroupX = groupState.panelX;
        int prevGroupY = groupState.panelY;
        int prevGroupWidth = groupState.scaledWidth;

        if (groupState.members.size() <= 1) {
            if (!groupState.members.isEmpty()) {
                PanelState remaining = groupState.members.get(0);
                remaining.currentScale = prevGroupScale;
                remaining.updateScaledDimensions();
                remaining.panelX = prevGroupX;
                remaining.panelY = prevGroupY;
            }
            groupState = null;
        } else {
            groupState.recalculate();
        }

        removed.currentScale = prevGroupScale;
        removed.updateScaledDimensions();
        int offsetX = prevGroupX + prevGroupWidth + 10;
        removed.panelX = Math.clamp(offsetX, 0, Math.max(0, this.width - removed.scaledWidth));
        removed.panelY = Math.clamp(prevGroupY, 0, Math.max(0, this.height - removed.scaledHeight));
    }

    private int getButtonClick(int mx, int my) {
        int bx = 8;
        int by = 8;
        int buttonH = this.font.lineHeight + BUTTON_PADDING_Y * 2;

        int resetPosW = this.font.width("Reset Positions") + BUTTON_PADDING_X * 2;
        if (mx >= bx && mx <= bx + resetPosW && my >= by && my <= by + buttonH) {
            return 0;
        }

        by += buttonH + BUTTON_SPACING;
        int resetScaleW = this.font.width("Reset Scale") + BUTTON_PADDING_X * 2;
        if (mx >= bx && mx <= bx + resetScaleW && my >= by && my <= by + buttonH) {
            return 1;
        }

        by += buttonH + BUTTON_SPACING;
        String suppressText = "Suppression Rules " + (suppressionMenuExpanded ? "\u25BC" : "\u25B6");
        int suppressW = this.font.width(suppressText) + BUTTON_PADDING_X * 2;
        if (mx >= bx && mx <= bx + suppressW && my >= by && my <= by + buttonH) {
            return 2;
        }

        if (suppressionMenuExpanded) {
            by += buttonH + BUTTON_SPACING;
            int childX = bx + 4;
            String[] labels = {"Raids", "Hideouts", "Dungeons", "Battles", "Hide HUD"};
            boolean[] values = {
                    hudConfig.isSuppressInRaids(), hudConfig.isSuppressInHideouts(),
                    hudConfig.isSuppressInDungeons(), hudConfig.isSuppressInBattles(),
                    hudConfig.isHudHidden()
            };
            for (int i = 0; i < labels.length; i++) {
                String label = labels[i] + ": " + (values[i] ? "ON" : "OFF");
                int labelW = this.font.width(label) + BUTTON_PADDING_X * 2;
                if (mx >= childX && mx <= childX + labelW && my >= by && my <= by + buttonH) {
                    return 3 + i;
                }
                by += buttonH + BUTTON_SPACING;
            }
        }

        return -1;
    }

    private void resetPositions() {
        groupState = null;

        safariPanel.currentScale = 1.0f;
        safariPanel.updateScaledDimensions();
        safariPanel.panelX = this.width - safariPanel.scaledWidth - 5;
        safariPanel.panelY = 5;

        daycarePanel.currentScale = 1.0f;
        daycarePanel.updateScaledDimensions();
        daycarePanel.panelX = 5;
        daycarePanel.panelY = 5;

        wtPanel.currentScale = 1.0f;
        wtPanel.updateScaledDimensions();
        wtPanel.panelX = this.width - wtPanel.scaledWidth - 5;
        wtPanel.panelY = this.height - wtPanel.scaledHeight - 5;

        cardStatsPanel.currentScale = 1.0f;
        cardStatsPanel.updateScaledDimensions();
        cardStatsPanel.panelX = 5;
        cardStatsPanel.panelY = this.height - cardStatsPanel.scaledHeight - 5;
    }

    private void resetScales() {
        if (groupState != null) {
            groupState.currentScale = 1.0f;
            groupState.recalculate();
        }

        for (PanelState panel : new PanelState[]{safariPanel, daycarePanel, wtPanel, cardStatsPanel}) {
            if (!isInGroup(panel)) {
                panel.currentScale = 1.0f;
                panel.updateScaledDimensions();
            }
        }
    }

    private void renderCardStatsPreview(GuiGraphics graphics, PanelState panel) {
        boolean transparent = hudConfig.getHudStyle() == HudConfig.HudStyle.TRANSPARENT;

        if (!transparent) {
            graphics.fill(0, 0, panel.unscaledWidth, panel.unscaledHeight, COLOR_BG);
        }

        if (hudConfig.isCompact()) {
            renderCompactCardStatsPreview(graphics, panel);
        } else {
            renderFullCardStatsPreview(graphics, panel);
        }
    }

    private void renderCompactCardStatsPreview(GuiGraphics graphics, PanelState panel) {
        int y = PADDING;

        String title = "Stats";
        graphics.drawString(this.font, title, PADDING, y, COLOR_HEADER, true);
        y += LINE_HEIGHT;

        graphics.drawString(this.font, "Player", PADDING, y, COLOR_SECTION_HEADER, true);
        y += LINE_HEIGHT;

        String[][] playerStats = {
                {"Movement Speed", "+13.6%"},
                {"Block Reach", "+0.45"},
                {"Armor", "+2%"},
        };
        for (String[] stat : playerStats) {
            graphics.drawString(this.font, stat[0], PADDING + 2, y, COLOR_TEXT, true);
            int valW = this.font.width(stat[1]);
            graphics.drawString(this.font, stat[1], panel.unscaledWidth - PADDING - valW, y, 0xFF55FF55, true);
            y += LINE_HEIGHT;
        }

        graphics.drawString(this.font, "Cards", PADDING, y, COLOR_SECTION_HEADER, true);
        y += LINE_HEIGHT;

        String[][] cardStats = {
                {"Capture XP", "+5%"},
                {"Shiny Chance", "+2%"},
                {"Type Spawn Chance", "+3%"},
        };
        for (String[] stat : cardStats) {
            graphics.drawString(this.font, stat[0], PADDING + 2, y, COLOR_TEXT, true);
            int valW = this.font.width(stat[1]);
            graphics.drawString(this.font, stat[1], panel.unscaledWidth - PADDING - valW, y, 0xFF55FF55, true);
            y += LINE_HEIGHT;
        }
    }

    private void renderFullCardStatsPreview(GuiGraphics graphics, PanelState panel) {
        int y = PADDING;

        String header = "SAA Stats";
        int headerW = this.font.width(header);
        graphics.drawString(this.font, header, (panel.unscaledWidth - headerW) / 2, y, COLOR_HEADER, true);
        y += LINE_HEIGHT;

        y += 2;
        graphics.fill(PADDING, y, panel.unscaledWidth - PADDING, y + 1, 0xFF555555);
        y += SECTION_SPACING;

        graphics.drawString(this.font, "Player", PADDING, y, COLOR_SECTION_HEADER, true);
        y += LINE_HEIGHT;

        String[][] playerStats = {
                {"Movement Speed", "+13.6%"},
                {"Block Reach", "+0.45"},
                {"Armor", "+2%"},
        };
        for (String[] stat : playerStats) {
            graphics.drawString(this.font, stat[0], PADDING + 2, y, COLOR_TEXT, true);
            int valW = this.font.width(stat[1]);
            graphics.drawString(this.font, stat[1], panel.unscaledWidth - PADDING - valW, y, 0xFF55FF55, true);
            y += LINE_HEIGHT;
        }

        y += 2;
        graphics.fill(PADDING, y, panel.unscaledWidth - PADDING, y + 1, 0xFF555555);
        y += SECTION_SPACING;

        graphics.drawString(this.font, "Cards", PADDING, y, COLOR_SECTION_HEADER, true);
        y += LINE_HEIGHT;

        String[][] cardStats = {
                {"Capture XP", "+5%"},
                {"Shiny Chance", "+2%"},
                {"Type Spawn Chance", "+3%"},
        };
        for (String[] stat : cardStats) {
            graphics.drawString(this.font, stat[0], PADDING + 2, y, COLOR_TEXT, true);
            int valW = this.font.width(stat[1]);
            graphics.drawString(this.font, stat[1], panel.unscaledWidth - PADDING - valW, y, 0xFF55FF55, true);
            y += LINE_HEIGHT;
        }
    }

    private int getPlaceholderPenCount() {
        int actual = daycareManager.getDisplayPens().size();
        return Math.max(actual, 2);
    }

    private int placeholderSafariWidth(boolean compact) {
        if (compact) {
            int maxWidth = PANEL_MIN_WIDTH;
            maxWidth = Math.max(maxWidth, this.font.width("Safari: ") + this.font.width("24:31") + PADDING * 2);
            maxWidth = Math.max(maxWidth, this.font.width("Electric Type") + 4
                    + this.font.width("[12/25]") + this.font.width(" - 3h 20m") + PADDING * 2);
            return maxWidth + PADDING * 2;
        } else {
            int maxWidth = PANEL_MIN_WIDTH;
            maxWidth = Math.max(maxWidth, this.font.width("SAA Safari Helper") + PADDING * 2);
            maxWidth = Math.max(maxWidth, this.font.width("Electric Type") + 8
                    + this.font.width("3h 20m") + PADDING * 2);
            return maxWidth + PADDING * 2;
        }
    }

    private int placeholderSafariHeight(boolean compact) {
        if (compact) {
            return PADDING + LINE_HEIGHT + SAFARI_MAX_HUNTS * LINE_HEIGHT + PADDING;
        } else {
            int height = PADDING;
            height += LINE_HEIGHT + TIMER_BAR_HEIGHT;
            height += SECTION_SPACING * 2 + 1;
            height += LINE_HEIGHT + 2;
            height += SAFARI_MAX_HUNTS * (LINE_HEIGHT * 2);
            height += PADDING;
            return height;
        }
    }

    private int placeholderDaycareWidth(boolean compact) {
        int penCount = getPlaceholderPenCount();
        String widestPenLabel = "[PEN " + penCount + "]";

        if (compact) {
            int maxWidth = this.font.width("Daycare") + PADDING * 2;
            maxWidth = Math.max(maxWidth, this.font.width(widestPenLabel) + 4
                    + this.font.width("Eevee") + 8 + this.font.width("~12:34") + PADDING * 2 + 4);
            maxWidth = Math.max(maxWidth, this.font.width("Charmander") + this.font.width("~5:12") + PADDING * 2 + 8);
            return maxWidth + PADDING * 2;
        } else {
            int maxWidth = PANEL_MIN_WIDTH;
            maxWidth = Math.max(maxWidth, this.font.width("SAA Daycare Helper") + PADDING * 2);
            maxWidth = Math.max(maxWidth, this.font.width(widestPenLabel) + 4
                    + this.font.width("Eevee") + 8 + this.font.width("~12:34") + PADDING * 2 + 4);
            maxWidth = Math.max(maxWidth, this.font.width("Charmander") + this.font.width("~5:12") + PADDING * 2 + 8);
            return maxWidth + PADDING * 2;
        }
    }

    private int placeholderDaycareHeight(boolean compact) {
        int maxEggs = hudConfig.getDaycareEggsHatchingSlots();
        int penCount = getPlaceholderPenCount();

        if (compact) {
            int height = PADDING;
            height += LINE_HEIGHT;
            height += LINE_HEIGHT;
            height += penCount * LINE_HEIGHT;
            if (maxEggs > 0) {
                height += LINE_HEIGHT;
                height += maxEggs * LINE_HEIGHT;
                height += LINE_HEIGHT;
            }
            height += PADDING;
            return height;
        } else {
            int height = PADDING;
            height += LINE_HEIGHT;
            height += 2 + SECTION_SPACING;
            height += LINE_HEIGHT;
            height += penCount * (LINE_HEIGHT + 8);
            if (maxEggs > 0) {
                height += 2 + SECTION_SPACING;
                height += LINE_HEIGHT;
                height += maxEggs * (LINE_HEIGHT + 8);
                height += LINE_HEIGHT;
            }
            height += PADDING;
            return height;
        }
    }

    private int placeholderWtWidth(boolean compact) {
        if (compact) {
            return this.font.width("WT Time: ") + this.font.width("42:15") + PADDING * 2;
        } else {
            int maxWidth = PANEL_MIN_WIDTH;
            maxWidth = Math.max(maxWidth, this.font.width("SAA Wondertrade Helper") + PADDING * 2);
            maxWidth = Math.max(maxWidth, this.font.width("Please use WT once to set menu.") + PADDING * 2);
            return maxWidth + PADDING * 2;
        }
    }

    private int placeholderWtHeight(boolean compact) {
        if (compact) {
            return PADDING + LINE_HEIGHT + PADDING;
        } else {
            return PADDING + LINE_HEIGHT + 2 + SECTION_SPACING + LINE_HEIGHT + 6 + PADDING;
        }
    }

    private int placeholderCardStatsWidth(boolean compact) {
        if (compact) {
            int maxWidth = PANEL_MIN_WIDTH;
            maxWidth = Math.max(maxWidth, this.font.width("Type Spawn Chance") + this.font.width("+3%") + PADDING * 2 + 8);
            return maxWidth + PADDING * 2;
        } else {
            int maxWidth = PANEL_MIN_WIDTH;
            maxWidth = Math.max(maxWidth, this.font.width("SAA Stats") + PADDING * 2);
            maxWidth = Math.max(maxWidth, this.font.width("Type Spawn Chance") + this.font.width("+3%") + PADDING * 2 + 8);
            return maxWidth + PADDING * 2;
        }
    }

    private int placeholderCardStatsHeight(boolean compact) {
        if (compact) {
            return PADDING + LINE_HEIGHT + LINE_HEIGHT + 3 * LINE_HEIGHT + LINE_HEIGHT + 3 * LINE_HEIGHT + PADDING;
        } else {
            return PADDING + LINE_HEIGHT + 2 + SECTION_SPACING + LINE_HEIGHT + 3 * LINE_HEIGHT
                    + 2 + SECTION_SPACING + LINE_HEIGHT + 3 * LINE_HEIGHT + PADDING;
        }
    }

    private void drawResizeIcon(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x, y, x + 6, y + 1, color);
        graphics.fill(x, y + 1, x + 5, y + 2, color);
        graphics.fill(x, y + 2, x + 4, y + 3, color);
        graphics.fill(x, y + 3, x + 3, y + 4, color);
        graphics.fill(x, y + 4, x + 2, y + 5, color);
        graphics.fill(x, y + 5, x + 1, y + 6, color);
    }

    private void drawUpArrow(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x + 3, y, x + 4, y + 1, color);
        graphics.fill(x + 2, y + 1, x + 5, y + 2, color);
        graphics.fill(x + 1, y + 2, x + 6, y + 3, color);
        graphics.fill(x, y + 3, x + 7, y + 4, color);
    }

    private void drawDownArrow(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x, y, x + 7, y + 1, color);
        graphics.fill(x + 1, y + 1, x + 6, y + 2, color);
        graphics.fill(x + 2, y + 2, x + 5, y + 3, color);
        graphics.fill(x + 3, y + 3, x + 4, y + 4, color);
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
