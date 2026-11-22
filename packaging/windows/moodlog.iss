; Inno Setup recipe for Sottovoce (formerly Moodlog) on Windows. Built by .github/workflows/windows.yml:
;   ISCC /DVersion=2.12.0 /DSource=dist\Moodlog packaging\windows\moodlog.iss
; Installs for all users in Program Files (it needs to add the firewall rule for phone
; sync), adds a Start menu entry, and registers moodlog: so the reminder notification can
; open the app. Your data stays in %LOCALAPPDATA%\Moodlog (the old name, kept so nothing
; moves), also when you uninstall. The AppId is Moodlog's, so it updates an installed Moodlog.

#ifndef Version
  #define Version "0.0.0"
#endif
#ifndef Source
  #define Source "..\..\dist\Sottovoce"
#endif

[Setup]
AppId={{6F0C7A52-3B8E-4C1D-9E57-0D6B3E5A2F41}
AppName=Sottovoce
AppVersion={#Version}
AppVerName=Sottovoce {#Version}
AppPublisher=Sottovoce
AppPublisherURL=https://github.com/Akii-G/sottovoce
AppSupportURL=https://github.com/Akii-G/sottovoce/issues
DefaultDirName={autopf}\Sottovoce
DisableProgramGroupPage=yes
DisableDirPage=auto
PrivilegesRequired=admin
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0.17763
OutputDir=..\..\dist
OutputBaseFilename=Sottovoce-Setup-{#Version}
SetupIconFile=moodlog.ico
UninstallDisplayIcon={app}\Sottovoce.exe
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
en.SyncRule=Sottovoce phone sync
it.SyncRule=Sottovoce sincronizzazione telefono
en.Launch=Open Sottovoce
it.Launch=Apri Sottovoce
en.Desktop=Shortcut on the desktop
it.Desktop=Collegamento sul desktop

[Tasks]
Name: "desktopicon"; Description: "{cm:Desktop}"; Flags: unchecked

[InstallDelete]
; left over from Moodlog 2.x, before the new name
Type: files; Name: "{app}\Moodlog.exe"
Type: files; Name: "{app}\Moodlog-console.exe"
Type: files; Name: "{autoprograms}\Moodlog.lnk"
Type: files; Name: "{autodesktop}\Moodlog.lnk"

[Files]
Source: "{#Source}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
; AppUserModelID: Windows shows the reminder notifications under this entry
Name: "{autoprograms}\Sottovoce"; Filename: "{app}\Sottovoce.exe"; AppUserModelID: "Moodlog.Moodlog"
Name: "{autodesktop}\Sottovoce"; Filename: "{app}\Sottovoce.exe"; Tasks: desktopicon

[Registry]
; moodlog: links open Sottovoce (clicking the reminder notification)
Root: HKA; Subkey: "Software\Classes\moodlog"; ValueType: string; ValueName: ""; ValueData: "URL:Sottovoce"; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\Classes\moodlog"; ValueType: string; ValueName: "URL Protocol"; ValueData: ""
Root: HKA; Subkey: "Software\Classes\moodlog\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\Sottovoce.exe,0"
Root: HKA; Subkey: "Software\Classes\moodlog\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\Sottovoce.exe"" ""%1"""

[Run]
; phone sync: let the phones reach port 8765, on private (home) networks only
Filename: "{sys}\netsh.exe"; Parameters: "advfirewall firewall delete rule name=""{cm:SyncRule}"""; Flags: runhidden
Filename: "{sys}\netsh.exe"; Parameters: "advfirewall firewall delete rule name=""Moodlog phone sync"""; Flags: runhidden
Filename: "{sys}\netsh.exe"; Parameters: "advfirewall firewall delete rule name=""Moodlog sincronizzazione telefono"""; Flags: runhidden
Filename: "{sys}\netsh.exe"; Parameters: "advfirewall firewall add rule name=""{cm:SyncRule}"" dir=in action=allow protocol=TCP localport=8765 profile=private program=""{app}\Sottovoce.exe"""; Flags: runhidden
; after an update: start the phone receiver again if it was on
Filename: "{app}\Sottovoce.exe"; Parameters: "--resume-sync"; Flags: nowait runasoriginaluser
Filename: "{app}\Sottovoce.exe"; Description: "{cm:Launch}"; Flags: nowait postinstall skipifsilent runasoriginaluser

[UninstallRun]
Filename: "{sys}\taskkill.exe"; Parameters: "/IM Sottovoce.exe /F"; Flags: runhidden; RunOnceId: "StopMoodlog"
Filename: "{sys}\netsh.exe"; Parameters: "advfirewall firewall delete rule name=""{cm:SyncRule}"""; Flags: runhidden; RunOnceId: "DelSyncRule"
Filename: "{sys}\schtasks.exe"; Parameters: "/Delete /TN ""Moodlog reminder"" /F"; Flags: runhidden; RunOnceId: "DelReminder"
Filename: "{sys}\reg.exe"; Parameters: "delete HKCU\Software\Microsoft\Windows\CurrentVersion\Run /v ""Moodlog phone sync"" /f"; Flags: runhidden; RunOnceId: "DelAutostart"

[Code]
// Moodlog 2.x ran as Moodlog.exe (the app and the phone receiver): close it before the update,
// so its files can be removed. --resume-sync starts the receiver again afterwards.
function PrepareToInstall(var NeedsRestart: Boolean): String;
var
  Code: Integer;
begin
  Exec(ExpandConstant('{sys}\taskkill.exe'), '/IM Moodlog.exe /F', '', SW_HIDE, ewWaitUntilTerminated, Code);
  Exec(ExpandConstant('{sys}\taskkill.exe'), '/IM Sottovoce.exe /F', '', SW_HIDE, ewWaitUntilTerminated, Code);
  Result := '';
end;
