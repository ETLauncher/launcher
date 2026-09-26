package com.atlauncher.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.atlauncher.Gsons;
import com.google.gson.reflect.TypeToken;

class LauncherAccountsTest {
    @Test
    void offlineProfileUsesMinecraftUuidAndNoCredentials() {
        OfflineAccount account = new OfflineAccount("Player_123");
        assertEquals(UUID.nameUUIDFromBytes("OfflinePlayer:Player_123".getBytes(StandardCharsets.UTF_8)),
            account.getRealUUID());
        assertEquals("0", account.getAccessToken());
        assertTrue(account.ensureAccessTokenValid());
        assertThrows(IllegalArgumentException.class, () -> new OfflineAccount("bad name"));
    }

    @Test
    void accountTypesSurvivePersistence() {
        Type type = new TypeToken<List<AbstractAccount>>() {}.getType();
        List<AbstractAccount> accounts = Arrays.asList(new OfflineAccount("Player_123"));
        String json = Gsons.DEFAULT.toJson(accounts, type);
        List<AbstractAccount> restored = Gsons.DEFAULT.fromJson(json, type);
        assertEquals(OfflineAccount.class, restored.get(0).getClass());
        assertEquals(accounts.get(0).uuid, restored.get(0).uuid);
    }
}
