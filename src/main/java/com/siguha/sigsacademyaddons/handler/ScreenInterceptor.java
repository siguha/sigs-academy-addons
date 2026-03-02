package com.siguha.sigsacademyaddons.handler;

import com.siguha.sigsacademyaddons.SigsAcademyAddons;
import com.siguha.sigsacademyaddons.feature.safari.SafariHuntManager;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.core.component.DataComponents;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// scrapes hunt data from container screens by detecting paper items with "caught:" lore
public class ScreenInterceptor {

    private final SafariHuntManager safariHuntManager;
    private boolean hasScrapeScheduled = false;
    private int ticksWaited = 0;

    public ScreenInterceptor(SafariHuntManager safariHuntManager) {
        this.safariHuntManager = safariHuntManager;
    }

    // intercepts container screens to scrape hunts after slot sync
    public void onScreenInit(Minecraft client, Screen screen, int scaledWidth, int scaledHeight) {
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            return;
        }

        hasScrapeScheduled = false;
        ticksWaited = 0;

        // wait a few ticks for server slot sync then scan
        ScreenEvents.afterTick(screen).register(tickedScreen -> {
            if (hasScrapeScheduled) {
                return;
            }

            ticksWaited++;

            // wait for server to sync slot contents
            if (ticksWaited >= 5) {
                hasScrapeScheduled = true;
                tryIdentifyAndScrapeHunts(containerScreen);
            }
        });
    }

    // scans container for paper items with hunt lore, deduplicates by name
    private void tryIdentifyAndScrapeHunts(AbstractContainerScreen<?> containerScreen) {
        AbstractContainerMenu menu = containerScreen.getMenu();
        List<ScrapedHuntItem> scrapedItems = new ArrayList<>();
        Set<String> seenNames = new LinkedHashSet<>();

        for (Slot slot : menu.slots) {
            // skip player inventory slots
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) {
                continue;
            }

            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                continue;
            }

            if (!stack.is(Items.PAPER)) {
                continue;
            }

            Component itemName = stack.getHoverName();
            String itemNameStr = itemName.getString();

            // deduplicate multi-slot buttons
            if (seenNames.contains(itemNameStr)) {
                continue;
            }

            List<Component> loreLines = new ArrayList<>();
            ItemLore lore = stack.get(DataComponents.LORE);
            if (lore != null) {
                loreLines.addAll(lore.lines());
            }

            // check for "caught:" pattern in lore
            boolean isHuntItem = false;
            for (Component loreLine : loreLines) {
                String lineText = loreLine.getString();
                if (lineText.contains("Caught:")) {
                    isHuntItem = true;
                    break;
                }
            }

            if (isHuntItem) {
                seenNames.add(itemNameStr);
                List<String> loreStrings = loreLines.stream().map(Component::getString).toList();

                scrapedItems.add(new ScrapedHuntItem(itemNameStr, loreStrings));
            }
        }

        if (!scrapedItems.isEmpty()) {
            SigsAcademyAddons.LOGGER.info("[sig Safari] Scraped {} hunts from HUNTS screen", scrapedItems.size());
            safariHuntManager.onHuntsScreenScraped(scrapedItems);
        }
    }

    public record ScrapedHuntItem(String name, List<String> loreLines) {
    }
}
