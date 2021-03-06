package pl.szczodrzynski.nicknametag;

import com.comphenix.packetwrapper.*;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.earth2me.essentials.User;
import net.ess3.api.events.NickChangeEvent;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.lang.reflect.InvocationTargetException;
import java.util.Collections;

import static pl.szczodrzynski.nicknametag.Main.*;

public class EssentialsEvents implements Listener {

    private Main plugin;
    private Server server;

    EssentialsEvents(Main plugin, Server server) {
        this.plugin = plugin;
        this.server = server;
        server.getPluginManager().registerEvents(this, plugin);
    }

    private void sendPacket(Player player, PacketContainer packet) throws InvocationTargetException {
        ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet, false /*includeFilters*/ );
    }

    private WrapperPlayServerPlayerInfo buildPlayerInfoPacket(Player player, String newDisplayName, EnumWrappers.PlayerInfoAction action) throws NoSuchFieldException, IllegalAccessException {
        // create a PacketPlayOutPlayerInfo
        WrapperPlayServerPlayerInfo playerInfo = new WrapperPlayServerPlayerInfo();
        // set the action to adding a player
        playerInfo.setAction(action);
        // create a PlayerInfoData, passing a GameProfile
        PlayerInfoData playerInfoData = new PlayerInfoData(
                WrappedGameProfile.fromPlayer(player),
                10,// TODO: 2019-07-06 get correct latency from entityPlayer
                EnumWrappers.NativeGameMode.fromBukkit(player.getGameMode()),
                WrappedChatComponent.fromText(newDisplayName)
        );
        // modify the newly created PlayerInfoData to contain the new nickname
        plugin.modifyPlayerInfoData(playerInfoData, newDisplayName, headTagAddPrefix, headTagAddSuffix);
        // add the PlayerInfoData to the packet
        playerInfo.setData(Collections.singletonList(playerInfoData));

        return playerInfo;
    }

    @EventHandler
    public void onNickChange(NickChangeEvent event) {
        if (!headTagEnable)
            return;
        Player player = event.getController().getBase();
        User target = (User) event.getController();
        event.setCancelled(true);

        // get the user input
        String newDisplayName = event.getValue();
        // change two underscores to allow spaces in the nickname
        newDisplayName = newDisplayName == null ? null : allowSpaces ? newDisplayName.replace("__", " ") : newDisplayName;
        // set the nickname in Essentials config (does not update Player.displayName yet)
        target.setNickname(newDisplayName);

        // get a nickname containing (optionally) 'ops-name-color', 'nickname-prefix'
        newDisplayName = target.getNick(true, headTagAddPrefix, headTagAddSuffix);

        // can't call setDisplayNick here:
        // this command changes playerlist display name (Player.setPlayerListName();)
        // this may contain prefixes & suffixes
        // since they are not displayed in the head tag, they will quickly be replaced with NicknameTag's packets
        //target.setDisplayNick();

        try {
            // get EntityPlayer from the CraftPlayer
            // TODO: 2019-07-06 get latency
            //Object entityPlayer = player.getClass().getMethod("getHandle").invoke(player);
            Location location = player.getLocation();

            WrapperPlayServerPlayerInfo removeInfo = buildPlayerInfoPacket(player, newDisplayName, EnumWrappers.PlayerInfoAction.REMOVE_PLAYER);
            WrapperPlayServerPlayerInfo addInfo = buildPlayerInfoPacket(player, newDisplayName, EnumWrappers.PlayerInfoAction.ADD_PLAYER);


            WrapperPlayServerEntityDestroy removeEntity = new WrapperPlayServerEntityDestroy();
            removeEntity.setEntityIds(new int[] {player.getEntityId()} );

            WrapperPlayServerNamedEntitySpawn addNamed = new WrapperPlayServerNamedEntitySpawn();
            addNamed.setEntityID(player.getEntityId());
            addNamed.setPlayerUUID(player.getUniqueId());
            addNamed.setX(location.getX());
            addNamed.setY(location.getY());
            addNamed.setZ(location.getZ());
            addNamed.setYaw(location.getYaw());
            addNamed.setPitch(location.getPitch());
            //addNamed.setMetadata(new WrappedDataWatcher(player));


            WrapperPlayServerRespawn respawn = new WrapperPlayServerRespawn();
            World world = player.getWorld();
            respawn.setDimension(world.getEnvironment().getId());
            //respawn.setDifficulty(EnumWrappers.Difficulty.valueOf(world.getDifficulty().name()));
            respawn.setLevelType(world.getWorldType());
            respawn.setGamemode(EnumWrappers.NativeGameMode.fromBukkit(player.getGameMode()));

            WrapperPlayServerPosition position = new WrapperPlayServerPosition();
            position.setX(location.getX());
            position.setY(location.getY());
            position.setZ(location.getZ());
            position.setYaw(location.getYaw());
            position.setPitch(location.getPitch());

            WrapperPlayServerHeldItemSlot slot = new WrapperPlayServerHeldItemSlot();
            slot.setSlot(player.getInventory().getHeldItemSlot());

            // Equipment packets
            WrapperPlayServerEntityEquipment mainHand = new WrapperPlayServerEntityEquipment();
            mainHand.setEntityID(player.getEntityId());
            mainHand.setSlot(EnumWrappers.ItemSlot.MAINHAND);
            mainHand.setItem(player.getInventory().getItemInMainHand());

            WrapperPlayServerEntityEquipment offHand = new WrapperPlayServerEntityEquipment();
            offHand.setEntityID(player.getEntityId());
            offHand.setSlot(EnumWrappers.ItemSlot.OFFHAND);
            offHand.setItem(player.getInventory().getItemInOffHand());

            WrapperPlayServerEntityEquipment helmet = new WrapperPlayServerEntityEquipment();
            helmet.setEntityID(player.getEntityId());
            helmet.setSlot(EnumWrappers.ItemSlot.HEAD);
            helmet.setItem(player.getInventory().getHelmet());

            WrapperPlayServerEntityEquipment chestplate = new WrapperPlayServerEntityEquipment();
            chestplate.setEntityID(player.getEntityId());
            chestplate.setSlot(EnumWrappers.ItemSlot.CHEST);
            chestplate.setItem(player.getInventory().getChestplate());

            WrapperPlayServerEntityEquipment leggins = new WrapperPlayServerEntityEquipment();
            leggins.setEntityID(player.getEntityId());
            leggins.setSlot(EnumWrappers.ItemSlot.LEGS);
            leggins.setItem(player.getInventory().getLeggings());

            WrapperPlayServerEntityEquipment boots = new WrapperPlayServerEntityEquipment();
            boots.setEntityID(player.getEntityId());
            boots.setSlot(EnumWrappers.ItemSlot.FEET);
            boots.setItem(player.getInventory().getBoots());


            for (Player onlinePlayer: server.getOnlinePlayers()) {
                Object craftHandle = onlinePlayer.getClass().getMethod("getHandle").invoke(onlinePlayer);

                if (onlinePlayer.equals(player)) {
                    sendPacket(onlinePlayer, removeInfo.getHandle());
                    sendPacket(onlinePlayer, addInfo.getHandle());
                    sendPacket(onlinePlayer, respawn.getHandle());

                    craftHandle.getClass().getMethod("updateAbilities").invoke(craftHandle);

                    sendPacket(onlinePlayer, position.getHandle());
                    sendPacket(onlinePlayer, slot.getHandle());
                    onlinePlayer.getClass().getMethod("updateScaledHealth").invoke(onlinePlayer);
                    onlinePlayer.getClass().getMethod("updateInventory").invoke(onlinePlayer);
                    craftHandle.getClass().getMethod("triggerHealthUpdate").invoke(craftHandle);

                    if (onlinePlayer.isOp()) {
                        onlinePlayer.setOp(false);
                        onlinePlayer.setOp(true);
                    }
                }
                else if (onlinePlayer.getWorld().equals(player.getWorld()) && onlinePlayer.canSee(player)) {
                    sendPacket(onlinePlayer, removeEntity.getHandle());
                    sendPacket(onlinePlayer, removeInfo.getHandle());
                    sendPacket(onlinePlayer, addInfo.getHandle());
                    sendPacket(onlinePlayer, addNamed.getHandle());

                    sendPacket(onlinePlayer, mainHand.getHandle());
                    sendPacket(onlinePlayer, offHand.getHandle());

                    sendPacket(onlinePlayer, helmet.getHandle());
                    sendPacket(onlinePlayer, chestplate.getHandle());
                    sendPacket(onlinePlayer, leggins.getHandle());
                    sendPacket(onlinePlayer, boots.getHandle());
                }
                else {
                    sendPacket(onlinePlayer, removeInfo.getHandle());
                    sendPacket(onlinePlayer, addInfo.getHandle());
                }
            } /* for (Player onlinePlayer: server.getOnlinePlayers()) */
        } catch (Exception e) {
            e.printStackTrace();
        }

        // call this here to update Player.displayName and playerlist
        target.setDisplayNick();
    }
}
