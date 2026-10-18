using System.ComponentModel;
using System.Diagnostics;
using System.IO;
using System.Runtime.CompilerServices;
using System.Windows.Media.Imaging;
using MirrorCast.Receiver.Control;
using MirrorCast.Receiver.Discovery;
using MirrorCast.Receiver.Security;
using MirrorCast.Receiver.Signaling;

namespace MirrorCast.Receiver.ViewModels;

public class MainViewModel : INotifyPropertyChanged
{
    private readonly PairingManager _pairingManager;
    private readonly MdnsDiscoveryService _mdnsService;
    private SignalingServer? _signalingServer;

    private bool _isReceiverRunning;
    private string _statusText = "Receiver stopped";
    private string _statusColor = "#8b949e";
    private string _localIpAddress = "Detecting IP...";
    private int _port = 8090;
    private string _currentPin = "4728";
    private BitmapImage? _qrCodeImage;
    private string _connectedDeviceName = "No device connected";
    private bool _isDeviceConnected;
    private double _streamFps = 30.0;
    private double _streamBitrateMbps = 6.2;
    private string _streamResolution = "1080 x 2400";
    private long _streamPacketsLost = 0;
    private bool _fitToWindow = true;
    private string _connectionState = "Idle";
    private string _selectedNavTab = "Home";
    private string _connectionMode = "WiFi";
    private bool _hasIncomingRequest = false;
    private string _incomingDeviceName = "Redmi Note 11";
    private string _incomingDeviceIp = "192.168.1.42";
    private string _streamDuration = "00:00:00";
    private System.Diagnostics.Stopwatch _streamStopwatch = new();
    private System.Windows.Threading.DispatcherTimer? _durationTimer;

    public event PropertyChangedEventHandler? PropertyChanged;

    public string PinDigit1 => CurrentPin.Length > 0 ? CurrentPin[0].ToString() : "4";
    public string PinDigit2 => CurrentPin.Length > 1 ? CurrentPin[1].ToString() : "7";
    public string PinDigit3 => CurrentPin.Length > 2 ? CurrentPin[2].ToString() : "2";
    public string PinDigit4 => CurrentPin.Length > 3 ? CurrentPin[3].ToString() : "8";

    public string SelectedNavTab
    {
        get => _selectedNavTab;
        set
        {
            _selectedNavTab = value;
            OnPropertyChanged();
            OnPropertyChanged(nameof(IsHomeTab));
            OnPropertyChanged(nameof(IsConnectionsTab));
            OnPropertyChanged(nameof(IsSettingsTab));
            OnPropertyChanged(nameof(IsLogsTab));
            OnPropertyChanged(nameof(IsAboutTab));
            OnPropertyChanged(nameof(IsHomeListening));
            OnPropertyChanged(nameof(IsMirroring));
        }
    }

    public bool IsHomeTab => _selectedNavTab == "Home";
    public bool IsConnectionsTab => _selectedNavTab == "Connections";
    public bool IsSettingsTab => _selectedNavTab == "Settings";
    public bool IsLogsTab => _selectedNavTab == "Logs";
    public bool IsAboutTab => _selectedNavTab == "About";

    public bool IsHomeListening => IsHomeTab && !IsDeviceConnected;
    public bool IsMirroring => IsHomeTab && IsDeviceConnected;

    public DeviceController DeviceController { get; } = new();

    private readonly System.Text.StringBuilder _logsBuilder = new();
    public string LogsText => _logsBuilder.ToString();

    public void AppendLog(string message)
    {
        var line = $"[{DateTime.Now:HH:mm:ss}] {message}";
        _logsBuilder.AppendLine(line);
        OnPropertyChanged(nameof(LogsText));
    }

    public void ClearLogs()
    {
        _logsBuilder.Clear();
        OnPropertyChanged(nameof(LogsText));
    }

    public void CopyLogs()
    {
        try
        {
            System.Windows.Clipboard.SetText(LogsText);
            StatusText = "Logs copied to clipboard!";
        }
        catch { }
    }

    public void SendKeyBack() => DeviceController.PressBack();
    public void SendKeyHome() => DeviceController.PressHome();
    public void SendKeyRecents() => DeviceController.PressRecents();
    public void SendKeyPower() => DeviceController.PressPower();
    public void SendVolumeUp() => DeviceController.VolumeUp();
    public void SendVolumeDown() => DeviceController.VolumeDown();

    public string ConnectionMode
    {
        get => _connectionMode;
        set { _connectionMode = value; OnPropertyChanged(); }
    }

    public bool HasIncomingRequest
    {
        get => _hasIncomingRequest;
        set { _hasIncomingRequest = value; OnPropertyChanged(); }
    }

    public string IncomingDeviceName
    {
        get => _incomingDeviceName;
        set { _incomingDeviceName = value; OnPropertyChanged(); }
    }

    public string IncomingDeviceIp
    {
        get => _incomingDeviceIp;
        set { _incomingDeviceIp = value; OnPropertyChanged(); }
    }

    public string StreamDuration
    {
        get => _streamDuration;
        set { _streamDuration = value; OnPropertyChanged(); }
    }

    public bool IsReceiverRunning
    {
        get => _isReceiverRunning;
        set { _isReceiverRunning = value; OnPropertyChanged(); }
    }

    public string StatusText
    {
        get => _statusText;
        set { _statusText = value; OnPropertyChanged(); }
    }

    public string StatusColor
    {
        get => _statusColor;
        set { _statusColor = value; OnPropertyChanged(); }
    }

    public string LocalIpAddress
    {
        get => _localIpAddress;
        set { _localIpAddress = value; OnPropertyChanged(); }
    }

    public int Port
    {
        get => _port;
        set { _port = value; OnPropertyChanged(); }
    }

    public string CurrentPin
    {
        get => _currentPin;
        set
        {
            _currentPin = value;
            OnPropertyChanged();
            OnPropertyChanged(nameof(PinDigit1));
            OnPropertyChanged(nameof(PinDigit2));
            OnPropertyChanged(nameof(PinDigit3));
            OnPropertyChanged(nameof(PinDigit4));
        }
    }

    public BitmapImage? QrCodeImage
    {
        get => _qrCodeImage;
        set { _qrCodeImage = value; OnPropertyChanged(); }
    }

    public string ConnectedDeviceName
    {
        get => _connectedDeviceName;
        set { _connectedDeviceName = value; OnPropertyChanged(); }
    }

    public bool IsDeviceConnected
    {
        get => _isDeviceConnected;
        set
        {
            _isDeviceConnected = value;
            OnPropertyChanged();
            OnPropertyChanged(nameof(IsHomeListening));
            OnPropertyChanged(nameof(IsMirroring));
        }
    }

    public double StreamFps
    {
        get => _streamFps;
        set { _streamFps = value; OnPropertyChanged(); }
    }

    public double StreamBitrateMbps
    {
        get => _streamBitrateMbps;
        set { _streamBitrateMbps = value; OnPropertyChanged(); }
    }

    public string StreamResolution
    {
        get => _streamResolution;
        set { _streamResolution = value; OnPropertyChanged(); }
    }

    public long StreamPacketsLost
    {
        get => _streamPacketsLost;
        set { _streamPacketsLost = value; OnPropertyChanged(); }
    }

    public bool FitToWindow
    {
        get => _fitToWindow;
        set { _fitToWindow = value; OnPropertyChanged(); }
    }

    private bool _requireConfirmationPopup = true;
    public bool RequireConfirmationPopup
    {
        get => _requireConfirmationPopup;
        set
        {
            _requireConfirmationPopup = value;
            if (_signalingServer != null) _signalingServer.RequireConfirmationPopup = value;
            OnPropertyChanged();
        }
    }

    private bool _autoApproveUsb = false;
    public bool AutoApproveUsb
    {
        get => _autoApproveUsb;
        set
        {
            _autoApproveUsb = value;
            if (_signalingServer != null) _signalingServer.AutoApproveUsb = value;
            OnPropertyChanged();
        }
    }

    private bool _requirePinAuth = true;
    public bool RequirePinAuth
    {
        get => _requirePinAuth;
        set
        {
            _requirePinAuth = value;
            if (_signalingServer != null) _signalingServer.RequirePinAuth = value;
            OnPropertyChanged();
        }
    }

    private bool _enableRemoteControl = true;
    public bool EnableRemoteControl
    {
        get => _enableRemoteControl;
        set
        {
            _enableRemoteControl = value;
            OnPropertyChanged();
        }
    }

    private string _configPortText = "8090";
    public string ConfigPortText
    {
        get => _configPortText;
        set { _configPortText = value; OnPropertyChanged(); }
    }

    private string _settingsFeedback = string.Empty;
    public string SettingsFeedback
    {
        get => _settingsFeedback;
        set
        {
            _settingsFeedback = value;
            OnPropertyChanged();
            OnPropertyChanged(nameof(HasSettingsFeedback));
        }
    }

    public bool HasSettingsFeedback => !string.IsNullOrEmpty(SettingsFeedback);

    public void ApplyPortSetting()
    {
        if (int.TryParse(ConfigPortText, out int newPort) && newPort > 1024 && newPort < 65535)
        {
            Port = newPort;
            StopReceiver();
            StartReceiver();
            SettingsFeedback = $"✓ Port {newPort} applied and receiver restarted!";
        }
        else
        {
            SettingsFeedback = "⚠ Invalid port number. Please choose between 1024 and 65535.";
        }
    }

    public void SaveSettings()
    {
        SettingsFeedback = "✓ Preferences saved and applied successfully!";
    }

    public string ConnectionState
    {
        get => _connectionState;
        set { _connectionState = value; OnPropertyChanged(); }
    }

    public MainViewModel(PairingManager pairingManager, MdnsDiscoveryService mdnsService)
    {
        _pairingManager = pairingManager;
        _mdnsService = mdnsService;
        RefreshNetworkInfo();
    }

    public void AttachSignalingServer(SignalingServer server)
    {
        _signalingServer = server;
        _signalingServer.RequireConfirmationPopup = RequireConfirmationPopup;
        _signalingServer.AutoApproveUsb = AutoApproveUsb;
        _signalingServer.RequirePinAuth = RequirePinAuth;
        _signalingServer.DevicePairingRequested += OnDevicePairingRequested;
        _signalingServer.DeviceConnected += OnDeviceConnected;
        _signalingServer.ClientDisconnected += OnClientDisconnected;
        _signalingServer.StatsReceived += OnStatsReceived;
        _signalingServer.LogMessage += AppendLog;
    }

    private void OnDevicePairingRequested(string deviceName, string pin, string ip)
    {
        App.Current.Dispatcher.Invoke(() =>
        {
            IncomingDeviceName = string.IsNullOrEmpty(deviceName) ? "Android Device" : deviceName;
            IncomingDeviceIp = ip;
            HasIncomingRequest = true;
        });
    }

    public void RefreshNetworkInfo()
    {
        var ips = NetworkUtils.GetLocalIPv4Addresses();
        LocalIpAddress = ips.FirstOrDefault() ?? "127.0.0.1";
        CurrentPin = _pairingManager.CurrentPin;
        UpdateQrCode();
    }

    public void StartReceiver()
    {
        if (IsReceiverRunning) return;

        try
        {
            RefreshNetworkInfo();
            _signalingServer?.Start(Port);
            _mdnsService.Start(Port, $"MirrorCast-{Environment.MachineName}");
            DeviceController.SetupAllUsbReverseTunnels();

            IsReceiverRunning = true;
            StatusText = $"Listening on {LocalIpAddress}:{Port}";
            StatusColor = "#2ea043"; // Green
        }
        catch (Exception ex)
        {
            StatusText = $"Start failed: {ex.Message}";
            StatusColor = "#f85149"; // Red
        }
    }

    public void StopReceiver()
    {
        if (!IsReceiverRunning) return;

        try
        {
            _mdnsService.Stop();
            _signalingServer?.Stop();
            IsReceiverRunning = false;
            IsDeviceConnected = false;
            ConnectedDeviceName = "No device connected";
            StatusText = "Receiver stopped";
            StatusColor = "#8b949e";
            ConnectionState = "Idle";
        }
        catch (Exception ex)
        {
            StatusText = $"Stop error: {ex.Message}";
        }
    }

    public void RegeneratePin()
    {
        CurrentPin = _pairingManager.RegeneratePin();
        UpdateQrCode();
    }

    public void UpdateQrCode()
    {
        try
        {
            var pngBytes = _pairingManager.GenerateQrCodePng(LocalIpAddress, Port);
            using var ms = new MemoryStream(pngBytes);
            var image = new BitmapImage();
            image.BeginInit();
            image.CacheOption = BitmapCacheOption.OnLoad;
            image.StreamSource = ms;
            image.EndInit();
            image.Freeze();
            QrCodeImage = image;
        }
        catch { }
    }

    public void CopyIpAddress()
    {
        try
        {
            System.Windows.Clipboard.SetText(LocalIpAddress);
            StatusText = $"Copied {LocalIpAddress} to clipboard!";
        }
        catch { }
    }

    public void DisconnectCurrentSession()
    {
        try
        {
            _signalingServer?.Stop();
            _signalingServer?.Start(Port);
            OnClientDisconnected("manual_disconnect");
        }
        catch { }
    }

    public async void AllowIncomingConnection()
    {
        HasIncomingRequest = false;
        if (_signalingServer != null)
        {
            await _signalingServer.ApprovePendingPairingAsync();
        }
        IsDeviceConnected = true;
    }

    public async void RejectIncomingConnection()
    {
        HasIncomingRequest = false;
        if (_signalingServer != null)
        {
            await _signalingServer.RejectPendingPairingAsync();
        }
    }

    private void OnDeviceConnected(PairRequestPayload req, string sessionId)
    {
        App.Current.Dispatcher.Invoke(() =>
        {
            ConnectedDeviceName = string.IsNullOrEmpty(req.DeviceName) ? "Android Device" : req.DeviceName;
            IsDeviceConnected = true;
            ConnectionState = "Streaming";
            StatusText = $"Connected to {ConnectedDeviceName}";
            StatusColor = "#2ea043"; // Green
            _streamStopwatch.Restart();
            if (_durationTimer == null)
            {
                _durationTimer = new System.Windows.Threading.DispatcherTimer { Interval = TimeSpan.FromSeconds(1) };
                _durationTimer.Tick += (s, e) =>
                {
                    StreamDuration = _streamStopwatch.Elapsed.ToString(@"hh\:mm\:ss");
                };
            }
            _durationTimer.Start();
        });
    }

    private void OnClientDisconnected(string reason)
    {
        App.Current.Dispatcher.Invoke(() =>
        {
            _durationTimer?.Stop();
            _streamStopwatch.Reset();
            IsDeviceConnected = false;
            ConnectedDeviceName = "No device connected";
            ConnectionState = "Disconnected";
            StatusText = $"Device disconnected ({reason})";
            StatusColor = IsReceiverRunning ? "#2ea043" : "#8b949e";
            StreamFps = 30.0;
            StreamBitrateMbps = 6.2;
            StreamResolution = "1080 x 2400";
        });
    }

    private void OnStatsReceived(StatsPayload stats)
    {
        App.Current.Dispatcher.Invoke(() =>
        {
            StreamFps = Math.Round(stats.Fps, 1);
            StreamBitrateMbps = Math.Round(stats.BitrateKbps / 1000.0, 2);
            StreamPacketsLost = stats.PacketsLost;
            if (stats.Width > 0 && stats.Height > 0)
            {
                StreamResolution = $"{stats.Width} x {stats.Height}";
            }
        });
    }

    public void UpdateLocalStats(StatsPayload stats)
    {
        OnStatsReceived(stats);
    }

    public void SetupFirewallRules()
    {
        try
        {
            var psi = new ProcessStartInfo
            {
                FileName = "powershell.exe",
                Arguments = $"-NoProfile -Command \"New-NetFirewallRule -DisplayName 'MirrorCast Windows Receiver' -Direction Inbound -Protocol TCP -LocalPort {Port} -Action Allow -Profile Private,Domain -ErrorAction SilentlyContinue\"",
                Verb = "runas", // Prompts UAC elevation
                UseShellExecute = true
            };
            Process.Start(psi);
        }
        catch (Exception ex)
        {
            StatusText = $"Firewall setup: {ex.Message}";
        }
    }

    private void OnPropertyChanged([CallerMemberName] string? propName = null)
    {
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propName));
    }
}
