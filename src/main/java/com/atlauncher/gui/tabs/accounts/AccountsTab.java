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
package com.atlauncher.gui.tabs.accounts;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ItemEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.event.HyperlinkEvent;

import org.mini2Dx.gettext.GetText;

import com.atlauncher.App;
import com.atlauncher.builders.HTMLBuilder;
import com.atlauncher.data.AbstractAccount;
import com.atlauncher.data.ElyByAccount;
import com.atlauncher.data.OfflineAccount;
import com.atlauncher.gui.dialogs.LoginWithMicrosoftDialog;
import com.atlauncher.gui.dialogs.ProgressDialog;
import com.atlauncher.gui.panels.HierarchyPanel;
import com.atlauncher.gui.tabs.Tab;
import com.atlauncher.managers.AccountManager;
import com.atlauncher.managers.DialogManager;
import com.atlauncher.managers.LogManager;
import com.atlauncher.utils.ElyByAuthAPI;
import com.atlauncher.utils.ComboItem;
import com.atlauncher.utils.OS;
import com.atlauncher.utils.SkinUtils;
import com.atlauncher.utils.Utils;
import com.atlauncher.viewmodel.base.IAccountsViewModel;
import com.atlauncher.viewmodel.impl.AccountsViewModel;

public class AccountsTab extends HierarchyPanel implements Tab {
    private static final long serialVersionUID = 2493791137600123223L;

    private IAccountsViewModel viewModel;

    private JLabel userSkin;
    private JComboBox<ComboItem<String>> accountsComboBox;
    private JButton deleteButton;
    private JButton loginWithMicrosoftButton;
    private JButton offlineButton;
    private JButton elyByButton;
    private JMenuItem refreshAccessTokenMenuItem;
    private JMenuItem updateSkin;
    private JMenuItem changeSkin;
    private JPopupMenu contextMenu; // Right click menu

    public AccountsTab() {
        super(new BorderLayout());
    }

    @Override
    protected void onShow() {
        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BorderLayout());
        infoPanel.setBorder(BorderFactory.createEmptyBorder(60, 250, 0, 250));

        JEditorPane infoTextPane = new JEditorPane("text/html", new HTMLBuilder().center()
            .text("ETLauncher: выберите Microsoft, Ely.by или оффлайн аккаунт. "
                + "Оффлайн аккаунт работает в одиночной игре и на серверах, которые его допускают.")
            .build());
        infoTextPane.setEditable(false);
        infoTextPane.setFocusable(false);
        infoTextPane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                OS.openWebBrowser(e.getURL());
            }
        });

        infoPanel.add(infoTextPane);

        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BorderLayout());

        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        Insets TOP_INSETS = new Insets(0, 0, 20, 0);
        gbc.insets = TOP_INSETS;
        gbc.anchor = GridBagConstraints.CENTER;

        accountsComboBox = new JComboBox<>();
        accountsComboBox.setName("accountsTabAccountsComboBox");
        accountsComboBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                viewModel.setSelectedAccount(accountsComboBox.getSelectedIndex());
            }
        });
        bottomPanel.add(accountsComboBox, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        Insets BOTTOM_INSETS = new Insets(10, 0, 0, 0);
        gbc.insets = BOTTOM_INSETS;
        gbc.anchor = GridBagConstraints.CENTER;
        JPanel buttons = new JPanel();
        buttons.setLayout(new FlowLayout());
        deleteButton = new JButton(GetText.tr("Delete"));
        deleteButton.setVisible(false);
        deleteButton.addActionListener(e -> {
            int ret = DialogManager
                .yesNoDialog()
                .setTitle(GetText.tr("Delete"))
                .setContent(GetText.tr("Are you sure you want " +
                    "to delete this account?"))
                .setType(DialogManager.WARNING).show();
            if (ret == DialogManager.YES_OPTION) {
                viewModel.deleteAccount();
            }
        });
        loginWithMicrosoftButton = new JButton();
        loginWithMicrosoftButton.setBorderPainted(false);
        loginWithMicrosoftButton.setToolTipText(GetText.tr("Sign In with Microsoft"));
        loginWithMicrosoftButton.setIcon(Utils.getIconImage(
            App.THEME.getResourcePath("image/providers", "sign-in-with-microsoft")));
        loginWithMicrosoftButton.addActionListener(e -> {
            // TODO This should be handled by some reaction via listener
            int numberOfAccountsBefore = viewModel.accountCount();

            LoginWithMicrosoftDialog loginWithMicrosoftDialog = new LoginWithMicrosoftDialog();
            loginWithMicrosoftDialog.setVisible(true);

            if (numberOfAccountsBefore != viewModel.accountCount()) {
                // account was added, so get the skin
                if (loginWithMicrosoftDialog.account != null) {
                    loginWithMicrosoftDialog.account.updateSkin();
                }

                viewModel.pushNewAccounts();
                accountsComboBox.setSelectedItem(AccountManager.getSelectedAccount());
            }
        });
        buttons.add(deleteButton);
        buttons.add(loginWithMicrosoftButton);
        offlineButton = new JButton("Оффлайн");
        offlineButton.addActionListener(e -> {
            String name = DialogManager.okDialog().setTitle("Оффлайн аккаунт")
                .setContent("Введите имя игрока (3–16 латинских букв, цифр или _):")
                .showInput("");
            if (name == null) return;
            try {
                OfflineAccount account = new OfflineAccount(name.trim());
                if (AccountManager.isAccountByName(account.username)) {
                    throw new IllegalArgumentException("Аккаунт с таким именем уже существует");
                }
                AccountManager.addAccount(account);
                viewModel.pushNewAccounts();
            } catch (IllegalArgumentException ex) {
                DialogManager.okDialog().setTitle("Ошибка")
                    .setContent(ex.getMessage()).setType(DialogManager.ERROR).show();
            }
        });
        buttons.add(offlineButton);

        elyByButton = new JButton("Войти через Ely.by");
        elyByButton.addActionListener(e -> {
            JTextField usernameField = new JTextField(20);
            JPasswordField passwordField = new JPasswordField(20);
            JTextField totpField = new JTextField(8);
            JPanel fields = new JPanel(new GridBagLayout());
            GridBagConstraints fieldConstraints = new GridBagConstraints();
            fieldConstraints.insets = new Insets(4, 4, 4, 4);
            fieldConstraints.anchor = GridBagConstraints.WEST;
            fieldConstraints.gridx = 0;
            fieldConstraints.gridy = 0;
            fields.add(new JLabel("Логин или e-mail Ely.by:"), fieldConstraints);
            fieldConstraints.gridx = 1;
            fields.add(usernameField, fieldConstraints);
            fieldConstraints.gridx = 0;
            fieldConstraints.gridy = 1;
            fields.add(new JLabel("Пароль:"), fieldConstraints);
            fieldConstraints.gridx = 1;
            fields.add(passwordField, fieldConstraints);
            fieldConstraints.gridx = 0;
            fieldConstraints.gridy = 2;
            fields.add(new JLabel("Код 2FA (если включён):"), fieldConstraints);
            fieldConstraints.gridx = 1;
            fields.add(totpField, fieldConstraints);
            if (JOptionPane.showConfirmDialog(this, fields, "Вход через Ely.by",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
            String login = usernameField.getText().trim();
            char[] passwordChars = passwordField.getPassword();
            String password = new String(passwordChars);
            java.util.Arrays.fill(passwordChars, '\0');
            passwordField.setText("");
            String totp = totpField.getText().trim();
            if (login.isEmpty() || password.isEmpty()) {
                DialogManager.okDialog().setTitle("Ely.by")
                    .setContent("Введите логин и пароль Ely.by.").setType(DialogManager.ERROR).show();
                return;
            }
            ProgressDialog<ElyByAccount> dialog = new ProgressDialog<>("Вход через Ely.by", 0,
                "Проверка аккаунта Ely.by", "Вход через Ely.by отменён");
            final String[] error = new String[1];
            dialog.addThread(new Thread(() -> {
                try { dialog.setReturnValue(ElyByAuthAPI.loginWithPassword(login, password, totp)); }
                catch (Exception ex) { error[0] = ex.getMessage(); }
                finally { dialog.close(); }
            }));
            dialog.start();
            ElyByAccount account = dialog.getReturnValue();
            if (account != null) {
                AccountManager.addAccount(account);
                viewModel.pushNewAccounts();
            } else {
                DialogManager.okDialog().setTitle("Ely.by")
                    .setContent("Не удалось войти в Ely.by: " + (error[0] == null ? "проверьте соединение." : error[0]))
                    .setType(DialogManager.ERROR).show();
            }
        });
        buttons.add(elyByButton);
        bottomPanel.add(buttons, gbc);

        rightPanel.add(bottomPanel, BorderLayout.CENTER);

        contextMenu = new JPopupMenu();

        changeSkin = new JMenuItem(GetText.tr("Change Skin"));
        changeSkin.addActionListener(e -> {
            viewModel.changeSkin();

            // TODO Have this done via listener
            // To describe, userSkin icon should be reactive, not active.
            AbstractAccount account = viewModel.getSelectedAccount();
            if (account != null) {
                userSkin.setIcon(account.getMinecraftSkin());
            }
        });
        contextMenu.add(changeSkin);

        updateSkin = new JMenuItem(GetText.tr("Reload Skin"));
        updateSkin.addActionListener(e -> {
            viewModel.updateSkin();

            // TODO Have this done via listener
            // To describe, userSkin icon should be reactive, not active.
            AbstractAccount account = viewModel.getSelectedAccount();
            if (account != null) {
                userSkin.setIcon(account.getMinecraftSkin());
            }
        });
        contextMenu.add(updateSkin);

        JMenuItem updateUsername = new JMenuItem(GetText.tr("Update Username"));
        updateUsername.addActionListener(e -> viewModel.updateUsername());
        contextMenu.add(updateUsername);

        refreshAccessTokenMenuItem = new JMenuItem(GetText.tr("Refresh Access Token"));
        refreshAccessTokenMenuItem.setVisible(false);
        refreshAccessTokenMenuItem.addActionListener(e -> refreshAccessToken());
        contextMenu.add(refreshAccessTokenMenuItem);

        userSkin = new JLabel(SkinUtils.getDefaultSkin());
        userSkin.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON3) {
                    if (accountsComboBox.getSelectedIndex() != 0) {
                        contextMenu.show(userSkin, e.getX(), e.getY());
                    }
                }
            }
        });
        userSkin.setBorder(
            BorderFactory.createEmptyBorder(0, 60, 0, 0));
        add(infoPanel, BorderLayout.NORTH);
        add(userSkin, BorderLayout.WEST);
        add(rightPanel, BorderLayout.CENTER);

        observe();

        accountsComboBox.setSelectedIndex(0);
    }

    /**
     * Refresh the access token, and react to result
     */
    private void refreshAccessToken() {
        AbstractAccount account = viewModel.getSelectedAccount();
        if (account == null) {
            return;
        }

        final ProgressDialog<Boolean> dialog = new ProgressDialog<>(
            GetText.tr("Refreshing Access Token For {0}", account.minecraftUsername),
            0,
            GetText.tr("Refreshing Access Token For {0}", account.minecraftUsername),
            "Aborting refreshing access token for " + account.minecraftUsername);

        dialog.addThread(new Thread(() -> {
            boolean success = viewModel.refreshAccessToken();
            dialog.setReturnValue(success);
            dialog.close();
        }));
        dialog.start();

        boolean success = Boolean.TRUE.equals(dialog.getReturnValue());

        if (success) {
            DialogManager
                .okDialog()
                .setTitle(GetText.tr("Access Token Refreshed"))
                .setContent(
                    GetText.tr("Access token refreshed successfully"))
                .setType(DialogManager.INFO)
                .show();
        } else {
            DialogManager
                .okDialog()
                .setTitle(GetText.tr("Failed To Refresh Access Token"))
                .setContent(GetText.tr("Failed to refresh accessToken. Please login again."))
                .setType(DialogManager.ERROR)
                .show();

            if (account instanceof com.atlauncher.data.MicrosoftAccount) {
                LoginWithMicrosoftDialog loginWithMicrosoftDialog = new LoginWithMicrosoftDialog((com.atlauncher.data.MicrosoftAccount) account);
                loginWithMicrosoftDialog.setVisible(true);
            }
        }
    }

    /**
     * Start observing state changes from view model
     */
    private void observe() {
        viewModel.onAccountSelected(account -> {
            if (account == null) {
                deleteButton.setVisible(false);
                userSkin.setIcon(SkinUtils.getDefaultSkin());
                loginWithMicrosoftButton.setVisible(true);
                refreshAccessTokenMenuItem.setVisible(false);
            } else {
                deleteButton.setVisible(true);
                loginWithMicrosoftButton.setVisible(true);
                refreshAccessTokenMenuItem.setVisible(!(account instanceof com.atlauncher.data.OfflineAccount));
                changeSkin.setVisible(account.supportsSkinUpload());

                deleteButton.setText(GetText.tr("Delete"));
                userSkin.setIcon(account.getMinecraftSkin());
            }
        });
        viewModel.onAccountsNamesChanged(accounts -> {
            accountsComboBox.removeAllItems();
            accountsComboBox.addItem(new ComboItem<>(null, GetText.tr("Add An Account")));
            for (String account : accounts) {
                accountsComboBox.addItem(new ComboItem<>(null, account));
            }
        });
    }

    @Override
    public String getTitle() {
        return GetText.tr("Accounts");
    }

    @Override
    public String getAnalyticsScreenViewName() {
        return "Accounts";
    }

    @Override
    protected void createViewModel() {
        viewModel = new AccountsViewModel();
    }

    @Override
    protected void onDestroy() {
        removeAll();
        userSkin = null;
        accountsComboBox = null;
        deleteButton = null;
        loginWithMicrosoftButton = null;
        offlineButton = null;
        elyByButton = null;
        refreshAccessTokenMenuItem = null;
        updateSkin = null;
        changeSkin = null;
        contextMenu = null;
    }
}
