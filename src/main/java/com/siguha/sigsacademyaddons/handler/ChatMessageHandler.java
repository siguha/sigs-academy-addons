package com.siguha.sigsacademyaddons.handler;

import com.siguha.sigsacademyaddons.SigsAcademyAddons;
import com.siguha.sigsacademyaddons.config.HudConfig;
import com.siguha.sigsacademyaddons.feature.daycare.DaycareManager;
import com.siguha.sigsacademyaddons.feature.portal.PortalManager;
import com.siguha.sigsacademyaddons.feature.safari.SafariHuntManager;
import com.siguha.sigsacademyaddons.feature.safari.SafariManager;
import com.siguha.sigsacademyaddons.feature.wondertrade.WondertradeManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatMessageHandler {

    private static final Pattern SAFARI_ENTRY_PATTERN = Pattern.compile(
            "You have used a Safari Ticket for a (\\d+) minute Safari Zone entry!"
    );
    private static final Pattern WT_COOLDOWN_PATTERN = Pattern.compile(
            "You are on cooldown for (\\d+) minutes?"
    );
    private static final Pattern PORTAL_SPAWN_PATTERN = Pattern.compile(
            "A tier (\\d+) (Team Hideout|Raid Portal) has opened\\s+nearby!",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PARTY_INVITE_PATTERN = Pattern.compile(
            "You have been invited to a party by (\\S+)!"
    );

    private static final String HUNT_PROGRESS_MESSAGE = "Safari Hunt progress updated!";
    private static final String EGG_CREATED_MESSAGE = "An egg was created!";
    private static final String EGG_HATCHED_MESSAGE = "An egg has hatched!";

    private final SafariManager safariManager;
    private final SafariHuntManager safariHuntManager;
    private final CatchDetector catchDetector;
    private final DaycareManager daycareManager;
    private final WondertradeManager wondertradeManager;
    private final PortalManager portalManager;
    private final HudConfig hudConfig;

    public ChatMessageHandler(SafariManager safariManager, SafariHuntManager safariHuntManager,
                              CatchDetector catchDetector, DaycareManager daycareManager,
                              WondertradeManager wondertradeManager, PortalManager portalManager,
                              HudConfig hudConfig) {
        this.safariManager = safariManager;
        this.safariHuntManager = safariHuntManager;
        this.catchDetector = catchDetector;
        this.daycareManager = daycareManager;
        this.wondertradeManager = wondertradeManager;
        this.portalManager = portalManager;
        this.hudConfig = hudConfig;
    }

    private static MutableComponent buildClickSuffix(String command) {
        return Component.literal(" - ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal("Click Here")
                        .withStyle(Style.EMPTY
                                .withColor(ChatFormatting.AQUA)
                                .withUnderlined(true)
                                .withClickEvent(new ClickEvent(
                                        ClickEvent.Action.RUN_COMMAND, command))));
    }

    public Component modifyGameMessage(Component message, boolean overlay) {
        if (overlay) return message;

        String text = message.getString();

        if (text.contains(EGG_CREATED_MESSAGE)) {
            String species = daycareManager.getEggCreatorSpecies();
            int penNumber = daycareManager.getEggCreatorPenNumber();
            String command = penNumber > 0 ? "/saa daycare goto pen " + penNumber : "/daycare";
            daycareManager.onEggCreated();
            if (species != null) {
                return injectSpeciesAndAppendClick(text, species, command);
            }
            return message.copy().append(buildClickSuffix(command));
        }

        if (text.contains(EGG_HATCHED_MESSAGE)) {
            String species = daycareManager.getClosestHatchingEggSpecies();
            String command = "/saa daycare goto backpack";
            daycareManager.onEggHatched();
            if (species != null) {
                return injectSpeciesAndAppendClick(text, species, command);
            }
            return message.copy().append(buildClickSuffix(command));
        }

        Matcher portalMatcher = PORTAL_SPAWN_PATTERN.matcher(text);
        if (portalMatcher.find()) {
            try {
                int tier = Integer.parseInt(portalMatcher.group(1));
                String typeStr = portalMatcher.group(2).toLowerCase();
                PortalManager.PortalType type = typeStr.contains("hideout")
                        ? PortalManager.PortalType.HIDEOUT
                        : PortalManager.PortalType.RAID;
                int portalId = portalManager.registerPendingPortal(type, tier);
                if (portalId >= 0) {
                    return message.copy().append(buildPortalTrackSuffix(portalId));
                } else {
                    return message.copy().append(buildPortalUnableSuffix());
                }
            } catch (NumberFormatException e) {
                SigsAcademyAddons.LOGGER.warn("[SAA Portal] Failed to parse portal tier from: {}", text);
            }
        }

        return message;
    }

    private static MutableComponent buildPortalTrackSuffix(int portalId) {
        return Component.literal(" - ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal("Track")
                        .withStyle(Style.EMPTY
                                .withColor(ChatFormatting.AQUA)
                                .withUnderlined(true)
                                .withClickEvent(new ClickEvent(
                                        ClickEvent.Action.RUN_COMMAND,
                                        "/saa portal track " + portalId))));
    }

    private static MutableComponent buildPortalUnableSuffix() {
        return Component.literal(" - ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal("Unable to locate")
                        .withStyle(ChatFormatting.RED));
    }

    private static Component injectSpeciesAndAppendClick(String text, String species, String command) {
        int eggIdx = text.indexOf("egg ");
        if (eggIdx < 0) {
            return Component.literal(text).withStyle(ChatFormatting.GREEN)
                    .append(buildClickSuffix(command));
        }
        String before = text.substring(0, eggIdx + 4);
        String after = text.substring(eggIdx + 4);
        return Component.literal(before).withStyle(ChatFormatting.GREEN)
                .append(Component.literal("(" + species + ") ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(after).withStyle(ChatFormatting.GREEN))
                .append(buildClickSuffix(command));
    }

    public void onGameMessage(Component message, boolean overlay) {
        if (overlay) {
            return;
        }

        String text = message.getString();

        Matcher safariMatcher = SAFARI_ENTRY_PATTERN.matcher(text);
        if (safariMatcher.find()) {
            try {
                int minutes = Integer.parseInt(safariMatcher.group(1));
                safariManager.onSafariEntry(minutes);

            } catch (NumberFormatException e) {
                SigsAcademyAddons.LOGGER.warn("[SAA Safari] Failed to parse safari duration from: {}", text);
            }
            return;
        }

        if (text.contains(HUNT_PROGRESS_MESSAGE)) {
            safariHuntManager.onHuntProgressUpdate();
            catchDetector.requestPcScan();

            return;
        }

        Matcher wtMatcher = WT_COOLDOWN_PATTERN.matcher(text);
        if (wtMatcher.find()) {
            try {
                int minutes = Integer.parseInt(wtMatcher.group(1));
                wondertradeManager.onCooldownMessage(minutes);
            } catch (NumberFormatException e) {
                SigsAcademyAddons.LOGGER.warn("[SAA WT] Failed to parse cooldown minutes from: {}", text);
            }
            return;
        }

        if (hudConfig.isAutoAcceptPartyInvites()) {
            Matcher partyMatcher = PARTY_INVITE_PATTERN.matcher(text);
            if (partyMatcher.find()) {
                String inviterName = partyMatcher.group(1);
                Minecraft mc = Minecraft.getInstance();
                if (mc.getConnection() != null) {
                    mc.getConnection().sendCommand("party accept " + inviterName);
                    SigsAcademyAddons.LOGGER.info("[SAA] Auto-accepted party invite from {}", inviterName);
                }
            }
        }

    }
}
