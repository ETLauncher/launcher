package com.atlauncher.data;

import com.atlauncher.managers.AccountManager;
import com.atlauncher.utils.ElyByAuthAPI;
import com.google.gson.JsonObject;

/** Ely.by account. Passwords are never stored. */
public final class ElyByAccount extends AbstractAccount {
    private static final long serialVersionUID = 1L;
    public String accessToken;
    public String refreshToken;
    public String clientToken;
    public long expiresAt;

    public ElyByAccount(JsonObject token, JsonObject profile) {
        username = profile.get("username").getAsString();
        minecraftUsername = username;
        uuid = profile.get("uuid").getAsString();
        updateToken(token);
    }

    public ElyByAccount(JsonObject authentication) {
        JsonObject profile = authentication.getAsJsonObject("selectedProfile");
        username = profile.get("name").getAsString();
        minecraftUsername = username;
        uuid = profile.get("id").getAsString();
        accessToken = authentication.get("accessToken").getAsString();
        clientToken = authentication.get("clientToken").getAsString();
    }

    public void updateToken(JsonObject token) {
        accessToken = token.get("access_token").getAsString();
        if (token.has("refresh_token") && !token.get("refresh_token").isJsonNull()) {
            refreshToken = token.get("refresh_token").getAsString();
        }
        expiresAt = System.currentTimeMillis() + token.get("expires_in").getAsLong() * 1000L;
    }

    @Override public String getAccessToken() { return accessToken; }
    @Override public String getSessionToken() { return accessToken; }
    @Override public String getUserType() { return "mojang"; }
    @Override public String getCurrentUsername() {
        if (clientToken != null) return minecraftUsername;
        try { return ElyByAuthAPI.getProfile(accessToken).get("username").getAsString(); }
        catch (Exception e) { return null; }
    }
    @Override public void updateSkinPreCheck() { ensureAccessTokenValid(); }
    @Override public void changeSkinPreCheck() { }
    @Override public String getSkinUrl() { return "https://skinsystem.ely.by/skins/" + minecraftUsername + ".png"; }
    @Override public boolean ensureAccessTokenValid() {
        if (clientToken != null) return refreshNow();
        if (System.currentTimeMillis() < expiresAt - 60_000L) return true;
        try {
            updateToken(ElyByAuthAPI.refresh(refreshToken));
            AccountManager.saveAccounts();
            return true;
        } catch (Exception e) { return false; }
    }

    public boolean refreshNow() {
        try {
            if (clientToken != null) {
                JsonObject refreshed = ElyByAuthAPI.refreshLegacy(accessToken, clientToken);
                accessToken = refreshed.get("accessToken").getAsString();
                clientToken = refreshed.get("clientToken").getAsString();
            } else {
                updateToken(ElyByAuthAPI.refresh(refreshToken));
            }
            AccountManager.saveAccounts();
            return true;
        } catch (Exception e) { return false; }
    }
}
