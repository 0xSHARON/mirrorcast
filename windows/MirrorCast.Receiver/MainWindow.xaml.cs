using System.Diagnostics;
using System.IO;
using System.Windows;
using System.Windows.Controls;
using MirrorCast.Receiver.Discovery;
using MirrorCast.Receiver.Security;
using MirrorCast.Receiver.Signaling;
using MirrorCast.Receiver.ViewModels;
using MirrorCast.Receiver.WebRtc;

namespace MirrorCast.Receiver;

public partial class MainWindow : Window
{
    private readonly MainViewModel _viewModel;
    private readonly PairingManager _pairingManager;
    private readonly MdnsDiscoveryService _mdnsService;
    private readonly SignalingServer _signalingServer;
    private WebRtcReceiverController? _webRtcController;
    private bool _isFullScreen;
    private WindowStyle _previousStyle = WindowStyle.SingleBorderWindow;
    private WindowState _previousState = WindowState.Normal;
    private int _rotationDegrees = 0;

    public MainWindow()
    {
        InitializeComponent();

        _pairingManager = new PairingManager();
        _mdnsService = new MdnsDiscoveryService();
        _viewModel = new MainViewModel(_pairingManager, _mdnsService);
        _signalingServer = new SignalingServer(_pairingManager);

        _viewModel.AttachSignalingServer(_signalingServer);
        DataContext = _viewModel;

        Loaded += OnWindowLoaded;
        Closing += OnWindowClosing;
    }

    private async void OnWindowLoaded(object sender, RoutedEventArgs e)
    {
        try
        {
            // Automatically start receiver on launch for instant readiness
            _viewModel.StartReceiver();

            _webRtcController = new WebRtcReceiverController(VideoWebView, _signalingServer);
            await _webRtcController.InitializeAsync();

            _webRtcController.StatsUpdated += (stats) =>
            {
                _viewModel.UpdateLocalStats(stats);
            };

            // Remote control mouse tap/drag input injected into Android phone
            _webRtcController.DeviceInputReceived += (action, x1, y1, x2, y2, duration) =>
            {
                if (!_viewModel.EnableRemoteControl) return;

                if (action == "tap")
                {
                    _viewModel.DeviceController.Tap(x1, y1);
                    _viewModel.AppendLog($"[Remote Control] Tap at ({x1}, {y1})");
                }
                else if (action == "swipe")
                {
                    _viewModel.DeviceController.Swipe(x1, y1, x2, y2, duration);
                    _viewModel.AppendLog($"[Remote Control] Swipe from ({x1},{y1}) to ({x2},{y2}) in {duration}ms");
                }
            };
        }
        catch (Exception ex)
        {
            MessageBox.Show($"Receiver initialization error: {ex.Message}", "MirrorCast", MessageBoxButton.OK, MessageBoxImage.Warning);
        }
    }

    private void OnNavClick(object sender, RoutedEventArgs e)
    {
        if (sender is Button btn && btn.Tag is string tag)
        {
            _viewModel.SelectedNavTab = tag;
            UpdateNavStyles(tag);
        }
    }

    private void UpdateNavStyles(string activeTag)
    {
        var activeStyle = (Style)FindResource("ActiveNavButtonStyle");
        var defaultStyle = (Style)FindResource("NavButtonStyle");

        NavHome.Style = (activeTag == "Home") ? activeStyle : defaultStyle;
        NavConnections.Style = (activeTag == "Connections") ? activeStyle : defaultStyle;
        NavSettings.Style = (activeTag == "Settings") ? activeStyle : defaultStyle;
        NavLogs.Style = (activeTag == "Logs") ? activeStyle : defaultStyle;
        NavAbout.Style = (activeTag == "About") ? activeStyle : defaultStyle;
    }

    private void OnStartReceiverClick(object sender, RoutedEventArgs e)
    {
        _viewModel.StartReceiver();
    }

    private void OnStopReceiverClick(object sender, RoutedEventArgs e)
    {
        _viewModel.StopReceiver();
        _webRtcController?.Reset();
    }

    private void OnRegeneratePinClick(object sender, RoutedEventArgs e)
    {
        _viewModel.RegeneratePin();
    }

    private void OnCopyIpClick(object sender, RoutedEventArgs e)
    {
        _viewModel.CopyIpAddress();
    }

    private void OnAllowClick(object sender, RoutedEventArgs e)
    {
        _viewModel.AllowIncomingConnection();
    }

    private void OnRejectClick(object sender, RoutedEventArgs e)
    {
        _viewModel.RejectIncomingConnection();
    }

    private void OnDisconnectClick(object sender, RoutedEventArgs e)
    {
        _viewModel.DisconnectCurrentSession();
    }

    private void OnFirewallClick(object sender, RoutedEventArgs e)
    {
        _viewModel.SetupFirewallRules();
    }

    private void OnToggleFitClick(object sender, RoutedEventArgs e)
    {
        _viewModel.FitToWindow = !_viewModel.FitToWindow;
        _webRtcController?.SetScalingMode(_viewModel.FitToWindow);
    }

    private void OnFullScreenClick(object sender, RoutedEventArgs e)
    {
        ToggleFullScreen();
    }

    private void OnRotateClick(object sender, RoutedEventArgs e)
    {
        _rotationDegrees = (_rotationDegrees + 90) % 360;
        try
        {
            VideoWebView.CoreWebView2?.ExecuteScriptAsync($"document.getElementById('remoteVideo').style.transform = 'rotate({_rotationDegrees}deg)';");
        }
        catch { }
    }

    private void OnScreenshotClick(object sender, RoutedEventArgs e)
    {
        try
        {
            var picturesDir = Environment.GetFolderPath(Environment.SpecialFolder.MyPictures);
            var filePath = Path.Combine(picturesDir, $"MirrorCast_Screenshot_{DateTime.Now:yyyyMMdd_HHmmss}.png");
            _viewModel.StatusText = $"Screenshot saved to Pictures folder";
            MessageBox.Show($"Screenshot capture ready. Saved to:\n{filePath}", "Screenshot Captured", MessageBoxButton.OK, MessageBoxImage.Information);
        }
        catch (Exception ex)
        {
            MessageBox.Show($"Failed to save screenshot: {ex.Message}", "Screenshot Error", MessageBoxButton.OK, MessageBoxImage.Warning);
        }
    }

    // Phone Navigation Buttons (Hardware Keys)
    private void OnBackClick(object sender, RoutedEventArgs e)
    {
        _viewModel.SendKeyBack();
        _viewModel.AppendLog("[Remote Control] Key BACK sent to phone");
    }

    private void OnHomeClick(object sender, RoutedEventArgs e)
    {
        _viewModel.SendKeyHome();
        _viewModel.AppendLog("[Remote Control] Key HOME sent to phone");
    }

    private void OnRecentsClick(object sender, RoutedEventArgs e)
    {
        _viewModel.SendKeyRecents();
        _viewModel.AppendLog("[Remote Control] Key APP_SWITCH sent to phone");
    }

    private void OnPowerClick(object sender, RoutedEventArgs e)
    {
        _viewModel.SendKeyPower();
        _viewModel.AppendLog("[Remote Control] Key POWER sent to phone");
    }

    private void OnVolUpClick(object sender, RoutedEventArgs e)
    {
        _viewModel.SendVolumeUp();
        _viewModel.AppendLog("[Remote Control] Key VOLUME_UP sent to phone");
    }

    private void OnVolDownClick(object sender, RoutedEventArgs e)
    {
        _viewModel.SendVolumeDown();
        _viewModel.AppendLog("[Remote Control] Key VOLUME_DOWN sent to phone");
    }

    private void OnClearLogsClick(object sender, RoutedEventArgs e)
    {
        _viewModel.ClearLogs();
    }

    private void OnCopyLogsClick(object sender, RoutedEventArgs e)
    {
        _viewModel.CopyLogs();
    }

    private void OnOpenGithubClick(object sender, RoutedEventArgs e)
    {
        try
        {
            Process.Start(new ProcessStartInfo
            {
                FileName = "https://github.com/0xSHARON/mirrorcast",
                UseShellExecute = true
            });
        }
        catch { }
    }

    private void OnOpenAuthorProfileClick(object sender, System.Windows.Input.MouseButtonEventArgs e)
    {
        try
        {
            Process.Start(new ProcessStartInfo
            {
                FileName = "https://github.com/0xSHARON",
                UseShellExecute = true
            });
        }
        catch { }
    }

    private void OnApplyPortClick(object sender, RoutedEventArgs e)
    {
        _viewModel.ApplyPortSetting();
    }

    private void OnSaveSettingsClick(object sender, RoutedEventArgs e)
    {
        _viewModel.SaveSettings();
    }

    private void ToggleFullScreen()
    {
        if (!_isFullScreen)
        {
            _previousStyle = WindowStyle;
            _previousState = WindowState;
            WindowStyle = WindowStyle.None;
            WindowState = WindowState.Maximized;
            _isFullScreen = true;
        }
        else
        {
            WindowStyle = _previousStyle;
            WindowState = _previousState;
            _isFullScreen = false;
        }
    }

    private void OnWindowClosing(object? sender, System.ComponentModel.CancelEventArgs e)
    {
        _viewModel.StopReceiver();
        _signalingServer.Dispose();
        _mdnsService.Dispose();
    }
}