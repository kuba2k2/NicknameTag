package pl.szczodrzynski.nicknametag;

import com.comphenix.packetwrapper.WrapperPlayServerPlayerInfo;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;

import java.util.List;

import static pl.szczodrzynski.nicknametag.Main.*;

public class PlayerInfoListener extends PacketAdapter {

    private Main plugin;

    PlayerInfoListener(Main plugin) {
        super(plugin, ListenerPriority.NORMAL, PacketType.Play.Server.PLAYER_INFO);
        this.plugin = plugin;
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        if (!headTagEnable)
            return;
        WrapperPlayServerPlayerInfo playerInfo = new WrapperPlayServerPlayerInfo(event.getPacket());
        if (playerInfo.getAction() != EnumWrappers.PlayerInfoAction.ADD_PLAYER)
            return;

        List<PlayerInfoData> dataList = playerInfo.getData();
        for (PlayerInfoData playerInfoData: dataList) {
            try {
                plugin.modifyPlayerInfoData(playerInfoData, null, headTagAddPrefix, headTagAddSuffix);
            } catch (NullPointerException | NoSuchFieldException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        playerInfo.setData(dataList);

        event.setPacket(playerInfo.getHandle());
    }
}
