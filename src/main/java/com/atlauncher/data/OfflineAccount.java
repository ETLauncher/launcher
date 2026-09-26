package com.atlauncher.data;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Local profile for single player and servers that allow offline accounts. */
public final class OfflineAccount extends AbstractAccount {
    private static final long serialVersionUID = 1L;

    public OfflineAccount(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_]{3,16}")) {
            throw new IllegalArgumentException("Minecraft name must contain 3–16 letters, digits or underscores");
        }
        username = name;
        minecraftUsername = name;
        uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)).toString();
    }

    @Override public String getAccessToken() { return "0"; }
    @Override public String getSessionToken() { return "0"; }
    @Override public String getUserType() { return "legacy"; }
    @Override public String getCurrentUsername() { return minecraftUsername; }
    @Override public void updateSkinPreCheck() { }
    @Override public void changeSkinPreCheck() { }
    @Override public String getSkinUrl() { return null; }
    @Override public boolean ensureAccessTokenValid() { return true; }
}
