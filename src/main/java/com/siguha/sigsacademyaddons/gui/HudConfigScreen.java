package com.siguha.sigsacademyaddons.gui;

import com.siguha.sigsacademyaddons.config.HudConfig;
import com.siguha.sigsacademyaddons.feature.safari.SafariHuntData;
import com.siguha.sigsacademyaddons.feature.safari.SafariHuntManager;
import com.siguha.sigsacademyaddons.feature.safari.SafariManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

// drag-to-reposition and corner-resize screen for the safari hud panel
public class HudConfigScreen extends Screen {

    // layout constants — must match SafariHudRenderer
    private static final int PADDING = 4;
    private static final int LINE_HEIGHT = 11;
    private static final int SECTION_SPACING = 6;
    private static final int PROGRESS_BAR_HEIGHT = 3;
    private static final int PANEL_MIN_WIDTH = 140;

    // corner handle size and grab radius
    private static final int CORNER_HANDLE_SIZE = 6;
    private static final int CORNER_GRAB_RADIUS = 8;

    // scale limits
    private static final float MIN_SCALE = 0.5f;
    private static final float MAX_SCALE = 2.0f;

    // colors
    private static final int COLOR_BG = 0xAA000000;
    private static final int COLOR_HEADER = 0xFFFFAA00;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_HINT = 0xFFAAAAAA;
    private static final int COLOR_OUTLINE = 0xFFFFAA00;
    private static final int COLOR_OUTLINE_DRAG = 0xFF55FF55;
    private static final int COLOR_CORNER_HANDLE = 0xFFFFFFFF;
    private static final int COLOR_CORNER_HOVER = 0xFF55FF55;

    private final HudConfig hudConfig;
    private final SafariManager safariManager;
    private final SafariHuntManager safariHuntManager;

    // unscaled panel dimensions (computed from current state)
    private int unscaledWidth;
    private int unscaledHeight;

    private float currentScale;

    // scaled panel dimensions (screen-space)
    private int scaledWidth;
    private int scaledHeight;

    // absolute screen coords
    private int panelX;
    private int panelY;

    private enum DragMode { NONE, MOVE, RESIZE }
    private DragMode dragMode = DragMode.NONE;

    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    // resize anchor = opposite corner of the one being dragged
    private int resizeAnchorX;
    private int resizeAnchorY;
    private boolean resizeFromLeft;
    private boolean resizeFromTop;

    public HudConfigScreen(HudConfig hudConfig, SafariManager safariManager, SafariHuntManager safariHuntManager) {
        super(Component.literal("Safari HUD Position"));
        this.hudConfig = hudConfig;
        this.safariManager = safariManager;
        this.safariHuntManager = safariHuntManager;
    }

    @Override
    protected void init() {
        super.init();

        boolean showTimer = safariManager.isInSafari();
        boolean showHunts = safariHuntManager.hasActiveHunts();

        unscaledWidth = calculateActualWidth(showTimer, showHunts);
        unscaledHeight = calculateActualHeight(showTimer, showHunts);

        // minimum size so the preview is always draggable
        unscaledWidth = Math.max(unscaledWidth, 100);
        unscaledHeight = Math.max(unscaledHeight, 40);

        currentScale = hudConfig.getHudScale();
        updateScaledDimensions();
        panelX = hudConfig.getPanelX(this.width, scaledWidth);
        panelY = hudConfig.getPanelY(this.height, scaledHeight);
    }

    private void updateScaledDimensions() {
        scaledWidth = Math.round(unscaledWidth * currentScale);
        scaledHeight = Math.round(unscaledHeight * currentScale);
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

        String scaleText = String.format("Scale: %.0f%%", currentScale * 100);
        int scaleWidth = this.font.width(scaleText);
        graphics.drawString(this.font, scaleText, (this.width - scaleWidth) / 2, 34, COLOR_TEXT, true);

        // preview panel at current scale
        graphics.pose().pushPose();
        graphics.pose().translate(panelX, panelY, 0);
        graphics.pose().scale(currentScale, currentScale, 1.0f);
        renderPreviewPanel(graphics);
        graphics.pose().popPose();

        // outline in screen-space
        int outlineColor = dragMode == DragMode.MOVE ? COLOR_OUTLINE_DRAG
                : dragMode == DragMode.RESIZE ? COLOR_CORNER_HOVER
                : COLOR_OUTLINE;
        drawOutline(graphics, panelX, panelY, scaledWidth, scaledHeight, outlineColor);

        drawCornerHandles(graphics, mouseX, mouseY);
    }

    // draws 1px outline around the panel in screen space
    private void drawOutline(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x - 1, y - 1, x + w + 1, y, color);
        graphics.fill(x - 1, y + h, x + w + 1, y + h + 1, color);
        graphics.fill(x - 1, y - 1, x, y + h + 1, color);
        graphics.fill(x + w, y - 1, x + w + 1, y + h + 1, color);
    }

    // draws corner handles that highlight on hover
    private void drawCornerHandles(GuiGraphics graphics, int mouseX, int mouseY) {
        int[][] corners = {
                {panelX, panelY},                               // Top-left
                {panelX + scaledWidth, panelY},                 // Top-right
                {panelX, panelY + scaledHeight},                // Bottom-left
                {panelX + scaledWidth, panelY + scaledHeight}   // Bottom-right
        };

        for (int[] corner : corners) {
            boolean hovered = isNearPoint(mouseX, mouseY, corner[0], corner[1], CORNER_GRAB_RADIUS);
            int color = (hovered || dragMode == DragMode.RESIZE) ? COLOR_CORNER_HOVER : COLOR_CORNER_HANDLE;
            int hs = CORNER_HANDLE_SIZE / 2;
            graphics.fill(corner[0] - hs, corner[1] - hs,
                    corner[0] + hs, corner[1] + hs, color);
        }
    }

    // renders preview panel content at (0,0) in unscaled space
    private void renderPreviewPanel(GuiGraphics graphics) {
        graphics.fill(0, 0, unscaledWidth, unscaledHeight, COLOR_BG);

        int y = PADDING;

        boolean showTimer = safariManager.isInSafari();
        boolean showHunts = safariHuntManager.hasActiveHunts();

        // placeholder preview when nothing active
        if (!showTimer && !showHunts) {
            graphics.drawString(this.font, "Safari Zone", PADDING, y, COLOR_HEADER, true);
            y += LINE_HEIGHT;
            graphics.drawString(this.font, "24:31", PADDING, y, 0xFF55FF55, true);
            y += LINE_HEIGHT + SECTION_SPACING;
            graphics.drawString(this.font, "Active Hunts", PADDING, y, COLOR_HEADER, true);
            y += LINE_HEIGHT + 2;
            graphics.drawString(this.font, "Ice Type  5/30", PADDING, y, COLOR_HINT, true);
            return;
        }

        // timer section
        if (showTimer) {
            String header = safariManager.isInSafariZone() ? "Safari Zone" : "Safari Zone (away)";
            graphics.drawString(this.font, header, PADDING, y, COLOR_HEADER, true);
            y += LINE_HEIGHT;

            String timerText = safariManager.getRemainingTimeFormatted();
            graphics.drawString(this.font, timerText, PADDING, y, 0xFF55FF55, true);
            y += LINE_HEIGHT;

            // progress bar
            int barWidth = unscaledWidth - (PADDING * 2);
            float remainingPercent = 1.0f - safariManager.getTimerProgress();
            graphics.fill(PADDING, y, PADDING + barWidth, y + PROGRESS_BAR_HEIGHT, 0xFF333333);
            int filledWidth = (int) (barWidth * remainingPercent);
            if (filledWidth > 0) {
                graphics.fill(PADDING, y, PADDING + filledWidth, y + PROGRESS_BAR_HEIGHT, 0xFF55FF55);
            }
            y += PROGRESS_BAR_HEIGHT;

            if (showHunts) {
                y += SECTION_SPACING;
                graphics.fill(PADDING, y, PADDING + barWidth, y + 1, 0xFF555555);
                y += SECTION_SPACING;
            }
        }

        // hunt section
        if (showHunts) {
            graphics.drawString(this.font, "Active Hunts", PADDING, y, COLOR_HEADER, true);
            y += LINE_HEIGHT + 2;

            List<SafariHuntData> hunts = safariHuntManager.getActiveHunts();
            for (SafariHuntData hunt : hunts) {
                // hunt name with star rating
                int nameX = PADDING;
                if (hunt.getStarRating() > 0) {
                    String stars = "*".repeat(hunt.getStarRating());
                    graphics.drawString(this.font, stars, nameX, y, 0xFFFFD700, true);
                    nameX += this.font.width(stars) + 2;
                }

                int nameColor = hunt.isComplete() ? 0xFFFFAA00 : COLOR_TEXT;
                graphics.drawString(this.font, hunt.getDisplayName(), nameX, y, nameColor, true);

                // reset countdown (right-aligned)
                String resetText = hunt.getResetCountdownFormatted();
                if (!resetText.isEmpty()) {
                    int resetColor = hunt.isResetExpired() ? 0xFFFF5555 : 0xFFFF8855;
                    int resetWidth = this.font.width(resetText);
                    graphics.drawString(this.font, resetText, unscaledWidth - PADDING - resetWidth, y,
                            resetColor, true);
                }

                y += LINE_HEIGHT;

                // progress text
                String progressText = hunt.getProgressString();
                int progressColor = hunt.isComplete() ? 0xFFFFAA00 : COLOR_HINT;
                graphics.drawString(this.font, progressText, PADDING + 4, y, progressColor, true);

                y += LINE_HEIGHT;
            }
        }
    }

    // dimension calculations (must match SafariHudRenderer)

    private int calculateActualWidth(boolean showTimer, boolean showHunts) {
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
            String header = safariManager.isInSafariZone() ? "Safari Zone" : "Safari Zone (away)";
            maxWidth = Math.max(maxWidth, this.font.width(header) + PADDING * 2);
        }

        return maxWidth + PADDING * 2;
    }

    private int calculateActualHeight(boolean showTimer, boolean showHunts) {
        // default preview size when nothing active
        if (!showTimer && !showHunts) {
            return PADDING + LINE_HEIGHT + LINE_HEIGHT + SECTION_SPACING + LINE_HEIGHT + 2 + LINE_HEIGHT + PADDING;
        }

        int height = PADDING;

        if (showTimer) {
            height += LINE_HEIGHT;         // header
            height += LINE_HEIGHT;         // timer text
            height += PROGRESS_BAR_HEIGHT; // progress bar
        }

        if (showTimer && showHunts) {
            height += SECTION_SPACING * 2 + 1; // divider
        }

        if (showHunts) {
            height += LINE_HEIGHT + 2; // "active hunts" header
            height += safariHuntManager.getActiveHunts().size() * (LINE_HEIGHT * 2); // each hunt: name + progress
        }

        height += PADDING;
        return height;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int mx = (int) mouseX;
        int my = (int) mouseY;

        // corners first (resize priority over move)
        CornerHit corner = getCornerHit(mx, my);
        if (corner != null) {
            dragMode = DragMode.RESIZE;
            resizeFromLeft = corner.fromLeft;
            resizeFromTop = corner.fromTop;
            resizeAnchorX = corner.fromLeft ? (panelX + scaledWidth) : panelX;
            resizeAnchorY = corner.fromTop ? (panelY + scaledHeight) : panelY;
            return true;
        }
        if (isInsidePanel(mx, my)) {
            dragMode = DragMode.MOVE;
            dragOffsetX = mx - panelX;
            dragOffsetY = my - panelY;
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragMode != DragMode.NONE) {
            dragMode = DragMode.NONE;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button != 0) return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);

        int mx = (int) mouseX;
        int my = (int) mouseY;

        if (dragMode == DragMode.MOVE) {
            panelX = Math.clamp(mx - dragOffsetX, 0, Math.max(0, this.width - scaledWidth));
            panelY = Math.clamp(my - dragOffsetY, 0, Math.max(0, this.height - scaledHeight));
            return true;
        }

        if (dragMode == DragMode.RESIZE) {
            // new scale from anchor-to-mouse distance along width axis
            float distX;
            if (resizeFromLeft) {
                distX = resizeAnchorX - mx;
            } else {
                distX = mx - resizeAnchorX;
            }

            float newScale = distX / unscaledWidth;

            // cap scale so panel fits screen
            float maxFitScale = Math.min(
                    (float) this.width / unscaledWidth,
                    (float) this.height / unscaledHeight
            );
            newScale = Math.clamp(newScale, MIN_SCALE, Math.min(MAX_SCALE, maxFitScale));

            currentScale = newScale;
            updateScaledDimensions();

            // keep anchor corner fixed
            if (resizeFromLeft) {
                panelX = resizeAnchorX - scaledWidth;
            } else {
                panelX = resizeAnchorX;
            }
            if (resizeFromTop) {
                panelY = resizeAnchorY - scaledHeight;
            } else {
                panelY = resizeAnchorY;
            }

            // clamp to screen bounds
            panelX = Math.clamp(panelX, 0, Math.max(0, this.width - scaledWidth));
            panelY = Math.clamp(panelY, 0, Math.max(0, this.height - scaledHeight));

            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public void onClose() {
        hudConfig.setHudScale(currentScale);
        hudConfig.setPositionFromAbsolute(panelX, panelY, scaledWidth, scaledHeight, this.width, this.height);
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record CornerHit(boolean fromLeft, boolean fromTop) {}

    // returns which corner was hit for resize, or null
    private CornerHit getCornerHit(int mx, int my) {
        if (isNearPoint(mx, my, panelX, panelY, CORNER_GRAB_RADIUS)) {
            return new CornerHit(true, true);
        }
        if (isNearPoint(mx, my, panelX + scaledWidth, panelY, CORNER_GRAB_RADIUS)) {
            return new CornerHit(false, true);
        }
        if (isNearPoint(mx, my, panelX, panelY + scaledHeight, CORNER_GRAB_RADIUS)) {
            return new CornerHit(true, false);
        }
        if (isNearPoint(mx, my, panelX + scaledWidth, panelY + scaledHeight, CORNER_GRAB_RADIUS)) {
            return new CornerHit(false, false);
        }
        return null;
    }

    private boolean isInsidePanel(int mx, int my) {
        return mx >= panelX && mx <= panelX + scaledWidth
                && my >= panelY && my <= panelY + scaledHeight;
    }

    private static boolean isNearPoint(int mx, int my, int px, int py, int radius) {
        return Math.abs(mx - px) <= radius && Math.abs(my - py) <= radius;
    }
}
