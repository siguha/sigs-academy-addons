package com.siguha.sigsacademyaddons.feature.cardgrading;

import com.siguha.sigsacademyaddons.config.HudConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayDeque;
import java.util.Deque;

public class CardGradingSoundPlayer {

    private final HudConfig hudConfig;
    private final Deque<ScheduledNote> noteQueue = new ArrayDeque<>();
    private int tickCounter = 0;

    public CardGradingSoundPlayer(HudConfig hudConfig) {
        this.hudConfig = hudConfig;
    }

    public void playReadySound() {
        if (!hudConfig.isCardGradingSoundsEnabled()) {
            return;
        }

        queueDingPattern();
    }

    public void playTestSound() {
        queueDingPattern();
    }

    public void clearQueue() {
        noteQueue.clear();
    }

    public void tick() {
        tickCounter++;

        if (noteQueue.isEmpty()) {
            return;
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        while (!noteQueue.isEmpty() && noteQueue.peek().scheduledTick <= tickCounter) {
            ScheduledNote note = noteQueue.poll();
            switch (note.type) {
                case BIT -> player.playSound(SoundEvents.NOTE_BLOCK_BIT.value(), note.volume, note.pitch);
                case BELL -> player.playSound(SoundEvents.NOTE_BLOCK_BELL.value(), note.volume, note.pitch);
            }
        }
    }

    private void queueDingPattern() {
        int baseTick = tickCounter;
        noteQueue.add(new ScheduledNote(baseTick, NoteType.BIT, 1.45f, 0.75f));
        noteQueue.add(new ScheduledNote(baseTick + 4, NoteType.BELL, 1.8f, 0.9f));
    }

    private enum NoteType {
        BIT,
        BELL
    }

    private record ScheduledNote(int scheduledTick, NoteType type, float pitch, float volume) {}
}
