package pl.szczodrzynski.nicknametag;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static pl.szczodrzynski.nicknametag.Utils.t;
import static pl.szczodrzynski.nicknametag.Utils.ut;

public class Main extends JavaPlugin implements Listener {

    private Server server;
    private Scoreboard scoreboard;
    private Essentials ess;

    static boolean allowSpaces = true;
    static boolean headTagEnable = true;
    static boolean headTagAddPrefix = false;
    static boolean headTagAddSuffix = false;
    static boolean headTagIgnoreNoSkin = true;
    static boolean serverListEnable = true;
    static boolean serverListAddPrefix = true;
    static boolean serverListAddSuffix = true;

    @Override
    public void onEnable() {
        this.server = getServer();
        this.scoreboard = server.getScoreboardManager().getMainScoreboard();

        // load config values
        saveDefaultConfig();
        reloadConfig();
        FileConfiguration config = getConfig();
        allowSpaces = config.getBoolean("allow-spaces");
        headTagEnable = config.getBoolean("head-tag.enable");
        headTagAddPrefix = config.getBoolean("head-tag.add-prefix");
        headTagAddSuffix = config.getBoolean("head-tag.add-suffix");
        headTagIgnoreNoSkin = config.getBoolean("head-tag.ignore-no-skin");
        serverListEnable = config.getBoolean("server-list.enable");
        serverListAddPrefix = config.getBoolean("server-list.add-prefix");
        serverListAddSuffix = config.getBoolean("server-list.add-suffix");

        // register events
        getServer().getPluginManager().registerEvents(this, this);
        // register packet listeners
        ProtocolLibrary.getProtocolManager().addPacketListener(new PlayerInfoListener(this));
        ProtocolLibrary.getProtocolManager().addPacketListener(new ServerListListener(this));

        if (server.getPluginManager().isPluginEnabled("Essentials")) {
            ess = (Essentials) server.getPluginManager().getPlugin("Essentials");
            new EssentialsEvents(this, server);
        }
        else {
            ess = null;
            getLogger().warning("Essentials NOT found");
        }

    }

    /*@EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        prepareTeam(player.getName(), player.getDisplayName()); // somehow creating team here causes the prefix and suffix to disappear // probably no longer // somehow the nickname isn't loaded here yet
    }*/

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (scoreboard == null || player == null)
            return;
        Team team = scoreboard.getTeam(player.getName());
        if (team != null)
            team.unregister();
    }

    WrappedGameProfile modifyWrappedGameProfile(WrappedGameProfile originalProfile, String newDisplayName, boolean splitBy16Chars, boolean withPrefix, boolean withSuffix) throws IllegalAccessException, NoSuchFieldException {
        // get a GameProfile class
        Class gameProfileClass = originalProfile.getHandleType();

        // get the real player's data
        UUID playerUUID = originalProfile.getUUID();
        OfflinePlayer player = server.getPlayer(playerUUID);
        if (player == null)
            player = server.getOfflinePlayer(playerUUID);
        if (player == null) {
            System.out.println("Player == null for name: "+originalProfile.getName()+", UUID: "+originalProfile.getUUID());
        }

        if (ess != null && player != null && newDisplayName == null) {
            User user = ess.getUser(player);
            if (user == null) {
                user = ess.getOfflineUser(originalProfile.getName());
            }
            if (user == null) {
                System.out.println("Player "+player.getName()+" with UUID "+playerUUID.toString()+" is null");
            }
            else {
                newDisplayName = user.getNick(true, withPrefix, withSuffix);
            }
            /*if (withPrefix)
                newDisplayName = t(ess.getPermissionsHandler().getPrefix(player)) + newDisplayName;
            if (withSuffix)
                newDisplayName = newDisplayName + t(ess.getPermissionsHandler().getSuffix(player));*/
        }
        else if (newDisplayName == null && player instanceof Player)
            newDisplayName = ((Player)player).getDisplayName();

        String name;
        if (splitBy16Chars && player != null)
            name = prepareTeam(player.getName(), newDisplayName);
        else
            name = newDisplayName;

        // get the PropertyMap field of GameProfile
        Field propertiesField = gameProfileClass.getDeclaredField("properties");
        propertiesField.setAccessible(true);

        // disable "final" modifier of "private final PropertyMap properties"
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(propertiesField, propertiesField.getModifiers() & ~Modifier.FINAL);

        // get the original properties from GameProfile
        Object properties = propertiesField.get(originalProfile.getHandle());

        // create a GameProfile wrapper with new name
        WrappedGameProfile newProfile = new WrappedGameProfile(playerUUID, name);

        // put the original properties into GameProfile
        propertiesField.set(newProfile.getHandle(), properties);

        return newProfile;
    }

    void modifyPlayerInfoData(PlayerInfoData playerInfoData, String newDisplayName, boolean withPrefix, boolean withSuffix) throws IllegalAccessException, NoSuchFieldException {
        // get the original WrappedGameProfile
        WrappedGameProfile originalProfile = playerInfoData.getProfile();
        // skip if no properties are set, to not modify the skin name
        // do not skip if the config option is disabled
        if (originalProfile.getProperties().size() == 0 && headTagIgnoreNoSkin)
            return;

        // modify the profile with given display name
        WrappedGameProfile newProfile = modifyWrappedGameProfile(originalProfile, newDisplayName, true, withPrefix, withSuffix);

        // set the newly created WrappedGameProfile into PlayerInfoData
        Field profileField = playerInfoData.getClass().getDeclaredField("profile");
        profileField.setAccessible(true);
        profileField.set(playerInfoData, newProfile);
    }

    private String prepareTeam(String playerName, String displayName) {
        displayName = ut(displayName);

        if (displayName.endsWith("&r")) {
            displayName = displayName.substring(0, displayName.length()-2);
        }

        // create or get a Team for the player
        Team team;
        if ((team = scoreboard.getTeam(playerName)) == null) {
            team = scoreboard.registerNewTeam(playerName);
        }
        if (!team.hasEntry(playerName)) {
            team.addEntry(playerName);
        }

        String prefix = "";
        String name = "";
        String suffix = "";

        Matcher matcher = Pattern.compile("(.{1,13}[^&]?)").matcher(displayName);

        List<String> parts = new ArrayList<>();
        while (matcher.find()) {
            parts.add(matcher.group(1));
        }

        if (parts.size() == 1) {
            name = parts.get(0);
        }
        else if (parts.size() == 2) {
            prefix = parts.get(0);
            name = parts.get(1);
        }
        else if (parts.size() >= 3) {
            prefix = parts.get(0);
            name = parts.get(1);
            suffix = parts.get(2);
        }

        team.setPrefix(t(prefix));
        team.setSuffix(t(suffix));

        // gather all formatting other than colors
        StringBuilder format = new StringBuilder();

        // find all formatting codes
        matcher = Pattern.compile("&([0-9A-z])").matcher(prefix);
        while (matcher.find()) {
            ChatColor code = ChatColor.getByChar(matcher.group(1));
            if (code != null) {
                if (code.ordinal() <= 0xF) {
                    // set team color to last color in nickname
                    team.setColor(code);
                    // clear other formatting codes
                    format = new StringBuilder();
                }
                else {
                    // save formatting code to prepend it to player's name part
                    format.append('&');
                    format.append(code.getChar());
                }
            }
        }

        name = format.toString() + name;

        if (!team.hasEntry(t(name))) {
            team.addEntry(t(name));
        }

        System.out.println("Prefix: "+prefix+", name: "+name+", suffix: "+suffix+", color: "+team.getColor().name()+". Display name: "+displayName);

        return t(name);
    }

    @Override
    public void onDisable() {

    }
}
