#define MyAppName "ETLauncher"
#define MyAppVersion "3.4.41.4"
#define MyAppURL "https://github.com/ETLauncher/launcher"

[Setup]
AppId={{F5CFE749-A982-42BA-ABF9-6B3B6091A789}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} {#MyAppVersion}
AppPublisher=ETLauncher
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}/issues
AppUpdatesURL={#MyAppURL}/releases
DefaultDirName={localappdata}\Programs\{#MyAppName}
DefaultGroupName={#MyAppName}
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
LicenseFile=..\..\LICENSE
SetupIconFile=..\..\src\main\resources\assets\image\icon.ico
OutputDir=..\..\dist
OutputBaseFilename=ETLauncher-setup-{#MyAppVersion}
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
UninstallDisplayIcon={app}\ETLauncher.exe
CloseApplications=yes

[Languages]
Name: "russian"; MessagesFile: "compiler:Languages\Russian.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: checkedonce

[Files]
Source: "..\..\dist\ETLauncher.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\..\dist\jre\*"; DestDir: "{app}\jre"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\ETLauncher"; Filename: "{app}\ETLauncher.exe"
Name: "{autodesktop}\ETLauncher"; Filename: "{app}\ETLauncher.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\ETLauncher.exe"; Description: "{cm:LaunchProgram,ETLauncher}"; Flags: nowait postinstall skipifsilent
