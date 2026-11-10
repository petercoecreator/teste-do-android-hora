[Setup]
AppId={{B26A4B42-2D35-4B52-8CB7-1E2C54E6A019}
AppName=Relógio Remoto
AppVersion=2.1.0
AppPublisher=Relógio Remoto
DefaultDirName={autopf}\Relógio Remoto
DefaultGroupName=Relógio Remoto
OutputDir=Output
OutputBaseFilename=RelogioRemoto-Setup
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=admin
ArchitecturesInstallIn64BitMode=x64
UninstallDisplayIcon={app}\RelogioRemoto.exe

[Tasks]
Name: "autostart"; Description: "Iniciar o Relógio Remoto automaticamente quando eu entrar no Windows"; GroupDescription: "Inicialização:"; Flags: checkedonce
Name: "desktopicon"; Description: "Criar atalho na área de trabalho"; GroupDescription: "Atalhos:"; Flags: unchecked

[Files]
Source: "..\dist\RelogioRemoto.exe"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\Relógio Remoto"; Filename: "{app}\RelogioRemoto.exe"
Name: "{autodesktop}\Relógio Remoto"; Filename: "{app}\RelogioRemoto.exe"; Tasks: desktopicon

[Run]
Filename: "{sys}\schtasks.exe"; Parameters: "/Create /TN ""Relogio Remoto"" /SC ONLOGON /TR """"""{app}\RelogioRemoto.exe"""" --background"" /RL HIGHEST /F"; Flags: runhidden; Tasks: autostart
