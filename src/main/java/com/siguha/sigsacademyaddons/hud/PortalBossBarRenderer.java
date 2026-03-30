package com.siguha.sigsacademyaddons.hud;

import com.siguha.sigsacademyaddons.feature.portal.PortalManager;
import com.siguha.sigsacademyaddons.feature.suppression.SuppressionManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class PortalBossBarRenderer {

    private static final int BAR_WIDTH = 182;
    private static final int BAR_HEIGHT = 5;
    private static final int TITLE_Y = 5;
    private static final int DISTANCE_Y = 14;
    private static final int BAR_Y = 26;
    private static final int INDICATOR_WIDTH = 3;
    private static final int INDICATOR_HEIGHT = 7;
    private static final int TRIANGLE_HEIGHT = 2;
    private static final int TRIANGLE_GAP = 2;
    private static final float TITLE_TEXT_SCALE = 0.8f;
    private static final float DISTANCE_TEXT_SCALE = 0.65f;

    private static final int COLOR_TITLE = 0xFFFF5555;
    private static final int COLOR_INDICATOR = 0xFF2C2E2C;
    private static final int COLOR_DISTANCE = 0xFFFFFFFF;

    private static final ResourceLocation RED_BG = ResourceLocation.withDefaultNamespace("boss_bar/red_background");
    private static final ResourceLocation RED_PROGRESS = ResourceLocation.withDefaultNamespace("boss_bar/red_progress");

    private final PortalManager portalManager;
    private final SuppressionManager suppressionManager;

    public PortalBossBarRenderer(PortalManager portalManager, SuppressionManager suppressionManager) {
        this.portalManager = portalManager;
        this.suppressionManager = suppressionManager;
    }

    public void onHudRender(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!portalManager.isActive()) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) return;
        if (suppressionManager.isSuppressed()) return;

        Font font = client.font;
        int screenWidth = graphics.guiWidth();
        int screenCenterX = screenWidth / 2;
        int barX = screenCenterX - BAR_WIDTH / 2;

        String titleText = portalManager.getDisplayText();
        drawScaledCenteredString(graphics, font, Component.literal(titleText), screenCenterX, TITLE_Y,
                TITLE_TEXT_SCALE, COLOR_TITLE);

        int horizontalDist = (int) portalManager.getHorizontalDistance();
        double verticalDelta = portalManager.getVerticalDelta();
        Component distText;
        if (Math.abs(verticalDelta) > 2.0) {
            int vertDist = (int) verticalDelta;
            distText = vertDist > 0 ? Component.translatable("text.saa.portal.tracker_above", horizontalDist, Math.abs(vertDist)) : Component.translatable("text.saa.portal.tracker_below", horizontalDist, Math.abs(vertDist));
        } else {
            distText = Component.translatable("text.saa.portal.tracker");
        }
        drawScaledCenteredString(graphics, font, distText, screenCenterX, DISTANCE_Y,
                DISTANCE_TEXT_SCALE, COLOR_DISTANCE);

        graphics.blitSprite(RED_BG, barX, BAR_Y, BAR_WIDTH, BAR_HEIGHT);
        graphics.blitSprite(RED_PROGRESS, barX, BAR_Y, BAR_WIDTH, BAR_HEIGHT);

        double relativeAngle = portalManager.getRelativeAngle();
        int barCenter = barX + BAR_WIDTH / 2;
        int rawCenterX = barCenter + (int) ((relativeAngle / 180.0) * (BAR_WIDTH / 2));
        int indicatorLeft = rawCenterX - INDICATOR_WIDTH / 2;
        indicatorLeft = Math.max(barX, Math.min(barX + BAR_WIDTH - INDICATOR_WIDTH, indicatorLeft));
        int indicatorCenterX = indicatorLeft + INDICATOR_WIDTH / 2;
        int indicatorTop = BAR_Y + (BAR_HEIGHT - INDICATOR_HEIGHT) / 2;

        graphics.fill(indicatorLeft, indicatorTop,
                indicatorLeft + INDICATOR_WIDTH, indicatorTop + INDICATOR_HEIGHT, COLOR_INDICATOR);

        if (verticalDelta > 1.0) {
            drawTriangleUp(graphics, indicatorCenterX, indicatorTop - TRIANGLE_HEIGHT - TRIANGLE_GAP);
        } else if (verticalDelta < -1.0) {
            drawTriangleDown(graphics, indicatorCenterX, indicatorTop + INDICATOR_HEIGHT + TRIANGLE_GAP);
        }
    }

    private void drawScaledCenteredString(GuiGraphics graphics, Font font, Component text,
                                          int centerX, int y, float scale, int color) {
        float textWidth = font.width(text);
        float scaledHalfWidth = (textWidth * scale) / 2.0f;
        graphics.pose().pushPose();
        graphics.pose().translate(centerX - scaledHalfWidth, y, 0);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.drawString(font, text, 0, 0, color, true);
        graphics.pose().popPose();
    }

    private void drawTriangleUp(GuiGraphics graphics, int centerX, int topY) {
        for (int row = 0; row < TRIANGLE_HEIGHT; row++) {
            int halfWidth = row;
            graphics.fill(centerX - halfWidth, topY + row,
                    centerX + halfWidth + 1, topY + row + 1, COLOR_INDICATOR);
        }
    }

    private void drawTriangleDown(GuiGraphics graphics, int centerX, int topY) {
        for (int row = 0; row < TRIANGLE_HEIGHT; row++) {
            int halfWidth = (TRIANGLE_HEIGHT - 1 - row);
            graphics.fill(centerX - halfWidth, topY + row,
                    centerX + halfWidth + 1, topY + row + 1, COLOR_INDICATOR);
        }
    }
}
