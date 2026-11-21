; Inno Setup recipe for Moodlog on Windows. Built by .github/workflows/windows.yml:
;   ISCC /DVersion=2.12.0 /DSource=dist\Moodlog packaging\windows\moodlog.iss
; Installs for all users in Program Files (it needs to add the firewall rule for phone
; sync), adds a Start menu entry, and registers moodlog: so the reminder notification can
; open the app. Your data stays in %LOCALAPPDATA%\Moodlog, also when you uninstall.

#ifndef Version
  #define Version "0.0.0"
#endif
#ifndef Source
  #define Source "..\..\dist\Moodlog"
#endif

[Setup]
AppId={{6F0C7A52-3B8E-4C1D-9E57-0D6B3E5A2F41}
AppName=Moodlog
AppVersion={#Version}
AppVerName=Moodlog {#Version}
AppPublisher=Moodlog
AppPublisherURL=https://github.com/Akii-G/moodlog
AppSupportURL=https://github.com/Akii-G/moodlog/issues
DefaultDirName={autopf}\Moodlog
DisableProgramGroupPage=yes
DisableDirPage=auto
PrivilegesRequired=admin
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0.17763
OutputDir=..\..\dist
OutputBaseFilename=Moodlog-Setup-{#Version}
SetupIconFile=moodlog.ico
UninstallDisplayIcon={app}\Moodlog.exe
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
CloseApplications=force
RestartApplications=no
LicenseFile=..\..\LICENSE

[Languages]
Name: "en"; MessagesFile: "compiler:Default.isl"
Name: "it"; MessagesFile: "compiler:Languages\Italian.isl"

[CustomMessages]
en.SyncRule=Moodlog phone sync
it.SyncRule=Moodlog sincronizzazione telefono
en.Launch=Open Moodlog
it.Launch=Apri Moodlog
en.Desktop=Shortcut on the desktop
it.Desktop=Collegamento sul desktop

[Tasks]
Name: "desktopicon"; Description: "{cm:Desktop}"; Flags: unchecked

[Files]
Source: "{#Source}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
; AppUserModelID: Windows shows the reminder notifications under this entry
Name: "{autoprograms}\Moodlog"; Filename: "{app}\Moodlog.exe"; AppUserModelID: "Moodlog.Moodlog"
Name: "{autodesktop}\Moodlog"; Filename: "{app}\Moodlog.exe"; Tasks: desktopicon

[Registry]
; moodlog: links open Moodlog (clicking the reminder notification)
Root: HKA; Subkey: "Software\Classes\moodlog"; ValueType: string; ValueName: ""; ValueData: "URL:Moodlog"; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\Classes\moodlog"; ValueType: string; ValueName: "URL Protocol"; ValueData: ""
Root: HKA; Subkey: "Software\Classes\moodlog\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\Moodlog.exe,0"
Root: HKA; Subkey: "Software\Classes\moodlog\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\Moodlog.exe"" ""%1"""

[Run]
; phone sync: let the phones reach port 8765, on private (home) networks only
Filename: "{sys}\netsh.exe"; Parameters: "advfirewall firewall delete rule name=""{cm:SyncRule}"""; Flags: runhidden
Filename: "{sys}\netsh.exe"; Parameters: "advfirewall firewall add rule name=""{cm:SyncRule}"" dir=in action=allow protocol=TCP localport=8765 profile=private program=""{app}\Moodlog.exe"""; Flags: runhidden
; after an update: start the phone receiver again if it was on
Filename: "{app}\Moodlog.exe"; Parameters: "--resume-sync"; Flags: nowait runasoriginaluser
Filename: "{app}\Moodlog.exe"; Description: "{cm:Launch}"; Flags: nowait postinstall skipifsilent runasoriginaluser

[UninstallRun]
Filename: "{sys}\taskkill.exe"; Parameters: "/IM Moodlog.exe /F"; Flags: runhidden; RunOnceId: "StopMoodlog"
Filename: "{sys}\netsh.exe"; Parameters: "advfirewall firewall delete rule name=""{cm:SyncRule}"""; Flags: runhidden; RunOnceId: "DelSyncRule"
Filename: "{sys}\schtasks.exe"; Parameters: "/Delete /TN ""Moodlog reminder"" /F"; Flags: runhidden runasoriginaluser; RunOnceId: "DelReminder"
Filename: "{sys}\reg.exe"; Parameters: "delete HKCU\Software\Microsoft\Windows\CurrentVersion\Run /v ""Moodlog phone sync"" /f"; Flags: runhidden runasoriginaluser; RunOnceId: "DelAutostart"
