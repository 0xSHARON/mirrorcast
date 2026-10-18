<p align="center">
  <img src="../assets/banner.png" alt="MirrorCast Windows Banner" width="100%">
</p>

<p align="center">
  <img src="../assets/logo.png" alt="MirrorCast Logo" width="100">
</p>

<h1 align="center">MirrorCast — Windows Receiver Module</h1>

<p align="center">
  <strong>Desktop Application for Windows 10 & 11 (.NET 9 / WPF)</strong><br>
  <em>Direct3D11 / DXVA Hardware Video Decoding & Embedded WebSocket Signaling</em>
</p>

<p align="center">
  <a href="https://github.com/0xSHARON/mirrorcast"><img src="https://img.shields.io/badge/GitHub-0xSHARON%2Fmirrorcast-blue?logo=github" alt="Repository"></a>
  <a href="https://github.com/0xSHARON"><img src="https://img.shields.io/badge/Author-0xSHARON-blueviolet" alt="Author"></a>
</p>

---

## Technical Specifications

- **Target Framework**: .NET 9.0 (`net9.0-windows`)
- **UI Framework**: Windows Presentation Foundation (WPF) with modern dark Fluent styling
- **Hardware Acceleration**: Direct3D11 / DXVA video decoding via Microsoft WebView2
- **Discovery**: mDNS / DNS-SD via `Makaretu.Dns.Multicast` (`_mirrorcast._tcp`)
- **Signaling Server**: Asynchronous HTTP/WebSocket server listening on port `8089`
- **Security**: Cryptographic 6-digit random PIN generator (`RandomNumberGenerator.GetInt32`) and QR code generator (`QRCoder`)

---

## Directory Structure

```
windows/
├── MirrorCast.Receiver.sln          # Visual Studio / .NET 9 Solution
├── MirrorCast.Receiver/
│   ├── MirrorCast.Receiver.csproj   # WPF project configuration
│   ├── MainWindow.xaml              # Modern dark Fluent UI layout
│   ├── MainWindow.xaml.cs           # Window state and controller wiring
│   ├── ViewModels/                  # MainViewModel with telemetry & commands
│   ├── Discovery/                   # mDNS responder & network adapter detection
│   ├── Security/                    # 6-digit PIN generator & session manager
│   ├── Signaling/                   # HTTP/WebSocket server & protocol contracts
│   └── WebRtc/                      # WebView2 WebRTC host controller & renderer HTML
└── MirrorCast.Receiver.Tests/       # xUnit test project (10 automated unit tests)
```

---

## Building & Running

### Command Line (PowerShell)
To build and execute all automated unit tests:
```powershell
.\scripts\build-windows.ps1 -Configuration Release
```

To run the compiled desktop application directly:
```powershell
.\windows\MirrorCast.Receiver\bin\Release\net9.0-windows\MirrorCast.Receiver.exe
```

### Visual Studio 2022
1. Open `windows\MirrorCast.Receiver.sln` in Visual Studio 2022 (version 17.12 or newer with .NET 9 SDK).
2. Set `MirrorCast.Receiver` as the StartUp Project.
3. Select configuration `Release` or `Debug` and press **F5**.

---

## Inbound Firewall Setup

On first launch, Windows Defender Firewall will prompt to allow access on private networks. You can also run the following PowerShell command as Administrator:

```powershell
New-NetFirewallRule -DisplayName "MirrorCast Windows Receiver" `
  -Direction Inbound `
  -Protocol TCP `
  -LocalPort 8089 `
  -Action Allow `
  -Profile Private,Domain
```
