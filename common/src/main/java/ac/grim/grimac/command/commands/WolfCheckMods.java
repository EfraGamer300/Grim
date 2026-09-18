package ac.grim.grimac.command.commands;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.command.BuildableCommand;
import ac.grim.grimac.platform.api.command.PlayerSelector;
import ac.grim.grimac.platform.api.manager.cloud.CloudPlatformCommandArguments;
import ac.grim.grimac.platform.api.player.PlatformPlayer;
import ac.grim.grimac.platform.api.sender.Sender;
import ac.grim.grimac.utils.anticheat.MessageUtil;
import ac.grim.grimac.wolf.WolfModCheckManager;
import ac.grim.grimac.wolf.WolfModCheckManager.PendingCheck;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.description.Description;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * WolfNetwork Edition: {@code /wolfac checkmods <player>}.
 *
 * <p>Envia um pacote {@code wolfnetwork:settings} com a chave
 * {@code wolfac_checkmods} para o player especificado. O cliente com
 * WolfMOD responde com {@code wolfac_mods} contendo a lista de mods.
 * A resposta é entregue pelo listener Bukkit
 * ({@code BukkitWolfModCheckListener}).</p>
 */
public class WolfCheckMods implements BuildableCommand {
    @Override
    public void register(CommandManager<Sender> commandManager, CloudPlatformCommandArguments arguments) {
        // Comando principal: /wolfac checkmods <player>
        commandManager.command(
                commandManager.commandBuilder("wolfac")
                        .literal("checkmods", Description.of("Request mod list from a player's WolfMOD client"))
                        .permission("wolfac.checkmods")
                        .required("target", arguments.singlePlayerSelectorParser())
                        .handler(this::handle)
        );
        // Atalho compatível: /grim checkmods <player> e /grimac checkmods <player>
        commandManager.command(
                commandManager.commandBuilder("grim", "grimac")
                        .literal("checkmods", Description.of("Request mod list from a player's WolfMOD client"))
                        .permission("wolfac.checkmods")
                        .required("target", arguments.singlePlayerSelectorParser())
                        .handler(this::handle)
        );
    }

    private void handle(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();
        PlayerSelector target = context.get("target");

        PlatformPlayer targetPlayer = target.getSinglePlayer().getPlatformPlayer();
        if (Objects.requireNonNull(targetPlayer, "targetPlatformPlayer").isExternalPlayer()) {
            sender.sendMessage(MessageUtil.getParsedComponent(sender, "player-not-this-server",
                    "%prefix% &cThis player isn't on this server!"));
            return;
        }
        if (!targetPlayer.isOnline()) {
            sender.sendMessage(MessageUtil.getParsedComponent(sender, "player-not-found",
                    "%prefix% &cPlayer is exempt or offline!"));
            return;
        }

        PendingCheck pending = WolfModCheckManager.registerRequest(
                targetPlayer.getUniqueId(), targetPlayer.getName(),
                sender.getName(), sender.isConsole() ? null : sender.getUniqueId());

        byte[] payload = WolfModCheckManager.encode(
                WolfModCheckManager.REQUEST_KEY, pending.requestId().toString());
        try {
            GrimAPI.INSTANCE.getPlatformServer()
                    .registerOutgoingPluginChannel(WolfModCheckManager.CHANNEL);
            targetPlayer.sendPluginMessage(WolfModCheckManager.CHANNEL, payload);
        } catch (Exception e) {
            WolfModCheckManager.timeoutIfCurrent(pending.requestId());
            sender.sendMessage(MessageUtil.miniMessage(
                    "%prefix% &cFalha ao enviar pacote checkmods: "
                            + MessageUtil.miniMessageSafe(e.getMessage())));
            return;
        }

        sender.sendMessage(MessageUtil.getParsedComponent(sender, "wolfac-checkmods-sent",
                "%prefix% &fSolicitando mods de &b" + targetPlayer.getName()
                        + "&f... (aguardando resposta do WolfMOD, 10s)"));

        // Timeout: se o cliente for vanilla / sem WolfMOD, não haverá resposta.
        GrimAPI.INSTANCE.getScheduler().getAsyncScheduler().runDelayed(
                GrimAPI.INSTANCE.getGrimPlugin(),
                () -> {
                    PendingCheck stillPending = WolfModCheckManager.getPendingForTarget(targetPlayer.getUniqueId());
                    if (stillPending != null && stillPending.requestId().equals(pending.requestId())) {
                        WolfModCheckManager.timeoutIfCurrent(pending.requestId());
                        try {
                            if (sender.isConsole()) {
                                GrimAPI.INSTANCE.getPlatformServer().getConsoleSender().sendMessage(
                                        MessageUtil.miniMessage("%prefix% &cSem resposta de &f"
                                                + targetPlayer.getName()
                                                + "&c. Cliente sem WolfMOD ou offline."));
                            } else {
                                sender.sendMessage(MessageUtil.miniMessage(
                                        "%prefix% &cSem resposta de &f" + targetPlayer.getName()
                                                + "&c. Cliente sem WolfMOD ou offline."));
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }, 10, TimeUnit.SECONDS);
    }
}
