package com.siguha.sigsacademyaddons.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.siguha.sigsacademyaddons.SigsAcademyAddonsClient;
import com.siguha.sigsacademyaddons.config.HudConfig;
import com.siguha.sigsacademyaddons.feature.safari.HuntEntityTracker;
import com.siguha.sigsacademyaddons.feature.safari.SafariManager;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Map;

// draws tracer lines from player to hunt-matching pokemon (depth test disabled, per-slot color)
public class HuntLineRenderer {

    private static final float LINE_WIDTH = 4.0f;
    private static final float LINE_ALPHA = 0.8f;
    private static final double TRACER_RANGE = 16.0;

    private final SafariManager safariManager;
    private final HudConfig hudConfig;

    public HuntLineRenderer(SafariManager safariManager, HudConfig hudConfig) {
        this.safariManager = safariManager;
        this.hudConfig = hudConfig;
    }

    // called every frame by WorldRenderEvents.LAST
    public void onWorldRenderLast(WorldRenderContext context) {
        if (!hudConfig.isSafariQuestMonTracers()) return;
        if (!safariManager.isInSafariZone()) return;

        HuntEntityTracker tracker = SigsAcademyAddonsClient.getHuntEntityTracker();
        if (tracker == null) return;

        Map<Integer, Integer> matched = tracker.getMatchedEntities();
        if (matched.isEmpty()) return;

        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;

        Vec3 cameraPos = context.camera().getPosition();

        // player chest-level position as line origin
        float partialTick = context.tickCounter().getGameTimeDeltaPartialTick(false);
        Vec3 playerPos = client.player.getEyePosition(partialTick).subtract(0, 0.4, 0);

        // camera-relative player position
        float playerRelX = (float) (playerPos.x - cameraPos.x);
        float playerRelY = (float) (playerPos.y - cameraPos.y);
        float playerRelZ = (float) (playerPos.z - cameraPos.z);
        PoseStack poseStack = context.matrixStack();
        poseStack.pushPose();
        Matrix4f modelView = poseStack.last().pose();

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.lineWidth(LINE_WIDTH);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        for (Map.Entry<Integer, Integer> entry : matched.entrySet()) {
            int entityId = entry.getKey();
            int color = entry.getValue();

            Entity entity = client.level.getEntity(entityId);
            if (entity == null || entity.isRemoved()) continue;

            Vec3 entityPos = entity.getPosition(partialTick)
                    .add(0, entity.getBbHeight() / 2.0, 0);

            if (playerPos.distanceTo(entityPos) > TRACER_RANGE) continue;

            float entityRelX = (float) (entityPos.x - cameraPos.x);
            float entityRelY = (float) (entityPos.y - cameraPos.y);
            float entityRelZ = (float) (entityPos.z - cameraPos.z);

            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g = ((color >> 8) & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;

            buffer.addVertex(modelView, playerRelX, playerRelY, playerRelZ)
                    .setColor(r, g, b, LINE_ALPHA);
            buffer.addVertex(modelView, entityRelX, entityRelY, entityRelZ)
                    .setColor(r, g, b, LINE_ALPHA);
        }

        // build() returns null when no vertices were added
        MeshData meshData = buffer.build();
        if (meshData != null) {
            BufferUploader.drawWithShader(meshData);
        }

        // restore render state
        poseStack.popPose();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.lineWidth(1.0f);
    }
}
