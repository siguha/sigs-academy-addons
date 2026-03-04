package com.siguha.sigsacademyaddons;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.siguha.sigsacademyaddons.config.HudConfig;
import com.siguha.sigsacademyaddons.data.DaycareDataStore;
import com.siguha.sigsacademyaddons.data.HuntDataStore;
import com.siguha.sigsacademyaddons.data.WondertradeDataStore;
import com.siguha.sigsacademyaddons.feature.daycare.DaycareManager;
import com.siguha.sigsacademyaddons.feature.daycare.DaycareSoundPlayer;
import com.siguha.sigsacademyaddons.feature.safari.HuntEntityTracker;
import com.siguha.sigsacademyaddons.feature.safari.SafariHuntManager;
import com.siguha.sigsacademyaddons.feature.safari.SafariManager;
import com.siguha.sigsacademyaddons.feature.wondertrade.WondertradeManager;
import com.siguha.sigsacademyaddons.feature.wondertrade.WondertradeSoundPlayer;
import com.siguha.sigsacademyaddons.gui.HudConfigScreen;
import com.siguha.sigsacademyaddons.handler.CatchDetector;
import com.siguha.sigsacademyaddons.handler.ChatMessageHandler;
import com.siguha.sigsacademyaddons.handler.ScreenInterceptor;
import com.siguha.sigsacademyaddons.hud.DaycareHudRenderer;
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
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
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
    private static HudConfig hudConfig;

    private static boolean openConfigScreenNextTick = false;
    private static boolean glfwFilterReinstalled = false;

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

        ChatMessageHandler chatHandler = new ChatMessageHandler(safariManager, safariHuntManager,
                catchDetector, daycareManager, wondertradeManager);
        ScreenInterceptor screenInterceptor = new ScreenInterceptor(safariHuntManager, daycareManager,
                wondertradeManager);
        SafariHudRenderer hudRenderer = new SafariHudRenderer(safariManager, safariHuntManager, hudConfig);
        DaycareHudRenderer daycareHudRenderer = new DaycareHudRenderer(daycareManager, hudConfig);
        WondertradeHudRenderer wtHudRenderer = new WondertradeHudRenderer(wondertradeManager, hudConfig);

        ClientReceiveMessageEvents.MODIFY_GAME.register(chatHandler::modifyGameMessage);
        ClientReceiveMessageEvents.GAME.register(chatHandler::onGameMessage);
        ScreenEvents.AFTER_INIT.register(screenInterceptor::onScreenInit);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            safariManager.tick();
            safariHuntManager.tick();
            huntEntityTracker.tick();
            catchDetector.tick(client);
            daycareManager.tick();
            daycareSoundPlayer.tick();
            wondertradeManager.tick();
            wondertradeSoundPlayer.tick();

            if (!glfwFilterReinstalled) {
                glfwFilterReinstalled = true;
                installGlfwErrorFilter();
            }

            if (openConfigScreenNextTick && client.player != null) {
                openConfigScreenNextTick = false;
                client.setScreen(new HudConfigScreen(hudConfig, safariManager, safariHuntManager,
                        daycareManager, wondertradeManager));
            }
        });

        HudRenderCallback.EVENT.register(hudRenderer::onHudRender);
        HudRenderCallback.EVENT.register(daycareHudRenderer::onHudRender);
        HudRenderCallback.EVENT.register(wtHudRenderer::onHudRender);

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            daycareManager.onServerJoined();
            wondertradeManager.onServerJoined();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            daycareManager.onServerDisconnected();
            wondertradeManager.onServerDisconnected();
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

                                    context.getSource().sendFeedback(Component.literal(sb.toString()));
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
                                .executes(context -> {
                                    context.getSource().sendFeedback(Component.literal(
                                            "\u00A76Configuration:\n" +
                                            "\u00A77safariMenuEnabled = \u00A7f" + hudConfig.isSafariMenuEnabled() +
                                            "\n\u00A77safariTimerAlways = \u00A7f" + hudConfig.isSafariTimerAlways() +
                                            "\n\u00A77safariQuestMonGlow = \u00A7f" + hudConfig.isSafariQuestMonGlow() +
                                            "\n\u00A77daycareMenuEnabled = \u00A7f" + hudConfig.isDaycareMenuEnabled() +
                                            "\n\u00A77daycareSoundsEnabled = \u00A7f" + hudConfig.isDaycareSoundsEnabled() +
                                            "\n\u00A77daycareEggsHatchingSlots = \u00A7f" + hudConfig.getDaycareEggsHatchingSlots() +
                                            "\n\u00A77wtMenuEnabled = \u00A7f" + hudConfig.isWtMenuEnabled() +
                                            "\n\u00A77wtShowChatReminders = \u00A7f" + hudConfig.isWtShowChatReminders() +
                                            "\n\u00A77wtSoundsEnabled = \u00A7f" + hudConfig.isWtSoundsEnabled() +
                                            "\n\u00A77hudStyle = \u00A7f" + hudConfig.getHudStyle().name().toLowerCase() +
                                            "\n\u00A77safariScale = \u00A7f" + String.format("%.0f%%", hudConfig.getHudScale() * 100) +
                                            "\n\u00A77daycareScale = \u00A7f" + String.format("%.0f%%", hudConfig.getDaycareScale() * 100) +
                                            "\n\u00A77wtScale = \u00A7f" + String.format("%.0f%%", hudConfig.getWtScale() * 100)
                                    ));
                                    return 1;
                                })
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
                                    "\u00A7e/saa wt\u00A77 — View wondertrade status\n" +
                                    "\u00A7e/saa wt clear\u00A77 — Clear wondertrade timer\n" +
                                    "\u00A7e/saa config\u00A77 — View configuration\n" +
                                    "\u00A7e/saa config safariMenuEnabled <bool>\u00A77 — Safari HUD toggle\n" +
                                    "\u00A7e/saa config safariTimerAlways <bool>\u00A77 — Show HUD outside safari zone\n" +
                                    "\u00A7e/saa config safariQuestMonGlow <bool>\u00A77 — Glow on quest-matching Pokemon\n" +
                                    "\u00A7e/saa config daycareMenuEnabled <bool>\u00A77 — Daycare HUD toggle\n" +
                                    "\u00A7e/saa config daycareSoundsEnabled <bool>\u00A77 — Daycare sound alerts toggle\n" +
                                    "\u00A7e/saa config daycareEggsHatchingSlots <0-5>\u00A77 — Max eggs shown (0=hide)\n" +
                                    "\u00A7e/saa config wtMenuEnabled <bool>\u00A77 — Wondertrade HUD toggle\n" +
                                    "\u00A7e/saa config wtShowChatReminders <bool>\u00A77 — WT chat reminder toggle\n" +
                                    "\u00A7e/saa config wtSoundsEnabled <bool>\u00A77 — WT sound alerts toggle\n" +
                                    "\u00A7e/saa config hudStyle <solid|transparent>\u00A77 — HUD background style"
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
    public static HudConfig getHudConfig() { return hudConfig; }
}
