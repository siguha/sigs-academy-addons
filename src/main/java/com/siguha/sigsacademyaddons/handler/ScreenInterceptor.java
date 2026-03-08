package com.siguha.sigsacademyaddons.handler;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.storage.ClientParty;
import com.siguha.sigsacademyaddons.SigsAcademyAddons;
import com.siguha.sigsacademyaddons.SigsAcademyAddonsClient;
import com.siguha.sigsacademyaddons.config.HudConfig;
import com.siguha.sigsacademyaddons.feature.daycare.DaycareManager;
import com.siguha.sigsacademyaddons.feature.daycare.DaycareState;
import com.siguha.sigsacademyaddons.feature.safari.SafariHuntManager;
import com.siguha.sigsacademyaddons.feature.wondertrade.WondertradeManager;
import com.siguha.sigsacademyaddons.mixin.ContainerScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
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
import net.minecraft.sounds.SoundEvents;

import com.siguha.sigsacademyaddons.feature.daycare.ParentIvData;
import com.siguha.sigsacademyaddons.feature.daycare.ParentIvOverlayRenderer;
import com.siguha.sigsacademyaddons.feature.daycare.ParentIvParser;

import com.cobblemon.mod.common.CobblemonItemComponents;
import com.cobblemon.mod.common.item.components.PokemonItemComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScreenInterceptor {

    private static ScreenInterceptor instance;

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

    private static final int BABY_GUARD_DURATION = 100;
    private static final int BABY_GUARD_FADE_TICKS = 20;

    private final ParentIvOverlayRenderer ivOverlayRenderer = new ParentIvOverlayRenderer();
    private final Set<Integer> backpackHighlightSlotsUpper = new LinkedHashSet<>();
    private final Set<Integer> backpackHighlightSlotsLower = new LinkedHashSet<>();
    private boolean backpackViewActive = false;

    private enum BabyGuardPhase { NONE, EGG_WARNING, BREEDING_WARNING, PARTY_FULL_WARNING }
    private BabyGuardPhase babyGuardPhase = BabyGuardPhase.NONE;
    private String babyGuardMessage = null;
    private int babyGuardConfirmButton = -1;
    private int babyGuardTicksLeft = 0;
    private int babyGuardSlotIndex = -1;

    public ScreenInterceptor(SafariHuntManager safariHuntManager, DaycareManager daycareManager,
                              WondertradeManager wondertradeManager) {
        this.safariHuntManager = safariHuntManager;
        this.daycareManager = daycareManager;
        this.wondertradeManager = wondertradeManager;
        instance = this;
    }

    public static boolean shouldAllowContainerClick(AbstractContainerScreen<?> screen, int button) {
        return instance == null || instance.handleBabyGuardClick(screen, button);
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
        clearBabyGuard();
        ivOverlayRenderer.clear();
        backpackHighlightSlotsUpper.clear();
        backpackHighlightSlotsLower.clear();
        backpackViewActive = false;

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

        ScreenEvents.afterRender(screen).register((renderedScreen, graphics, mouseX, mouseY, tickDelta) -> {
            renderBabyGuardMessage(containerScreen, graphics, tickDelta);
            if (isDaycareScreen) {
                ivOverlayRenderer.render(containerScreen, graphics);
                renderBackpackHighlights(containerScreen, graphics);
                renderBackpackFilterLabel(containerScreen, graphics);
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
                    ivOverlayRenderer.setProcessing();
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

            if (babyGuardTicksLeft > 0) {
                babyGuardTicksLeft--;
                if (babyGuardTicksLeft == 0) {
                    clearBabyGuard();
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
                case PEN_VIEW -> {
                    backpackViewActive = false;
                    backpackHighlightSlotsUpper.clear();
                    backpackHighlightSlotsLower.clear();
                    scrapePenView(menu);
                }
                case EGG_BACKPACK -> {
                    backpackViewActive = true;
                    ivOverlayRenderer.clear();
                    scanBackpackIvSlots(menu);
                }
                case UNKNOWN -> {
                    backpackViewActive = false;
                    ivOverlayRenderer.clear();
                    backpackHighlightSlotsUpper.clear();
                    backpackHighlightSlotsLower.clear();
                    SigsAcademyAddons.LOGGER.debug("[SAA Daycare] Could not determine daycare view");
                }
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
        boolean gender1Female = false;
        boolean gender2Female = false;
        ParentIvData ivData1 = null;
        ParentIvData ivData2 = null;
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
                    String resolvedName = null;
                    boolean isFemale = false;

                    PokemonItemComponent pokemonComp = stack.get(
                            CobblemonItemComponents.POKEMON_ITEM);
                    if (pokemonComp != null) {
                        var speciesId = pokemonComp.getSpecies();
                        if (speciesId != null) {
                            String path = speciesId.getPath();
                            if (!path.isEmpty()) {
                                resolvedName = path.substring(0, 1).toUpperCase() + path.substring(1);
                            }
                        }
                        var aspects = pokemonComp.getAspects();
                        if (aspects != null) {
                            isFemale = aspects.contains("female");
                        }
                    }

                    if (resolvedName == null) {
                        Component customName = stack.get(DataComponents.CUSTOM_NAME);
                        if (customName != null) {
                            String rawName = customName.getString()
                                    .replaceAll("\u00A7[0-9a-fk-or]", "").trim();
                            resolvedName = cleanPokemonName(rawName);
                        }
                    }

                    if (resolvedName != null) {
                        if (pokemon1 == null) {
                            pokemon1 = resolvedName;
                            gender1Female = isFemale;
                            ivData1 = ParentIvParser.parse(stack, resolvedName);
                        } else if (pokemon2 == null) {
                            pokemon2 = resolvedName;
                            gender2Female = isFemale;
                            ivData2 = ParentIvParser.parse(stack, resolvedName);
                        }
                    }
                } catch (Exception e) {
                    SigsAcademyAddons.LOGGER.warn("[SAA Daycare] Error reading pokemon_model data", e);
                }
                continue;
            }

            if (itemId.startsWith("cobblemon:")) continue;

            String itemName = stack.getHoverName().getString()
                    .replaceAll("\u00A7[0-9a-fk-or]", "").trim();

            if (stack.is(Items.PAPER) && itemName.isEmpty()) {
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

        ivOverlayRenderer.setData(ivData1, ivData2);

        daycareManager.onPenViewScraped(
                new DaycareManager.ScrapedPenData(penNumber, pokemon1, pokemon2,
                        gender1Female, gender2Female, stage,
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

    private void scanBackpackIvSlots(AbstractContainerMenu menu) {
        backpackHighlightSlotsUpper.clear();
        backpackHighlightSlotsLower.clear();

        HudConfig config = SigsAcademyAddonsClient.getHudConfig();
        if (config == null) return;
        int lower = config.getDaycareIvPercentLower();
        int upper = config.getDaycareIvPercentUpper();

        for (Slot slot : menu.slots) {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;

            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;

            String name = "Egg";
            Component customName = stack.get(DataComponents.CUSTOM_NAME);
            if (customName != null) {
                String raw = customName.getString()
                        .replaceAll("\u00A7[0-9a-fk-or]", "").trim();
                String cleaned = cleanPokemonName(raw);
                if (cleaned != null) name = cleaned;
            }

            ParentIvData ivData = ParentIvParser.parse(stack, name);
            if (ivData != null) {
                int pct = ivData.getIvPercent();
                if (pct >= upper) {
                    backpackHighlightSlotsUpper.add(slot.index);
                } else if (pct >= lower) {
                    backpackHighlightSlotsLower.add(slot.index);
                }
            }
        }
    }

    private void renderBackpackFilterLabel(AbstractContainerScreen<?> containerScreen, GuiGraphics graphics) {
        if (!backpackViewActive) return;

        HudConfig config = SigsAcademyAddonsClient.getHudConfig();
        if (config == null) return;

        ContainerScreenAccessor accessor = (ContainerScreenAccessor) containerScreen;
        Font font = Minecraft.getInstance().font;

        String label = "Filtering for " + config.getDaycareIvPercentLower() + "%+ IVs";
        int textWidth = font.width(label);
        int containerRight = accessor.getLeftPos() + accessor.getImageWidth();

        float scale = 0.7f;
        int scaledWidth = (int)(textWidth * scale);
        int x = containerRight - scaledWidth - 8;
        int y = accessor.getTopPos() + (int)(accessor.getImageHeight() * 0.20) + 1;

        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.drawString(font, label, 0, 0, 0xFFD8D8D8, false);
        graphics.pose().popPose();
    }

    private static final int COLOR_HIGHLIGHT_UPPER = 0x44AA00FF;
    private static final int COLOR_HIGHLIGHT_LOWER = 0x66FFD700;

    private void renderBackpackHighlights(AbstractContainerScreen<?> containerScreen, GuiGraphics graphics) {
        if (backpackHighlightSlotsUpper.isEmpty() && backpackHighlightSlotsLower.isEmpty()) return;

        ContainerScreenAccessor accessor = (ContainerScreenAccessor) containerScreen;
        int leftPos = accessor.getLeftPos();
        int topPos = accessor.getTopPos();

        for (Slot slot : containerScreen.getMenu().slots) {
            int color;
            if (backpackHighlightSlotsUpper.contains(slot.index)) {
                color = COLOR_HIGHLIGHT_UPPER;
            } else if (backpackHighlightSlotsLower.contains(slot.index)) {
                color = COLOR_HIGHLIGHT_LOWER;
            } else {
                continue;
            }
            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            graphics.fill(x, y, x + 16, y + 16, color);
        }
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

    private boolean handleBabyGuardClick(AbstractContainerScreen<?> containerScreen, int button) {
        if (!isDaycareScreen && !looksLikeDaycareScreen(containerScreen)) return true;

        HudConfig config = SigsAcademyAddonsClient.getHudConfig();
        if (config == null || !config.isDaycareBabyGuards()) return true;
        if (detectDaycareView(containerScreen.getMenu()) != DaycareView.PEN_VIEW) return true;

        ContainerScreenAccessor accessor = (ContainerScreenAccessor) containerScreen;
        Slot hovered = accessor.getHoveredSlot();
        if (hovered == null || hovered.container instanceof net.minecraft.world.entity.player.Inventory) return true;

        if (!isParentSlot(hovered)) {
            clearBabyGuard();
            return true;
        }

        int slotIdx = hovered.index;

        if (babyGuardPhase != BabyGuardPhase.NONE && slotIdx != babyGuardSlotIndex) {
            clearBabyGuard();
        }

        AbstractContainerMenu menu = containerScreen.getMenu();
        boolean hasEgg = hasUnclaimedEgg(menu);
        boolean breeding = isBreedingActive(menu);
        boolean partyFull = (button == 1) && isPartyFull();
        String clickName = (button == 0) ? "Left Click" : "Right Click";

        if (babyGuardPhase != BabyGuardPhase.NONE && button == babyGuardConfirmButton) {
            BabyGuardPhase nextPhase = nextGuardPhase(babyGuardPhase, breeding, partyFull);
            if (nextPhase != null) {
                activateBabyGuard(guardMessage(nextPhase, clickName), nextPhase,
                        nextPhase == BabyGuardPhase.PARTY_FULL_WARNING ? 1 : button, slotIdx);
                return false;
            }
            clearBabyGuard();
            return true;
        }

        if (hasEgg) {
            activateBabyGuard(
                    "\u00A7eThere's an unclaimed egg in the parent slot! "
                            + "If you're sure, " + clickName + " again to confirm.",
                    BabyGuardPhase.EGG_WARNING, button, slotIdx);
            return false;
        }

        if (breeding) {
            activateBabyGuard(guardMessage(BabyGuardPhase.BREEDING_WARNING, clickName),
                    BabyGuardPhase.BREEDING_WARNING, button, slotIdx);
            return false;
        }

        if (partyFull) {
            activateBabyGuard(guardMessage(BabyGuardPhase.PARTY_FULL_WARNING, clickName),
                    BabyGuardPhase.PARTY_FULL_WARNING, 1, slotIdx);
            return false;
        }

        clearBabyGuard();
        return true;
    }

    private BabyGuardPhase nextGuardPhase(BabyGuardPhase current, boolean breeding, boolean partyFull) {
        if (current == BabyGuardPhase.EGG_WARNING && breeding) return BabyGuardPhase.BREEDING_WARNING;
        if ((current == BabyGuardPhase.EGG_WARNING || current == BabyGuardPhase.BREEDING_WARNING) && partyFull)
            return BabyGuardPhase.PARTY_FULL_WARNING;
        return null;
    }

    private String guardMessage(BabyGuardPhase phase, String clickName) {
        return switch (phase) {
            case BREEDING_WARNING -> "\u00A7eThese parents are breeding. If you're sure, "
                    + clickName + " again to confirm.";
            case PARTY_FULL_WARNING -> "\u00A7eYour party is full and the parent will be sent to your PC. "
                    + "If you're sure, Right Click again to confirm.";
            default -> "";
        };
    }

    private void activateBabyGuard(String message, BabyGuardPhase phase, int confirmButton, int slotIndex) {
        babyGuardMessage = message;
        babyGuardPhase = phase;
        babyGuardConfirmButton = confirmButton;
        babyGuardSlotIndex = slotIndex;
        babyGuardTicksLeft = BABY_GUARD_DURATION;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6f, 1.0f);
        }
    }

    private void renderBabyGuardMessage(AbstractContainerScreen<?> containerScreen, GuiGraphics graphics,
                                         float tickDelta) {
        if (babyGuardMessage == null) return;

        float effectiveTicks = babyGuardTicksLeft - tickDelta;
        int alpha = effectiveTicks >= BABY_GUARD_FADE_TICKS ? 255
                  : effectiveTicks <= 0 ? 0
                  : (int) (effectiveTicks / BABY_GUARD_FADE_TICKS * 255);
        if (alpha <= 0) return;

        ContainerScreenAccessor accessor = (ContainerScreenAccessor) containerScreen;
        Font font = Minecraft.getInstance().font;
        int centerX = containerScreen.width / 2;
        int textY = accessor.getTopPos() + accessor.getImageHeight() + 6;
        int textWidth = font.width(babyGuardMessage);
        graphics.drawString(font, babyGuardMessage, centerX - textWidth / 2, textY,
                (alpha << 24) | 0xFFAA00, true);
    }

    private void clearBabyGuard() {
        babyGuardPhase = BabyGuardPhase.NONE;
        babyGuardMessage = null;
        babyGuardConfirmButton = -1;
        babyGuardTicksLeft = 0;
        babyGuardSlotIndex = -1;
    }

    private boolean looksLikeDaycareScreen(AbstractContainerScreen<?> containerScreen) {
        String title = containerScreen.getTitle().getString();
        int titleNum = decodeTitleNumber(title);
        if (titleNum >= 1 && titleNum <= 9) return true;
        String clean = title.replaceAll("\u00A7[0-9a-fk-or]", "").trim();
        if (clean.toUpperCase().contains("DAYCARE")) return true;
        return detectDaycareByContent(containerScreen.getMenu());
    }

    private boolean isParentSlot(Slot slot) {
        if (slot.getItem().isEmpty()) return false;
        return BuiltInRegistries.ITEM.getKey(slot.getItem().getItem()).toString()
                .equals("cobblemon:pokemon_model");
    }

    private boolean isBreedingActive(AbstractContainerMenu menu) {
        for (Slot slot : menu.slots) {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty() || !stack.is(Items.PAPER)) continue;
            if (!stack.getHoverName().getString().replaceAll("\u00A7[0-9a-fk-or]", "").trim().isEmpty()) continue;
            var cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA);
            if (cmd != null) {
                int value = cmd.value();
                if ((value >= 9001 && value <= 9007) || (value >= 9009 && value <= 9015)) return true;
            }
        }
        return false;
    }

    private boolean hasUnclaimedEgg(AbstractContainerMenu menu) {
        for (Slot slot : menu.slots) {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            if (BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().startsWith("cobblemon:")) continue;

            String itemName = stack.getHoverName().getString()
                    .replaceAll("\u00A7[0-9a-fk-or]", "").trim();
            if (itemName.equalsIgnoreCase("New Egg") || itemName.equalsIgnoreCase("Egg")) return true;

            ItemLore lore = stack.get(DataComponents.LORE);
            if (lore != null) {
                for (Component line : lore.lines()) {
                    if (line.getString().toLowerCase().contains("claim egg")) return true;
                }
            }
        }
        return false;
    }

    private boolean isPartyFull() {
        try {
            ClientParty party = CobblemonClient.INSTANCE.getStorage().getParty();
            if (party == null) return false;
            int occupied = 0;
            for (int i = 0; i < 6; i++) {
                if (party.get(i) != null) occupied++;
            }
            return occupied >= 6;
        } catch (Exception e) {
            SigsAcademyAddons.LOGGER.warn("[SAA BabyGuard] Error checking party", e);
            return false;
        }
    }
}
