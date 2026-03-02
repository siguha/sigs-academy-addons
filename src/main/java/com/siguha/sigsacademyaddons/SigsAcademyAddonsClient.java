package com.siguha.sigsacademyaddons;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.siguha.sigsacademyaddons.config.HudConfig;
import com.siguha.sigsacademyaddons.data.HuntDataStore;
import com.siguha.sigsacademyaddons.feature.safari.HuntEntityTracker;
import com.siguha.sigsacademyaddons.feature.safari.SafariHuntManager;
import com.siguha.sigsacademyaddons.feature.safari.SafariManager;
import com.siguha.sigsacademyaddons.gui.HudConfigScreen;
import com.siguha.sigsacademyaddons.handler.CatchDetector;
import com.siguha.sigsacademyaddons.handler.ChatMessageHandler;
import com.siguha.sigsacademyaddons.handler.ScreenInterceptor;
import com.siguha.sigsacademyaddons.hud.HuntLineRenderer;
import com.siguha.sigsacademyaddons.hud.SafariHudRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;

public class SigsAcademyAddonsClient implements ClientModInitializer {

    private static SafariManager safariManager;
    private static SafariHuntManager safariHuntManager;
    private static HuntEntityTracker huntEntityTracker;
    private static CatchDetector catchDetector;
    private static HudConfig hudConfig;

    private static boolean openConfigScreenNextTick = false;

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

        ChatMessageHandler chatHandler = new ChatMessageHandler(safariManager, safariHuntManager, catchDetector);
        ScreenInterceptor screenInterceptor = new ScreenInterceptor(safariHuntManager);
        SafariHudRenderer hudRenderer = new SafariHudRenderer(safariManager, safariHuntManager, hudConfig);

        ClientReceiveMessageEvents.GAME.register(chatHandler::onGameMessage);
        ScreenEvents.AFTER_INIT.register(screenInterceptor::onScreenInit);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            safariManager.tick();
            safariHuntManager.tick();
            huntEntityTracker.tick();
            catchDetector.tick(client);

            if (openConfigScreenNextTick && client.player != null) {
                openConfigScreenNextTick = false;
                client.setScreen(new HudConfigScreen(hudConfig, safariManager, safariHuntManager));
            }
        });

        HudRenderCallback.EVENT.register(hudRenderer::onHudRender);

        HuntLineRenderer huntLineRenderer = new HuntLineRenderer(safariManager, hudConfig);
        WorldRenderEvents.LAST.register(huntLineRenderer::onWorldRenderLast);

        ClientCommandRegistrationCallback.EVENT.register(SigsAcademyAddonsClient::registerCommands);

        SigsAcademyAddons.LOGGER.info("[Sigs Academy Addons] Client initialization complete.");
    }

    private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher,
                                          CommandBuildContext registryAccess) {
        dispatcher.register(
                ClientCommandManager.literal("sig")
                        // gui
                        .then(ClientCommandManager.literal("gui")
                                .then(ClientCommandManager.literal("reset")
                                        .executes(context -> {
                                            hudConfig.setHudScale(1.0f);
                                            hudConfig.setPositionFromAbsolute(
                                                    context.getSource().getClient().getWindow().getGuiScaledWidth() - 145,
                                                    5, 140, 100,
                                                    context.getSource().getClient().getWindow().getGuiScaledWidth(),
                                                    context.getSource().getClient().getWindow().getGuiScaledHeight()
                                            );
                                            context.getSource().sendFeedback(
                                                    Component.literal("\u00A7aHUD position and scale reset to default."));
                                            return 1;
                                        })
                                )
                                .executes(context -> {
                                    openConfigScreenNextTick = true;
                                    context.getSource().sendFeedback(
                                            Component.literal("\u00A7aOpening Safari HUD configuration..."));
                                    return 1;
                                })
                        )
                        // safari subcds
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
                        // config subcds
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
                                            // show current value
                                            boolean current = hudConfig.isSafariTimerAlways();
                                            context.getSource().sendFeedback(Component.literal(
                                                    "\u00A77safariTimerAlways = \u00A7f" + current +
                                                    "\n\u00A77Usage: \u00A7e/sig config safariTimerAlways <true|false>"));
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
                                                    "\n\u00A77Usage: \u00A7e/sig config safariQuestMonGlow <true|false>"));
                                            return 1;
                                        })
                                )
                                .then(ClientCommandManager.literal("safariQuestMonTracers")
                                        .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                                .executes(context -> {
                                                    boolean value = BoolArgumentType.getBool(context, "value");
                                                    hudConfig.setSafariQuestMonTracers(value);
                                                    String msg = value
                                                            ? "\u00A7aQuest Pokemon tracers enabled (16 block range)."
                                                            : "\u00A7aQuest Pokemon tracers disabled.";
                                                    context.getSource().sendFeedback(Component.literal(msg));
                                                    return 1;
                                                })
                                        )
                                        .executes(context -> {
                                            boolean current = hudConfig.isSafariQuestMonTracers();
                                            context.getSource().sendFeedback(Component.literal(
                                                    "\u00A77safariQuestMonTracers = \u00A7f" + current +
                                                    "\n\u00A77Usage: \u00A7e/sig config safariQuestMonTracers <true|false>"));
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
                                                    "\n\u00A77Usage: \u00A7e/sig config hudStyle <solid|transparent>"));
                                            return 1;
                                        })
                                )
                                .executes(context -> {
                                    // show all config values
                                    context.getSource().sendFeedback(Component.literal(
                                            "\u00A76Configuration:\n" +
                                            "\u00A77safariTimerAlways = \u00A7f" + hudConfig.isSafariTimerAlways() +
                                            "\n\u00A77safariQuestMonGlow = \u00A7f" + hudConfig.isSafariQuestMonGlow() +
                                            "\n\u00A77safariQuestMonTracers = \u00A7f" + hudConfig.isSafariQuestMonTracers() +
                                            "\n\u00A77hudStyle = \u00A7f" + hudConfig.getHudStyle().name().toLowerCase() +
                                            "\n\u00A77hudScale = \u00A7f" + String.format("%.0f%%", hudConfig.getHudScale() * 100) +
                                            "\n\u00A77anchor = \u00A7f" + hudConfig.getAnchor().name() +
                                            "\n\u00A77offset = \u00A7f(" + hudConfig.getOffsetX() + ", " + hudConfig.getOffsetY() + ")"
                                    ));
                                    return 1;
                                })
                        )
                        // show help
                        .executes(context -> {
                            context.getSource().sendFeedback(Component.literal(
                                    "\u00A76Sigs Academy Addons Commands:\n" +
                                    "\u00A7e/sig gui\u00A77 — Reposition the Safari HUD\n" +
                                    "\u00A7e/sig gui reset\u00A77 — Reset HUD to default position\n" +
                                    "\u00A7e/sig safari\u00A77 — View safari status\n" +
                                    "\u00A7e/sig safari clear\u00A77 — Clear all safari/hunt data\n" +
                                    "\u00A7e/sig config\u00A77 — View configuration\n" +
                                    "\u00A7e/sig config safariTimerAlways <bool>\u00A77 — Show HUD outside safari zone\n" +
                                    "\u00A7e/sig config safariQuestMonGlow <bool>\u00A77 — Glow on quest-matching Pokemon\n" +
                                    "\u00A7e/sig config safariQuestMonTracers <bool>\u00A77 — Tracers to nearby quest Pokemon\n" +
                                    "\u00A7e/sig config hudStyle <solid|transparent>\u00A77 — HUD background style"
                            ));
                            return 1;
                        })
        );
    }

    public static SafariManager getSafariManager() { return safariManager; }
    public static SafariHuntManager getSafariHuntManager() { return safariHuntManager; }
    public static HuntEntityTracker getHuntEntityTracker() { return huntEntityTracker; }
    public static CatchDetector getCatchDetector() { return catchDetector; }
    public static HudConfig getHudConfig() { return hudConfig; }
}
