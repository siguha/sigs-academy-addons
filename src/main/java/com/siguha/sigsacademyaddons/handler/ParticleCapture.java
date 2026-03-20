package com.siguha.sigsacademyaddons.handler;

import com.siguha.sigsacademyaddons.SigsAcademyAddons;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ParticleCapture {

    private static volatile boolean capturing = false;
    private static final Queue<ParticleEntry> entries = new ConcurrentLinkedQueue<>();
    private static double captureRadius = 30.0;
    private static int remainingTicks = 0;
    private static int totalTicks = 0;
    private static long captureStartMs = 0;

    private static final int PROGRESS_INTERVAL = 40;

    public static void startCapture(int durationSeconds, double radius) {
        entries.clear();
        captureRadius = radius;
        totalTicks = durationSeconds * 20;
        remainingTicks = totalTicks;
        captureStartMs = System.currentTimeMillis();
        capturing = true;
        SigsAcademyAddons.LOGGER.info("[SAA] Particle capture started: {}s, radius {}", durationSeconds, radius);
    }

    public static boolean isCapturing() {
        return capturing;
    }

    public static void tick() {
        if (!capturing) return;
        remainingTicks--;

        // Show progress every 2 seconds
        int elapsed = totalTicks - remainingTicks;
        if (elapsed > 0 && elapsed % PROGRESS_INTERVAL == 0) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                int secondsElapsed = elapsed / 20;
                int secondsRemaining = remainingTicks / 20;
                player.sendSystemMessage(Component.translatable(
                    "text.saa.particle_capture.progress",
                    entries.size(), secondsElapsed, secondsRemaining)
                        .withStyle(ChatFormatting.YELLOW));
            }
        }

        if (remainingTicks <= 0) {
            capturing = false;
            String path = dumpResults();
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                if (path != null) {
                        player.sendSystemMessage(Component.translatable(
                            "text.saa.particle_capture.complete",
                            entries.size(), path)
                                .withStyle(ChatFormatting.GREEN));
                } else {
                        player.sendSystemMessage(Component.translatable("text.saa.particle_capture.failed")
                                .withStyle(ChatFormatting.RED));
                }
            }
        }
    }

    public static void onParticlePacket(ParticleOptions particle, double x, double y, double z,
                                         float xDist, float yDist, float zDist,
                                         float speed, int count, boolean overrideLimiter) {
        if (!capturing) return;
        captureEntry(particle, x, y, z, "packet");
    }

    public static void onLevelParticle(ParticleOptions particle, double x, double y, double z, String source) {
        if (!capturing) return;
        captureEntry(particle, x, y, z, source);
    }

    private static void captureEntry(ParticleOptions particle, double x, double y, double z, String source) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        double dx = x - player.getX();
        double dy = y - player.getY();
        double dz = z - player.getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist > captureRadius) return;

        String typeKey = BuiltInRegistries.PARTICLE_TYPE.getKey(particle.getType()).toString();
        long elapsed = System.currentTimeMillis() - captureStartMs;
        entries.add(new ParticleEntry(typeKey, x, y, z, source, elapsed));
    }

    private static String dumpResults() {
        List<ParticleEntry> captured = new ArrayList<>(entries);
        LocalPlayer player = Minecraft.getInstance().player;

        StringBuilder sb = new StringBuilder();
        sb.append("=== SAA Particle Dump ===\n");
        sb.append("Timestamp: ").append(LocalDateTime.now()
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n");
        if (player != null) {
            sb.append("Player: ").append(String.format("%.1f, %.1f, %.1f",
                    player.getX(), player.getY(), player.getZ())).append("\n");
        }
        sb.append("Capture Radius: ").append(captureRadius).append(" blocks\n");
        sb.append("Total Captured: ").append(captured.size()).append("\n");

        Map<String, Long> sourceCounts = new LinkedHashMap<>();
        for (ParticleEntry e : captured) {
            sourceCounts.merge(e.source, 1L, Long::sum);
        }
        for (Map.Entry<String, Long> sc : sourceCounts.entrySet()) {
            sb.append("  Source [").append(sc.getKey()).append("]: ").append(sc.getValue()).append("\n");
        }
        sb.append("\n");

        Map<String, List<ParticleEntry>> grouped = new LinkedHashMap<>();
        for (ParticleEntry e : captured) {
            grouped.computeIfAbsent(e.type, k -> new ArrayList<>()).add(e);
        }

        List<Map.Entry<String, List<ParticleEntry>>> sortedGroups = new ArrayList<>(grouped.entrySet());
        sortedGroups.sort((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()));

        sb.append("========== PARTICLE SUMMARY (by type, sorted by count) ==========\n\n");
        for (Map.Entry<String, List<ParticleEntry>> group : sortedGroups) {
            List<ParticleEntry> list = group.getValue();

            Map<String, Long> typeSources = new LinkedHashMap<>();
            for (ParticleEntry e : list) {
                typeSources.merge(e.source, 1L, Long::sum);
            }

            sb.append(group.getKey()).append(" -- ").append(list.size()).append(" total");
            sb.append(" (");
            boolean first = true;
            for (Map.Entry<String, Long> ts : typeSources.entrySet()) {
                if (!first) sb.append(", ");
                sb.append(ts.getKey()).append("=").append(ts.getValue());
                first = false;
            }
            sb.append(")\n");

            double avgX = 0, avgY = 0, avgZ = 0;
            for (ParticleEntry e : list) {
                avgX += e.x;
                avgY += e.y;
                avgZ += e.z;
            }
            avgX /= list.size();
            avgY /= list.size();
            avgZ /= list.size();
            sb.append("  Avg position: ").append(String.format("%.2f, %.2f, %.2f",
                    avgX, avgY, avgZ)).append("\n");

            double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
            double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
            for (ParticleEntry e : list) {
                minX = Math.min(minX, e.x); maxX = Math.max(maxX, e.x);
                minY = Math.min(minY, e.y); maxY = Math.max(maxY, e.y);
                minZ = Math.min(minZ, e.z); maxZ = Math.max(maxZ, e.z);
            }
            sb.append("  Spread: X[").append(String.format("%.1f..%.1f", minX, maxX));
            sb.append("] Y[").append(String.format("%.1f..%.1f", minY, maxY));
            sb.append("] Z[").append(String.format("%.1f..%.1f", minZ, maxZ)).append("]\n");
            sb.append("\n");
        }

        sb.append("========== RAW PARTICLE DATA ==========\n\n");
        for (ParticleEntry e : captured) {
            sb.append(String.format("[+%.1fs] [%s] %s @ %.2f, %.2f, %.2f\n",
                    e.elapsedMs / 1000.0, e.source, e.type, e.x, e.y, e.z));
        }

        try {
            Path dumpDir = Path.of(SigsAcademyAddons.CONFIG_DIR, "dumps");
            Files.createDirectories(dumpDir);
            String timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            Path dumpFile = dumpDir.resolve("particle-dump-" + timestamp + ".txt");
            Files.writeString(dumpFile, sb.toString());
            return dumpFile.toAbsolutePath().toString();
        } catch (IOException e) {
            SigsAcademyAddons.LOGGER.error("[SAA] Failed to write particle dump file", e);
            return null;
        }
    }

    private record ParticleEntry(String type, double x, double y, double z,
                                  String source, long elapsedMs) {}
}
