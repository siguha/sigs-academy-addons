package com.siguha.sigsacademyaddons.handler;

import com.siguha.sigsacademyaddons.SigsAcademyAddons;
import com.siguha.sigsacademyaddons.feature.daycare.DaycareManager;
import com.siguha.sigsacademyaddons.feature.safari.SafariHuntManager;
import com.siguha.sigsacademyaddons.feature.safari.SafariManager;
import com.siguha.sigsacademyaddons.feature.wondertrade.WondertradeManager;
import net.minecraft.ChatFormatting;
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

    private static final String HUNT_PROGRESS_MESSAGE = "Safari Hunt progress updated!";
    private static final String EGG_CREATED_MESSAGE = "An egg was created!";
    private static final String EGG_HATCHED_MESSAGE = "An egg has hatched!";

    private final SafariManager safariManager;
    private final SafariHuntManager safariHuntManager;
    private final CatchDetector catchDetector;
    private final DaycareManager daycareManager;
    private final WondertradeManager wondertradeManager;

    public ChatMessageHandler(SafariManager safariManager, SafariHuntManager safariHuntManager,
                              CatchDetector catchDetector, DaycareManager daycareManager,
                              WondertradeManager wondertradeManager) {
        this.safariManager = safariManager;
        this.safariHuntManager = safariHuntManager;
        this.catchDetector = catchDetector;
        this.daycareManager = daycareManager;
        this.wondertradeManager = wondertradeManager;
    }

    private static final MutableComponent DAYCARE_CLICK_SUFFIX = buildClickSuffix("/daycare");
    private static final MutableComponent BACKPACK_CLICK_SUFFIX = buildClickSuffix("/saa daycare goto backpack");

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
            if (species != null) {
                daycareManager.onEggCreated();
                return buildDaycareClickMessage(species, " egg was created!", command);
            }
            return message.copy().append(DAYCARE_CLICK_SUFFIX);
        }

        if (text.contains(EGG_HATCHED_MESSAGE)) {
            String species = daycareManager.getClosestHatchingEggSpecies();
            daycareManager.onEggHatched();
            if (species != null) {
                return buildDaycareClickMessage(species, " egg hatched!", "/saa daycare goto backpack");
            }
            return message.copy().append(BACKPACK_CLICK_SUFFIX);
        }

        return message;
    }

    private static Component buildDaycareClickMessage(String species, String action, String command) {
        return Component.literal("[SAA] A ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal(species)
                        .withStyle(Style.EMPTY
                                .withColor(ChatFormatting.GOLD)
                                .withBold(true)))
                .append(Component.literal(action + " - ")
                        .withStyle(ChatFormatting.GREEN))
                .append(Component.literal("Click Here")
                        .withStyle(Style.EMPTY
                                .withColor(ChatFormatting.AQUA)
                                .withUnderlined(true)
                                .withClickEvent(new ClickEvent(
                                        ClickEvent.Action.RUN_COMMAND, command))));
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

        if (text.contains(EGG_CREATED_MESSAGE)) {
            daycareManager.onEggCreated();
            return;
        }

        if (text.contains(EGG_HATCHED_MESSAGE)) {
            daycareManager.onEggHatched();
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
    }
}
