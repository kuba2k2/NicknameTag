package pl.szczodrzynski.nicknametag;

import com.comphenix.packetwrapper.WrapperStatusServerServerInfo;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedServerPing;

import java.util.ArrayList;
import java.util.List;

import static pl.szczodrzynski.nicknametag.Main.*;

public class ServerListListener extends PacketAdapter {

    private Main plugin;

    ServerListListener(Main plugin) {
        super(plugin, ListenerPriority.NORMAL, PacketType.Status.Server.SERVER_INFO);
        this.plugin = plugin;
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        if (!serverListEnable)
            return;
        WrapperStatusServerServerInfo serverInfo = new WrapperStatusServerServerInfo(event.getPacket());

        WrappedServerPing ping = serverInfo.getJsonResponse();

        List<WrappedGameProfile> playerList = new ArrayList<>(ping.getPlayers());
        for (int i = 0; i < playerList.size(); i++) {
            try {
                playerList.set(i, plugin.modifyWrappedGameProfile(playerList.get(i), null, false, serverListAddPrefix, serverListAddSuffix));
            } catch (NullPointerException | IllegalAccessException | NoSuchFieldException e) {
                e.printStackTrace();
            }
        }
        ping.setPlayers(playerList);

        serverInfo.setJsonResponse(ping);

        event.setPacket(serverInfo.getHandle());
    }
}
