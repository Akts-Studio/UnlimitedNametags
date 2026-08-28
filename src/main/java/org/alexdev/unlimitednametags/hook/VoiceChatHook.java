package org.alexdev.unlimitednametags.hook;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.PlayerConnectedEvent;
import de.maxhenkel.voicechat.api.events.PlayerDisconnectedEvent;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.config.Settings;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Hooks into Simple Voice Chat and provides %vc% placeholder resolution.
 * Folia-safe: uses TaskScheduler, never touches Bukkit.getScheduler().
 */
public class VoiceChatHook extends Hook implements VoicechatPlugin {

    private static final String PLUGIN_ID = "unt_voicechat_hook";

    // Thread-safe state maps
    private final ConcurrentHashMap<UUID, Long> lastPacket = new ConcurrentHashMap<>();
    private final Set<UUID> inVc = ConcurrentHashMap.newKeySet();

    private volatile VoicechatServerApi vcApi;
    private volatile long talkTimeoutMs;
    private volatile boolean registered = false;

    public VoiceChatHook(@NotNull UnlimitedNameTags plugin) {
        super(plugin);
        this.talkTimeoutMs = plugin.getConfigManager().getSettings().getVoiceChat().getTalkTimeoutMs();
    }

    // ---------------------- Hook lifecycle ----------------------

    @Override
    public void onEnable() {
        final BukkitVoicechatService service = Bukkit.getServicesManager().load(BukkitVoicechatService.class);
        if (service == null) {
            plugin.getLogger().warning("[VoiceChatHook] BukkitVoicechatService not available.");
            return;
        }
        service.registerPlugin(this);
        registered = true;
        plugin.getLogger().info("[VoiceChatHook] Registered with Simple Voice Chat.");

        // Cleanup task: remove stale entries every 200 ticks (Folia-safe)
        plugin.getTaskScheduler().runTaskTimerAsynchronously(() -> {
            final long now = System.currentTimeMillis();
            lastPacket.entrySet().removeIf(e -> now - e.getValue() > 10_000L);
        }, 200L, 200L);
    }

    @Override
    public void onDisable() {
        lastPacket.clear();
        inVc.clear();
        vcApi = null;
        registered = false;
    }

    /** Call from /unt reload to pick up new config values. */
    public void reload() {
        this.talkTimeoutMs = plugin.getConfigManager().getSettings().getVoiceChat().getTalkTimeoutMs();
    }

    // ---------------------- VoicechatPlugin ----------------------

    @Override
    public @NotNull String getPluginId() {
        return PLUGIN_ID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        this.vcApi = (VoicechatServerApi) api;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophone);
        registration.registerEvent(PlayerConnectedEvent.class, this::onConnect);
        registration.registerEvent(PlayerDisconnectedEvent.class, this::onDisconnect);
    }

    // ---------------------- Event handlers ----------------------

    private void onMicrophone(MicrophonePacketEvent event) {
        if (event.getSenderConnection() == null) return;
        final var player = event.getSenderConnection().getPlayer();
        if (player == null) return;
        lastPacket.put(player.getUuid(), System.currentTimeMillis());
    }

    private void onConnect(PlayerConnectedEvent event) {
        inVc.add(event.getConnection().getPlayer().getUuid());
    }

    private void onDisconnect(PlayerDisconnectedEvent event) {
        final UUID uuid = event.getPlayerUuid();
        lastPacket.remove(uuid);
        inVc.remove(uuid);
    }

    // ---------------------- Public API ----------------------

    /**
     * Returns the voice status for the given player UUID.
     */
    public @NotNull VcStatus getStatus(@NotNull UUID uuid) {
        if (!registered || vcApi == null) return VcStatus.DISABLED;

        final VoicechatConnection conn = vcApi.getConnectionOf(uuid);
        if (conn == null || conn.isDisabled()) return VcStatus.DISABLED;
        if (!conn.isInstalled()) return VcStatus.NOT_INSTALLED;
        if (!inVc.contains(uuid)) return VcStatus.DISABLED;

        final Long last = lastPacket.get(uuid);
        final boolean talking = last != null && (System.currentTimeMillis() - last) <= talkTimeoutMs;
        if (!talking) return VcStatus.QUIET;

        final Player bukkit = Bukkit.getPlayer(uuid);
        return (bukkit != null && bukkit.isSneaking()) ? VcStatus.WHISPERING : VcStatus.TALKING;
    }

    /**
     * Returns the configured display text for the player's voice status.
     */
    public @NotNull String getStatusText(@NotNull UUID uuid) {
        final VcStatus status = getStatus(uuid);
        final Settings.VoiceChatSettings cfg = plugin.getConfigManager().getSettings().getVoiceChat();
        return switch (status) {
            case TALKING      -> cfg.getTalking();
            case WHISPERING   -> cfg.getWhispering();
            case QUIET        -> cfg.getQuiet();
            case NOT_INSTALLED -> cfg.getNotInstalled();
            default           -> cfg.getDisabled();
        };
    }
}
