package pl.szczodrzynski.nicknametag;

import org.bukkit.ChatColor;

class Utils {
    /**
     * Translate an &-escaped string with color codes to Minecraft's internal color codes (\u00a7 prefixed)
     */
    static String t(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    /**
     * Un-translate a \u00a7 escaped color string to more human-readable &-escaped string.
     */
    static String ut(String text) {
        return text.replace('\u00a7', '&');
    }
}
