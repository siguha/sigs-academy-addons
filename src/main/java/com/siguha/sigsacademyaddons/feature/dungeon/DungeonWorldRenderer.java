package com.siguha.sigsacademyaddons.feature.dungeon;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

public class DungeonWorldRenderer {

    private static final float STAKE_R = 0.6f, STAKE_G = 0.0f, STAKE_B = 0.8f;
    private static final float POKELOOT_R = 0.0f, POKELOOT_G = 0.8f, POKELOOT_B = 0.8f;
    private static final float CHEST_R = 1.0f, CHEST_G = 0.6f, CHEST_B = 0.0f;
    private static final float FILL_ALPHA = 0.35f;
    private static final float WIRE_ALPHA = 0.8f;

    private final DungeonManager manager;

    public DungeonWorldRenderer(DungeonManager manager) {
        this.manager = manager;
    }

    public void onWorldRender(WorldRenderContext context) {
        if (!manager.isInDungeon() || !manager.isSpawnCaptured()) return;

        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;

        PoseStack poseStack = context.matrixStack();
        Vec3 cam = context.camera().getPosition();

        poseStack.pushPose();
        beginOverlayState();

        renderEntityOverlays(poseStack, cam, client.level);
        renderBlockOverlays(poseStack, cam);

        endOverlayState();
        poseStack.popPose();
    }

    private void beginOverlayState() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
    }

    private void endOverlayState() {
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private void renderEntityOverlays(PoseStack poseStack, Vec3 cam, ClientLevel level) {
        List<DungeonEntityScanner.ScannedEntity> entities = manager.getEntityScanner().getScannedEntities();
        for (DungeonEntityScanner.ScannedEntity scanned : entities) {
            Entity entity = level.getEntity(scanned.entityId());
            if (entity == null) continue;

            float r, g, b;
            switch (scanned.type()) {
                case STAKE -> { r = STAKE_R; g = STAKE_G; b = STAKE_B; }
                case POKELOOT -> { r = POKELOOT_R; g = POKELOOT_G; b = POKELOOT_B; }
                default -> { continue; }
            }

            double ex = entity.getX(), ey = entity.getY(), ez = entity.getZ();
            renderOverlay(poseStack, cam, ex - 0.35, ey, ez - 0.35, ex + 0.35, ey + 0.8, ez + 0.35, r, g, b);
        }
    }

    private void renderBlockOverlays(PoseStack poseStack, Vec3 cam) {
        List<DungeonBlockScanner.ScannedBlock> blocks = manager.getBlockScanner().getScannedBlocks();
        for (DungeonBlockScanner.ScannedBlock scanned : blocks) {
            BlockPos pos = scanned.pos();
            if (manager.isChestOpened(pos)) continue;
            renderOverlay(poseStack, cam,
                    pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0,
                    CHEST_R, CHEST_G, CHEST_B);
        }
    }

    private void renderOverlay(PoseStack poseStack, Vec3 cam,
                                double x1, double y1, double z1,
                                double x2, double y2, double z2,
                                float r, float g, float b) {
        renderBox(poseStack, cam, x1, y1, z1, x2, y2, z2, r, g, b, FILL_ALPHA);
        renderWireframe(poseStack, cam, x1, y1, z1, x2, y2, z2, r, g, b, WIRE_ALPHA);
    }

    private void renderBox(PoseStack poseStack, Vec3 cam,
                            double x1, double y1, double z1,
                            double x2, double y2, double z2,
                            float r, float g, float b, float a) {
        Matrix4f matrix = poseStack.last().pose();
        float fx1 = (float) (x1 - cam.x), fy1 = (float) (y1 - cam.y), fz1 = (float) (z1 - cam.z);
        float fx2 = (float) (x2 - cam.x), fy2 = (float) (y2 - cam.y), fz2 = (float) (z2 - cam.z);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buf = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        buf.addVertex(matrix, fx1, fy1, fz1).setColor(r, g, b, a);
        buf.addVertex(matrix, fx2, fy1, fz1).setColor(r, g, b, a);
        buf.addVertex(matrix, fx2, fy1, fz2).setColor(r, g, b, a);
        buf.addVertex(matrix, fx1, fy1, fz2).setColor(r, g, b, a);

        buf.addVertex(matrix, fx1, fy2, fz1).setColor(r, g, b, a);
        buf.addVertex(matrix, fx1, fy2, fz2).setColor(r, g, b, a);
        buf.addVertex(matrix, fx2, fy2, fz2).setColor(r, g, b, a);
        buf.addVertex(matrix, fx2, fy2, fz1).setColor(r, g, b, a);

        buf.addVertex(matrix, fx1, fy1, fz1).setColor(r, g, b, a);
        buf.addVertex(matrix, fx1, fy2, fz1).setColor(r, g, b, a);
        buf.addVertex(matrix, fx2, fy2, fz1).setColor(r, g, b, a);
        buf.addVertex(matrix, fx2, fy1, fz1).setColor(r, g, b, a);

        buf.addVertex(matrix, fx1, fy1, fz2).setColor(r, g, b, a);
        buf.addVertex(matrix, fx2, fy1, fz2).setColor(r, g, b, a);
        buf.addVertex(matrix, fx2, fy2, fz2).setColor(r, g, b, a);
        buf.addVertex(matrix, fx1, fy2, fz2).setColor(r, g, b, a);

        buf.addVertex(matrix, fx1, fy1, fz1).setColor(r, g, b, a);
        buf.addVertex(matrix, fx1, fy1, fz2).setColor(r, g, b, a);
        buf.addVertex(matrix, fx1, fy2, fz2).setColor(r, g, b, a);
        buf.addVertex(matrix, fx1, fy2, fz1).setColor(r, g, b, a);

        buf.addVertex(matrix, fx2, fy1, fz1).setColor(r, g, b, a);
        buf.addVertex(matrix, fx2, fy2, fz1).setColor(r, g, b, a);
        buf.addVertex(matrix, fx2, fy2, fz2).setColor(r, g, b, a);
        buf.addVertex(matrix, fx2, fy1, fz2).setColor(r, g, b, a);

        BufferUploader.drawWithShader(buf.buildOrThrow());
    }

    private void renderWireframe(PoseStack poseStack, Vec3 cam,
                                  double x1, double y1, double z1,
                                  double x2, double y2, double z2,
                                  float r, float g, float b, float a) {
        Matrix4f matrix = poseStack.last().pose();
        float fx1 = (float) (x1 - cam.x), fy1 = (float) (y1 - cam.y), fz1 = (float) (z1 - cam.z);
        float fx2 = (float) (x2 - cam.x), fy2 = (float) (y2 - cam.y), fz2 = (float) (z2 - cam.z);

        RenderSystem.lineWidth(2.0f);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buf = tesselator.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        buf.addVertex(matrix, fx1, fy1, fz1).setColor(r, g, b, a);
        buf.addVertex(matrix, fx2, fy1, fz1).setColor(r, g, b, a);
        buf.addVertex(matrix, fx2, fy1, fz1).setColor(r, g, b, a);
        buf.addVertex(matrix, fx2, fy1, fz2).setColor(r, g, b, a);
        buf.addVertex(matrix, fx2, fy1, fz2).setColor(r, g, b, a);
        buf.addVertex(matrix, fx1, fy1, fz2).setColor(r, g, b, a);
        buf.addVertex(matrix, fx1, fy1, fz2).setColor(r, g, b, a);
        buf.addVertex(matrix, fx1, fy1, fz1).setColor(r, g, b, a);

        buf.addVertex(matrix, fx1, fy2, fz1).setColor(r, g, b, a);
        buf.addVertex(matrix, fx2, fy2, fz1).setColor(r, g, b, a);
        buf.addVertex(matrix, fx2, fy2, fz1).setColor(r, g, b, a);
        buf.addVertex(matrix, fx2, fy2, fz2).setColor(r, g, b, a);
        buf.addVertex(matrix, fx2, fy2, fz2).setColor(r, g, b, a);
        buf.addVertex(matrix, fx1, fy2, fz2).setColor(r, g, b, a);
        buf.addVertex(matrix, fx1, fy2, fz2).setColor(r, g, b, a);
        buf.addVertex(matrix, fx1, fy2, fz1).setColor(r, g, b, a);

        buf.addVertex(matrix, fx1, fy1, fz1).setColor(r, g, b, a);
        buf.addVertex(matrix, fx1, fy2, fz1).setColor(r, g, b, a);
        buf.addVertex(matrix, fx2, fy1, fz1).setColor(r, g, b, a);
        buf.addVertex(matrix, fx2, fy2, fz1).setColor(r, g, b, a);
        buf.addVertex(matrix, fx2, fy1, fz2).setColor(r, g, b, a);
        buf.addVertex(matrix, fx2, fy2, fz2).setColor(r, g, b, a);
        buf.addVertex(matrix, fx1, fy1, fz2).setColor(r, g, b, a);
        buf.addVertex(matrix, fx1, fy2, fz2).setColor(r, g, b, a);

        BufferUploader.drawWithShader(buf.buildOrThrow());
        RenderSystem.lineWidth(1.0f);
    }
}
