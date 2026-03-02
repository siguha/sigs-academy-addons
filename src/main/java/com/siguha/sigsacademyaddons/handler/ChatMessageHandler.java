package com.siguha.sigsacademyaddons.handler;

import com.siguha.sigsacademyaddons.SigsAcademyAddons;
import com.siguha.sigsacademyaddons.feature.safari.SafariHuntManager;
import com.siguha.sigsacademyaddons.feature.safari.SafariManager;
import net.minecraft.network.chat.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// routes server chat messages to safari/hunt feature managers
public class ChatMessageHandler {

    // matches safari ticket entry message, captures duration in minutes
    private static final Pattern SAFARI_ENTRY_PATTERN = Pattern.compile(
            "You have used a Safari Ticket for a (\\d+) minute Safari Zone entry!"
    );

    private static final String HUNT_PROGRESS_MESSAGE = "Safari Hunt progress updated!";

    private final SafariManager safariManager;
    private final SafariHuntManager safariHuntManager;
    private final CatchDetector catchDetector;

    public ChatMessageHandler(SafariManager safariManager, SafariHuntManager safariHuntManager,
                              CatchDetector catchDetector) {
        this.safariManager = safariManager;
        this.safariHuntManager = safariHuntManager;
        this.catchDetector = catchDetector;
    }

    // handles each server game message (ignores action bar overlays)
    public void onGameMessage(Component message, boolean overlay) {
        if (overlay) {
            return;
        }

        String text = message.getString();

        // check for safari entry
        Matcher safariMatcher = SAFARI_ENTRY_PATTERN.matcher(text);
        if (safariMatcher.find()) {
            try {
                int minutes = Integer.parseInt(safariMatcher.group(1));
                safariManager.onSafariEntry(minutes);
                SigsAcademyAddons.LOGGER.info("[sig Safari] Detected safari entry: {} minutes", minutes);
            } catch (NumberFormatException e) {
                SigsAcademyAddons.LOGGER.warn("[sig Safari] Failed to parse safari duration from: {}", text);
            }
            return;
        }

        // check for hunt progress update
        if (text.contains(HUNT_PROGRESS_MESSAGE)) {
            safariHuntManager.onHuntProgressUpdate();
            // trigger pc scan to auto-increment hunt progress
            catchDetector.requestPcScan();
            SigsAcademyAddons.LOGGER.debug("[sig Safari] Hunt progress update — PC scan triggered");
            return;
        }
    }
}
