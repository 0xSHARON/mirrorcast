using System.Text.Json;
using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.Wpf;
using MirrorCast.Receiver.Signaling;

namespace MirrorCast.Receiver.WebRtc;

public class WebRtcReceiverController
{
    private readonly WebView2 _webView;
    private readonly SignalingServer _signalingServer;

    public event Action<StatsPayload>? StatsUpdated;
    public event Action<string>? StreamStateChanged;
    public event Action<string, int, int, int, int, int>? DeviceInputReceived;

    public WebRtcReceiverController(WebView2 webView, SignalingServer signalingServer)
    {
        _webView = webView;
        _signalingServer = signalingServer;

        _signalingServer.OfferReceived += OnOfferReceived;
        _signalingServer.IceCandidateReceived += OnIceCandidateReceived;
        _signalingServer.ClientDisconnected += OnClientDisconnected;
    }

    public async Task InitializeAsync()
    {
        var options = new CoreWebView2EnvironmentOptions
        {
            AdditionalBrowserArguments = "--disable-features=WebRtcHideLocalIpsWithMdns --autoplay-policy=no-user-gesture-required"
        };
        var env = await CoreWebView2Environment.CreateAsync(null, null, options);
        await _webView.EnsureCoreWebView2Async(env);
        _webView.CoreWebView2.IsMuted = false;
        _webView.CoreWebView2.WebMessageReceived += OnWebMessageReceived;
        _webView.NavigateToString(ReceiverRendererHtml.GetHtmlContent(_signalingServer.Port));
    }

    private void OnOfferReceived(SdpPayload offer)
    {
        _webView.Dispatcher.Invoke(() =>
        {
            var msg = new
            {
                action = "set_offer",
                sdp = offer.Sdp
            };
            _webView.CoreWebView2.PostWebMessageAsJson(JsonSerializer.Serialize(msg));
        });
    }

    private void OnIceCandidateReceived(IceCandidatePayload candidate)
    {
        _webView.Dispatcher.Invoke(() =>
        {
            var msg = new
            {
                action = "add_candidate",
                candidate = candidate.Candidate,
                sdpMid = candidate.SdpMid,
                sdpMLineIndex = candidate.SdpMLineIndex
            };
            _webView.CoreWebView2.PostWebMessageAsJson(JsonSerializer.Serialize(msg));
        });
    }

    private void OnClientDisconnected(string reason)
    {
        _webView.Dispatcher.Invoke(() =>
        {
            var msg = new
            {
                action = "reset",
                message = $"Phone disconnected ({reason})"
            };
            _webView.CoreWebView2.PostWebMessageAsJson(JsonSerializer.Serialize(msg));
        });
    }

    private async void OnWebMessageReceived(object? sender, Microsoft.Web.WebView2.Core.CoreWebView2WebMessageReceivedEventArgs e)
    {
        try
        {
            var json = e.WebMessageAsJson;
            using var doc = JsonDocument.Parse(json);
            var root = doc.RootElement;
            var type = root.GetProperty("type").GetString();

            switch (type)
            {
                case "answer":
                    var sdp = root.GetProperty("sdp").GetString();
                    if (!string.IsNullOrEmpty(sdp))
                    {
                        await _signalingServer.SendAnswerAsync(sdp);
                    }
                    break;

                case "ice_candidate":
                    var candidate = root.GetProperty("candidate").GetString();
                    var sdpMid = root.TryGetProperty("sdpMid", out var midProp) ? midProp.GetString() : null;
                    var sdpMLineIndex = root.TryGetProperty("sdpMLineIndex", out var indexProp) ? indexProp.GetInt32() : 0;
                    if (!string.IsNullOrEmpty(candidate))
                    {
                        await _signalingServer.SendIceCandidateAsync(candidate, sdpMid, sdpMLineIndex);
                    }
                    break;

                case "stream_started":
                    StreamStateChanged?.Invoke("active");
                    break;

                case "connection_state":
                    var state = root.GetProperty("state").GetString();
                    StreamStateChanged?.Invoke(state ?? "unknown");
                    break;

                case "device_input":
                    var action = root.GetProperty("action").GetString() ?? "tap";
                    if (action == "tap")
                    {
                        int x = root.GetProperty("x").GetInt32();
                        int y = root.GetProperty("y").GetInt32();
                        DeviceInputReceived?.Invoke(action, x, y, 0, 0, 0);
                    }
                    else if (action == "swipe")
                    {
                        int x1 = root.GetProperty("x1").GetInt32();
                        int y1 = root.GetProperty("y1").GetInt32();
                        int x2 = root.GetProperty("x2").GetInt32();
                        int y2 = root.GetProperty("y2").GetInt32();
                        int duration = root.GetProperty("duration").GetInt32();
                        DeviceInputReceived?.Invoke(action, x1, y1, x2, y2, duration);
                    }
                    break;

                case "stats":
                    var stats = new StatsPayload
                    {
                        Fps = root.GetProperty("fps").GetDouble(),
                        BitrateKbps = root.GetProperty("bitrateKbps").GetDouble(),
                        PacketsLost = root.GetProperty("packetsLost").GetInt64(),
                        Width = root.GetProperty("width").GetInt32(),
                        Height = root.GetProperty("height").GetInt32()
                    };
                    StatsUpdated?.Invoke(stats);
                    break;
            }
        }
        catch (Exception ex)
        {
            System.Diagnostics.Debug.WriteLine($"[WebRtcReceiverController] Error processing web message: {ex.Message}");
        }
    }

    public void SetScalingMode(bool fitToWindow)
    {
        _webView.Dispatcher.Invoke(() =>
        {
            var msg = new
            {
                action = "set_fit",
                fit = fitToWindow ? "contain" : "cover"
            };
            _webView.CoreWebView2.PostWebMessageAsJson(JsonSerializer.Serialize(msg));
        });
    }

    public void Reset()
    {
        _webView.Dispatcher.Invoke(() =>
        {
            var msg = new
            {
                action = "reset",
                message = "Ready to mirror"
            };
            _webView.CoreWebView2.PostWebMessageAsJson(JsonSerializer.Serialize(msg));
        });
    }
}
