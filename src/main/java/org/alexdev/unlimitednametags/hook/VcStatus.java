package org.alexdev.unlimitednametags.hook;

/**
 * Represents a player's current voice chat status.
 */
public enum VcStatus {

    TALKING("talking"),
    WHISPERING("whispering"),
    QUIET("quiet"),
    DISABLED("disabled"),
    NOT_INSTALLED("notInstalled");

    /** Key used to look up the display text in {@code VoiceChatSettings}. */
    public final String configKey;

    VcStatus(String configKey) {
        this.configKey = configKey;
    }
}
