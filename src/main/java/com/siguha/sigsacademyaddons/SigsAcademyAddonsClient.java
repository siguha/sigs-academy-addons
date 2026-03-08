package com.siguha.sigsacademyaddons;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.siguha.sigsacademyaddons.config.HudConfig;
import com.siguha.sigsacademyaddons.data.DaycareDataStore;
import com.siguha.sigsacademyaddons.data.HuntDataStore;
import com.siguha.sigsacademyaddons.data.WondertradeDataStore;
import com.siguha.sigsacademyaddons.feature.daycare.DaycareManager;
import com.siguha.sigsacademyaddons.feature.daycare.DaycareSoundPlayer;
import com.siguha.sigsacademyaddons.feature.drifloot.DriflootDetector;
import com.siguha.sigsacademyaddons.feature.portal.PortalManager;
import com.siguha.sigsacademyaddons.feature.portal.PortalParticleDetector;
import com.siguha.sigsacademyaddons.feature.suppression.SuppressionManager;
import com.siguha.sigsacademyaddons.feature.safari.HuntEntityTracker;
import com.siguha.sigsacademyaddons.feature.safari.SafariHuntManager;
import com.siguha.sigsacademyaddons.feature.safari.SafariManager;
import com.siguha.sigsacademyaddons.feature.wondertrade.WondertradeManager;
import com.siguha.sigsacademyaddons.feature.wondertrade.WondertradeSoundPlayer;
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
import com.siguha.sigsacademyaddons.hud.WondertradeHudRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.ChatFormatting;
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
    private static PortalManager portalManager;
    private static DriflootDetector driflootDetector;
    private static SuppressionManager suppressionManager;
    private static HudConfig hudConfig;

    private static boolean openConfigScreenNextTick = false;
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

        portalManager = new PortalManager();
        driflootDetector = new DriflootDetector(hudConfig);
        suppressionManager = new SuppressionManager(hudConfig);

        ChatMessageHandler chatHandler = new ChatMessageHandler(safariManager, safariHuntManager,
                catchDetector, daycareManager, wondertradeManager, portalManager);
        ScreenInterceptor screenInterceptor = new ScreenInterceptor(safariHuntManager, daycareManager,
                wondertradeManager);
        SafariHudRenderer hudRenderer = new SafariHudRenderer(safariManager, safariHuntManager, hudConfig);
        DaycareHudRenderer daycareHudRenderer = new DaycareHudRenderer(daycareManager, hudConfig);
        WondertradeHudRenderer wtHudRenderer = new WondertradeHudRenderer(wondertradeManager, hudConfig);
        PortalBossBarRenderer portalBossBarRenderer = new PortalBossBarRenderer(portalManager, suppressionManager);

        ClientReceiveMessageEvents.MODIFY_GAME.register(chatHandler::modifyGameMessage);
        ClientReceiveMessageEvents.GAME.register(chatHandler::onGameMessage);
        ScreenEvents.AFTER_INIT.register(screenInterceptor::onScreenInit);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            suppressionManager.tick();
            safariManager.tick();
            safariHuntManager.tick();
            huntEntityTracker.tick();
            catchDetector.tick(client);
            daycareManager.tick();
            daycareSoundPlayer.tick();
            wondertradeManager.tick();
            wondertradeSoundPlayer.tick();
            portalManager.tick();
            driflootDetector.tick();
            PortalParticleDetector.tick();
            ParticleCapture.tick();

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
                        daycareManager, wondertradeManager));
            }
        });

        HudGroupRenderer groupRenderer = new HudGroupRenderer(hudConfig, suppressionManager);
        groupRenderer.registerPanel(hudRenderer);
        groupRenderer.registerPanel(daycareHudRenderer);
        groupRenderer.registerPanel(wtHudRenderer);
        HudRenderCallback.EVENT.register(groupRenderer::onHudRender);
        HudRenderCallback.EVENT.register(portalBossBarRenderer::onHudRender);

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            daycareManager.onServerJoined();
            wondertradeManager.onServerJoined();
            welcomeDelayTicks = 60;
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            daycareManager.onServerDisconnected();
            wondertradeManager.onServerDisconnected();
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
                                .then(ClientCommandManager.literal("ivPercentPreference")
                                        .then(ClientCommandManager.argument("lowerBound", IntegerArgumentType.integer(0, 100))
                                                .then(ClientCommandManager.argument("upperBound", IntegerArgumentType.integer(0, 100))
                                                        .executes(context -> {
                                                            int lower = IntegerArgumentType.getInteger(context, "lowerBound");
                                                            int upper = IntegerArgumentType.getInteger(context, "upperBound");
                                                            hudConfig.setDaycareIvPercentLower(lower);
                                                            hudConfig.setDaycareIvPercentUpper(upper);
                                                            context.getSource().sendFeedback(Component.literal(
                                                                    "\u00A7aIV% thresholds set to \u00A76" + lower
                                                                    + "%\u00A7a (orange) / \u00A7b" + upper + "%\u00A7a (blue)."));
                                                            return 1;
                                                        })
                                                )
                                        )
                                        .executes(context -> {
                                            context.getSource().sendFeedback(Component.literal(
                                                    "\u00A77IV% highlight thresholds:" +
                                                    "\n\u00A76  Orange: \u00A7f" + hudConfig.getDaycareIvPercentLower() + "%+" +
                                                    "\n\u00A7b  Blue: \u00A7f" + hudConfig.getDaycareIvPercentUpper() + "%+" +
                                                    "\n\u00A77Usage: \u00A7e/saa daycare ivPercentPreference <lower> <upper>"));
                                            return 1;
                                        })
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
                                    sb.append("\n\u00A77IV% highlight: \u00A76").append(hudConfig.getDaycareIvPercentLower()).append("%+\u00A77 (orange) / \u00A7b").append(hudConfig.getDaycareIvPercentUpper()).append("%+\u00A77 (blue)");

                                    context.getSource().sendFeedback(Component.literal(sb.toString()));
                                    return 1;
                                })
                        )
                        .then(ClientCommandManager.literal("manualHatchMultiplier")
                                .then(ClientCommandManager.argument("value", FloatArgumentType.floatArg(0f, 1.5f))
                                        .executes(context -> {
                                            float value = FloatArgumentType.getFloat(context, "value");
                                            if (value != 0f && value != 1.5f) {
                                                context.getSource().sendFeedback(
                                                        Component.literal("\u00A7cInvalid value. Use 0 (auto-detect) or 1.5 (force 1.5x speed)."));
                                                return 0;
                                            }
                                            hudConfig.setManualHatchMultiplier(value);
                                            String label = value == 0f ? "\u00A7bauto-detect" : "\u00A7b1.5x";
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
                                            "\n\u00A77Usage: \u00A7e/saa manualHatchMultiplier <0 | 1.5>" +
                                            "\n\u00A770 = auto-detect from rank, 1.5 = force 1.5x hatch speed"));
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
                                .then(ClientCommandManager.literal("safariTimerAlways")
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
                                                    "\u00A77safariTimerAlways = \u00A7f" + current +
                                                    "\n\u00A77Usage: \u00A7e/saa config safariTimerAlways <true|false>"));
                                            return 1;
                                        })
                                )
                                .then(ClientCommandManager.literal("safariMenuEnabled")
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
                                                    "\u00A77safariMenuEnabled = \u00A7f" + current +
                                                    "\n\u00A77Usage: \u00A7e/saa config safariMenuEnabled <true|false>"));
                                            return 1;
                                        })
                                )
                                .then(ClientCommandManager.literal("safariQuestMonGlow")
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
                                                    "\u00A77safariQuestMonGlow = \u00A7f" + current +
                                                    "\n\u00A77Usage: \u00A7e/saa config safariQuestMonGlow <true|false>"));
                                            return 1;
                                        })
                                )
                                .then(ClientCommandManager.literal("daycareMenuEnabled")
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
                                                    "\u00A77daycareMenuEnabled = \u00A7f" + current +
                                                    "\n\u00A77Usage: \u00A7e/saa config daycareMenuEnabled <true|false>"));
                                            return 1;
                                        })
                                )
                                .then(ClientCommandManager.literal("daycareSoundsEnabled")
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
                                                    "\u00A77daycareSoundsEnabled = \u00A7f" + current +
                                                    "\n\u00A77Usage: \u00A7e/saa config daycareSoundsEnabled <true|false>"));
                                            return 1;
                                        })
                                )
                                .then(ClientCommandManager.literal("daycareEggsHatchingSlots")
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
                                                    "\u00A77daycareEggsHatchingSlots = \u00A7f" + current +
                                                    "\n\u00A77Usage: \u00A7e/saa config daycareEggsHatchingSlots <0-5>" +
                                                    "\n\u00A77Set to 0 to hide hatching section."));
                                            return 1;
                                        })
                                )
                                .then(ClientCommandManager.literal("daycareBabyGuards")
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
                                                    "\u00A77daycareBabyGuards = \u00A7f" + current +
                                                    "\n\u00A77Usage: \u00A7e/saa config daycareBabyGuards <true|false>"));
                                            return 1;
                                        })
                                )
                                .then(ClientCommandManager.literal("wtMenuEnabled")
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
                                                    "\u00A77wtMenuEnabled = \u00A7f" + current +
                                                    "\n\u00A77Usage: \u00A7e/saa config wtMenuEnabled <true|false>"));
                                            return 1;
                                        })
                                )
                                .then(ClientCommandManager.literal("wtShowChatReminders")
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
                                                    "\u00A77wtShowChatReminders = \u00A7f" + current +
                                                    "\n\u00A77Usage: \u00A7e/saa config wtShowChatReminders <true|false>"));
                                            return 1;
                                        })
                                )
                                .then(ClientCommandManager.literal("wtSoundsEnabled")
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
                                                    "\u00A77wtSoundsEnabled = \u00A7f" + current +
                                                    "\n\u00A77Usage: \u00A7e/saa config wtSoundsEnabled <true|false>"));
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
                                .then(ClientCommandManager.literal("suppressInRaids")
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
                                                    "\u00A77suppressInRaids = \u00A7f" + current +
                                                    "\n\u00A77Usage: \u00A7e/saa config suppressInRaids <true|false>"));
                                            return 1;
                                        })
                                )
                                .then(ClientCommandManager.literal("suppressInHideouts")
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
                                                    "\u00A77suppressInHideouts = \u00A7f" + current +
                                                    "\n\u00A77Usage: \u00A7e/saa config suppressInHideouts <true|false>"));
                                            return 1;
                                        })
                                )
                                .then(ClientCommandManager.literal("suppressInDungeons")
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
                                                    "\u00A77suppressInDungeons = \u00A7f" + current +
                                                    "\n\u00A77Usage: \u00A7e/saa config suppressInDungeons <true|false>"));
                                            return 1;
                                        })
                                )
                                .then(ClientCommandManager.literal("suppressInBattles")
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
                                                    "\u00A77suppressInBattles = \u00A7f" + current +
                                                    "\n\u00A77Usage: \u00A7e/saa config suppressInBattles <true|false>"));
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
                                .executes(context -> {
                                    context.getSource().sendFeedback(Component.literal(
                                            "\u00A76Configuration:\n" +
                                            "\u00A77safariMenuEnabled = \u00A7f" + hudConfig.isSafariMenuEnabled() +
                                            "\n\u00A77safariTimerAlways = \u00A7f" + hudConfig.isSafariTimerAlways() +
                                            "\n\u00A77safariQuestMonGlow = \u00A7f" + hudConfig.isSafariQuestMonGlow() +
                                            "\n\u00A77daycareMenuEnabled = \u00A7f" + hudConfig.isDaycareMenuEnabled() +
                                            "\n\u00A77daycareSoundsEnabled = \u00A7f" + hudConfig.isDaycareSoundsEnabled() +
                                            "\n\u00A77daycareEggsHatchingSlots = \u00A7f" + hudConfig.getDaycareEggsHatchingSlots() +
                                            "\n\u00A77daycareBabyGuards = \u00A7f" + hudConfig.isDaycareBabyGuards() +
                                            "\n\u00A77wtMenuEnabled = \u00A7f" + hudConfig.isWtMenuEnabled() +
                                            "\n\u00A77wtShowChatReminders = \u00A7f" + hudConfig.isWtShowChatReminders() +
                                            "\n\u00A77wtSoundsEnabled = \u00A7f" + hudConfig.isWtSoundsEnabled() +
                                            "\n\u00A77hudStyle = \u00A7f" + hudConfig.getHudStyle().name().toLowerCase() +
                                            "\n\u00A77hudLayout = \u00A7f" + hudConfig.getHudLayout().name().toLowerCase() +
                                            "\n\u00A77safariScale = \u00A7f" + String.format("%.0f%%", hudConfig.getHudScale() * 100) +
                                            "\n\u00A77daycareScale = \u00A7f" + String.format("%.0f%%", hudConfig.getDaycareScale() * 100) +
                                            "\n\u00A77wtScale = \u00A7f" + String.format("%.0f%%", hudConfig.getWtScale() * 100) +
                                            "\n\u00A77suppressInRaids = \u00A7f" + hudConfig.isSuppressInRaids() +
                                            "\n\u00A77suppressInHideouts = \u00A7f" + hudConfig.isSuppressInHideouts() +
                                            "\n\u00A77suppressInDungeons = \u00A7f" + hudConfig.isSuppressInDungeons() +
                                            "\n\u00A77suppressInBattles = \u00A7f" + hudConfig.isSuppressInBattles() +
                                            "\n\u00A77driflootAlerts = \u00A7f" + hudConfig.isDriflootAlertsEnabled() +
                                            "\n\u00A77hudHidden = \u00A7f" + hudConfig.isHudHidden()
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
                        )
                        .executes(context -> {
                            context.getSource().sendFeedback(Component.literal(
                                    "\u00A76Sigs Academy Addons Commands:\n" +
                                    "\u00A7e/saa gui\u00A77 — Reposition HUDs\n" +
                                    "\u00A7e/saa gui reset\u00A77 — Reset HUD positions & scale\n" +
                                    "\u00A7e/saa safari\u00A77 — View safari status\n" +
                                    "\u00A7e/saa safari clear\u00A77 — Clear all safari/hunt data\n" +
                                    "\u00A7e/saa daycare\u00A77 — View daycare status\n" +
                                    "\u00A7e/saa daycare clear\u00A77 — Clear all daycare data\n" +
                                    "\u00A7e/saa manualHatchMultiplier <0|1.5>\u00A77 — Override hatch speed (0=auto)\n" +
                                    "\u00A7e/saa wt\u00A77 — View wondertrade status\n" +
                                    "\u00A7e/saa wt clear\u00A77 — Clear wondertrade timer\n" +
                                    "\u00A7e/saa portal\u00A77 — View portal tracking status\n" +
                                    "\u00A7e/saa portal clear\u00A77 — Clear portal tracking data\n" +
                                    "\u00A7e/saa config\u00A77 — View configuration\n" +
                                    "\u00A7e/saa config safariMenuEnabled <bool>\u00A77 — Safari HUD toggle\n" +
                                    "\u00A7e/saa config safariTimerAlways <bool>\u00A77 — Show HUD outside safari zone\n" +
                                    "\u00A7e/saa config safariQuestMonGlow <bool>\u00A77 — Glow on quest-matching Pokemon\n" +
                                    "\u00A7e/saa config daycareMenuEnabled <bool>\u00A77 — Daycare HUD toggle\n" +
                                    "\u00A7e/saa config daycareSoundsEnabled <bool>\u00A77 — Daycare sound alerts toggle\n" +
                                    "\u00A7e/saa config daycareEggsHatchingSlots <0-5>\u00A77 — Max eggs shown (0=hide)\n" +
                                    "\u00A7e/saa config daycareBabyGuards <bool>\u00A77 — Confirm before removing parents\n" +
                                    "\u00A7e/saa config wtMenuEnabled <bool>\u00A77 — Wondertrade HUD toggle\n" +
                                    "\u00A7e/saa config wtShowChatReminders <bool>\u00A77 — WT chat reminder toggle\n" +
                                    "\u00A7e/saa config wtSoundsEnabled <bool>\u00A77 — WT sound alerts toggle\n" +
                                    "\u00A7e/saa config hudStyle <solid|transparent>\u00A77 — HUD background style\n" +
                                    "\u00A7e/saa config hudLayout <full|compact>\u00A77 — HUD layout mode\n" +
                                    "\u00A7e/saa config suppressInRaids <bool>\u00A77 — Hide HUD in raids\n" +
                                    "\u00A7e/saa config suppressInHideouts <bool>\u00A77 — Hide HUD in hideouts\n" +
                                    "\u00A7e/saa config suppressInDungeons <bool>\u00A77 — Hide HUD in dungeons\n" +
                                    "\u00A7e/saa config suppressInBattles <bool>\u00A77 — Hide HUD in battles\n" +
                                    "\u00A7e/saa config driflootAlerts <bool>\u00A77 — Drifloot spawn alerts\n" +
                                    "\u00A7e/saa config hudHidden <bool>\u00A77 — Manually hide HUD"
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
    public static HudConfig getHudConfig() { return hudConfig; }
}
