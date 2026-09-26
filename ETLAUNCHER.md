# ETLauncher

Fork of ATLauncher 3.4.41.4 Beta. The original GPLv3 license and upstream attribution are retained.

## Changes

- ETLauncher name, supplied icon, orange interface accents and splash screen.
- Offline accounts with the standard Minecraft `OfflinePlayer:<name>` UUID.
- Ely.by sign in through its Minecraft authentication API, including optional 2FA. Only the access token and client token are saved; the password is not stored. Ely.by skin and session support uses authlib-injector.
- Russian selected by default and a bundled Russian translation. Some uncommon messages may still appear in English; automated translations should be reviewed by a native speaker.
- Upstream launcher binary self-updates are disabled so the fork is not replaced.

## Ely.by sign in

Click **Войти через Ely.by** and enter your Ely.by username or e-mail and password. If two-factor authentication is enabled, enter the current code too. ETLauncher sends these credentials directly to `https://authserver.ely.by/auth/authenticate` over HTTPS and does not save the password. Later sessions use the token refresh endpoint.

The public OAuth client ID `et` remains in the source. As of 26 September 2026, Ely.by accepts its PKCE authorization request at the validation API, but the Ely.by browser application omits the PKCE parameters when it calls that API. The browser then shows `Invalid request (null required)`. The launcher uses Ely.by's documented Minecraft authentication API until their browser flow is fixed.

The first Ely.by game launch downloads authlib-injector from its official distribution endpoint and verifies the SHA-256 value published there. Internet access is required for that first download and for Ely.by sign in. Offline accounts can play single player or join servers that allow offline accounts; they do not provide Microsoft or Ely.by authentication.

## Build

With JDK 17 installed:

```powershell
.\gradlew.bat test shadowJar createExe
```

The runnable JAR appears under `build/libs`, and the Windows launcher under `build/launch4j`. The Windows EXE requires a Java runtime on the user's machine. Run `java -jar ETLauncher-3.4.41.4.jar` to use the JAR. Both support `--version`.
