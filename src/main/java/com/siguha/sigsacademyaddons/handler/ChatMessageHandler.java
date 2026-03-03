package com.siguha.sigsacademyaddons.handler;

import com.siguha.sigsacademyaddons.SigsAcademyAddons;
import com.siguha.sigsacademyaddons.feature.daycare.DaycareManager;
import com.siguha.sigsacademyaddons.feature.safari.SafariHuntManager;
import com.siguha.sigsacademyaddons.feature.safari.SafariManager;
import net.minecraft.network.chat.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class ChatMessageHandler {

    private static final Pattern SAFARI_ENTRY_PATTERN = Pattern.compile(
            "You have used a Safari Ticket for a (\\d+) minute Safari Zone entry!"
    );

    private static final String HUNT_PROGRESS_MESSAGE = "Safari Hunt progress updated!";
    private static final String EGG_CREATED_MESSAGE = "An egg was created!";
    private static final String EGG_HATCHED_MESSAGE = "An egg has hatched!";

    private final SafariManager safariManager;
    private final SafariHuntManager safariHuntManager;
    private final CatchDetector catchDetector;
    private final DaycareManager daycareManager;

    public ChatMessageHandler(SafariManager safariManager, SafariHuntManager safariHuntManager,
                              CatchDetector catchDetector, DaycareManager daycareManager) {
        this.safariManager = safariManager;
        this.safariHuntManager = safariHuntManager;
        this.catchDetector = catchDetector;
        this.daycareManager = daycareManager;
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
    }
}
