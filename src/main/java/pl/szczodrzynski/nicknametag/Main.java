package pl.szczodrzynski.nicknametag;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import org.bukkit.Server;
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
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static pl.szczodrzynski.nicknametag.Utils.t;
import static pl.szczodrzynski.nicknametag.Utils.ut;

public class Main extends JavaPlugin implements Listener {

    private Server server;
    private Scoreboard scoreboard;

    @Override
    public void onEnable() {
        this.server = getServer();
        this.scoreboard = server.getScoreboardManager().getMainScoreboard();
        // register events
        getServer().getPluginManager().registerEvents(this, this);
        // register packet listener
        ProtocolLibrary.getProtocolManager().addPacketListener(new PlayerInfoListener(this));

        new EssentialsEvents(this, server);
    }

    /*@EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        prepareTeam(player.getName(), player.getDisplayName()); // somehow creating team here causes the prefix and suffix to disappear // probably no longer // somehow the nickname isn't loaded here yet
    }*/

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        scoreboard.getTeam(player.getName()).unregister();
    }

    void modifyPlayerInfoData(PlayerInfoData playerInfoData, String newDisplayName) throws IllegalAccessException, NoSuchFieldException {
        // get the original WrappedGameProfile
        WrappedGameProfile originalProfile = playerInfoData.getProfile();
        // skip if no properties are set, to not modify the skin name
        if (originalProfile.getProperties().size() == 0)
            return;

        // get a GameProfile class
        Class gameProfileClass = originalProfile.getHandleType();

        // get the real player's data
        UUID playerUUID = originalProfile.getUUID();
        Player player = server.getPlayer(playerUUID);
        if (newDisplayName == null)
            newDisplayName = player.getDisplayName();

        String name = prepareTeam(player.getName(), newDisplayName);

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

        // set the newly created WrappedGameProfile into PlayerInfoData
        Field profileField = playerInfoData.getClass().getDeclaredField("profile");
        profileField.setAccessible(true);
        profileField.set(playerInfoData, newProfile);
    }

    private String prepareTeam(String playerName, String displayName) {
        displayName = ut(displayName);

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

        Matcher matcher = Pattern.compile("(.{1,15}[^&]?)").matcher(displayName);

        List<String> parts = new ArrayList<>();
        while (matcher.find()) {
            parts.add(matcher.group(1));
        }

        getLogger().info("name "+displayName+" "+Arrays.toString(parts.toArray()));

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

        if (!team.hasEntry(t(name))) {
            team.addEntry(t(name));
        }

        return t(name);
    }

    @Override
    public void onDisable() {

    }
}
