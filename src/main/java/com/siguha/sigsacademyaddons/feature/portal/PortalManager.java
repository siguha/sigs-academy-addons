package com.siguha.sigsacademyaddons.feature.portal;

import com.siguha.sigsacademyaddons.SigsAcademyAddons;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class PortalManager {

    public enum PortalType { HIDEOUT, RAID }

    public enum PendingState { SCANNING, LOCATED, FAILED }

    public static class PendingPortal {
        public final int id;
        public final PortalType type;
        public final int tier;
        public PendingState state;
        public BlockPos position;

        PendingPortal(int id, PortalType type, int tier) {
            this.id = id;
            this.type = type;
            this.tier = tier;
            this.state = PendingState.SCANNING;
            this.position = null;
        }
    }

    private static final double ARRIVAL_DISTANCE = 3.0;
    private static final double MAX_DISTANCE = 100.0;
    private static final int PORTAL_CHECK_INTERVAL = 100;
    private static final int MAX_PENDING_PORTALS = 10;

    private PortalType activeType;
    private int activeTier;
    private BlockPos portalPos;
    private ResourceKey<Level> portalDimension;

    private final Map<Integer, PendingPortal> pendingPortals = new LinkedHashMap<>();
    private int nextPortalId = 0;
    private int activeScanId = -1;

    private final Deque<ScheduledNote> noteQueue = new ArrayDeque<>();
    private int tickCounter = 0;

    public int registerPendingPortal(PortalType type, int tier) {
        playPortalSound();

        if (PortalParticleDetector.isScanning()) {
            SigsAcademyAddons.LOGGER.info("[SAA] Portal detected (tier {} {}) but scanner busy — unable to locate",
                    tier, type);
            return -1;
        }

        int id = nextPortalId++;
        PendingPortal pending = new PendingPortal(id, type, tier);
        pendingPortals.put(id, pending);
        activeScanId = id;

        PortalParticleDetector.startScan(this);

        while (pendingPortals.size() > MAX_PENDING_PORTALS) {
            Iterator<Integer> it = pendingPortals.keySet().iterator();
            it.next();
            it.remove();
        }

        SigsAcademyAddons.LOGGER.info("[SAA] Registered pending portal #{} — tier {} {}, scanning...",
                id, tier, type);
        return id;
    }

    public void onScanComplete(BlockPos position) {
        if (activeScanId >= 0 && pendingPortals.containsKey(activeScanId)) {
            PendingPortal pending = pendingPortals.get(activeScanId);
            if (position != null) {
                pending.position = position;
                pending.state = PendingState.LOCATED;
                SigsAcademyAddons.LOGGER.info("[SAA] Pending portal #{} located at {} {} {}",
                        activeScanId, position.getX(), position.getY(), position.getZ());

                if (!isActive()) {
                    activatePortal(pending);
                    pendingPortals.remove(activeScanId);
                    SigsAcademyAddons.LOGGER.info("[SAA] Auto-tracking portal #{}", activeScanId);
                }
            } else {
                pending.state = PendingState.FAILED;
                SigsAcademyAddons.LOGGER.warn("[SAA] Pending portal #{} — scan found no clusters", activeScanId);
            }
        }
        activeScanId = -1;
    }

    public Component trackPortal(int id) {
        PendingPortal pending = pendingPortals.get(id);
        if (pending == null) {
            // Could have been auto-tracked already
            if (isActive()) {
                return Component.translatable("text.saa.already_tracking").withStyle(ChatFormatting.GREEN);
            }
            return Component.translatable("text.saa.expired_tracking").withStyle(ChatFormatting.RED);
        }

        switch (pending.state) {
            case SCANNING:
                return Component.translatable("text.saa.scanning_tracking").withStyle(ChatFormatting.YELLOW);
            case FAILED:
                pendingPortals.remove(id);
                return Component.translatable("text.saa.failed_tracking").withStyle(ChatFormatting.RED);
            case LOCATED:
                break;
        }

        activatePortal(pending);
        pendingPortals.remove(id);

        Component typeText = pending.type == PortalType.HIDEOUT ? Component.translatable("text.saa.hideouts") : Component.translatable("text.saa.raids");
        Component trackingText = Component.translatable("text.saa.tracking", pending.tier, typeText, pending.position.getX(), pending.position.getY(), pending.position.getZ()).withStyle(ChatFormatting.GREEN);

        return trackingText;
    }

    private void activatePortal(PendingPortal pending) {
        clearActivePortal();
        this.activeType = pending.type;
        this.activeTier = pending.tier;
        this.portalPos = pending.position;
        LocalPlayer player = Minecraft.getInstance().player;
        this.portalDimension = player != null ? player.level().dimension() : null;
        PortalParticleDetector.startTracking(pending.position);
    }

    public boolean isActive() {
        return portalPos != null;
    }

    public void clear() {
        clearActivePortal();
        pendingPortals.clear();
        activeScanId = -1;
        PortalParticleDetector.clearTracking();
    }

    private void clearActivePortal() {
        portalPos = null;
        activeType = null;
        activeTier = 0;
        portalDimension = null;
        PortalParticleDetector.clearPresenceTracking();
    }

    public PortalType getActiveType() {
        return activeType;
    }

    public int getActiveTier() {
        return activeTier;
    }

    public BlockPos getPortalPos() {
        return portalPos;
    }

    public Component getDisplayText() {
        if (!isActive()) return Component.literal("");
        Component typeText = activeType == PortalType.HIDEOUT ? Component.translatable("text.saa.hideouts") : Component.translatable("text.saa.raids");

        return Component.translatable("text.saa.portal_display_name", activeTier, typeText);
    }

    public double getHorizontalDistance() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || portalPos == null) return 0;
        double dx = portalPos.getX() + 0.5 - player.getX();
        double dz = portalPos.getZ() + 0.5 - player.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    public double getRelativeAngle() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || portalPos == null) return 0;
        double dx = portalPos.getX() + 0.5 - player.getX();
        double dz = portalPos.getZ() + 0.5 - player.getZ();
        double worldAngle = Math.toDegrees(Math.atan2(-dx, dz));
        double relativeAngle = worldAngle - player.getYRot();
        return Mth.wrapDegrees(relativeAngle);
    }

    public double getVerticalDelta() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || portalPos == null) return 0;
        return portalPos.getY() - player.getEyeY();
    }

    public void tick() {
        tickCounter++;

        if (portalPos != null) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                if (player.level().dimension() != portalDimension) {
                    clear();
                    return;
                }

                double distance = getHorizontalDistance();
                if (distance < ARRIVAL_DISTANCE || distance > MAX_DISTANCE) {
                    clear();
                    return;
                }

                if (tickCounter % PORTAL_CHECK_INTERVAL == 0) {
                    if (!PortalParticleDetector.isPortalStillPresent()) {
                        clear();
                        return;
                    }
                }
            }
        }

        if (noteQueue.isEmpty()) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        while (!noteQueue.isEmpty() && noteQueue.peek().scheduledTick <= tickCounter) {
            ScheduledNote note = noteQueue.poll();
            player.playSound(SoundEvents.NOTE_BLOCK_BELL.value(), note.volume, note.pitch);
        }
    }

    private void playPortalSound() {
        int baseTick = tickCounter;
        noteQueue.add(new ScheduledNote(baseTick, 0.8f, 1.0f));
        noteQueue.add(new ScheduledNote(baseTick + 3, 1.0f, 1.0f));
        noteQueue.add(new ScheduledNote(baseTick + 6, 1.2f, 1.0f));
        noteQueue.add(new ScheduledNote(baseTick + 9, 1.5f, 1.0f));
        noteQueue.add(new ScheduledNote(baseTick + 14, 1.8f, 1.0f));
    }

    private record ScheduledNote(int scheduledTick, float pitch, float volume) {}
}
