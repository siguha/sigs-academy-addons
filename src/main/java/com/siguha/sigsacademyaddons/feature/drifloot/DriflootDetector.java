package com.siguha.sigsacademyaddons.feature.drifloot;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.siguha.sigsacademyaddons.config.HudConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class DriflootDetector {

    private static final int SCAN_INTERVAL_TICKS = 200;
    private static final double SCAN_RADIUS = 64.0;

    private final HudConfig hudConfig;
    private final Deque<ScheduledNote> noteQueue = new ArrayDeque<>();
    private final Set<UUID> seenEntities = new HashSet<>();
    private int tickCounter = 0;

    public DriflootDetector(HudConfig hudConfig) {
        this.hudConfig = hudConfig;
    }

    public void tick() {
        tickCounter++;

        if (!noteQueue.isEmpty()) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                while (!noteQueue.isEmpty() && noteQueue.peek().scheduledTick <= tickCounter) {
                    ScheduledNote note = noteQueue.poll();
                    player.playSound(SoundEvents.NOTE_BLOCK_XYLOPHONE.value(), note.volume, note.pitch);
                }
            }
        }

        if (tickCounter % SCAN_INTERVAL_TICKS != 0) return;
        if (!hudConfig.isDriflootAlertsEnabled()) return;

        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        LocalPlayer player = client.player;
        if (level == null || player == null) return;

        AABB scanBox = player.getBoundingBox().inflate(SCAN_RADIUS);
        List<PokemonEntity> nearbyPokemon = level.getEntitiesOfClass(PokemonEntity.class, scanBox);

        seenEntities.removeIf(uuid -> nearbyPokemon.stream()
                .noneMatch(e -> e.getUUID().equals(uuid)));

        for (PokemonEntity pokemonEntity : nearbyPokemon) {
            try {
                if (pokemonEntity.getOwnerUUID() != null) continue;

                Pokemon pokemon = pokemonEntity.getPokemon();
                if (pokemon == null) continue;

                String speciesName = pokemon.getSpecies().getName();
                if (!speciesName.equalsIgnoreCase("Drifloon") && !speciesName.equalsIgnoreCase("Drifblim")) continue;
                if (!pokemon.getForcedAspects().contains("chest")) continue;

                if (seenEntities.add(pokemonEntity.getUUID())) {
                    playDriflootSound();
                    player.sendSystemMessage(Component.literal("Drifloot Spawned Nearby!")
                            .withStyle(ChatFormatting.GREEN));
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void playDriflootSound() {
        int baseTick = tickCounter;
        noteQueue.add(new ScheduledNote(baseTick, 1.0f, 0.8f));
        noteQueue.add(new ScheduledNote(baseTick + 3, 1.25f, 0.8f));
        noteQueue.add(new ScheduledNote(baseTick + 6, 1.5f, 0.9f));
    }

    private record ScheduledNote(int scheduledTick, float pitch, float volume) {}
}
