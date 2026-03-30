package com.siguha.sigsacademyaddons.hud;

import com.siguha.sigsacademyaddons.config.HudConfig;
import com.siguha.sigsacademyaddons.feature.daycare.DaycareManager;
import com.siguha.sigsacademyaddons.feature.daycare.DaycareState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

public class DaycareHudRenderer implements HudPanel {

    private static final int PADDING = 4;
    private static final int LINE_HEIGHT = 11;
    private static final int SECTION_SPACING = 6;
    private static final int TIMER_BAR_HEIGHT = 6;
    private static final int PANEL_MIN_WIDTH = 140;

    private static final int COLOR_BG = 0xAA000000;
    private static final int COLOR_HEADER = 0xFFFFAA00;
    private static final int COLOR_SECTION_HEADER = 0xFF55FFFF;
    private static final int COLOR_PEN_LABEL = 0xFF88DDFF;
    private static final int COLOR_TEXT_PRIMARY = 0xFFFFFFFF;
    private static final int COLOR_TEXT_SECONDARY = 0xFFAAAAAA;
    private static final int COLOR_TEXT_INACTIVE = 0xFF777777;
    private static final int COLOR_TIMER_SAFE = 0xFF55FF55;
    private static final int COLOR_TIMER_WARN = 0xFFFFFF55;
    private static final int COLOR_TIMER_DANGER = 0xFFFF8855;
    private static final int COLOR_TIMER_WAITING = 0xFFFF5555;
    private static final int COLOR_PROGRESS_BG = 0xFF333333;
    private static final int COLOR_NEEDS_RESET = 0xFFFF8800;
    private static final int COLOR_INCOMPATIBLE = 0xFFFF3333;
    private static final int COLOR_TEXT_UNSET = 0xFF888888;

    private static final Component EMPTY_MESSAGE_FULL = Component.translatable("interface.saa.daycare.empty_full");
    private static final Component EMPTY_MESSAGE_COMPACT = Component.translatable("interface.saa.daycare.empty_compact");

    private final DaycareManager daycareManager;
    private final HudConfig hudConfig;

    public DaycareHudRenderer(DaycareManager daycareManager, HudConfig hudConfig) {
        this.daycareManager = daycareManager;
        this.hudConfig = hudConfig;
    }

    @Override
    public String getPanelId() {
        return "daycare";
    }

    @Override
    public boolean shouldRender() {
        Minecraft client = Minecraft.getInstance();
        return hudConfig.isDaycareMenuEnabled() && client.player != null && !client.options.hideGui;
    }

    @Override
    public boolean hasVisibleContent() {
        return true;
    }

    @Override
    public int getContentWidth(Font font) {
        List<DaycareState.PenState> displayPens = daycareManager.getDisplayPens();
        List<DaycareState.ClaimedEgg> displayEggs = daycareManager.getDisplayEggs();
        int totalActiveEggs = daycareManager.getTotalActiveEggs();
        int maxDisplayEggs = hudConfig.getDaycareEggsHatchingSlots();
        boolean compact = hudConfig.isCompact();

        if (displayPens.isEmpty() && displayEggs.isEmpty()) {
            if (compact) {
                return Math.max(PANEL_MIN_WIDTH, font.width(EMPTY_MESSAGE_COMPACT) + PADDING * 4);
            } else {
                return Math.max(PANEL_MIN_WIDTH, font.width(EMPTY_MESSAGE_FULL) + PADDING * 4);
            }
        }

        return compact
                ? calculateCompactPanelWidth(font, displayPens, displayEggs, totalActiveEggs, maxDisplayEggs)
                : calculatePanelWidth(font, displayPens, displayEggs, totalActiveEggs, maxDisplayEggs);
    }

    @Override
    public int getContentHeight(Font font) {
        List<DaycareState.PenState> displayPens = daycareManager.getDisplayPens();
        List<DaycareState.ClaimedEgg> displayEggs = daycareManager.getDisplayEggs();
        int totalActiveEggs = daycareManager.getTotalActiveEggs();
        int maxDisplayEggs = hudConfig.getDaycareEggsHatchingSlots();
        boolean compact = hudConfig.isCompact();

        if (displayPens.isEmpty() && displayEggs.isEmpty()) {
            if (compact) {
                return PADDING + LINE_HEIGHT + LINE_HEIGHT + PADDING;
            } else {
                return PADDING + LINE_HEIGHT + 2 + SECTION_SPACING + LINE_HEIGHT + PADDING;
            }
        }

        return compact
                ? calculateCompactPanelHeight(displayPens, displayEggs, totalActiveEggs, maxDisplayEggs)
                : calculatePanelHeight(displayPens, displayEggs, totalActiveEggs, maxDisplayEggs);
    }

    @Override
    public int getContentHeight(Font font, int panelWidth) {
        List<DaycareState.PenState> displayPens = daycareManager.getDisplayPens();
        List<DaycareState.ClaimedEgg> displayEggs = daycareManager.getDisplayEggs();
        int totalActiveEggs = daycareManager.getTotalActiveEggs();
        int maxDisplayEggs = hudConfig.getDaycareEggsHatchingSlots();
        boolean compact = hudConfig.isCompact();

        if (displayPens.isEmpty() && displayEggs.isEmpty()) {
            if (compact) {
                return PADDING + LINE_HEIGHT + HudTextUtil.wrappedCenteredHeight(font, EMPTY_MESSAGE_COMPACT, panelWidth, LINE_HEIGHT) + PADDING;
            } else {
                int h = PADDING;
                h += HudTextUtil.wrappedCenteredHeight(font, Component.translatable("interface.saa.daycare.helper"), panelWidth, LINE_HEIGHT);
                h += 2 + SECTION_SPACING;
                h += HudTextUtil.wrappedCenteredHeight(font, EMPTY_MESSAGE_FULL, panelWidth, LINE_HEIGHT);
                h += PADDING;
                return h;
            }
        }

        return compact
                ? calculateCompactPanelHeightForWidth(font, panelWidth, displayPens, displayEggs, totalActiveEggs, maxDisplayEggs)
                : calculatePanelHeightForWidth(font, panelWidth, displayPens, displayEggs, totalActiveEggs, maxDisplayEggs);
    }

    @Override
    public void renderContent(GuiGraphics graphics, Font font, int panelWidth) {
        List<DaycareState.PenState> displayPens = daycareManager.getDisplayPens();
        List<DaycareState.ClaimedEgg> displayEggs = daycareManager.getDisplayEggs();
        int totalActiveEggs = daycareManager.getTotalActiveEggs();
        int maxDisplayEggs = hudConfig.getDaycareEggsHatchingSlots();
        boolean compact = hudConfig.isCompact();

        if (displayPens.isEmpty() && displayEggs.isEmpty()) {
            if (compact) {
                renderCompactEmpty(graphics, font, panelWidth);
            } else {
                renderFullEmpty(graphics, font, panelWidth);
            }
            return;
        }

        if (compact) {
            renderCompact(graphics, font, panelWidth, displayPens, displayEggs, totalActiveEggs, maxDisplayEggs);
        } else {
            renderFull(graphics, font, panelWidth, displayPens, displayEggs, totalActiveEggs, maxDisplayEggs);
        }
    }

    @Override
    public void onHudRender(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!shouldRender()) {
            return;
        }

        if (hudConfig.isInGroup("daycare")) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        Font font = client.font;
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        float scale = hudConfig.getDaycareScale();

        double guiScale = client.getWindow().getGuiScale();
        float minScale = (float) (6.0 / (8.0 * guiScale));
        float maxScale = (float) (32.0 / (8.0 * guiScale));
        scale = Math.max(minScale, Math.min(maxScale, scale));

        boolean transparent = hudConfig.getHudStyle() == HudConfig.HudStyle.TRANSPARENT;

        int widthOvr = hudConfig.getDaycareWidthOverride();
        int panelWidth = widthOvr > 0 ? widthOvr : getContentWidth(font);
        int panelHeight = getContentHeight(font, panelWidth);

        int scaledWidth = Math.round(panelWidth * scale);
        int scaledHeight = Math.round(panelHeight * scale);

        int panelX = hudConfig.getDaycarePanelX(screenWidth, scaledWidth);
        int panelY = hudConfig.getDaycarePanelY(screenHeight, scaledHeight);

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
                                List<DaycareState.PenState> displayPens,
                                List<DaycareState.ClaimedEgg> displayEggs,
                                int totalActiveEggs, int maxDisplayEggs) {
        int currentY = PADDING;

        graphics.drawString(font, "Daycare", PADDING, currentY, COLOR_HEADER, true);
        currentY += LINE_HEIGHT;

        boolean hasAnyPen = !displayPens.isEmpty();
        if (hasAnyPen) {
            graphics.drawString(font, "Breeding", PADDING, currentY, COLOR_SECTION_HEADER, true);
            currentY += LINE_HEIGHT;

            for (DaycareState.PenState pen : displayPens) {
                currentY = renderCompactPenEntry(graphics, font, pen, currentY, panelWidth);
            }
        }

        if (maxDisplayEggs > 0 && !displayEggs.isEmpty()) {
            graphics.drawString(font, "Hatching", PADDING, currentY, COLOR_SECTION_HEADER, true);

            int eggsHatched = daycareManager.getEggsHatchedSinceMenuOpen();
            if (eggsHatched > 0) {
                String hatchedText = eggsHatched + " Hatched";
                int hatchedWidth = font.width(hatchedText);
                graphics.drawString(font, hatchedText, panelWidth - PADDING - hatchedWidth,
                        currentY, COLOR_NEEDS_RESET, true);
            }
            currentY += LINE_HEIGHT;

            for (DaycareState.ClaimedEgg egg : displayEggs) {
                currentY = renderCompactEggTimer(graphics, font, egg, currentY, panelWidth);
            }

            int extraEggs = totalActiveEggs - displayEggs.size();
            if (extraEggs > 0) {
                String moreText = "(+" + extraEggs + " more)";
                graphics.drawString(font, moreText, PADDING + 2, currentY, COLOR_TEXT_SECONDARY, true);
            }
        }
    }

    private void renderCompactEmpty(GuiGraphics graphics, Font font, int panelWidth) {
        int currentY = PADDING;
        graphics.drawString(font, "Daycare", PADDING, currentY, COLOR_HEADER, true);
        currentY += LINE_HEIGHT;
        HudTextUtil.renderWrappedCentered(graphics, font, EMPTY_MESSAGE_COMPACT, panelWidth, currentY, COLOR_TEXT_UNSET, LINE_HEIGHT);
    }

    private void renderFullEmpty(GuiGraphics graphics, Font font, int panelWidth) {
        int currentY = PADDING;
        currentY = HudTextUtil.renderWrappedCentered(graphics, font, Component.translatable("interface.saa.daycare.helper"), panelWidth, currentY, COLOR_HEADER, LINE_HEIGHT);

        currentY += 2;
        graphics.fill(PADDING, currentY, panelWidth - PADDING, currentY + 1, 0xFF555555);
        currentY += SECTION_SPACING;

        HudTextUtil.renderWrappedCentered(graphics, font, EMPTY_MESSAGE_FULL, panelWidth, currentY, COLOR_TEXT_UNSET, LINE_HEIGHT);
    }

    private int renderCompactPenEntry(GuiGraphics graphics, Font font, DaycareState.PenState pen,
                                       int startY, int panelWidth) {
        int y = startY;
        DaycareState.BreedingStage stage = pen.getStage();
        String penLabel = "[PEN " + pen.getPenNumber() + "]";

        graphics.drawString(font, penLabel, PADDING + 2, y, COLOR_PEN_LABEL, true);
        int textX = PADDING + 2 + font.width(penLabel) + 4;

        switch (stage) {
            case EMPTY, ONE_POKEMON -> {
                graphics.drawString(font, "Inactive", textX, y, COLOR_TEXT_INACTIVE, true);
                y += LINE_HEIGHT;
            }
            case BREEDING, EGG_READY -> {
                String species = getPenSpeciesLabel(pen);
                String timerText = pen.getRemainingFormatted();
                float progress = pen.getProgress();
                int timerColor = progress >= 1.0f ? COLOR_TIMER_SAFE : getTimerColor(progress);

                if (!timerText.isEmpty()) {
                    int speciesW = font.width(species);
                    int timerW = font.width(timerText);
                    int available = panelWidth - textX - PADDING;
                    if (speciesW + 8 + timerW <= available) {
                        graphics.drawString(font, species, textX, y, COLOR_TEXT_PRIMARY, true);
                        graphics.drawString(font, timerText, panelWidth - PADDING - timerW, y, timerColor, true);
                        y += LINE_HEIGHT;
                    } else {
                        graphics.drawString(font, species, textX, y, COLOR_TEXT_PRIMARY, true);
                        y += LINE_HEIGHT;
                        graphics.drawString(font, timerText, panelWidth - PADDING - timerW, y, timerColor, true);
                        y += LINE_HEIGHT;
                    }
                } else {
                    graphics.drawString(font, species, textX, y, COLOR_TEXT_PRIMARY, true);
                    y += LINE_HEIGHT;
                }
            }
            case NEEDS_RESET -> {
                String species = getPenSpeciesLabel(pen);
                String resetText = "Reset!";
                int speciesW = font.width(species);
                int resetW = font.width(resetText);
                int available = panelWidth - textX - PADDING;
                if (speciesW + 8 + resetW <= available) {
                    graphics.drawString(font, species, textX, y, COLOR_TEXT_PRIMARY, true);
                    graphics.drawString(font, resetText, panelWidth - PADDING - resetW, y, COLOR_NEEDS_RESET, true);
                    y += LINE_HEIGHT;
                } else {
                    graphics.drawString(font, species, textX, y, COLOR_TEXT_PRIMARY, true);
                    y += LINE_HEIGHT;
                    graphics.drawString(font, resetText, panelWidth - PADDING - resetW, y, COLOR_NEEDS_RESET, true);
                    y += LINE_HEIGHT;
                }
            }
            case INCOMPATIBLE -> {
                graphics.drawString(font, "Incompatible", textX, y, COLOR_INCOMPATIBLE, true);
                y += LINE_HEIGHT;
            }
        }
        return y;
    }

    private int renderCompactEggTimer(GuiGraphics graphics, Font font, DaycareState.ClaimedEgg egg,
                                       int startY, int panelWidth) {
        int y = startY;
        Component label = egg.getDisplayLabel();
        Component timerText = egg.getRemainingFormatted();
        int timerColor = getTimerColor(egg.getProgress());
        return HudTextUtil.renderStatLine(graphics, font, label, timerText,
                COLOR_TEXT_PRIMARY, timerColor, y, panelWidth, PADDING, LINE_HEIGHT);
    }

    private void renderFull(GuiGraphics graphics, Font font, int panelWidth,
                             List<DaycareState.PenState> displayPens,
                             List<DaycareState.ClaimedEgg> displayEggs,
                             int totalActiveEggs, int maxDisplayEggs) {
        int currentY = PADDING;

        currentY = HudTextUtil.renderWrappedCentered(graphics, font, Component.translatable("interface.saa.daycare.helper"), panelWidth, currentY, COLOR_HEADER, LINE_HEIGHT);

        boolean hasAnyPen = !displayPens.isEmpty();
        if (hasAnyPen) {
            currentY += 2;
            graphics.fill(PADDING, currentY, panelWidth - PADDING, currentY + 1, 0xFF555555);
            currentY += SECTION_SPACING;

            graphics.drawString(font, "Breeding", PADDING, currentY, COLOR_SECTION_HEADER, true);
            currentY += LINE_HEIGHT;

            for (DaycareState.PenState pen : displayPens) {
                currentY = renderPenEntry(graphics, font, pen, currentY, panelWidth);
            }
        }

        if (maxDisplayEggs > 0 && !displayEggs.isEmpty()) {
            currentY += 2;
            graphics.fill(PADDING, currentY, panelWidth - PADDING, currentY + 1, 0xFF555555);
            currentY += SECTION_SPACING;

            graphics.drawString(font, "Hatching", PADDING, currentY, COLOR_SECTION_HEADER, true);

            int eggsHatched = daycareManager.getEggsHatchedSinceMenuOpen();
            if (eggsHatched > 0) {
                String hatchedText = eggsHatched + " Egg" + (eggsHatched != 1 ? "s" : "") + " Hatched";
                int hatchedWidth = font.width(hatchedText);
                graphics.drawString(font, hatchedText, panelWidth - PADDING - hatchedWidth,
                        currentY, COLOR_NEEDS_RESET, true);
            }
            currentY += LINE_HEIGHT;

            for (DaycareState.ClaimedEgg egg : displayEggs) {
                currentY = renderEggTimer(graphics, font, egg, currentY, panelWidth);
            }

            int extraEggs = totalActiveEggs - displayEggs.size();
            if (extraEggs > 0) {
                String moreText = "(+" + extraEggs + " more)";
                graphics.drawString(font, moreText, PADDING + 2, currentY, COLOR_TEXT_SECONDARY, true);
            }
        }
    }

    private String getPenSpeciesLabel(DaycareState.PenState pen) {
        String species = pen.getInferredEggSpecies();

        if (species != null && !species.isEmpty()) return species;
        if (pen.getPokemon1() != null) return pen.getPokemon1();

        return "Unknown";
    }

    private int renderPenEntry(GuiGraphics graphics, Font font, DaycareState.PenState pen,
                                int startY, int panelWidth) {
        int y = startY;
        DaycareState.BreedingStage stage = pen.getStage();
        String penLabel = "[PEN " + pen.getPenNumber() + "]";
        int textX = PADDING + 2 + font.width(penLabel) + 4;

        switch (stage) {
            case EMPTY, ONE_POKEMON -> {
                graphics.drawString(font, penLabel, PADDING + 2, y, COLOR_PEN_LABEL, true);
                graphics.drawString(font, "Inactive", textX, y, COLOR_TEXT_INACTIVE, true);
                y += LINE_HEIGHT;
            }

            case BREEDING, EGG_READY -> {
                String species = getPenSpeciesLabel(pen);
                String timerText = pen.getRemainingFormatted();
                float progress = pen.getProgress();
                int timerColor = progress >= 1.0f ? COLOR_TIMER_SAFE : getTimerColor(progress);

                graphics.drawString(font, penLabel, PADDING + 2, y, COLOR_PEN_LABEL, true);
                graphics.drawString(font, species, textX, y, COLOR_TEXT_PRIMARY, true);

                if (!timerText.isEmpty()) {
                    int speciesW = font.width(species);
                    int timerW = font.width(timerText);
                    int available = panelWidth - textX - PADDING;
                    if (speciesW + 8 + timerW <= available) {
                        graphics.drawString(font, timerText, panelWidth - PADDING - timerW, y, timerColor, true);
                        y += LINE_HEIGHT;
                    } else {
                        y += LINE_HEIGHT;
                        graphics.drawString(font, timerText, panelWidth - PADDING - timerW, y, timerColor, true);
                        y += LINE_HEIGHT;
                    }
                } else {
                    y += LINE_HEIGHT;
                }

                int barX = PADDING + 2;
                int barWidth = panelWidth - barX - PADDING;
                int barColor = progress >= 1.0f ? COLOR_TIMER_SAFE : getTimerColor(progress);

                graphics.fill(barX, y, barX + barWidth, y + TIMER_BAR_HEIGHT, COLOR_PROGRESS_BG);
                int filled = (int) (barWidth * progress);
                if (filled > 0) {
                    graphics.fill(barX, y, barX + filled, y + TIMER_BAR_HEIGHT, barColor);
                }
                y += TIMER_BAR_HEIGHT + 2;
            }

            case NEEDS_RESET -> {
                String species = getPenSpeciesLabel(pen);
                Component resetText = Component.translatable("interface.saa.daycare.parent_reset");

                graphics.drawString(font, penLabel, PADDING + 2, y, COLOR_PEN_LABEL, true);
                graphics.drawString(font, species, textX, y, COLOR_TEXT_PRIMARY, true);

                int speciesW = font.width(species);
                int resetW = font.width(resetText);
                int available = panelWidth - textX - PADDING;
                if (speciesW + 8 + resetW <= available) {
                    graphics.drawString(font, resetText, panelWidth - PADDING - resetW, y, COLOR_NEEDS_RESET, true);
                    y += LINE_HEIGHT;
                } else {
                    y += LINE_HEIGHT;
                    graphics.drawString(font, resetText, panelWidth - PADDING - resetW, y, COLOR_NEEDS_RESET, true);
                    y += LINE_HEIGHT;
                }

                int barX = PADDING + 2;
                int barWidth = panelWidth - barX - PADDING;
                graphics.fill(barX, y, barX + barWidth, y + TIMER_BAR_HEIGHT, COLOR_NEEDS_RESET);
                y += TIMER_BAR_HEIGHT + 2;
            }

            case INCOMPATIBLE -> {
                graphics.drawString(font, penLabel, PADDING + 2, y, COLOR_PEN_LABEL, true);
                graphics.drawString(font, "Incompatible Setup", textX, y, COLOR_INCOMPATIBLE, true);
                y += LINE_HEIGHT;

                int barX = PADDING + 2;
                int barWidth = panelWidth - barX - PADDING;
                graphics.fill(barX, y, barX + barWidth, y + TIMER_BAR_HEIGHT, COLOR_INCOMPATIBLE);
                y += TIMER_BAR_HEIGHT + 2;
            }
        }

        return y;
    }

    private int renderEggTimer(GuiGraphics graphics, Font font, DaycareState.ClaimedEgg egg,
                                int startY, int panelWidth) {
        int y = startY;

        Component label = egg.getDisplayLabel();
        Component timerText = egg.getRemainingFormatted();
        int timerColor = getTimerColor(egg.getProgress());

        y = HudTextUtil.renderStatLine(graphics, font, label, timerText,
                COLOR_TEXT_PRIMARY, timerColor, y, panelWidth, PADDING, LINE_HEIGHT);

        int barX = PADDING + 2;
        int barWidth = panelWidth - barX - PADDING;
        float progress = egg.getProgress();
        int barColor = getTimerColor(progress);
        graphics.fill(barX, y, barX + barWidth, y + TIMER_BAR_HEIGHT, COLOR_PROGRESS_BG);
        int filled = (int) (barWidth * progress);

        if (filled > 0) {
            graphics.fill(barX, y, barX + filled, y + TIMER_BAR_HEIGHT, barColor);
        }

        y += TIMER_BAR_HEIGHT + 2;

        return y;
    }

    private int calculateCompactPanelWidth(Font font, List<DaycareState.PenState> pens,
                                            List<DaycareState.ClaimedEgg> eggs,
                                            int totalActiveEggs, int maxDisplayEggs) {
        int maxWidth = font.width("Daycare") + PADDING * 2;

        for (DaycareState.PenState pen : pens) {
            String penLabel = "[PEN " + pen.getPenNumber() + "]";
            String species = getPenSpeciesLabel(pen);
            DaycareState.BreedingStage stage = pen.getStage();

            int lineWidth;
            if (stage == DaycareState.BreedingStage.EMPTY || stage == DaycareState.BreedingStage.ONE_POKEMON) {
                lineWidth = font.width(penLabel) + 4 + font.width("Inactive");
            } else if (stage == DaycareState.BreedingStage.NEEDS_RESET) {
                lineWidth = font.width(penLabel) + 4 + font.width(species) + 8 + font.width("Reset!");
            } else if (stage == DaycareState.BreedingStage.INCOMPATIBLE) {
                lineWidth = font.width(penLabel) + 4 + font.width("Incompatible");
            } else {
                String timer = pen.getRemainingFormatted();
                lineWidth = font.width(penLabel) + 4 + font.width(species) + 8 + font.width(timer);
            }
            maxWidth = Math.max(maxWidth, lineWidth + PADDING * 2 + 4);
        }

        for (DaycareState.ClaimedEgg egg : eggs) {
            Component label = egg.getDisplayLabel();
            Component timer = egg.getRemainingFormatted();
            int lineWidth = font.width(label) + font.width(timer) + PADDING * 2 + 8;
            maxWidth = Math.max(maxWidth, lineWidth);
        }

        if (!eggs.isEmpty()) {
            int eggsHatched = daycareManager.getEggsHatchedSinceMenuOpen();
            if (eggsHatched > 0) {
                String hatchedText = eggsHatched + " Hatched";
                int headerLineWidth = font.width("Hatching") + 8 + font.width(hatchedText) + PADDING * 2;
                maxWidth = Math.max(maxWidth, headerLineWidth);
            }
        }

        return maxWidth + PADDING * 2;
    }

    private int calculateCompactPanelHeight(List<DaycareState.PenState> pens,
                                             List<DaycareState.ClaimedEgg> eggs,
                                             int totalActiveEggs, int maxDisplayEggs) {
        int height = PADDING;
        height += LINE_HEIGHT;

        if (!pens.isEmpty()) {
            height += LINE_HEIGHT;
            height += pens.size() * LINE_HEIGHT;
        }

        if (maxDisplayEggs > 0 && !eggs.isEmpty()) {
            height += LINE_HEIGHT;
            height += eggs.size() * LINE_HEIGHT;
            int extraEggs = totalActiveEggs - eggs.size();
            if (extraEggs > 0) height += LINE_HEIGHT;
        }

        height += PADDING;
        return height;
    }

    private int calculatePanelWidth(Font font, List<DaycareState.PenState> pens,
                                     List<DaycareState.ClaimedEgg> eggs,
                                     int totalActiveEggs, int maxDisplayEggs) {
        int maxWidth = PANEL_MIN_WIDTH;

        maxWidth = Math.max(maxWidth, font.width(Component.translatable("interface.saa.daycare.helper")) + PADDING * 2);

        for (DaycareState.PenState pen : pens) {
            String penLabel = "[PEN " + pen.getPenNumber() + "]";
            String species = getPenSpeciesLabel(pen);
            DaycareState.BreedingStage stage = pen.getStage();

            int lineWidth;
            if (stage == DaycareState.BreedingStage.EMPTY || stage == DaycareState.BreedingStage.ONE_POKEMON) {
                lineWidth = font.width(penLabel) + 4 + font.width("Inactive");

            } else if (stage == DaycareState.BreedingStage.NEEDS_RESET) {
                lineWidth = font.width(penLabel) + 4 + font.width(species)
                        + 8 + font.width(Component.translatable("interface.saa.daycare.parent_reset"));

            } else if (stage == DaycareState.BreedingStage.INCOMPATIBLE) {
                lineWidth = font.width(penLabel) + 4 + font.width("Incompatible Setup");

            } else {
                String timer = pen.getRemainingFormatted();
                int rightWidth = Math.max(font.width(timer), font.width(Component.translatable("interface.saa.daycare.parent_reset")));
                lineWidth = font.width(penLabel) + 4 + font.width(species) + 8 + rightWidth;

            }
            maxWidth = Math.max(maxWidth, lineWidth + PADDING * 2 + 4);
        }

        for (DaycareState.ClaimedEgg egg : eggs) {
            Component label = egg.getDisplayLabel();
            Component timer = egg.getRemainingFormatted();
            int lineWidth = font.width(label) + font.width(timer) + PADDING * 2 + 8;
            maxWidth = Math.max(maxWidth, lineWidth);
        }

        if (!eggs.isEmpty()) {
            int eggsHatched = daycareManager.getEggsHatchedSinceMenuOpen();

            if (eggsHatched > 0) {
                String hatchedText = eggsHatched + " Egg" + (eggsHatched != 1 ? "s" : "") + " Hatched";
                int headerLineWidth = font.width("Hatching") + 8 + font.width(hatchedText) + PADDING * 2;
                maxWidth = Math.max(maxWidth, headerLineWidth);
            }
        }

        return maxWidth + PADDING * 2;
    }

    private int calculatePanelHeight(List<DaycareState.PenState> pens,
                                      List<DaycareState.ClaimedEgg> eggs,
                                      int totalActiveEggs, int maxDisplayEggs) {
        int height = PADDING;

        height += LINE_HEIGHT;

        if (!pens.isEmpty()) {
            height += 2 + SECTION_SPACING;
            height += LINE_HEIGHT;
            for (DaycareState.PenState pen : pens) {
                DaycareState.BreedingStage stage = pen.getStage();
                if (stage == DaycareState.BreedingStage.BREEDING
                        || stage == DaycareState.BreedingStage.EGG_READY
                        || stage == DaycareState.BreedingStage.NEEDS_RESET
                        || stage == DaycareState.BreedingStage.INCOMPATIBLE) {
                    height += LINE_HEIGHT + TIMER_BAR_HEIGHT + 2;
                } else {
                    height += LINE_HEIGHT;
                }
            }
        }

        if (maxDisplayEggs > 0 && !eggs.isEmpty()) {
            height += 2 + SECTION_SPACING;
            height += LINE_HEIGHT;
            height += eggs.size() * (LINE_HEIGHT + TIMER_BAR_HEIGHT + 2);

            int extraEggs = totalActiveEggs - eggs.size();
            if (extraEggs > 0) {
                height += LINE_HEIGHT;
            }
        }

        height += PADDING;
        return height;
    }

    private int compactPenEntryHeight(Font font, DaycareState.PenState pen, int panelWidth) {
        DaycareState.BreedingStage stage = pen.getStage();
        String penLabel = "[PEN " + pen.getPenNumber() + "]";
        int textX = PADDING + 2 + font.width(penLabel) + 4;
        int available = panelWidth - textX - PADDING;

        switch (stage) {
            case BREEDING, EGG_READY -> {
                String species = getPenSpeciesLabel(pen);
                String timerText = pen.getRemainingFormatted();
                if (!timerText.isEmpty()) {
                    int speciesW = font.width(species);
                    int timerW = font.width(timerText);
                    return (speciesW + 8 + timerW <= available) ? LINE_HEIGHT : LINE_HEIGHT * 2;
                }
                return LINE_HEIGHT;
            }
            case NEEDS_RESET -> {
                String species = getPenSpeciesLabel(pen);
                int speciesW = font.width(species);
                int resetW = font.width("Reset!");
                return (speciesW + 8 + resetW <= available) ? LINE_HEIGHT : LINE_HEIGHT * 2;
            }
            default -> {
                return LINE_HEIGHT;
            }
        }
    }

    private int fullPenEntryHeight(Font font, DaycareState.PenState pen, int panelWidth) {
        DaycareState.BreedingStage stage = pen.getStage();
        String penLabel = "[PEN " + pen.getPenNumber() + "]";
        int textX = PADDING + 2 + font.width(penLabel) + 4;
        int available = panelWidth - textX - PADDING;

        switch (stage) {
            case EMPTY, ONE_POKEMON -> {
                return LINE_HEIGHT;
            }
            case BREEDING, EGG_READY -> {
                String species = getPenSpeciesLabel(pen);
                String timerText = pen.getRemainingFormatted();
                int textLines = LINE_HEIGHT;
                if (!timerText.isEmpty()) {
                    int speciesW = font.width(species);
                    int timerW = font.width(timerText);
                    if (speciesW + 8 + timerW > available) {
                        textLines = LINE_HEIGHT * 2;
                    }
                }
                return textLines + TIMER_BAR_HEIGHT + 2;
            }
            case NEEDS_RESET -> {
                String species = getPenSpeciesLabel(pen);
                int speciesW = font.width(species);
                int resetW = font.width(Component.translatable("interface.saa.daycare.parent_reset"));
                int textLines = (speciesW + 8 + resetW <= available) ? LINE_HEIGHT : LINE_HEIGHT * 2;
                return textLines + TIMER_BAR_HEIGHT + 2;
            }
            case INCOMPATIBLE -> {
                return LINE_HEIGHT + TIMER_BAR_HEIGHT + 2;
            }
            default -> {
                return LINE_HEIGHT;
            }
        }
    }

    private int calculateCompactPanelHeightForWidth(Font font, int panelWidth,
                                                     List<DaycareState.PenState> pens,
                                                     List<DaycareState.ClaimedEgg> eggs,
                                                     int totalActiveEggs, int maxDisplayEggs) {
        int height = PADDING;
        height += LINE_HEIGHT;
        if (!pens.isEmpty()) {
            height += LINE_HEIGHT;            for (DaycareState.PenState pen : pens) {
                height += compactPenEntryHeight(font, pen, panelWidth);
            }
        }

        if (maxDisplayEggs > 0 && !eggs.isEmpty()) {
            height += LINE_HEIGHT;            for (DaycareState.ClaimedEgg egg : eggs) {
                height += HudTextUtil.statLineHeight(font, egg.getDisplayLabel(),
                        egg.getRemainingFormatted(), panelWidth, PADDING, LINE_HEIGHT);
            }
            int extraEggs = totalActiveEggs - eggs.size();
            if (extraEggs > 0) height += LINE_HEIGHT;
        }

        height += PADDING;
        return height;
    }

    private int calculatePanelHeightForWidth(Font font, int panelWidth,
                                              List<DaycareState.PenState> pens,
                                              List<DaycareState.ClaimedEgg> eggs,
                                              int totalActiveEggs, int maxDisplayEggs) {
        int height = PADDING;
        height += HudTextUtil.wrappedCenteredHeight(font, Component.translatable("interface.saa.daycare.helper"), panelWidth, LINE_HEIGHT);

        if (!pens.isEmpty()) {
            height += 2 + SECTION_SPACING;
            height += LINE_HEIGHT;            for (DaycareState.PenState pen : pens) {
                height += fullPenEntryHeight(font, pen, panelWidth);
            }
        }

        if (maxDisplayEggs > 0 && !eggs.isEmpty()) {
            height += 2 + SECTION_SPACING;
            height += LINE_HEIGHT;            for (DaycareState.ClaimedEgg egg : eggs) {
                height += HudTextUtil.statLineHeight(font, egg.getDisplayLabel(),
                        egg.getRemainingFormatted(), panelWidth, PADDING, LINE_HEIGHT);
                height += TIMER_BAR_HEIGHT + 2;
            }
            int extraEggs = totalActiveEggs - eggs.size();
            if (extraEggs > 0) height += LINE_HEIGHT;
        }

        height += PADDING;
        return height;
    }

    private int getTimerColor(float progress) {
        if (progress >= 1.0f) return COLOR_TIMER_WAITING;
        if (progress >= 0.9f) return COLOR_TIMER_DANGER;
        if (progress >= 0.75f) return COLOR_TIMER_WARN;
        return COLOR_TIMER_SAFE;
    }
}
