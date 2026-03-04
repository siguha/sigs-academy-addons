package com.siguha.sigsacademyaddons.feature.wondertrade;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.storage.ClientParty;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.siguha.sigsacademyaddons.config.HudConfig;
import com.siguha.sigsacademyaddons.data.WondertradeDataStore;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class WondertradeManager {

    private static final long COOLDOWN_DURATION_MS = 61 * 60 * 1000L;
    private static final int POST_CLOSE_MONITOR_TICKS = 200;

    private final WondertradeDataStore dataStore;
    private final WondertradeSoundPlayer soundPlayer;
    private final HudConfig hudConfig;

    private long cooldownEndTimeMs = -1;
    private boolean timerInitialized = false;
    private boolean notifiedCompletion = false;

    private boolean wtScreenOpen = false;
    private Set<UUID> partySnapshot = null;
    private int postCloseMonitorTicks = 0;

    public WondertradeManager(WondertradeDataStore dataStore, WondertradeSoundPlayer soundPlayer,
                               HudConfig hudConfig) {
        this.dataStore = dataStore;
        this.soundPlayer = soundPlayer;
        this.hudConfig = hudConfig;
    }

    public void onServerJoined() {
        long loaded = dataStore.load();

        if (loaded > 0) {
            cooldownEndTimeMs = loaded;
            timerInitialized = true;
            notifiedCompletion = getRemainingMs() <= 0;
        }
    }

    public void onServerDisconnected() {
        cooldownEndTimeMs = -1;
        timerInitialized = false;
        notifiedCompletion = false;
        wtScreenOpen = false;
        partySnapshot = null;
    }

    public void onWtScreenOpened() {
        wtScreenOpen = true;
    }

    public void onWtScreenClicked() {
        partySnapshot = snapshotPartyUuids();
        postCloseMonitorTicks = POST_CLOSE_MONITOR_TICKS;
    }

    public void onWtScreenTick() {
        if (!wtScreenOpen || partySnapshot == null) return;

        checkForTradeCompletion();
    }

    public void onWtScreenClosed() {
        wtScreenOpen = false;
    }

    public void onCooldownMessage(int minutesRemaining) {
        if (timerInitialized && !notifiedCompletion) return;

        cooldownEndTimeMs = System.currentTimeMillis() + ((minutesRemaining + 1) * 60_000L);
        timerInitialized = true;
        notifiedCompletion = false;
        dataStore.save(cooldownEndTimeMs);
    }

    public void tick() {
        if (postCloseMonitorTicks > 0 && partySnapshot != null) {
            postCloseMonitorTicks--;
            checkForTradeCompletion();

            if (postCloseMonitorTicks <= 0) {
                partySnapshot = null;
            }
        }

        if (!timerInitialized || notifiedCompletion) return;

        if (getRemainingMs() <= 0) {
            notifiedCompletion = true;
            soundPlayer.playCooldownCompleteSound();

            if (hudConfig.isWtShowChatReminders()) {
                Minecraft client = Minecraft.getInstance();
                if (client.player != null) {
                    MutableComponent msg = Component.literal("Your wondertrade timer is over! - ")
                            .withStyle(ChatFormatting.GREEN)
                            .append(Component.literal("Click Here")
                                    .withStyle(Style.EMPTY
                                            .withColor(ChatFormatting.AQUA)
                                            .withUnderlined(true)
                                            .withClickEvent(new ClickEvent(
                                                    ClickEvent.Action.RUN_COMMAND, "/wt"))));
                    client.player.sendSystemMessage(msg);
                }
            }
        }
    }

    public void clearAll() {
        cooldownEndTimeMs = -1;
        timerInitialized = false;
        notifiedCompletion = false;
        dataStore.clear();
    }

    public boolean hasTimer() {
        return timerInitialized;
    }

    public boolean isCooldownOver() {
        return timerInitialized && getRemainingMs() <= 0;
    }

    public long getRemainingMs() {
        if (cooldownEndTimeMs < 0) return 0;

        return Math.max(0, cooldownEndTimeMs - System.currentTimeMillis());
    }

    public String getRemainingFormatted() {
        long remainingMs = getRemainingMs();
        if (remainingMs <= 0) return "0:00";

        long totalSeconds = remainingMs / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;

        return String.format("%d:%02d", minutes, seconds);
    }

    public float getProgress() {
        if (cooldownEndTimeMs < 0) return 0f;
        long remaining = getRemainingMs();
        float progress = 1.0f - ((float) remaining / COOLDOWN_DURATION_MS);

        return Math.max(0f, Math.min(1f, progress));
    }

    private void checkForTradeCompletion() {
        if (partySnapshot == null) return;
        Set<UUID> current = snapshotPartyUuids();
        if (current == null) return;

        if (!current.equals(partySnapshot)) {
            onSuccessfulTrade();
            partySnapshot = null;
            postCloseMonitorTicks = 0;
        }
    }

    private void onSuccessfulTrade() {
        cooldownEndTimeMs = System.currentTimeMillis() + COOLDOWN_DURATION_MS;
        timerInitialized = true;
        notifiedCompletion = false;
        dataStore.save(cooldownEndTimeMs);
    }

    private Set<UUID> snapshotPartyUuids() {
        try {
            ClientParty party = CobblemonClient.INSTANCE.getStorage().getParty();
            if (party == null) return null;

            Set<UUID> uuids = new HashSet<>();

            for (int slot = 0; slot < 6; slot++) {
                Pokemon pokemon = party.get(slot);
                if (pokemon != null) {
                    uuids.add(pokemon.getUuid());
                }
            }

            return uuids;
        } catch (Exception e) {
            
            return null;
        }
    }
}
