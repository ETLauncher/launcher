/*
 * ATLauncher - https://github.com/ATLauncher/ATLauncher
 * Copyright (C) 2013-2022 ATLauncher
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.atlauncher.managers;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.mini2Dx.gettext.GetText;

import com.atlauncher.App;
import com.atlauncher.FileSystem;
import com.atlauncher.Gsons;
import com.atlauncher.data.AbstractAccount;
import com.atlauncher.data.MicrosoftAccount;
import com.atlauncher.data.ElyByAccount;
import com.atlauncher.data.OfflineAccount;
import com.google.gson.JsonIOException;
import com.google.gson.reflect.TypeToken;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;

public class AccountManager {
    private static final Type accountListType = new TypeToken<List<AbstractAccount>>() {
    }.getType();

    private static final BehaviorSubject<List<AbstractAccount>> ACCOUNTS = BehaviorSubject
        .createDefault(Collections.emptyList());

    /**
     * Account using the Launcher
     */
    private static final BehaviorSubject<Optional<AbstractAccount>> SELECTED_ACCOUNT = BehaviorSubject
        .createDefault(Optional.empty());

    public static Observable<List<AbstractAccount>> getAccountsObservable() {
        return ACCOUNTS;
    }

    public static Observable<Optional<AbstractAccount>> getSelectedAccountObservable() {
        return SELECTED_ACCOUNT;
    }

    @Nonnull
    public static List<AbstractAccount> getAccounts() {
        return Optional.ofNullable(ACCOUNTS.getValue()).orElse(Collections.emptyList());
    }

    @Nullable
    public static AbstractAccount getSelectedAccount() {
        AbstractAccount selectedAccount = SELECTED_ACCOUNT.getValue().orElse(null);

        if (isAcceptedAccount(selectedAccount) && getAccounts().contains(selectedAccount)) {
            return selectedAccount;
        }

        return null;
    }

    /**
     * Loads the saved Accounts
     */
    public static void loadAccounts() {
        PerformanceManager.start();
        LogManager.debug("Loading accounts");

        ArrayList<AbstractAccount> newAccounts = new ArrayList<>();

        if (Files.exists(FileSystem.ACCOUNTS)) {
            try (InputStreamReader fileReader = new InputStreamReader(
                Files.newInputStream(FileSystem.ACCOUNTS), StandardCharsets.UTF_8)) {
                List<AbstractAccount> accounts;
                com.google.gson.JsonElement data = new com.google.gson.JsonParser().parse(fileReader);
                if (data.isJsonArray() && data.getAsJsonArray().size() > 0
                    && !data.getAsJsonArray().get(0).getAsJsonObject().has("internalType")) {
                    // Migration: the original accounts.json is a plain MicrosoftAccount list.
                    java.lang.reflect.Type legacy = new TypeToken<List<MicrosoftAccount>>() {}.getType();
                    accounts = new ArrayList<>(Gsons.DEFAULT.fromJson(data, legacy));
                } else {
                    accounts = Gsons.DEFAULT.fromJson(data, accountListType);
                }

                if (accounts != null) {
                    newAccounts.addAll(accounts.stream().filter(AccountManager::isAcceptedAccount)
                        .collect(Collectors.toList()));
                }
            } catch (Exception e) {
                LogManager.logStackTrace("Exception loading accounts", e);
            }
        }

        ACCOUNTS.onNext(immutableAccounts(newAccounts));

        for (AbstractAccount account : newAccounts) {
            if (account.username.equalsIgnoreCase(App.settings.lastAccount)) {
                SELECTED_ACCOUNT.onNext(Optional.of(account));
            }
        }

        if (!SELECTED_ACCOUNT.getValue().isPresent() && !newAccounts.isEmpty()) {
            SELECTED_ACCOUNT.onNext(Optional.of(newAccounts.get(0)));
        }

        LogManager.debug("Finished loading accounts");
        PerformanceManager.end();
    }

    public static void saveAccounts() {
        saveAccounts(ACCOUNTS.getValue());
    }

    private static void saveAccounts(List<AbstractAccount> accounts) {
        try (OutputStreamWriter fileWriter = new OutputStreamWriter(
            Files.newOutputStream(FileSystem.ACCOUNTS), StandardCharsets.UTF_8)) {
            Gsons.DEFAULT.toJson(accounts, accountListType, fileWriter);
        } catch (JsonIOException | IOException e) {
            LogManager.logStackTrace(e);
        }
    }

    public static void addAccount(AbstractAccount account) {
        if (!isAcceptedAccount(account)) {
            LogManager.warn("Refusing to add unsupported account " + account);
            return;
        }

        LogManager.info("Added account " + account);

        List<AbstractAccount> accounts = new ArrayList<>(getAccounts());
        accounts.add(account);
        ACCOUNTS.onNext(immutableAccounts(accounts));

        if (accounts.size() > 1) {
            // not first account? ask if they want to switch to it
            int ret = DialogManager.optionDialog().setTitle(GetText.tr("Account Added"))
                .setContent(GetText.tr("Account added successfully. Switch to it now?")).setType(DialogManager.INFO)
                .addOption(GetText.tr("Yes"), true).addOption(GetText.tr("No")).show();

            if (ret == 0) {
                switchAccount(account);
            }
        } else {
            // first account? switch to it immediately
            switchAccount(account);
        }

        saveAccounts();
    }

    public static void removeAccount(AbstractAccount account) {
        List<AbstractAccount> accounts = new ArrayList<>(getAccounts());
        if (SELECTED_ACCOUNT.getValue().orElse(null) == account) {
            if (accounts.size() == 1) {
                // if this was the only account, don't set an account
                switchAccount(null);
            } else {
                // if they have more accounts, switch to the first one
                switchAccount(accounts.get(0));
            }
        }
        accounts.remove(account);
        ACCOUNTS.onNext(immutableAccounts(accounts));
        saveAccounts();
    }

    /**
     * Switch account currently used and save it
     *
     * @param account Account to switch to
     */
    public static void switchAccount(@Nullable AbstractAccount account) {
        if (account == null) {
            LogManager.info("Logging out of account");
            SELECTED_ACCOUNT.onNext(Optional.empty());
            App.settings.lastAccount = null;
        } else if (!isAcceptedAccount(account) || !getAccounts().contains(account)) {
            LogManager.warn("Refusing to switch to unsupported account " + account);
            return;
        } else {
            LogManager.info("Changed account to " + account);
            SELECTED_ACCOUNT.onNext(Optional.of(account));
            App.settings.lastAccount = account.username;
        }
        App.launcher.refreshPacksBrowserPanel();
        App.settings.save();
    }

    /**
     * Finds an Account from the given username
     *
     * @param username Username of the Account to find
     * @return Account if the Account is found from the username
     */
    public static AbstractAccount getAccountByName(String username) {
        for (AbstractAccount account : getAccounts()) {
            if (account.username.equalsIgnoreCase(username) || account.minecraftUsername.equalsIgnoreCase(username)) {
                return account;
            }
        }
        return null;
    }

    public static MicrosoftAccount getMicrosoftAccountByName(String username) {
        for (AbstractAccount account : getAccounts()) {
            if (account instanceof MicrosoftAccount && account.username.equalsIgnoreCase(username)) {
                return (MicrosoftAccount) account;
            }
        }
        return null;
    }

    /**
     * Finds if an Account is available
     *
     * @param username The username of the Account
     * @return true if found, false if not
     */
    public static boolean isAccountByName(String username) {
        for (AbstractAccount account : getAccounts()) {
            if (account.username.equalsIgnoreCase(username) || account.minecraftUsername.equalsIgnoreCase(username)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAcceptedAccount(AbstractAccount account) {
        if (account == null || account.username == null || account.minecraftUsername == null || account.uuid == null)
            return false;
        if (account.getClass() == MicrosoftAccount.class) {
            MicrosoftAccount msa = (MicrosoftAccount) account;
            return msa.accessToken != null && msa.oauthToken != null
                && msa.oauthToken.accessToken != null && msa.oauthToken.refreshToken != null;
        }
        if (account.getClass() == ElyByAccount.class)
            return ((ElyByAccount) account).accessToken != null;
        return account.getClass() == OfflineAccount.class;
    }

    private static List<AbstractAccount> immutableAccounts(List<AbstractAccount> accounts) {
        return Collections.unmodifiableList(new ArrayList<>(accounts));
    }
}
