package com.siguha.sigsacademyaddons;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.siguha.sigsacademyaddons.config.HudConfig;
import com.siguha.sigsacademyaddons.data.CardGradingDataStore;
import com.siguha.sigsacademyaddons.data.DaycareDataStore;
import com.siguha.sigsacademyaddons.data.HuntDataStore;
import com.siguha.sigsacademyaddons.data.WondertradeDataStore;
import com.siguha.sigsacademyaddons.feature.cardalbum.CardAlbumQuickOpen;
import com.siguha.sigsacademyaddons.feature.cardgrading.CardGradingManager;
import com.siguha.sigsacademyaddons.feature.cardgrading.CardGradingSoundPlayer;
import com.siguha.sigsacademyaddons.feature.cardstats.CardStatsManager;
import com.siguha.sigsacademyaddons.feature.dex.DexDataManager;
import com.siguha.sigsacademyaddons.feature.daycare.DaycareManager;
import com.siguha.sigsacademyaddons.feature.daycare.DaycareSoundPlayer;
import com.siguha.sigsacademyaddons.feature.drifloot.DriflootDetector;
import com.siguha.sigsacademyaddons.feature.dungeon.DungeonManager;
import com.siguha.sigsacademyaddons.feature.hideout.GruntFinderTracker;
import com.siguha.sigsacademyaddons.feature.portal.PortalManager;
import com.siguha.sigsacademyaddons.feature.portal.PortalParticleDetector;
import com.siguha.sigsacademyaddons.feature.suppression.SuppressionManager;
import com.siguha.sigsacademyaddons.feature.safari.HuntEntityTracker;
import com.siguha.sigsacademyaddons.feature.safari.SafariHuntManager;
import com.siguha.sigsacademyaddons.feature.safari.SafariManager;
import com.siguha.sigsacademyaddons.feature.wondertrade.WondertradeManager;
import com.siguha.sigsacademyaddons.feature.wondertrade.WondertradeSoundPlayer;
import com.siguha.sigsacademyaddons.gui.DexScreen;
import com.siguha.sigsacademyaddons.gui.HudConfigScreen;
import com.siguha.sigsacademyaddons.handler.CatchDetector;
import com.siguha.sigsacademyaddons.handler.ChatMessageHandler;
import com.siguha.sigsacademyaddons.handler.DumpSelfCommand;
import com.siguha.sigsacademyaddons.handler.NearbyDumpCommand;
import com.siguha.sigsacademyaddons.handler.ParticleCapture;
import com.siguha.sigsacademyaddons.handler.ScreenInterceptor;
import com.siguha.sigsacademyaddons.handler.UpdateChecker;
import com.siguha.sigsacademyaddons.hud.DaycareHudRenderer;
import com.siguha.sigsacademyaddons.hud.HudGroupRenderer;
import com.siguha.sigsacademyaddons.hud.PortalBossBarRenderer;
import com.siguha.sigsacademyaddons.hud.SafariHudRenderer;
import com.siguha.sigsacademyaddons.hud.CardStatsHudRenderer;
import com.siguha.sigsacademyaddons.hud.CardGradingHudRenderer;
import com.siguha.sigsacademyaddons.hud.WondertradeHudRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import com.siguha.sigsacademyaddons.mixin.ContainerScreenAccessor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.lwjgl.glfw.GLFW;
import java.util.Collections;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.system.MemoryUtil;

public class SigsAcademyAddonsClient implements ClientModInitializer {

    private static SafariManager safariManager;
    private static SafariHuntManager safariHuntManager;
    private static HuntEntityTracker huntEntityTracker;
    private static CatchDetector catchDetector;
    private static DaycareManager daycareManager;
    private static DaycareSoundPlayer daycareSoundPlayer;
    private static WondertradeManager wondertradeManager;
    private static WondertradeSoundPlayer wondertradeSoundPlayer;
    private static CardGradingManager cardGradingManager;
    private static PortalManager portalManager;
    private static DriflootDetector driflootDetector;
    private static GruntFinderTracker gruntFinderTracker;
    private static SuppressionManager suppressionManager;
    private static DungeonManager dungeonManager;
    private static CardAlbumQuickOpen cardAlbumQuickOpen;
    private static CardStatsManager cardStatsManager;
        private static DexDataManager dexDataManager;
    private static HudConfig hudConfig;

    private static boolean openConfigScreenNextTick = false;
        private static boolean openDexScreenNextTick = false;
    private static boolean glfwFilterReinstalled = false;
    private static int welcomeDelayTicks = -1;

    @Override
    public void onInitializeClient() {
        SigsAcademyAddons.LOGGER.info("[Sigs Academy Addons] Client initialization starting...");

        hudConfig = new HudConfig();
        HuntDataStore huntDataStore = new HuntDataStore();
        safariManager = new SafariManager();
        safariHuntManager = new SafariHuntManager(huntDataStore);
        huntEntityTracker = new HuntEntityTracker(safariManager, safariHuntManager, hudConfig);
        catchDetector = new CatchDetector();
        catchDetector.setOnCatchListener(safariHuntManager::onPokemonCaught);

        DaycareDataStore daycareDataStore = new DaycareDataStore();
        daycareSoundPlayer = new DaycareSoundPlayer(hudConfig);
        daycareManager = new DaycareManager(daycareDataStore, daycareSoundPlayer, hudConfig);

        WondertradeDataStore wtDataStore = new WondertradeDataStore();
        wondertradeSoundPlayer = new WondertradeSoundPlayer(hudConfig);
        wondertradeManager = new WondertradeManager(wtDataStore, wondertradeSoundPlayer, hudConfig);
        CardGradingSoundPlayer cardGradingSoundPlayer = new CardGradingSoundPlayer(hudConfig);
        cardGradingManager = new CardGradingManager(new CardGradingDataStore(), cardGradingSoundPlayer);

        portalManager = new PortalManager();
        driflootDetector = new DriflootDetector(hudConfig);
        gruntFinderTracker = new GruntFinderTracker(hudConfig);
        suppressionManager = new SuppressionManager(hudConfig);

        dungeonManager = new DungeonManager();
        cardAlbumQuickOpen = new CardAlbumQuickOpen();
        cardStatsManager = new CardStatsManager();
        dexDataManager = new DexDataManager();

        KeyMapping cardAlbumKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.sigsacademyaddons.card_album",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "Sigs Academy Addons"));

        ChatMessageHandler chatHandler = new ChatMessageHandler(safariManager, safariHuntManager,
                catchDetector, daycareManager, wondertradeManager, portalManager, hudConfig);
        ScreenInterceptor screenInterceptor = new ScreenInterceptor(safariHuntManager, daycareManager,
                wondertradeManager);
        SafariHudRenderer hudRenderer = new SafariHudRenderer(safariManager, safariHuntManager, hudConfig);
        DaycareHudRenderer daycareHudRenderer = new DaycareHudRenderer(daycareManager, hudConfig);
        WondertradeHudRenderer wtHudRenderer = new WondertradeHudRenderer(wondertradeManager, hudConfig);
        CardStatsHudRenderer cardStatsHudRenderer = new CardStatsHudRenderer(cardStatsManager, hudConfig);
        CardGradingHudRenderer cardGradingHudRenderer = new CardGradingHudRenderer(cardGradingManager, hudConfig);
        PortalBossBarRenderer portalBossBarRenderer = new PortalBossBarRenderer(portalManager, suppressionManager);

        ClientReceiveMessageEvents.MODIFY_GAME.register(chatHandler::modifyGameMessage);
        ClientReceiveMessageEvents.GAME.register(chatHandler::onGameMessage);
        ScreenEvents.AFTER_INIT.register(screenInterceptor::onScreenInit);
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) return;
            ScreenEvents.afterRender(screen).register((renderedScreen, graphics, mouseX, mouseY, tickDelta) -> {
                ContainerScreenAccessor accessor = (ContainerScreenAccessor) containerScreen;
                cardStatsHudRenderer.renderInInventory(graphics,
                        accessor.getLeftPos(), accessor.getTopPos(), accessor.getImageHeight());
            });
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (cardAlbumKey.consumeClick()) {
                cardAlbumQuickOpen.start();
            }
            cardAlbumQuickOpen.tick();

            suppressionManager.tick();
            safariManager.tick();
            safariHuntManager.tick();
            huntEntityTracker.tick();
            catchDetector.tick(client);
            daycareManager.tick();
            daycareSoundPlayer.tick();
            wondertradeManager.tick();
            wondertradeSoundPlayer.tick();
            cardGradingManager.tick();
            portalManager.tick();
            driflootDetector.tick();
            dungeonManager.tick();
            gruntFinderTracker.tick();
            PortalParticleDetector.tick();
            ParticleCapture.tick();
            cardStatsManager.tick();

            if (!glfwFilterReinstalled) {
                glfwFilterReinstalled = true;
                installGlfwErrorFilter();
            }

            if (welcomeDelayTicks > 0) {
                welcomeDelayTicks--;
            } else if (welcomeDelayTicks == 0 && client.player != null) {
                welcomeDelayTicks = -1;
                if (!hudConfig.hasSeenWelcome()) {
                    client.player.sendSystemMessage(buildWelcomeMessage());
                    hudConfig.setHasSeenWelcome(true);
                }
                UpdateChecker.checkForUpdatesAsync();
            }

            if (openConfigScreenNextTick && client.player != null) {
                openConfigScreenNextTick = false;
                client.setScreen(new HudConfigScreen(hudConfig, safariManager, safariHuntManager,
                        daycareManager, wondertradeManager, cardStatsManager));
            }

                        if (openDexScreenNextTick && client.player != null) {
                                openDexScreenNextTick = false;
                                client.setScreen(new DexScreen(dexDataManager));
                        }
        });

        HudGroupRenderer groupRenderer = new HudGroupRenderer(hudConfig, suppressionManager);
        groupRenderer.registerPanel(hudRenderer);
        groupRenderer.registerPanel(daycareHudRenderer);
        groupRenderer.registerPanel(wtHudRenderer);
        groupRenderer.registerPanel(cardGradingHudRenderer);
        groupRenderer.registerPanel(cardStatsHudRenderer);
        HudRenderCallback.EVENT.register(groupRenderer::onHudRender);
        HudRenderCallback.EVENT.register(portalBossBarRenderer::onHudRender);

        WorldRenderEvents.AFTER_TRANSLUCENT.register(
                context -> dungeonManager.getWorldRenderer().onWorldRender(context));

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide() && dungeonManager.isInDungeon()) {
                dungeonManager.markChestOpened(hitResult.getBlockPos());
            }
            return net.minecraft.world.InteractionResult.PASS;
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            daycareManager.onServerJoined();
            wondertradeManager.onServerJoined();
            cardGradingManager.onServerJoined();
            welcomeDelayTicks = 60;
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            daycareManager.onServerDisconnected();
            wondertradeManager.onServerDisconnected();
            cardGradingManager.onServerDisconnected();
            portalManager.clear();
            welcomeDelayTicks = -1;
            UpdateChecker.resetForNewSession();
        });

        ClientCommandRegistrationCallback.EVENT.register(SigsAcademyAddonsClient::registerCommands);

        installGlfwErrorFilter();

        SigsAcademyAddons.LOGGER.info("[Sigs Academy Addons] Client initialization complete.");
    }

    private static void installGlfwErrorFilter() {
        try {
            GLFWErrorCallback previousCallback = GLFW.glfwSetErrorCallback(null);
            GLFW.glfwSetErrorCallback(GLFWErrorCallback.create((error, description) -> {
                if (error == 0x10003) {
                    String msg = MemoryUtil.memUTF8Safe(description);
                    if (msg != null && msg.contains("Invalid key")) {
                        return;
                    }
                }
                if (previousCallback != null) {
                    previousCallback.invoke(error, description);
                }
            }));
        } catch (Exception e) {
            SigsAcademyAddons.LOGGER.warn("[SAA] Failed to install GLFW error filter", e);
        }
    }

    private static Component buildWelcomeMessage() {
        MutableComponent msg = Component.empty();

        msg.append(Component.literal("Thanks for using SAA!")
                .withStyle(ChatFormatting.AQUA));

        msg.append(Component.literal("\n\nThis mod requires some data be scraped initially for setup -- this includes things like the Daycare, WT, Safari, etc. If you want everything to work properly, please open these menus to initialize the mod.")
                .withStyle(ChatFormatting.AQUA));

        msg.append(Component.literal("\n\nDon't like the placement, style, or size of some of the menus? Want to disable some features? SAA is incredibly configurable!")
                .withStyle(ChatFormatting.AQUA));

        msg.append(Component.literal("\n* Use ")
                .withStyle(ChatFormatting.AQUA));
        msg.append(Component.literal("/saa gui")
                .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.GOLD)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/saa gui"))));
        msg.append(Component.literal(" to edit your menus")
                .withStyle(ChatFormatting.AQUA));

        msg.append(Component.literal("\n* Use ")
                .withStyle(ChatFormatting.AQUA));
        msg.append(Component.literal("/saa config")
                .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.GOLD)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/saa config"))));
        msg.append(Component.literal(" to explore our config options!")
                .withStyle(ChatFormatting.AQUA));

        msg.append(Component.literal("\n\nWe hope you enjoy!")
                .withStyle(ChatFormatting.AQUA));

        return msg;
    }

    private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher,
                                          CommandBuildContext registryAccess) {
        dispatcher.register(
                ClientCommandManager.literal("saa")
                        .then(ClientCommandManager.literal("gui")
                                .then(ClientCommandManager.literal("reset")
                                        .executes(context -> {
                                            int sw = context.getSource().getClient().getWindow().getGuiScaledWidth();
                                            int sh = context.getSource().getClient().getWindow().getGuiScaledHeight();
                                            hudConfig.setHudScale(1.0f);
                                            hudConfig.setPositionFromAbsolute(
                                                    sw - 145, 5, 140, 100, sw, sh);
                                            hudConfig.setDaycareScale(1.0f);
                                            hudConfig.setDaycarePositionFromAbsolute(
                                                    5, 5, 140, 100, sw, sh);
                                            hudConfig.setWtScale(1.0f);
                                            hudConfig.setWtPositionFromAbsolute(
                                                    sw - 145, sh - 80, 140, 70, sw, sh);
                                            hudConfig.setCardGradingScale(1.0f);
                                            hudConfig.setCardGradingPositionFromAbsolute(
                                                    sw - 145, sh - 150, 140, 70, sw, sh);
                                            hudConfig.setCardStatsScale(1.0f);
                                            hudConfig.setCardStatsPositionFromAbsolute(
                                                    5, sh - 100, 120, 80, sw, sh);
                                            hudConfig.setJoinedGroup(Collections.emptyList());
                                            context.getSource().sendFeedback(
                                                    Component.literal("\u00A7aAll HUD positions and scales reset to default."));
                                            return 1;
                                        })
                                )
                                .executes(context -> {
                                    openConfigScreenNextTick = true;
                                    context.getSource().sendFeedback(
                                            Component.literal("\u00A7aOpening HUD configuration..."));
                                    return 1;
                                })
                        )
                        .then(ClientCommandManager.literal("dex")
                                .executes(context -> {
                                    openDexScreenNextTick = true;
                                    context.getSource().sendFeedback(
                                            Component.literal("\u00A7aOpening Dex..."));
                                    return 1;
                                })
                        )
                        .then(ClientCommandManager.literal("safari")
                                .then(ClientCommandManager.literal("clear")
                                        .executes(context -> {
                                            safariHuntManager.clearHunts();
                                            safariManager.endSafari();
                                            context.getSource().sendFeedback(
                                                    Component.literal("\u00A7aCleared all safari data."));
                                            return 1;
                                        })
                                )
                                .executes(context -> {
                                    boolean inSafari = safariManager.isInSafari();
                                    boolean inZone = safariManager.isInSafariZone();
                                    int huntCount = safariHuntManager.getActiveHunts().size();
                                    int pending = safariHuntManager.getPendingUpdates();

                                    StringBuilder sb = new StringBuilder();
                                    sb.append("\u00A76Safari Status:\n");
                                    sb.append("\u00A77Timer: ");
                                    sb.append(inSafari ? "\u00A7a" + safariManager.getRemainingTimeFormatted() + " remaining" : "\u00A7cInactive");
                                    sb.append("\n\u00A77Zone: ");
                                    sb.append(inZone ? "\u00A7aIn safari zone" : "\u00A7eOutside safari");
                                    sb.append("\n\u00A77Active hunts: \u00A7f" + huntCount);
                                    if (pending > 0) {
                                        sb.append("\n\u00A77Pending updates: \u00A7b" + pending);
                                    }

                                    context.getSource().sendFeedback(Component.literal(sb.toString()));
                                    return 1;
                                })
                        )
                        .then(ClientCommandManager.literal("daycare")
                                .then(ClientCommandManager.literal("clear")
                                        .executes(context -> {
                                            daycareManager.clearAll();
                                            context.getSource().sendFeedback(
                                                    Component.literal("\u00A7aCleared all daycare data."));
                                            return 1;
                                        })
                                )
                                .then(ClientCommandManager.literal("goto")
                                        .then(ClientCommandManager.literal("pen")
                                                .then(ClientCommandManager.argument("penNumber", IntegerArgumentType.integer(1, 4))
                                                        .executes(context -> {
                                                            int pen = IntegerArgumentType.getInteger(context, "penNumber");
                                                            daycareManager.setPendingNavTarget("pen:" + pen);
                                                            var conn = context.getSource().getClient().getConnection();
                                                            if (conn != null) conn.sendCommand("daycare");
                                                            return 1;
                                                        })
                                                )
                                        )
                                        .then(ClientCommandManager.literal("backpack")
                                                .executes(context -> {
                                                    daycareManager.setPendingNavTarget("backpack");
                                                    var conn = context.getSource().getClient().getConnection();
                                                    if (conn != null) conn.sendCommand("daycare");
                                                    return 1;
                                                })
                                        )
                                )
                                .executes(context -> {
                                    int unlockedPens = daycareManager.getDisplayPens().size();
                                    long breedingPens = daycareManager.getDisplayPens().stream()
                                            .filter(p -> p.isBreeding()).count();
                                    int activeEggs = daycareManager.getTotalActiveEggs();
                                    int maxDisplay = hudConfig.getDaycareEggsHatchingSlots();

                                    StringBuilder sb = new StringBuilder();
                                    sb.append("\u00A76Daycare Status:\n");
                                    sb.append("\u00A77Unlocked pens: \u00A7f").append(unlockedPens);
                                    sb.append("\n\u00A77Breeding: \u00A7f").append(breedingPens);
                                    sb.append("\n\u00A77Hatching eggs: \u00A7f").append(activeEggs);
                                    sb.append("\n\u00A77Max eggs displayed: \u00A7f").append(maxDisplay);
                                    sb.append("\n\u00A77Menu enabled: \u00A7f").append(hudConfig.isDaycareMenuEnabled());
                                    sb.append("\n\u00A77Sounds enabled: \u00A7f").append(hudConfig.isDaycareSoundsEnabled());
                                    sb.append("\n\u00A77Daycare scale: \u00A7f").append(String.format("%.0f%%", hudConfig.getDaycareScale() * 100));
                                    sb.append("\n\u00A77IV% highlight: \u00A76").append(hudConfig.getDaycareIvPercentLower())
                                            .append("%+\u00A77 (orange) / \u00A7b").append(hudConfig.getDaycareIvPercentUpper())
                                            .append("%+\u00A77 (blue) / \u00A7a").append(hudConfig.getDaycareIvPercentTop())
                                            .append("%+\u00A77 (green)");

                                    context.getSource().sendFeedback(Component.literal(sb.toString()));
                                    return 1;
                                })
                        )
                        .then(ClientCommandManager.literal("manualHatchMultiplier")
                                .then(ClientCommandManager.argument("value", FloatArgumentType.floatArg(0f, 2.0f))
                                        .executes(context -> {
                                            float value = FloatArgumentType.getFloat(context, "value");
                                            if (value != 0f && value != 2.0f) {
                                                context.getSource().sendFeedback(
                                                        Component.literal("\u00A7cInvalid value. Use 0 (auto-detect) or 2.0 (force 2.0x speed)."));
                                                return 0;
                                            }
                                            hudConfig.setManualHatchMultiplier(value);
                                            String label = value == 0f ? "\u00A7bauto-detect" : "\u00A7b2.0x";
                                            context.getSource().sendFeedback(
                                                    Component.literal("\u00A7aHatch multiplier set to " + label + "\u00A7a. Takes effect on next server join."));
                                            return 1;
                                        })
                                )
                                .executes(context -> {
                                    float current = hudConfig.getManualHatchMultiplier();
                                    String label = current == 0f ? "auto-detect" : String.format("%.1fx", current);
                                    context.getSource().sendFeedback(Component.literal(
                                            "\u00A77manualHatchMultiplier = \u00A7f" + label +
                                            "\n\u00A77Usage: \u00A7e/saa manualHatchMultiplier <0 | 2.0>" +
                                            "\n\u00A770 = auto-detect from rank, 2.0 = force 2.0x hatch speed"));
                                    return 1;
                                })
                        )
                        .then(ClientCommandManager.literal("wt")
                                .then(ClientCommandManager.literal("clear")
                                        .executes(context -> {
                                            wondertradeManager.clearAll();
                                            context.getSource().sendFeedback(
                                                    Component.literal("\u00A7aCleared wondertrade timer data."));
                                            return 1;
                                        })
                                )
                                .executes(context -> {
                                    StringBuilder sb = new StringBuilder();
                                    sb.append("\u00A76Wondertrade Status:\n");
                                    if (!wondertradeManager.hasTimer()) {
                                        sb.append("\u00A77Timer: \u00A7cNot set");
                                    } else if (wondertradeManager.isCooldownOver()) {
                                        sb.append("\u00A77Timer: \u00A7aCooldown over!");
                                    } else {
                                        sb.append("\u00A77Timer: \u00A7f").append(wondertradeManager.getRemainingFormatted()).append(" remaining");
                                    }
                                    sb.append("\n\u00A77Menu enabled: \u00A7f").append(hudConfig.isWtMenuEnabled());
                                    sb.append("\n\u00A77Chat reminders: \u00A7f").append(hudConfig.isWtShowChatReminders());
                                    sb.append("\n\u00A77Sounds enabled: \u00A7f").append(hudConfig.isWtSoundsEnabled());
                                    sb.append("\n\u00A77WT scale: \u00A7f").append(String.format("%.0f%%", hudConfig.getWtScale() * 100));

                                    context.getSource().sendFeedback(Component.literal(sb.toString()));
                                    return 1;
                                })
                        )
                        .then(ClientCommandManager.literal("grading")
                                .then(ClientCommandManager.literal("clear")
                                        .executes(context -> {
                                            cardGradingManager.clearAll();
                                            context.getSource().sendFeedback(
                                                    Component.literal("\u00A7aCleared card grading timer data."));
                                            return 1;
                                        })
                                )
                                .executes(context -> {
                                    StringBuilder sb = new StringBuilder();
                                    sb.append("\u00A76Card Grading Status:\n");
                                    if (!cardGradingManager.hasTimer()) {
                                        sb.append("\u00A77Timer: \u00A7cNot set");
                                    } else if (cardGradingManager.isReadyToClaim()) {
                                        sb.append("\u00A77Timer: \u00A7aReady to claim!");
                                    } else {
                                        sb.append("\u00A77Timer: \u00A7f")
                                                .append(cardGradingManager.getRemainingFormatted())
                                                .append(" remaining");
                                    }
                                    sb.append("\n\u00A77Menu enabled: \u00A7f").append(hudConfig.isCardGradingMenuEnabled());
                                    sb.append("\n\u00A77Sounds enabled: \u00A7f").append(hudConfig.isCardGradingSoundsEnabled());
                                    sb.append("\n\u00A77Grading scale: \u00A7f")
                                            .append(String.format("%.0f%%", hudConfig.getCardGradingScale() * 100));

                                    context.getSource().sendFeedback(Component.literal(sb.toString()));
                                    return 1;
                                })
                        )
                        .then(ClientCommandManager.literal("portal")
                                .then(ClientCommandManager.literal("track")
                                        .then(ClientCommandManager.argument("id", IntegerArgumentType.integer(0))
                                                .executes(context -> {
                                                    int id = IntegerArgumentType.getInteger(context, "id");
                                                    String result = portalManager.trackPortal(id);
                                                    context.getSource().sendFeedback(Component.literal(result));
                                                    return 1;
                                                })
                                        )
                                )
                                .then(ClientCommandManager.literal("clear")
                                        .executes(context -> {
                                            portalManager.clear();
                                            context.getSource().sendFeedback(
                                                    Component.literal("\u00A7aCleared portal tracking data."));
                                            return 1;
                                        })
                                )
                                .executes(context -> {
                                    boolean active = portalManager.isActive();
                                    StringBuilder sb = new StringBuilder();
                                    sb.append("\u00A76Portal Status:\n");
                                    if (active) {
                                        sb.append("\u00A77Tracking: \u00A7a").append(portalManager.getDisplayText());
                                        sb.append("\n\u00A77Position: \u00A7f")
                                                .append(portalManager.getPortalPos().getX()).append(", ")
                                                .append(portalManager.getPortalPos().getY()).append(", ")
                                                .append(portalManager.getPortalPos().getZ());
                                        sb.append("\n\u00A77Distance: \u00A7f")
                                                .append(String.format("%.0f", portalManager.getHorizontalDistance()))
                                                .append(" blocks");
                                    } else {
                                        sb.append("\u00A77Tracking: \u00A7cNone");
                                    }
                                    context.getSource().sendFeedback(Component.literal(sb.toString()));
                                    return 1;
                                })
                        )
                        .then(ClientCommandManager.literal("config")
                                .then(ClientCommandManager.literal("safari")
                                        .then(ClientCommandManager.literal("timerAlways")
                                                .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                                        .executes(context -> {
                                                            boolean value = BoolArgumentType.getBool(context, "value");
                                                            hudConfig.setSafariTimerAlways(value);
                                                            String msg = value
                                                                    ? "\u00A7aHUD will always display when active."
                                                                    : "\u00A7aHUD will only display in the safari zone.";
                                                            context.getSource().sendFeedback(Component.literal(msg));
                                                            return 1;
                                                        })
                                                )
                                                .executes(context -> {
                                                    boolean current = hudConfig.isSafariTimerAlways();
                                                    context.getSource().sendFeedback(Component.literal(
                                                            "\u00A77timerAlways = \u00A7f" + current +
                                                            "\n\u00A77Usage: \u00A7e/saa config safari timerAlways <true|false>"));
                                                    return 1;
                                                })
                                        )
                                        .then(ClientCommandManager.literal("menuEnabled")
                                                .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                                        .executes(context -> {
                                                            boolean value = BoolArgumentType.getBool(context, "value");
                                                            hudConfig.setSafariMenuEnabled(value);
                                                            String msg = value
                                                                    ? "\u00A7aSafari HUD enabled."
                                                                    : "\u00A7aSafari HUD disabled.";
                                                            context.getSource().sendFeedback(Component.literal(msg));
                                                            return 1;
                                                        })
                                                )
                                                .executes(context -> {
                                                    boolean current = hudConfig.isSafariMenuEnabled();
                                                    context.getSource().sendFeedback(Component.literal(
                                                            "\u00A77menuEnabled = \u00A7f" + current +
                                                            "\n\u00A77Usage: \u00A7e/saa config safari menuEnabled <true|false>"));
                                                    return 1;
                                                })
                                        )
                                        .then(ClientCommandManager.literal("questMonGlow")
                                                .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                                        .executes(context -> {
                                                            boolean value = BoolArgumentType.getBool(context, "value");
                                                            hudConfig.setSafariQuestMonGlow(value);
                                                            String msg = value
                                                                    ? "\u00A7aQuest Pokemon glow enabled."
                                                                    : "\u00A7aQuest Pokemon glow disabled.";
                                                            context.getSource().sendFeedback(Component.literal(msg));
                                                            return 1;
                                                        })
                                                )
                                                .executes(context -> {
                                                    boolean current = hudConfig.isSafariQuestMonGlow();
                                                    context.getSource().sendFeedback(Component.literal(
                                                            "\u00A77questMonGlow = \u00A7f" + current +
                                                            "\n\u00A77Usage: \u00A7e/saa config safari questMonGlow <true|false>"));
                                                    return 1;
                                                })
                                        )
                                        .executes(context -> {
                                            context.getSource().sendFeedback(Component.literal(
                                                    "\u00A76Safari Config:\n" +
                                                    "\u00A77menuEnabled = \u00A7f" + hudConfig.isSafariMenuEnabled() +
                                                    "\n\u00A77timerAlways = \u00A7f" + hudConfig.isSafariTimerAlways() +
                                                    "\n\u00A77questMonGlow = \u00A7f" + hudConfig.isSafariQuestMonGlow()));
                                            return 1;
                                        })
                                )
                                .then(ClientCommandManager.literal("daycare")
                                        .then(ClientCommandManager.literal("menuEnabled")
                                                .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                                        .executes(context -> {
                                                            boolean value = BoolArgumentType.getBool(context, "value");
                                                            hudConfig.setDaycareMenuEnabled(value);
                                                            String msg = value
                                                                    ? "\u00A7aDaycare HUD enabled."
                                                                    : "\u00A7aDaycare HUD disabled.";
                                                            context.getSource().sendFeedback(Component.literal(msg));
                                                            return 1;
                                                        })
                                                )
                                                .executes(context -> {
                                                    boolean current = hudConfig.isDaycareMenuEnabled();
                                                    context.getSource().sendFeedback(Component.literal(
                                                            "\u00A77menuEnabled = \u00A7f" + current +
                                                            "\n\u00A77Usage: \u00A7e/saa config daycare menuEnabled <true|false>"));
                                                    return 1;
                                                })
                                        )
                                        .then(ClientCommandManager.literal("soundsEnabled")
                                                .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                                        .executes(context -> {
                                                            boolean value = BoolArgumentType.getBool(context, "value");
                                                            hudConfig.setDaycareSoundsEnabled(value);
                                                            String msg = value
                                                                    ? "\u00A7aDaycare sounds enabled."
                                                                    : "\u00A7aDaycare sounds disabled.";
                                                            context.getSource().sendFeedback(Component.literal(msg));
                                                            return 1;
                                                        })
                                                )
                                                .executes(context -> {
                                                    boolean current = hudConfig.isDaycareSoundsEnabled();
                                                    context.getSource().sendFeedback(Component.literal(
                                                            "\u00A77soundsEnabled = \u00A7f" + current +
                                                            "\n\u00A77Usage: \u00A7e/saa config daycare soundsEnabled <true|false>"));
                                                    return 1;
                                                })
                                        )
                                        .then(ClientCommandManager.literal("eggsHatchingSlots")
                                                .then(ClientCommandManager.argument("value", IntegerArgumentType.integer(0, 5))
                                                        .executes(context -> {
                                                            int value = IntegerArgumentType.getInteger(context, "value");
                                                            hudConfig.setDaycareEggsHatchingSlots(value);
                                                            String msg = value == 0
                                                                    ? "\u00A7aDaycare hatching section hidden."
                                                                    : "\u00A7aDaycare will show up to " + value + " hatching eggs.";
                                                            context.getSource().sendFeedback(Component.literal(msg));
                                                            return 1;
                                                        })
                                                )
                                                .executes(context -> {
                                                    int current = hudConfig.getDaycareEggsHatchingSlots();
                                                    context.getSource().sendFeedback(Component.literal(
                                                            "\u00A77eggsHatchingSlots = \u00A7f" + current +
                                                            "\n\u00A77Usage: \u00A7e/saa config daycare eggsHatchingSlots <0-5>" +
                                                            "\n\u00A77Set to 0 to hide hatching section."));
                                                    return 1;
                                                })
                                        )
                                        .then(ClientCommandManager.literal("babyGuards")
                                                .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                                        .executes(context -> {
                                                            boolean value = BoolArgumentType.getBool(context, "value");
                                                            hudConfig.setDaycareBabyGuards(value);
                                                            String msg = value
                                                                    ? "\u00A7aDaycare baby guards enabled."
                                                                    : "\u00A7aDaycare baby guards disabled.";
                                                            context.getSource().sendFeedback(Component.literal(msg));
                                                            return 1;
                                                        })
                                                )
                                                .executes(context -> {
                                                    boolean current = hudConfig.isDaycareBabyGuards();
                                                    context.getSource().sendFeedback(Component.literal(
                                                            "\u00A77babyGuards = \u00A7f" + current +
                                                            "\n\u00A77Usage: \u00A7e/saa config daycare babyGuards <true|false>"));
                                                    return 1;
                                                })
                                        )
                                        .then(ClientCommandManager.literal("ivPercentPreference")
                                                .then(ClientCommandManager.argument("lowerBound", IntegerArgumentType.integer(0, 100))
                                                        .then(ClientCommandManager.argument("upperBound", IntegerArgumentType.integer(0, 100))
                                                                .executes(context -> {
                                                                    int lower = IntegerArgumentType.getInteger(context, "lowerBound");
                                                                    int upper = IntegerArgumentType.getInteger(context, "upperBound");
                                                                    if (lower > upper) {
                                                                        context.getSource().sendFeedback(Component.literal(
                                                                                "\u00A7cInvalid IV% thresholds. Expected lower <= upper."));
                                                                        return 0;
                                                                    }
                                                                    hudConfig.setDaycareIvPercentLower(lower);
                                                                    hudConfig.setDaycareIvPercentUpper(upper);
                                                                    context.getSource().sendFeedback(Component.literal(
                                                                            "\u00A7aIV% thresholds set to \u00A76" + lower
                                                                            + "%\u00A7a (orange) / \u00A7b" + upper
                                                                            + "%\u00A7a (blue). \u00A77Top green threshold remains \u00A7a"
                                                                            + hudConfig.getDaycareIvPercentTop() + "%\u00A77."));
                                                                    return 1;
                                                                })
                                                                .then(ClientCommandManager.argument("topBound", IntegerArgumentType.integer(0, 100))
                                                                        .executes(context -> {
                                                                            int lower = IntegerArgumentType.getInteger(context, "lowerBound");
                                                                            int upper = IntegerArgumentType.getInteger(context, "upperBound");
                                                                            int top = IntegerArgumentType.getInteger(context, "topBound");
                                                                            if (lower > upper || upper > top) {
                                                                                context.getSource().sendFeedback(Component.literal(
                                                                                        "\u00A7cInvalid IV% thresholds. Expected lower <= upper <= top."));
                                                                                return 0;
                                                                            }
                                                                            hudConfig.setDaycareIvPercentLower(lower);
                                                                            hudConfig.setDaycareIvPercentUpper(upper);
                                                                            hudConfig.setDaycareIvPercentTop(top);
                                                                            context.getSource().sendFeedback(Component.literal(
                                                                                    "\u00A7aIV% thresholds set to \u00A76" + lower
                                                                                    + "%\u00A7a (orange) / \u00A7b" + upper
                                                                                    + "%\u00A7a (blue) / \u00A7a" + top
                                                                                    + "%\u00A7a (green)."));
                                                                            return 1;
                                                                        })
                                                                )
                                                        )
                                                )
                                                .executes(context -> {
                                                    context.getSource().sendFeedback(Component.literal(
                                                            "\u00A77IV% highlight thresholds:" +
                                                            "\n\u00A76  Orange: \u00A7f" + hudConfig.getDaycareIvPercentLower() + "%+" +
                                                            "\n\u00A7b  Blue: \u00A7f" + hudConfig.getDaycareIvPercentUpper() + "%+" +
                                                            "\n\u00A7a  Green: \u00A7f" + hudConfig.getDaycareIvPercentTop() + "%+" +
                                                            "\n\u00A77Usage: \u00A7e/saa config daycare ivPercentPreference <lower> <upper> [top]"));
                                                    return 1;
                                                })
                                        )
                                        .executes(context -> {
                                            context.getSource().sendFeedback(Component.literal(
                                                    "\u00A76Daycare Config:\n" +
                                                    "\u00A77menuEnabled = \u00A7f" + hudConfig.isDaycareMenuEnabled() +
                                                    "\n\u00A77soundsEnabled = \u00A7f" + hudConfig.isDaycareSoundsEnabled() +
                                                    "\n\u00A77eggsHatchingSlots = \u00A7f" + hudConfig.getDaycareEggsHatchingSlots() +
                                                    "\n\u00A77babyGuards = \u00A7f" + hudConfig.isDaycareBabyGuards() +
                                                    "\n\u00A77ivPercentPreference = \u00A76" + hudConfig.getDaycareIvPercentLower()
                                                    + "%+\u00A77 (orange) / \u00A7b" + hudConfig.getDaycareIvPercentUpper()
                                                    + "%+\u00A77 (blue) / \u00A7a" + hudConfig.getDaycareIvPercentTop()
                                                    + "%+\u00A77 (green)"));
                                            return 1;
                                        })
                                )
                                .then(ClientCommandManager.literal("wt")
                                        .then(ClientCommandManager.literal("menuEnabled")
                                                .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                                        .executes(context -> {
                                                            boolean value = BoolArgumentType.getBool(context, "value");
                                                            hudConfig.setWtMenuEnabled(value);
                                                            String msg = value
                                                                    ? "\u00A7aWondertrade HUD enabled."
                                                                    : "\u00A7aWondertrade HUD disabled.";
                                                            context.getSource().sendFeedback(Component.literal(msg));
                                                            return 1;
                                                        })
                                                )
                                                .executes(context -> {
                                                    boolean current = hudConfig.isWtMenuEnabled();
                                                    context.getSource().sendFeedback(Component.literal(
                                                            "\u00A77menuEnabled = \u00A7f" + current +
                                                            "\n\u00A77Usage: \u00A7e/saa config wt menuEnabled <true|false>"));
                                                    return 1;
                                                })
                                        )
                                        .then(ClientCommandManager.literal("showChatReminders")
                                                .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                                        .executes(context -> {
                                                            boolean value = BoolArgumentType.getBool(context, "value");
                                                            hudConfig.setWtShowChatReminders(value);
                                                            String msg = value
                                                                    ? "\u00A7aWT chat reminders enabled."
                                                                    : "\u00A7aWT chat reminders disabled.";
                                                            context.getSource().sendFeedback(Component.literal(msg));
                                                            return 1;
                                                        })
                                                )
                                                .executes(context -> {
                                                    boolean current = hudConfig.isWtShowChatReminders();
                                                    context.getSource().sendFeedback(Component.literal(
                                                            "\u00A77showChatReminders = \u00A7f" + current +
                                                            "\n\u00A77Usage: \u00A7e/saa config wt showChatReminders <true|false>"));
                                                    return 1;
                                                })
                                        )
                                        .then(ClientCommandManager.literal("soundsEnabled")
                                                .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                                        .executes(context -> {
                                                            boolean value = BoolArgumentType.getBool(context, "value");
                                                            hudConfig.setWtSoundsEnabled(value);
                                                            String msg = value
                                                                    ? "\u00A7aWT sounds enabled."
                                                                    : "\u00A7aWT sounds disabled.";
                                                            context.getSource().sendFeedback(Component.literal(msg));
                                                            return 1;
                                                        })
                                                )
                                                .executes(context -> {
                                                    boolean current = hudConfig.isWtSoundsEnabled();
                                                    context.getSource().sendFeedback(Component.literal(
                                                            "\u00A77soundsEnabled = \u00A7f" + current +
                                                            "\n\u00A77Usage: \u00A7e/saa config wt soundsEnabled <true|false>"));
                                                    return 1;
                                                })
                                        )
                                        .executes(context -> {
                                            context.getSource().sendFeedback(Component.literal(
                                                    "\u00A76Wondertrade Config:\n" +
                                                    "\u00A77menuEnabled = \u00A7f" + hudConfig.isWtMenuEnabled() +
                                                    "\n\u00A77showChatReminders = \u00A7f" + hudConfig.isWtShowChatReminders() +
                                                    "\n\u00A77soundsEnabled = \u00A7f" + hudConfig.isWtSoundsEnabled()));
                                            return 1;
                                        })
                                )
                                .then(ClientCommandManager.literal("grading")
                                        .then(ClientCommandManager.literal("menuEnabled")
                                                .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                                        .executes(context -> {
                                                            boolean value = BoolArgumentType.getBool(context, "value");
                                                            hudConfig.setCardGradingMenuEnabled(value);
                                                            String msg = value
                                                                    ? "\u00A7aCard grading HUD enabled."
                                                                    : "\u00A7aCard grading HUD disabled.";
                                                            context.getSource().sendFeedback(Component.literal(msg));
                                                            return 1;
                                                        })
                                                )
                                                .executes(context -> {
                                                    boolean current = hudConfig.isCardGradingMenuEnabled();
                                                    context.getSource().sendFeedback(Component.literal(
                                                            "\u00A77menuEnabled = \u00A7f" + current +
                                                            "\n\u00A77Usage: \u00A7e/saa config grading menuEnabled <true|false>"));
                                                    return 1;
                                                })
                                        )
                                        .then(ClientCommandManager.literal("soundsEnabled")
                                                .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                                        .executes(context -> {
                                                            boolean value = BoolArgumentType.getBool(context, "value");
                                                            hudConfig.setCardGradingSoundsEnabled(value);
                                                            String msg = value
                                                                    ? "\u00A7aCard grading sounds enabled."
                                                                    : "\u00A7aCard grading sounds disabled.";
                                                            context.getSource().sendFeedback(Component.literal(msg));
                                                            return 1;
                                                        })
                                                )
                                                .executes(context -> {
                                                    boolean current = hudConfig.isCardGradingSoundsEnabled();
                                                    context.getSource().sendFeedback(Component.literal(
                                                            "\u00A77soundsEnabled = \u00A7f" + current +
                                                            "\n\u00A77Usage: \u00A7e/saa config grading soundsEnabled <true|false>"));
                                                    return 1;
                                                })
                                        )
                                        .executes(context -> {
                                            context.getSource().sendFeedback(Component.literal(
                                                    "\u00A76Card Grading Config:\n" +
                                                    "\u00A77menuEnabled = \u00A7f" + hudConfig.isCardGradingMenuEnabled() +
                                                    "\n\u00A77soundsEnabled = \u00A7f" + hudConfig.isCardGradingSoundsEnabled()));
                                            return 1;
                                        })
                                )
                                .then(ClientCommandManager.literal("suppress")
                                        .then(ClientCommandManager.literal("inRaids")
                                                .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                                        .executes(context -> {
                                                            boolean value = BoolArgumentType.getBool(context, "value");
                                                            hudConfig.setSuppressInRaids(value);
                                                            String msg = value
                                                                    ? "\u00A7aHUD will be suppressed during raids."
                                                                    : "\u00A7aHUD will show during raids.";
                                                            context.getSource().sendFeedback(Component.literal(msg));
                                                            return 1;
                                                        })
                                                )
                                                .executes(context -> {
                                                    boolean current = hudConfig.isSuppressInRaids();
                                                    context.getSource().sendFeedback(Component.literal(
                                                            "\u00A77inRaids = \u00A7f" + current +
                                                            "\n\u00A77Usage: \u00A7e/saa config suppress inRaids <true|false>"));
                                                    return 1;
                                                })
                                        )
                                        .then(ClientCommandManager.literal("inHideouts")
                                                .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                                        .executes(context -> {
                                                            boolean value = BoolArgumentType.getBool(context, "value");
                                                            hudConfig.setSuppressInHideouts(value);
                                                            String msg = value
                                                                    ? "\u00A7aHUD will be suppressed in hideouts."
                                                                    : "\u00A7aHUD will show in hideouts.";
                                                            context.getSource().sendFeedback(Component.literal(msg));
                                                            return 1;
                                                        })
                                                )
                                                .executes(context -> {
                                                    boolean current = hudConfig.isSuppressInHideouts();
                                                    context.getSource().sendFeedback(Component.literal(
                                                            "\u00A77inHideouts = \u00A7f" + current +
                                                            "\n\u00A77Usage: \u00A7e/saa config suppress inHideouts <true|false>"));
                                                    return 1;
                                                })
                                        )
                                        .then(ClientCommandManager.literal("inDungeons")
                                                .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                                        .executes(context -> {
                                                            boolean value = BoolArgumentType.getBool(context, "value");
                                                            hudConfig.setSuppressInDungeons(value);
                                                            String msg = value
                                                                    ? "\u00A7aHUD will be suppressed in dungeons."
                                                                    : "\u00A7aHUD will show in dungeons.";
                                                            context.getSource().sendFeedback(Component.literal(msg));
                                                            return 1;
                                                        })
                                                )
                                                .executes(context -> {
                                                    boolean current = hudConfig.isSuppressInDungeons();
                                                    context.getSource().sendFeedback(Component.literal(
                                                            "\u00A77inDungeons = \u00A7f" + current +
                                                            "\n\u00A77Usage: \u00A7e/saa config suppress inDungeons <true|false>"));
                                                    return 1;
                                                })
                                        )
                                        .then(ClientCommandManager.literal("inBattles")
                                                .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                                        .executes(context -> {
                                                            boolean value = BoolArgumentType.getBool(context, "value");
                                                            hudConfig.setSuppressInBattles(value);
                                                            String msg = value
                                                                    ? "\u00A7aHUD will be suppressed during battles."
                                                                    : "\u00A7aHUD will show during battles.";
                                                            context.getSource().sendFeedback(Component.literal(msg));
                                                            return 1;
                                                        })
                                                )
                                                .executes(context -> {
                                                    boolean current = hudConfig.isSuppressInBattles();
                                                    context.getSource().sendFeedback(Component.literal(
                                                            "\u00A77inBattles = \u00A7f" + current +
                                                            "\n\u00A77Usage: \u00A7e/saa config suppress inBattles <true|false>"));
                                                    return 1;
                                                })
                                        )
                                        .executes(context -> {
                                            context.getSource().sendFeedback(Component.literal(
                                                    "\u00A76Suppress Config:\n" +
                                                    "\u00A77inRaids = \u00A7f" + hudConfig.isSuppressInRaids() +
                                                    "\n\u00A77inHideouts = \u00A7f" + hudConfig.isSuppressInHideouts() +
                                                    "\n\u00A77inDungeons = \u00A7f" + hudConfig.isSuppressInDungeons() +
                                                    "\n\u00A77inBattles = \u00A7f" + hudConfig.isSuppressInBattles()));
                                            return 1;
                                        })
                                )
                                .then(ClientCommandManager.literal("hudStyle")
                                        .then(ClientCommandManager.literal("solid")
                                                .executes(context -> {
                                                    hudConfig.setHudStyle(HudConfig.HudStyle.SOLID);
                                                    context.getSource().sendFeedback(
                                                            Component.literal("\u00A7aHUD style set to solid."));
                                                    return 1;
                                                })
                                        )
                                        .then(ClientCommandManager.literal("transparent")
                                                .executes(context -> {
                                                    hudConfig.setHudStyle(HudConfig.HudStyle.TRANSPARENT);
                                                    context.getSource().sendFeedback(
                                                            Component.literal("\u00A7aHUD style set to transparent."));
                                                    return 1;
                                                })
                                        )
                                        .executes(context -> {
                                            String current = hudConfig.getHudStyle().name().toLowerCase();
                                            context.getSource().sendFeedback(Component.literal(
                                                    "\u00A77hudStyle = \u00A7f" + current +
                                                    "\n\u00A77Usage: \u00A7e/saa config hudStyle <solid|transparent>"));
                                            return 1;
                                        })
                                )
                                .then(ClientCommandManager.literal("hudLayout")
                                        .then(ClientCommandManager.literal("full")
                                                .executes(context -> {
                                                    hudConfig.setHudLayout(HudConfig.HudLayout.FULL);
                                                    context.getSource().sendFeedback(
                                                            Component.literal("\u00A7aHUD layout set to full."));
                                                    return 1;
                                                })
                                        )
                                        .then(ClientCommandManager.literal("compact")
                                                .executes(context -> {
                                                    hudConfig.setHudLayout(HudConfig.HudLayout.COMPACT);
                                                    context.getSource().sendFeedback(
                                                            Component.literal("\u00A7aHUD layout set to compact."));
                                                    return 1;
                                                })
                                        )
                                        .executes(context -> {
                                            String layout = hudConfig.getHudLayout().name().toLowerCase();
                                            context.getSource().sendFeedback(Component.literal(
                                                    "\u00A77hudLayout = \u00A7f" + layout +
                                                    "\n\u00A77Usage: \u00A7e/saa config hudLayout <full|compact>"));
                                            return 1;
                                        })
                                )
                                .then(ClientCommandManager.literal("hudHidden")
                                        .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                                .executes(context -> {
                                                    boolean value = BoolArgumentType.getBool(context, "value");
                                                    hudConfig.setHudHidden(value);
                                                    String msg = value
                                                            ? "\u00A7aHUD manually hidden."
                                                            : "\u00A7aHUD manually shown.";
                                                    context.getSource().sendFeedback(Component.literal(msg));
                                                    return 1;
                                                })
                                        )
                                        .executes(context -> {
                                            boolean current = hudConfig.isHudHidden();
                                            context.getSource().sendFeedback(Component.literal(
                                                    "\u00A77hudHidden = \u00A7f" + current +
                                                    "\n\u00A77Usage: \u00A7e/saa config hudHidden <true|false>"));
                                            return 1;
                                        })
                                )
                                .then(ClientCommandManager.literal("driflootAlerts")
                                        .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                                .executes(context -> {
                                                    boolean value = BoolArgumentType.getBool(context, "value");
                                                    hudConfig.setDriflootAlertsEnabled(value);
                                                    String msg = value
                                                            ? "\u00A7aDrifloot alerts enabled."
                                                            : "\u00A7aDrifloot alerts disabled.";
                                                    context.getSource().sendFeedback(Component.literal(msg));
                                                    return 1;
                                                })
                                        )
                                        .executes(context -> {
                                            boolean current = hudConfig.isDriflootAlertsEnabled();
                                            context.getSource().sendFeedback(Component.literal(
                                                    "\u00A77driflootAlerts = \u00A7f" + current +
                                                    "\n\u00A77Usage: \u00A7e/saa config driflootAlerts <true|false>"));
                                            return 1;
                                        })
                                )
                                .then(ClientCommandManager.literal("gruntFinder")
                                        .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                                .executes(context -> {
                                                    boolean value = BoolArgumentType.getBool(context, "value");
                                                    hudConfig.setGruntFinderEnabled(value);
                                                    String msg = value
                                                            ? "\u00A7aGrunt finder enabled."
                                                            : "\u00A7aGrunt finder disabled.";
                                                    context.getSource().sendFeedback(Component.literal(msg));
                                                    return 1;
                                                })
                                        )
                                        .executes(context -> {
                                            boolean current = hudConfig.isGruntFinderEnabled();
                                            context.getSource().sendFeedback(Component.literal(
                                                    "\u00A77gruntFinder = \u00A7f" + current +
                                                    "\n\u00A77Usage: \u00A7e/saa config gruntFinder <true|false>"));
                                            return 1;
                                        })
                                )
                                .then(ClientCommandManager.literal("messageNotificationSound")
                                        .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                                .executes(context -> {
                                                    boolean value = BoolArgumentType.getBool(context, "value");
                                                    hudConfig.setMessageNotificationSound(value);
                                                    String msg = value
                                                            ? "\u00A7aMessage notification sound enabled."
                                                            : "\u00A7aMessage notification sound disabled.";
                                                    context.getSource().sendFeedback(Component.literal(msg));
                                                    return 1;
                                                })
                                        )
                                        .executes(context -> {
                                            boolean current = hudConfig.isMessageNotificationSound();
                                            context.getSource().sendFeedback(Component.literal(
                                                    "\u00A77messageNotificationSound = \u00A7f" + current +
                                                    "\n\u00A77Usage: \u00A7e/saa config messageNotificationSound <true|false>"));
                                            return 1;
                                        })
                                )
                                .then(ClientCommandManager.literal("autoAcceptPartyInvites")
                                        .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                                .executes(context -> {
                                                    boolean value = BoolArgumentType.getBool(context, "value");
                                                    hudConfig.setAutoAcceptPartyInvites(value);
                                                    String msg = value
                                                            ? "\u00A7aAuto-accept party invites enabled."
                                                            : "\u00A7aAuto-accept party invites disabled.";
                                                    context.getSource().sendFeedback(Component.literal(msg));
                                                    return 1;
                                                })
                                        )
                                        .executes(context -> {
                                            boolean current = hudConfig.isAutoAcceptPartyInvites();
                                            context.getSource().sendFeedback(Component.literal(
                                                    "\u00A77autoAcceptPartyInvites = \u00A7f" + current +
                                                    "\n\u00A77Usage: \u00A7e/saa config autoAcceptPartyInvites <true|false>"));
                                            return 1;
                                        })
                                )
                                .then(ClientCommandManager.literal("stats")
                                        .then(ClientCommandManager.literal("menuEnabled")
                                                .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                                        .executes(context -> {
                                                            boolean value = BoolArgumentType.getBool(context, "value");
                                                            hudConfig.setCardStatsMenuEnabled(value);
                                                            String msg = value
                                                                    ? "\u00A7aStats menu enabled."
                                                                    : "\u00A7aStats menu disabled.";
                                                            context.getSource().sendFeedback(Component.literal(msg));
                                                            return 1;
                                                        })
                                                )
                                                .executes(context -> {
                                                    boolean current = hudConfig.isCardStatsMenuEnabled();
                                                    context.getSource().sendFeedback(Component.literal(
                                                            "\u00A77menuEnabled = \u00A7f" + current +
                                                            "\n\u00A77Usage: \u00A7e/saa config stats menuEnabled <true|false>"));
                                                    return 1;
                                                })
                                        )
                                        .then(ClientCommandManager.literal("displayAlways")
                                                .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                                        .executes(context -> {
                                                            boolean value = BoolArgumentType.getBool(context, "value");
                                                            hudConfig.setCardStatsDisplayAlways(value);
                                                            String msg = value
                                                                    ? "\u00A7aStats will always display on HUD."
                                                                    : "\u00A7aStats will not display on HUD.";
                                                            context.getSource().sendFeedback(Component.literal(msg));
                                                            return 1;
                                                        })
                                                )
                                                .executes(context -> {
                                                    boolean current = hudConfig.isCardStatsDisplayAlways();
                                                    context.getSource().sendFeedback(Component.literal(
                                                            "\u00A77displayAlways = \u00A7f" + current +
                                                            "\n\u00A77Usage: \u00A7e/saa config stats displayAlways <true|false>"));
                                                    return 1;
                                                })
                                        )
                                        .then(ClientCommandManager.literal("displayInInventory")
                                                .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                                        .executes(context -> {
                                                            boolean value = BoolArgumentType.getBool(context, "value");
                                                            hudConfig.setCardStatsDisplayInInventory(value);
                                                            String msg = value
                                                                    ? "\u00A7aStats will display in inventory."
                                                                    : "\u00A7aStats will not display in inventory.";
                                                            context.getSource().sendFeedback(Component.literal(msg));
                                                            return 1;
                                                        })
                                                )
                                                .executes(context -> {
                                                    boolean current = hudConfig.isCardStatsDisplayInInventory();
                                                    context.getSource().sendFeedback(Component.literal(
                                                            "\u00A77displayInInventory = \u00A7f" + current +
                                                            "\n\u00A77Usage: \u00A7e/saa config stats displayInInventory <true|false>"));
                                                    return 1;
                                                })
                                        )
                                        .executes(context -> {
                                            context.getSource().sendFeedback(Component.literal(
                                                    "\u00A76Stats Config:\n" +
                                                    "\u00A77menuEnabled = \u00A7f" + hudConfig.isCardStatsMenuEnabled() +
                                                    "\n\u00A77displayAlways = \u00A7f" + hudConfig.isCardStatsDisplayAlways() +
                                                    "\n\u00A77displayInInventory = \u00A7f" + hudConfig.isCardStatsDisplayInInventory()));
                                            return 1;
                                        })
                                )
                                .executes(context -> {
                                    context.getSource().sendFeedback(Component.literal(
                                            "\u00A76Configuration:" +
                                            "\n\n\u00A7e[Global]" +
                                            "\n\u00A77hudStyle = \u00A7f" + hudConfig.getHudStyle().name().toLowerCase() +
                                            "\n\u00A77hudLayout = \u00A7f" + hudConfig.getHudLayout().name().toLowerCase() +
                                            "\n\u00A77hudHidden = \u00A7f" + hudConfig.isHudHidden() +
                                            "\n\u00A77driflootAlerts = \u00A7f" + hudConfig.isDriflootAlertsEnabled() +
                                            "\n\u00A77gruntFinder = \u00A7f" + hudConfig.isGruntFinderEnabled() +
                                            "\n\u00A77messageNotificationSound = \u00A7f" + hudConfig.isMessageNotificationSound() +
                                            "\n\u00A77autoAcceptPartyInvites = \u00A7f" + hudConfig.isAutoAcceptPartyInvites() +
                                            "\n\n\u00A7e[Safari] \u00A77(/saa config safari)" +
                                            "\n\u00A77menuEnabled = \u00A7f" + hudConfig.isSafariMenuEnabled() +
                                            "\n\u00A77timerAlways = \u00A7f" + hudConfig.isSafariTimerAlways() +
                                            "\n\u00A77questMonGlow = \u00A7f" + hudConfig.isSafariQuestMonGlow() +
                                            "\n\n\u00A7e[Daycare] \u00A77(/saa config daycare)" +
                                            "\n\u00A77menuEnabled = \u00A7f" + hudConfig.isDaycareMenuEnabled() +
                                            "\n\u00A77soundsEnabled = \u00A7f" + hudConfig.isDaycareSoundsEnabled() +
                                            "\n\u00A77eggsHatchingSlots = \u00A7f" + hudConfig.getDaycareEggsHatchingSlots() +
                                            "\n\u00A77babyGuards = \u00A7f" + hudConfig.isDaycareBabyGuards() +
                                            "\n\u00A77ivPercentPreference = \u00A76" + hudConfig.getDaycareIvPercentLower() +
                                            "%+\u00A77 (orange) / \u00A7b" + hudConfig.getDaycareIvPercentUpper() +
                                            "%+\u00A77 (blue) / \u00A7a" + hudConfig.getDaycareIvPercentTop() +
                                            "%+\u00A77 (green)" +
                                            "\n\n\u00A7e[Wondertrade] \u00A77(/saa config wt)" +
                                            "\n\u00A77menuEnabled = \u00A7f" + hudConfig.isWtMenuEnabled() +
                                            "\n\u00A77showChatReminders = \u00A7f" + hudConfig.isWtShowChatReminders() +
                                            "\n\u00A77soundsEnabled = \u00A7f" + hudConfig.isWtSoundsEnabled() +
                                            "\n\n\u00A7e[Card Grading] \u00A77(/saa config grading)" +
                                            "\n\u00A77menuEnabled = \u00A7f" + hudConfig.isCardGradingMenuEnabled() +
                                            "\n\u00A77soundsEnabled = \u00A7f" + hudConfig.isCardGradingSoundsEnabled() +
                                            "\n\n\u00A7e[Stats] \u00A77(/saa config stats)" +
                                            "\n\u00A77menuEnabled = \u00A7f" + hudConfig.isCardStatsMenuEnabled() +
                                            "\n\u00A77displayAlways = \u00A7f" + hudConfig.isCardStatsDisplayAlways() +
                                            "\n\u00A77displayInInventory = \u00A7f" + hudConfig.isCardStatsDisplayInInventory() +
                                            "\n\n\u00A7e[Suppress] \u00A77(/saa config suppress)" +
                                            "\n\u00A77inRaids = \u00A7f" + hudConfig.isSuppressInRaids() +
                                            "\n\u00A77inHideouts = \u00A7f" + hudConfig.isSuppressInHideouts() +
                                            "\n\u00A77inDungeons = \u00A7f" + hudConfig.isSuppressInDungeons() +
                                            "\n\u00A77inBattles = \u00A7f" + hudConfig.isSuppressInBattles() +
                                            "\n\n\u00A7e[Scales] \u00A77(use /saa gui to adjust)" +
                                            "\n\u00A77safariScale = \u00A7f" + String.format("%.0f%%", hudConfig.getHudScale() * 100) +
                                            "\n\u00A77daycareScale = \u00A7f" + String.format("%.0f%%", hudConfig.getDaycareScale() * 100) +
                                            "\n\u00A77wtScale = \u00A7f" + String.format("%.0f%%", hudConfig.getWtScale() * 100) +
                                            "\n\u00A77gradingScale = \u00A7f" + String.format("%.0f%%", hudConfig.getCardGradingScale() * 100) +
                                            "\n\u00A77cardStatsScale = \u00A7f" + String.format("%.0f%%", hudConfig.getCardStatsScale() * 100)
                                    ));
                                    return 1;
                                })
                        )
                        .then(ClientCommandManager.literal("dev")
                                .then(ClientCommandManager.literal("portalSpawn")
                                        .then(ClientCommandManager.literal("raid")
                                                .then(ClientCommandManager.argument("tier", IntegerArgumentType.integer(1, 5))
                                                        .executes(context -> {
                                                            int tier = IntegerArgumentType.getInteger(context, "tier");
                                                            int id = portalManager.registerPendingPortal(PortalManager.PortalType.RAID, tier);
                                                            String msg = id >= 0
                                                                    ? "\u00A7a[Dev] Simulated tier " + tier + " raid portal spawn (id=" + id + ", scanning...)"
                                                                    : "\u00A7e[Dev] Simulated tier " + tier + " raid portal spawn (scanner busy)";
                                                            context.getSource().sendFeedback(Component.literal(msg));
                                                            return 1;
                                                        })
                                                )
                                        )
                                        .then(ClientCommandManager.literal("hideout")
                                                .then(ClientCommandManager.argument("tier", IntegerArgumentType.integer(1, 5))
                                                        .executes(context -> {
                                                            int tier = IntegerArgumentType.getInteger(context, "tier");
                                                            int id = portalManager.registerPendingPortal(PortalManager.PortalType.HIDEOUT, tier);
                                                            String msg = id >= 0
                                                                    ? "\u00A7a[Dev] Simulated tier " + tier + " hideout portal spawn (id=" + id + ", scanning...)"
                                                                    : "\u00A7e[Dev] Simulated tier " + tier + " hideout portal spawn (scanner busy)";
                                                            context.getSource().sendFeedback(Component.literal(msg));
                                                            return 1;
                                                        })
                                                )
                                        )
                                )
                                .then(ClientCommandManager.literal("nearbyDump")
                                        .then(ClientCommandManager.argument("radius", IntegerArgumentType.integer(1, 64))
                                                .executes(context -> {
                                                    int radius = IntegerArgumentType.getInteger(context, "radius");
                                                    String path = NearbyDumpCommand.execute(radius);
                                                    if (path != null) {
                                                        context.getSource().sendFeedback(Component.literal(
                                                                "\u00A7a[Dev] Dump saved to: \u00A7f" + path));
                                                    } else {
                                                        context.getSource().sendFeedback(Component.literal(
                                                                "\u00A7c[Dev] Failed to write dump file."));
                                                    }
                                                    return 1;
                                                })
                                        )
                                        .executes(context -> {
                                            String path = NearbyDumpCommand.execute(16);
                                            if (path != null) {
                                                context.getSource().sendFeedback(Component.literal(
                                                        "\u00A7a[Dev] Dump saved to: \u00A7f" + path));
                                            } else {
                                                context.getSource().sendFeedback(Component.literal(
                                                        "\u00A7c[Dev] Failed to write dump file."));
                                            }
                                            return 1;
                                        })
                                )
                                .then(ClientCommandManager.literal("particleDump")
                                        .then(ClientCommandManager.argument("seconds", IntegerArgumentType.integer(1, 30))
                                                .then(ClientCommandManager.argument("radius", IntegerArgumentType.integer(1, 64))
                                                        .executes(context -> {
                                                            int seconds = IntegerArgumentType.getInteger(context, "seconds");
                                                            int radius = IntegerArgumentType.getInteger(context, "radius");
                                                            ParticleCapture.startCapture(seconds, radius);
                                                            context.getSource().sendFeedback(Component.literal(
                                                                    "\u00A7a[Dev] Capturing particles for " + seconds +
                                                                            "s within " + radius + " blocks..."));
                                                            return 1;
                                                        })
                                                )
                                                .executes(context -> {
                                                    int seconds = IntegerArgumentType.getInteger(context, "seconds");
                                                    ParticleCapture.startCapture(seconds, 30);
                                                    context.getSource().sendFeedback(Component.literal(
                                                            "\u00A7a[Dev] Capturing particles for " + seconds +
                                                                    "s within 30 blocks..."));
                                                    return 1;
                                                })
                                        )
                                        .executes(context -> {
                                            ParticleCapture.startCapture(10, 30);
                                            context.getSource().sendFeedback(Component.literal(
                                                    "\u00A7a[Dev] Capturing particles for 10s within 30 blocks..."));
                                            return 1;
                                        })
                                )
                                .then(ClientCommandManager.literal("dumpSelf")
                                        .executes(context -> {
                                            String path = DumpSelfCommand.execute();
                                            if (path != null) {
                                                context.getSource().sendFeedback(Component.literal(
                                                        "\u00A7a[Dev] Self-dump saved to: \u00A7f" + path));
                                            } else {
                                                context.getSource().sendFeedback(Component.literal(
                                                        "\u00A7c[Dev] Failed to write self-dump file."));
                                            }
                                            return 1;
                                        })
                                )
                                .then(ClientCommandManager.literal("testMsgSound")
                                        .executes(context -> {
                                            net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
                                            if (player != null) {
                                                player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_COW_BELL.value(), 0.8f, 1.0f);
                                                context.getSource().sendFeedback(Component.literal(
                                                        "\u00A7a[Dev] Played message notification sound."));
                                            }
                                            return 1;
                                        })
                                )
                        )
                        .executes(context -> {
                            context.getSource().sendFeedback(Component.literal(
                                    "\u00A76Sigs Academy Addons Commands:\n" +
                                    "\u00A7e/saa gui\u00A77 — Reposition HUDs\n" +
                                    "\u00A7e/saa gui reset\u00A77 — Reset HUD positions & scale\n" +
                                    "\u00A7e/saa dex\u00A77 — Open in-game Dex search\n" +
                                    "\u00A7e/saa safari\u00A77 — View safari status\n" +
                                    "\u00A7e/saa safari clear\u00A77 — Clear all safari/hunt data\n" +
                                    "\u00A7e/saa daycare\u00A77 — View daycare status\n" +
                                    "\u00A7e/saa daycare clear\u00A77 — Clear all daycare data\n" +
                                    "\u00A7e/saa manualHatchMultiplier <0|2.0>\u00A77 — Override hatch speed (0=auto)\n" +
                                    "\u00A7e/saa wt\u00A77 — View wondertrade status\n" +
                                    "\u00A7e/saa wt clear\u00A77 — Clear wondertrade timer\n" +
                                    "\u00A7e/saa grading\u00A77 — View card grading timer\n" +
                                    "\u00A7e/saa grading clear\u00A77 — Clear card grading timer\n" +
                                    "\u00A7e/saa portal\u00A77 — View portal tracking status\n" +
                                    "\u00A7e/saa portal clear\u00A77 — Clear portal tracking data\n" +
                                    "\n\u00A76Config Commands:\n" +
                                    "\u00A7e/saa config\u00A77 — View all configuration\n" +
                                    "\u00A7e/saa config hudStyle <solid|transparent>\u00A77 — HUD background style\n" +
                                    "\u00A7e/saa config hudLayout <full|compact>\u00A77 — HUD layout mode\n" +
                                    "\u00A7e/saa config hudHidden <bool>\u00A77 — Manually hide HUD\n" +
                                    "\u00A7e/saa config driflootAlerts <bool>\u00A77 — Drifloot spawn alerts\n" +
                                    "\u00A7e/saa config gruntFinder <bool>\u00A77 — Grunt finder toggle\n" +
                                    "\u00A7e/saa config messageNotificationSound <bool>\u00A77 — PM notification sound\n" +
                                    "\u00A7e/saa config autoAcceptPartyInvites <bool>\u00A77 — Auto-accept party invites\n" +
                                    "\u00A7e/saa config safari\u00A77 — Safari config (menuEnabled, timerAlways, questMonGlow)\n" +
                                    "\u00A7e/saa config daycare\u00A77 — Daycare config (menuEnabled, soundsEnabled, eggsHatchingSlots, babyGuards, ivPercentPreference)\n" +
                                    "\u00A7e/saa config wt\u00A77 — Wondertrade config (menuEnabled, showChatReminders, soundsEnabled)\n" +
                                    "\u00A7e/saa config grading\u00A77 — Card grading config (menuEnabled, soundsEnabled)\n" +
                                    "\u00A7e/saa config suppress\u00A77 — Suppress config (inRaids, inHideouts, inDungeons, inBattles)\n" +
                                    "\u00A7e/saa config cardstats\u00A77 — Card Stats config (menuEnabled)"
                            ));
                            return 1;
                        })
        );
    }

    public static SafariManager getSafariManager() { return safariManager; }
    public static SafariHuntManager getSafariHuntManager() { return safariHuntManager; }
    public static HuntEntityTracker getHuntEntityTracker() { return huntEntityTracker; }
    public static CatchDetector getCatchDetector() { return catchDetector; }
    public static DaycareManager getDaycareManager() { return daycareManager; }
    public static WondertradeManager getWondertradeManager() { return wondertradeManager; }
    public static PortalManager getPortalManager() { return portalManager; }
    public static DungeonManager getDungeonManager() { return dungeonManager; }
    public static GruntFinderTracker getGruntFinderTracker() { return gruntFinderTracker; }
    public static HudConfig getHudConfig() { return hudConfig; }

}
