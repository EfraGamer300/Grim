package ac.grim.grimac.platform.bukkit.wolf;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.manager.init.start.StartableInitable;
import ac.grim.grimac.platform.bukkit.GrimACBukkitLoaderPlugin;
import ac.grim.grimac.utils.anticheat.LogUtil;
import ac.grim.grimac.utils.anticheat.MessageUtil;
import ac.grim.grimac.wolf.WolfModCheckManager;
import ac.grim.grimac.wolf.WolfModCheckManager.ModListResult;
import ac.grim.grimac.wolf.WolfModCheckManager.PendingCheck;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * WolfNetwork Edition: recebe {@code wolfac_mods} do cliente WolfMOD.
 *
 * <p>Formato da resposta: value = {@code requestId|mod1:ver,mod2:ver,...}.</p>
 */
public class BukkitWolfModCheckListener implements PluginMessageListener, StartableInitable {
    @Override
    public void start() {
        try {
            GrimAPI.INSTANCE.getPlatformServer()
                    .registerOutgoingPluginChannel(WolfModCheckManager.CHANNEL);
            Bukkit.getMessenger().registerIncomingPluginChannel(
                    GrimACBukkitLoaderPlugin.LOADER, WolfModCheckManager.CHANNEL, this);
            LogUtil.info("WolfAC checkmods: canal " + WolfModCheckManager.CHANNEL + " registrado.");
        } catch (Exception e) {
            LogUtil.warn("WolfAC checkmods: falha ao registrar canal " + WolfModCheckManager.CHANNEL, e);
        }
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        if (!WolfModCheckManager.CHANNEL.equals(channel)) return;

        WolfModCheckManager.Decoded decoded;
        try {
            decoded = WolfModCheckManager.decode(message);
        } catch (Exception e) {
            return; // pacote de outro sistema (ex: WolfPlugin handshake) — ignorar
        }
        if (!WolfModCheckManager.RESPONSE_KEY.equals(decoded.key())) return;

        String value = decoded.value();
        int sep = value.indexOf('|');
        if (sep <= 0) return;
        UUID requestId;
        try {
            requestId = UUID.fromString(value.substring(0, sep));
        } catch (IllegalArgumentException e) {
            return;
        }
        String modListRaw = value.substring(sep + 1);

        PendingCheck pending = WolfModCheckManager.takeByRequestId(requestId);
        if (pending == null) return;
        if (!pending.targetUuid().equals(player.getUniqueId())) return;

        long now = System.currentTimeMillis();
        int count = modListRaw.isBlank() ? 0 : modListRaw.split(",", -1).length;
        WolfModCheckManager.storeResult(new ModListResult(
                player.getUniqueId(), player.getName(), modListRaw, count, now,
                now - pending.timestampMillis()));

        String header = "%prefix% &bMods de &f" + player.getName()
                + " &7(" + count + " mods, " + (now - pending.timestampMillis()) + "ms)&f:";
        String body = modListRaw.isBlank() ? "&7(nenhum mod reportado)" : "&7" + modListRaw;

        if (pending.requesterUuid() == null) {
            GrimAPI.INSTANCE.getPlatformServer().getConsoleSender()
                    .sendMessage(MessageUtil.miniMessage(header));
            GrimAPI.INSTANCE.getPlatformServer().getConsoleSender()
                    .sendMessage(MessageUtil.miniMessage(body));
        } else {
            Player requester = Bukkit.getPlayer(pending.requesterUuid());
            if (requester != null && requester.isOnline()) {
                requester.sendMessage(MessageUtil.translateAlternateColorCodes('&',
                        header.replace("%prefix%", GrimAPI.INSTANCE.getConfigManager().getPrefix())));
                requester.sendMessage(MessageUtil.translateAlternateColorCodes('&', body));
            } else {
                LogUtil.info("[WolfAC] checkmods " + player.getName() + " (" + count + " mods): " + modListRaw);
            }
        }
    }
}
