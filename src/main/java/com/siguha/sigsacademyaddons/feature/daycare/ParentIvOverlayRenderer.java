package com.siguha.sigsacademyaddons.feature.daycare;

import com.siguha.sigsacademyaddons.mixin.ContainerScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

public class ParentIvOverlayRenderer {

    private static final int COLOR_IV_LOW = 0xFFFF5555;
    private static final int COLOR_IV_MED = 0xFFFFFF55;
    private static final int COLOR_IV_HIGH = 0xFF55FF55;
    private static final int COLOR_IV_MAX = 0xFFAA00FF;

    private static final int COLOR_HEADER = 0xFFFFAA00;
    private static final int COLOR_LABEL = 0xFFAAAAAA;
    private static final int COLOR_PERCENT = 0xFF55FFFF;
    private static final int COLOR_POWER_BG = 0x4455FFFF;
    private static final int COLOR_POWER_MARKER = 0xFF55FFFF;
    private static final int COLOR_SEPARATOR = 0xFF000000;
    private static final int COLOR_WARNING = 0xFFFF5555;
    private static final int COLOR_KEY_TEXT = 0xFF888888;

    private static final int PADDING = 6;
    private static final int ROW_HEIGHT = 12;
    private static final int HEADER_HEIGHT = 14;
    private static final int SEPARATOR_GAP = 3;

    private static final String[] STAT_LABELS = {
            "HP", "ATK", "DEF", "SPE", "SP.DEF", "SP.ATK", "%"
    };

    private ParentIvData parent1;
    private ParentIvData parent2;
    private boolean processing;

    public void setData(ParentIvData parent1, ParentIvData parent2) {
        this.parent1 = parent1;
        this.parent2 = parent2;
        this.processing = false;
    }

    public void setProcessing() {
        this.processing = true;
        this.parent1 = null;
        this.parent2 = null;
    }

    public void clear() {
        this.parent1 = null;
        this.parent2 = null;
        this.processing = false;
    }

    public boolean hasData() {
        return parent1 != null || parent2 != null;
    }

    public void render(AbstractContainerScreen<?> screen, GuiGraphics graphics) {
        if (!hasData() && !processing) return;

        ContainerScreenAccessor accessor = (ContainerScreenAccessor) screen;
        Font font = Minecraft.getInstance().font;

        if (processing) {
            renderProcessing(accessor, font, graphics, screen.width);
            return;
        }

        int labelColWidth = calculateLabelColumnWidth(font);
        int parentColWidth = calculateFixedParentColumnWidth(font);
        int numParents = (parent1 != null ? 1 : 0) + (parent2 != null ? 1 : 0);
        int totalWidth = PADDING + labelColWidth + SEPARATOR_GAP
                + numParents * (parentColWidth + SEPARATOR_GAP) + PADDING;

        boolean duplicatePowerItem = hasDuplicatePowerItem();
        boolean hasAnyPowerItem = (parent1 != null && parent1.getPowerItemStat() != null)
                || (parent2 != null && parent2.getPowerItemStat() != null);

        int footerHeight = hasAnyPowerItem ? ROW_HEIGHT + 2 : 0;
        int totalHeight = PADDING + HEADER_HEIGHT + 4 + (7 * ROW_HEIGHT) + footerHeight + PADDING;

        int containerRight = accessor.getLeftPos() + accessor.getImageWidth();
        int availableWidth = screen.width - containerRight - 6;
        int availableHeight = screen.height - accessor.getTopPos() - 4;

        float scaleX = availableWidth > 0 ? Math.min(1.0f, (float) availableWidth / totalWidth) : 0.5f;
        float scaleY = availableHeight > 0 ? Math.min(1.0f, (float) availableHeight / totalHeight) : 0.5f;
        float scale = Math.max(0.4f, Math.min(scaleX, scaleY));

        graphics.pose().pushPose();
        graphics.pose().translate(containerRight + 4, (float) accessor.getTopPos(), 0);
        graphics.pose().scale(scale, scale, 1.0f);

        int currentY = PADDING;

        int labelEndX = PADDING + labelColWidth;
        int p1StartX = labelEndX + SEPARATOR_GAP;
        int p2StartX = parent1 != null ? p1StartX + parentColWidth + SEPARATOR_GAP : p1StartX;

        graphics.drawString(font, "IVs", PADDING, currentY, COLOR_HEADER, true);

        if (parent1 != null) {
            renderSpeciesHeader(graphics, font, parent1.getSpecies(), p1StartX, currentY, parentColWidth);
        }
        if (parent2 != null) {
            renderSpeciesHeader(graphics, font, parent2.getSpecies(), p2StartX, currentY, parentColWidth);
        }

        currentY += HEADER_HEIGHT;

        int headerLineY = currentY;
        graphics.fill(PADDING, headerLineY, totalWidth - PADDING, headerLineY + 1, COLOR_SEPARATOR);
        currentY += 4;

        int dataBottomY = currentY + (7 * ROW_HEIGHT);

        int sepX1 = labelEndX + SEPARATOR_GAP / 2;
        graphics.fill(sepX1, headerLineY, sepX1 + 1, dataBottomY, COLOR_SEPARATOR);

        if (parent1 != null && parent2 != null) {
            int sepX2 = p1StartX + parentColWidth + SEPARATOR_GAP / 2;
            graphics.fill(sepX2, headerLineY, sepX2 + 1, dataBottomY, COLOR_SEPARATOR);
        }

        for (int row = 0; row < 7; row++) {
            graphics.drawString(font, STAT_LABELS[row], PADDING, currentY,
                    row == 6 ? COLOR_HEADER : COLOR_LABEL, true);

            if (row < 6) {
                if (parent1 != null) renderIvCell(graphics, font, parent1, row, p1StartX, currentY, parentColWidth);
                if (parent2 != null) renderIvCell(graphics, font, parent2, row, p2StartX, currentY, parentColWidth);
            } else {
                if (parent1 != null) renderPercentCell(graphics, font, parent1, p1StartX, currentY, parentColWidth);
                if (parent2 != null) renderPercentCell(graphics, font, parent2, p2StartX, currentY, parentColWidth);
            }

            currentY += ROW_HEIGHT;
        }

        if (hasAnyPowerItem) {
            currentY += 2;
            String keyMarker = "*";
            String keyText = " = Guaranteed IV";
            int keyX = (totalWidth - font.width(keyMarker) - font.width(keyText)) / 2;
            graphics.drawString(font, keyMarker, keyX, currentY, COLOR_POWER_MARKER, true);
            graphics.drawString(font, keyText, keyX + font.width(keyMarker), currentY, COLOR_KEY_TEXT, true);
        }

        graphics.pose().popPose();

        if (duplicatePowerItem) {
            renderContainerWarning(accessor, font, graphics);
        }
    }

    private void renderContainerWarning(ContainerScreenAccessor accessor, Font font,
                                          GuiGraphics graphics) {
        String warning = "\u26A0 Both parents holding same power item!";
        int warnWidth = font.width(warning);
        int containerWidth = accessor.getImageWidth();
        int centerX = accessor.getLeftPos() + containerWidth / 2;
        int warnY = accessor.getTopPos() + (int)(accessor.getImageHeight() * 0.51)
                - font.lineHeight / 2 + 1;

        int containerInnerWidth = containerWidth - 16;
        if (warnWidth > containerInnerWidth) {
            float warnScale = (float) containerInnerWidth / warnWidth;
            graphics.pose().pushPose();
            graphics.pose().translate(centerX, warnY, 0);
            graphics.pose().scale(warnScale, warnScale, 1.0f);
            graphics.drawString(font, warning, -warnWidth / 2, 0, COLOR_WARNING, true);
            graphics.pose().popPose();
        } else {
            graphics.drawString(font, warning, centerX - warnWidth / 2, warnY, COLOR_WARNING, true);
        }
    }

    private void renderProcessing(ContainerScreenAccessor accessor, Font font,
                                   GuiGraphics graphics, int screenWidth) {
        String text = "Processing Parent Info...";
        int textWidth = font.width(text);
        int panelWidth = textWidth + PADDING * 2;

        int containerRight = accessor.getLeftPos() + accessor.getImageWidth();
        int availableWidth = screenWidth - containerRight - 6;
        float scale = Math.max(0.4f,
                availableWidth > 0 ? Math.min(1.0f, (float) availableWidth / panelWidth) : 0.5f);

        graphics.pose().pushPose();
        graphics.pose().translate(containerRight + 4, (float) accessor.getTopPos(), 0);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.drawString(font, text, PADDING, PADDING, COLOR_HEADER, true);
        graphics.pose().popPose();
    }

    private void renderIvCell(GuiGraphics graphics, Font font, ParentIvData parent,
                              int statRow, int x, int y, int colWidth) {
        int iv = parent.getIvForStatRow(statRow);
        String ivText = String.valueOf(iv);
        int color = getIvColor(iv);
        int textWidth = font.width(ivText);
        int cellCenter = x + colWidth / 2;

        boolean isPowerStat = parent.getPowerItemStat() != null
                && parent.getPowerItemStat() == ParentIvData.statForRow(statRow);

        if (isPowerStat) {
            int markerWidth = font.width("*");
            int totalTextWidth = textWidth + 1 + markerWidth;
            int textX = cellCenter - totalTextWidth / 2;

            graphics.fill(x + 1, y - 1, x + colWidth - 1, y + ROW_HEIGHT - 2, COLOR_POWER_BG);
            graphics.drawString(font, ivText, textX, y, color, true);
            graphics.drawString(font, "*", textX + textWidth + 1, y, COLOR_POWER_MARKER, true);
        } else {
            graphics.drawString(font, ivText, cellCenter - textWidth / 2, y, color, true);
        }
    }

    private void renderPercentCell(GuiGraphics graphics, Font font, ParentIvData parent,
                                   int x, int y, int colWidth) {
        String pctText = parent.getIvPercent() + "%";
        int textWidth = font.width(pctText);
        graphics.drawString(font, pctText, x + (colWidth - textWidth) / 2, y, COLOR_PERCENT, true);
    }

    private boolean hasDuplicatePowerItem() {
        if (parent1 == null || parent2 == null) return false;
        if (parent1.getPowerItemStat() == null || parent2.getPowerItemStat() == null) return false;
        return parent1.getPowerItemStat() == parent2.getPowerItemStat();
    }

    private static int getIvColor(int iv) {
        if (iv == 31) return COLOR_IV_MAX;
        if (iv >= 26) return COLOR_IV_HIGH;
        if (iv >= 16) return COLOR_IV_MED;
        return COLOR_IV_LOW;
    }

    private int calculateLabelColumnWidth(Font font) {
        int max = 0;
        for (String label : STAT_LABELS) {
            max = Math.max(max, font.width(label));
        }
        return max + 4;
    }

    private int calculateFixedParentColumnWidth(Font font) {
        return Math.max(font.width("31*") + 8, font.width("100%") + 6);
    }

    private void renderSpeciesHeader(GuiGraphics graphics, Font font, String species,
                                     int x, int y, int colWidth) {
        int nameWidth = font.width(species);
        if (nameWidth <= colWidth) {
            graphics.drawString(font, species, x + (colWidth - nameWidth) / 2, y, COLOR_HEADER, true);
        } else {
            float nameScale = (float) colWidth / nameWidth;
            float yOffset = font.lineHeight * (1 - nameScale) / 2.0f;
            graphics.pose().pushPose();
            graphics.pose().translate(x, y + yOffset, 0);
            graphics.pose().scale(nameScale, nameScale, 1.0f);
            graphics.drawString(font, species, 0, 0, COLOR_HEADER, true);
            graphics.pose().popPose();
        }
    }
}
