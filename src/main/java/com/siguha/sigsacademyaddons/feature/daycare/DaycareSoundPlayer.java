package com.siguha.sigsacademyaddons.feature.daycare;

import com.siguha.sigsacademyaddons.config.HudConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayDeque;
import java.util.Deque;

public class DaycareSoundPlayer {

    private final HudConfig hudConfig;
    private final Deque<ScheduledNote> noteQueue = new ArrayDeque<>();
    private int tickCounter = 0;

    public DaycareSoundPlayer(HudConfig hudConfig) {
        this.hudConfig = hudConfig;
    }

    public void playEggCreatedSound() {
        if (!hudConfig.isDaycareSoundsEnabled()) return;

        int baseTick = tickCounter;
        noteQueue.add(new ScheduledNote(baseTick, NoteType.BELL, 1.0f, 0.8f));
        noteQueue.add(new ScheduledNote(baseTick + 4, NoteType.BELL, 1.5f, 0.8f));
    }

    public void playEggHatchedSound() {
        if (!hudConfig.isDaycareSoundsEnabled()) return;

        int baseTick = tickCounter;
        noteQueue.add(new ScheduledNote(baseTick, NoteType.CHIME, 1.0f, 0.8f));
        noteQueue.add(new ScheduledNote(baseTick + 3, NoteType.CHIME, 1.25f, 0.8f));
        noteQueue.add(new ScheduledNote(baseTick + 6, NoteType.CHIME, 1.5f, 0.9f));
    }

    public void tick() {
        tickCounter++;

        if (noteQueue.isEmpty()) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        while (!noteQueue.isEmpty() && noteQueue.peek().scheduledTick <= tickCounter) {
            ScheduledNote note = noteQueue.poll();
            switch (note.type) {
                case BELL -> player.playSound(
                        SoundEvents.NOTE_BLOCK_BELL.value(),
                        note.volume, note.pitch);
                case CHIME -> player.playSound(
                        SoundEvents.NOTE_BLOCK_CHIME.value(),
                        note.volume, note.pitch);
            }
        }
    }

    private enum NoteType { BELL, CHIME }

    private record ScheduledNote(int scheduledTick, NoteType type, float pitch, float volume) {}
}
