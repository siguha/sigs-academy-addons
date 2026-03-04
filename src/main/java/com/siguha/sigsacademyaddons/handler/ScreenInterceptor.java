package com.siguha.sigsacademyaddons.handler;

import com.siguha.sigsacademyaddons.SigsAcademyAddons;
import com.siguha.sigsacademyaddons.feature.daycare.DaycareManager;
import com.siguha.sigsacademyaddons.feature.daycare.DaycareState;
import com.siguha.sigsacademyaddons.feature.safari.SafariHuntManager;
import com.siguha.sigsacademyaddons.feature.wondertrade.WondertradeManager;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScreenInterceptor {

    private static final Pattern PEN_LABEL_PATTERN = Pattern.compile("(?:Daycare )?Pen (\\d+)(.*)");

    private final SafariHuntManager safariHuntManager;
    private final DaycareManager daycareManager;
    private final WondertradeManager wondertradeManager;

    private boolean hasSafariScrapeScheduled = false;
    private int ticksWaited = 0;

    private boolean isDaycareScreen = false;
    private String lastDaycareTitle = "";
    private int daycareRescrapeCounter = 0;
    private int lastDaycareContentHash = 0;
    private int lastDetectedPenNumber = 1;
    private boolean backpackTabActive = false;

    private boolean isWtScreen = false;

    public ScreenInterceptor(SafariHuntManager safariHuntManager, DaycareManager daycareManager,
                              WondertradeManager wondertradeManager) {
        this.safariHuntManager = safariHuntManager;
        this.daycareManager = daycareManager;
        this.wondertradeManager = wondertradeManager;
    }

    public void onScreenInit(Minecraft client, Screen screen, int scaledWidth, int scaledHeight) {
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            return;
        }

        if (isDaycareScreen) {
            String newTitle = containerScreen.getTitle().getString();
            int newTitleNum = decodeTitleNumber(newTitle);
            String cleanNewTitle = newTitle.replaceAll("\u00A7[0-9a-fk-or]", "").trim();
            boolean newIsDaycare = (newTitleNum >= 1 && newTitleNum <= 9)
                    || cleanNewTitle.toUpperCase().contains("DAYCARE");

            if (!newIsDaycare) {
                daycareManager.onDaycareMenuClosed();
            }
        }

        if (isWtScreen) {
            wondertradeManager.onWtScreenClosed();
        }

        hasSafariScrapeScheduled = false;
        isDaycareScreen = false;
        lastDaycareTitle = "";
        ticksWaited = 0;
        daycareRescrapeCounter = 0;
        lastDaycareContentHash = 0;
        backpackTabActive = false;
        isWtScreen = false;

        ScreenEvents.remove(screen).register(removedScreen -> {
            if (isWtScreen) {
                wondertradeManager.onWtScreenClosed();
                isWtScreen = false;
            }
            if (isDaycareScreen) {
                daycareManager.onDaycareMenuClosed();
                isDaycareScreen = false;
            }
        });

        ScreenMouseEvents.afterMouseClick(screen).register((clickedScreen, mouseX, mouseY, button) -> {
            if (isWtScreen && button == 0) {
                wondertradeManager.onWtScreenClicked();
            }
        });

        ScreenEvents.afterTick(screen).register(tickedScreen -> {
            ticksWaited++;

            if (ticksWaited == 5) {
                if (!hasSafariScrapeScheduled) {
                    hasSafariScrapeScheduled = true;
                    tryIdentifyAndScrapeHunts(containerScreen);
                }

                String title = containerScreen.getTitle().getString();
                String cleanTitle = title.replaceAll("\u00A7[0-9a-fk-or]", "").trim();
                int titleNum = decodeTitleNumber(title);

                if (titleNum >= 1 && titleNum <= 9) {
                    isDaycareScreen = true;
                    lastDaycareTitle = title;
                }

                if (!isDaycareScreen && cleanTitle.toUpperCase().contains("DAYCARE")) {
                    isDaycareScreen = true;
                    lastDaycareTitle = title;
                }

                if (!isDaycareScreen) {
                    isDaycareScreen = detectDaycareByContent(containerScreen.getMenu());
                    if (isDaycareScreen) {
                        lastDaycareTitle = title;
                    }
                }

                if (isDaycareScreen) {
                    daycareManager.onDaycareMenuOpened();
                    scrapeDaycareView(containerScreen);
                }

                if (!isDaycareScreen) {
                    isWtScreen = detectWtScreen(cleanTitle, containerScreen.getMenu());
                    if (isWtScreen) {
                        wondertradeManager.onWtScreenOpened();
                    }
                }
                return;
            }

            if (isDaycareScreen && ticksWaited > 5) {
                daycareRescrapeCounter++;
                if (daycareRescrapeCounter >= 10) {
                    daycareRescrapeCounter = 0;
                    scrapeDaycareView(containerScreen);
                }
            }

            if (isWtScreen && ticksWaited > 5) {
                wondertradeManager.onWtScreenTick();
            }
        });
    }

    private boolean detectWtScreen(String cleanTitle, AbstractContainerMenu menu) {
        if (!cleanTitle.isEmpty()) {
            int codePoint = cleanTitle.codePointAt(0);
            if (codePoint == 0xF818) {
                for (Slot slot : menu.slots) {
                    if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
                    String itemId = BuiltInRegistries.ITEM.getKey(slot.getItem().getItem()).toString();
                    if (itemId.equals("cobblemon:pokemon_model")) return true;
                }
                return false;
            }
        }
        String upper = cleanTitle.toUpperCase();
        return upper.contains("WONDERTRADE") || upper.contains("WONDER TRADE");
    }

    private static int decodeTitleNumber(String title) {
        if (title == null || title.isEmpty()) return -1;

        String cleaned = title.replaceAll("\u00A7[0-9a-fk-or]", "").trim();
        if (cleaned.isEmpty()) return -1;

        for (int i = 0; i < cleaned.length(); i++) {
            char ch = cleaned.charAt(i);

            if (ch >= '\u0E50' && ch <= '\u0E59') {
                return ch - '\u0E50';
            }
        }
        return -1;
    }

    private boolean detectDaycareByContent(AbstractContainerMenu menu) {
        for (Slot slot : menu.slots) {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;

            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;

            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (itemId.startsWith("cobblemon:")) continue;

            String itemName = stack.getHoverName().getString()
                    .replaceAll("\u00A7[0-9a-fk-or]", "").trim();

            if (PEN_LABEL_PATTERN.matcher(itemName).matches()) return true;
            if (itemName.toLowerCase().contains("eggs in backpack")) return true;
        }
        return false;
    }

    private void tryIdentifyAndScrapeHunts(AbstractContainerScreen<?> containerScreen) {
        AbstractContainerMenu menu = containerScreen.getMenu();
        List<ScrapedHuntItem> scrapedItems = new ArrayList<>();
        Set<String> seenNames = new LinkedHashSet<>();

        for (Slot slot : menu.slots) {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;

            ItemStack stack = slot.getItem();

            if (stack.isEmpty()) continue;
            if (!stack.is(Items.PAPER)) continue;

            Component itemName = stack.getHoverName();
            String itemNameStr = itemName.getString();
            
            if (seenNames.contains(itemNameStr)) continue;

            List<Component> loreLines = new ArrayList<>();
            ItemLore lore = stack.get(DataComponents.LORE);

            if (lore != null) {
                loreLines.addAll(lore.lines());
            }

            boolean isHuntItem = loreLines.stream()
                    .anyMatch(line -> line.getString().contains("Caught:"));

            if (isHuntItem) {
                seenNames.add(itemNameStr);
                List<String> loreStrings = loreLines.stream().map(Component::getString).toList();
                scrapedItems.add(new ScrapedHuntItem(itemNameStr, loreStrings));
            }
        }

        if (!scrapedItems.isEmpty()) {
            safariHuntManager.onHuntsScreenScraped(scrapedItems);
        }
    }

    public record ScrapedHuntItem(String name, List<String> loreLines) {}

    private enum DaycareView {
        PEN_VIEW,
        EGG_BACKPACK,
        UNKNOWN
    }

    private void scrapeDaycareView(AbstractContainerScreen<?> containerScreen) {
        AbstractContainerMenu menu = containerScreen.getMenu();

        String currentTitle = containerScreen.getTitle().getString();

        if (!currentTitle.equals(lastDaycareTitle)) {
            lastDaycareTitle = currentTitle;
        }

        int contentHash = computeSlotHash(menu);

        if (contentHash == lastDaycareContentHash && contentHash != 0) {
            return;
        }

        lastDaycareContentHash = contentHash;

        try {
            scrapePenButtons(menu);

            DaycareView view = detectDaycareView(menu);

            switch (view) {
                case PEN_VIEW -> scrapePenView(menu);
                case EGG_BACKPACK -> {}
                case UNKNOWN -> SigsAcademyAddons.LOGGER.debug("[SAA Daycare] Could not determine daycare view");
            }
        } catch (Exception e) {
            SigsAcademyAddons.LOGGER.error("[SAA Daycare] Error during daycare scrape", e);
        }
    }

    private DaycareView detectDaycareView(AbstractContainerMenu menu) {
        int titleNum = decodeTitleNumber(lastDaycareTitle);

        if (titleNum == 7) {
            return DaycareView.EGG_BACKPACK;
        }

        boolean hasPokemonModel = false;
        boolean hasEggWithParents = false;

        for (Slot slot : menu.slots) {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;

            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;

            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

            if (itemId.equals("cobblemon:pokemon_model")) {
                hasPokemonModel = true;
                continue;
            }

            if (itemId.startsWith("cobblemon:")) continue;

            ItemLore lore = stack.get(DataComponents.LORE);
            if (lore != null) {
                for (Component line : lore.lines()) {
                    if (line.getString().contains("Parents:")) {
                        hasEggWithParents = true;
                        break;
                    }
                }
            }
        }

        if (hasPokemonModel) {
            if (titleNum >= 1 && titleNum <= 4) {
                lastDetectedPenNumber = titleNum;
            }
            return DaycareView.PEN_VIEW;
        }

        if (backpackTabActive || hasEggWithParents || titleNum == 7) {
            return DaycareView.EGG_BACKPACK;
        }

        if (titleNum >= 1 && titleNum <= 4) {
            lastDetectedPenNumber = titleNum;
            return DaycareView.PEN_VIEW;
        }

        return DaycareView.UNKNOWN;
    }

    private void scrapePenButtons(AbstractContainerMenu menu) {
        List<DaycareManager.ScrapedPenButton> buttons = new ArrayList<>();
        Set<Integer> seenPens = new LinkedHashSet<>();
        int activePenFromGlint = -1;
        boolean detectedBackpackActive = false;

        Map<Integer, Integer> penSlotIndices = new HashMap<>();
        int backpackSlotIndex = -1;

        for (Slot slot : menu.slots) {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;

            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;

            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (itemId.startsWith("cobblemon:")) continue;

            String itemName = stack.getHoverName().getString()
                    .replaceAll("\u00A7[0-9a-fk-or]", "").trim();

            Matcher matcher = PEN_LABEL_PATTERN.matcher(itemName);
            if (matcher.matches()) {
                int penNumber = Integer.parseInt(matcher.group(1));
                if (seenPens.contains(penNumber)) continue;
                seenPens.add(penNumber);

                String suffix = matcher.group(2).trim();
                boolean locked = suffix.toLowerCase().contains("locked");
                buttons.add(new DaycareManager.ScrapedPenButton(penNumber, locked));
                penSlotIndices.put(penNumber, slot.index);

                if (stack.hasFoil()) {
                    activePenFromGlint = penNumber;
                }
            }

            String nameLower = itemName.toLowerCase();
            if (nameLower.contains("backpack") || nameLower.contains("eggs in")) {
                backpackSlotIndex = slot.index;
                if (stack.hasFoil()) {
                    detectedBackpackActive = true;
                }
            }
        }

        backpackTabActive = detectedBackpackActive;

        if (activePenFromGlint > 0) {
            lastDetectedPenNumber = activePenFromGlint;
        }

        if (!buttons.isEmpty()) {
            daycareManager.onMainMenuScraped(buttons);
        }

        String navTarget = daycareManager.consumePendingNavTarget();
        if (navTarget != null && !penSlotIndices.isEmpty()) {
            int targetSlot = -1;

            if (navTarget.equals("backpack")) {
                targetSlot = backpackSlotIndex;
            } else if (navTarget.startsWith("pen:")) {
                try {
                    int penNum = Integer.parseInt(navTarget.substring(4));
                    targetSlot = penSlotIndices.getOrDefault(penNum, -1);
                } catch (NumberFormatException ignored) {}
            }

            if (targetSlot >= 0) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.gameMode != null && mc.player != null) {
                    mc.gameMode.handleInventoryMouseClick(
                            menu.containerId, targetSlot, 0, ClickType.PICKUP, mc.player);
                    SigsAcademyAddons.LOGGER.info("[SAA Daycare] Auto-navigated to {} (slot {})",
                            navTarget, targetSlot);
                }
            }
        }
    }

    private void scrapePenView(AbstractContainerMenu menu) {
        String pokemon1 = null;
        String pokemon2 = null;
        boolean hasEgg = false;
        boolean hasWarning = false;
        int leftArrowStage = -1;
        int rightArrowStage = -1;

        for (Slot slot : menu.slots) {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;

            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;

            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

            if (itemId.equals("cobblemon:pokemon_model")) {
                try {
                    Component customName = stack.get(DataComponents.CUSTOM_NAME);
                    if (customName != null) {
                        String rawName = customName.getString()
                                .replaceAll("\u00A7[0-9a-fk-or]", "").trim();
                        String cleanName = cleanPokemonName(rawName);
                        if (cleanName != null) {
                            if (pokemon1 == null) {
                                pokemon1 = cleanName;

                            } else if (pokemon2 == null) {
                                pokemon2 = cleanName;
                            }
                        }
                    }
                } catch (Exception e) {
                    SigsAcademyAddons.LOGGER.warn("[SAA Daycare] Error reading pokemon_model name", e);
                }
                continue;
            }

            if (itemId.startsWith("cobblemon:")) continue;

            String itemName = stack.getHoverName().getString()
                    .replaceAll("\u00A7[0-9a-fk-or]", "").trim();

            if (stack.is(Items.PAPER) && (itemName == null || itemName.isEmpty())) {
                var cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA);
                if (cmd != null) {
                    int value = cmd.value();
                    if (value >= 9000 && value <= 9007) {
                        leftArrowStage = value - 9000;
                    } else if (value >= 9008 && value <= 9015) {
                        rightArrowStage = value - 9008;
                    }
                }
                continue;
            }

            if (itemName.toLowerCase().contains("warning")) {
                hasWarning = true;
                continue;
            }

            if (itemName.equalsIgnoreCase("New Egg") || itemName.equalsIgnoreCase("Egg")) {
                hasEgg = true;
                continue;
            }

            ItemLore lore = stack.get(DataComponents.LORE);
            if (lore != null) {
                boolean isClaimEgg = lore.lines().stream()
                        .anyMatch(line -> line.getString().toLowerCase().contains("claim egg"));
                if (isClaimEgg) {
                    hasEgg = true;
                }
            }
        }

        boolean cursorHasEgg = false;
        ItemStack carried = menu.getCarried();
        if (!carried.isEmpty()) {
            String carriedName = carried.getHoverName().getString()
                    .replaceAll("\u00A7[0-9a-fk-or]", "").trim();
            if (carriedName.equalsIgnoreCase("New Egg") || carriedName.equalsIgnoreCase("Egg")) {
                cursorHasEgg = true;
            }
        }

        int penNumber = lastDetectedPenNumber;

        SigsAcademyAddons.LOGGER.info("[SAA Daycare] scrapePenView pen={}: hasEgg={}, cursorEgg={}, p1={}, p2={}, warning={}, arrows=({},{})",
                penNumber, hasEgg, cursorHasEgg, pokemon1, pokemon2, hasWarning, leftArrowStage, rightArrowStage);

        if (hasEgg && pokemon1 == null && pokemon2 == null) {
            return;
        }

        DaycareState.BreedingStage stage;
        if (hasWarning) {
            stage = DaycareState.BreedingStage.INCOMPATIBLE;

        } else if (pokemon1 != null && pokemon2 != null) {
            stage = DaycareState.BreedingStage.BREEDING;

        } else if (pokemon1 != null) {
            stage = DaycareState.BreedingStage.ONE_POKEMON;

        } else {
            stage = DaycareState.BreedingStage.EMPTY;

        }

        float serverBreedingProgress = -1f;
        if (leftArrowStage >= 0 && rightArrowStage >= 0) {
            serverBreedingProgress = (leftArrowStage + rightArrowStage) / 14.0f;
        }

        daycareManager.onPenViewScraped(
                new DaycareManager.ScrapedPenData(penNumber, pokemon1, pokemon2, stage,
                        hasEgg, cursorHasEgg, serverBreedingProgress));
    }

    static String cleanPokemonName(String rawName) {
        if (rawName == null || rawName.isEmpty()) return null;

        int lvIdx = rawName.indexOf("Lv.");
        if (lvIdx > 0) {
            rawName = rawName.substring(0, lvIdx);
        }

        rawName = rawName.replaceAll("[^\\x00-\\x7F]", "").trim();

        return rawName.isEmpty() ? null : rawName;
    }

    private int computeSlotHash(AbstractContainerMenu menu) {
        int hash = 0;
        int slotIdx = 0;

        for (Slot slot : menu.slots) {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
            ItemStack stack = slot.getItem();

            if (!stack.isEmpty()) {
                String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                hash = 31 * hash + itemId.hashCode();
                hash = 31 * hash + slotIdx;
                hash = 31 * hash + stack.getCount();
            }

            slotIdx++;
        }
        return hash;
    }
}
