using System.Diagnostics;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Net.WebSockets;
using System.Security.Cryptography;
using System.Text;
using MirrorCast.Receiver.Security;

namespace MirrorCast.Receiver.Signaling;

public class SignalingServer : IDisposable
{
    private TcpListener? _tcpListener;
    private CancellationTokenSource? _cts;
    private WebSocket? _clientWebSocket;
    private readonly SemaphoreSlim _sendLock = new(1, 1);
    private readonly PairingManager _pairingManager;
    private string? _activeSessionId;
    private string _currentClientIp = "127.0.0.1";
    private bool _isCurrentClientLoopback = false;
    private PairRequestPayload? _pendingPairRequest;

    public int Port { get; private set; }
    public bool IsRunning => _tcpListener?.Server?.IsBound == true;
    public string? ActiveSessionId => _activeSessionId;
    public bool RequireConfirmationPopup { get; set; } = true;
    public bool AutoApproveUsb { get; set; } = false;
    public bool RequirePinAuth { get; set; } = true;

    public event Action<string, string, string>? DevicePairingRequested; // deviceName, pin, ip
    public event Action<PairRequestPayload, string>? DeviceConnected; // payload, sessionId
    public event Action<SdpPayload>? OfferReceived;
    public event Action<IceCandidatePayload>? IceCandidateReceived;
    public event Action<ControlPayload>? ControlReceived;
    public event Action<StatsPayload>? StatsReceived;
    public event Action<string>? ClientDisconnected; // reason
    public event Action<string>? LogMessage;

    public SignalingServer(PairingManager pairingManager)
    {
        _pairingManager = pairingManager;
    }

    public void Start(int port = 8090)
    {
        if (IsRunning) return;

        Port = port;
        _cts = new CancellationTokenSource();

        try
        {
            _tcpListener = new TcpListener(IPAddress.Any, port);
            _tcpListener.Start();
            Log($"[SignalingServer] Listening on 0.0.0.0:{port}");
            Task.Run(() => TcpListenLoopAsync(_cts.Token));
        }
        catch (Exception ex)
        {
            Log($"[SignalingServer] Start failed on port {port}: {ex.Message}");
        }
    }

    public void Stop()
    {
        _cts?.Cancel();
        try
        {
            _clientWebSocket?.Dispose();
            _clientWebSocket = null;
            _tcpListener?.Stop();
        }
        catch { }
        finally
        {
            _tcpListener = null;
            _cts = null;
            _activeSessionId = null;
            Log("[SignalingServer] Stopped.");
        }
    }

    private async Task TcpListenLoopAsync(CancellationToken ct)
    {
        while (!ct.IsCancellationRequested && _tcpListener != null)
        {
            try
            {
                var tcpClient = await _tcpListener.AcceptTcpClientAsync(ct);
                _ = Task.Run(() => HandleTcpClientAsync(tcpClient, ct), ct);
            }
            catch when (ct.IsCancellationRequested)
            {
                break;
            }
            catch (Exception ex)
            {
                Log($"[SignalingServer] Accept error: {ex.Message}");
            }
        }
    }

    private async Task HandleTcpClientAsync(TcpClient client, CancellationToken ct)
    {
        try
        {
            var remoteEp = client.Client.RemoteEndPoint as IPEndPoint;
            _currentClientIp = remoteEp?.Address.ToString() ?? "127.0.0.1";
            _isCurrentClientLoopback = remoteEp != null && IPAddress.IsLoopback(remoteEp.Address);

            using (client)
            using (var stream = client.GetStream())
            {
                var utf8Encoding = new UTF8Encoding(encoderShouldEmitUTF8Identifier: false);
                using var reader = new StreamReader(stream, utf8Encoding, leaveOpen: true);
                using var writer = new StreamWriter(stream, utf8Encoding, leaveOpen: true) { NewLine = "\r\n" };

                string? requestLine = await reader.ReadLineAsync(ct);
                if (string.IsNullOrEmpty(requestLine)) return;

                var headers = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
                string? headerLine;
                while (!string.IsNullOrEmpty(headerLine = await reader.ReadLineAsync(ct)))
                {
                    int colonIdx = headerLine.IndexOf(':');
                    if (colonIdx > 0)
                    {
                        headers[headerLine[..colonIdx].Trim()] = headerLine[(colonIdx + 1)..].Trim();
                    }
                }

                if (headers.TryGetValue("Upgrade", out var upgrade) &&
                    upgrade.Equals("websocket", StringComparison.OrdinalIgnoreCase) &&
                    headers.TryGetValue("Sec-WebSocket-Key", out var secKey))
                {
                    string acceptKey = Convert.ToBase64String(
                        SHA1.HashData(Encoding.UTF8.GetBytes(secKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"))
                    );

                    await writer.WriteLineAsync("HTTP/1.1 101 Switching Protocols");
                    await writer.WriteLineAsync("Upgrade: websocket");
                    await writer.WriteLineAsync("Connection: Upgrade");
                    await writer.WriteLineAsync($"Sec-WebSocket-Accept: {acceptKey}");
                    await writer.WriteLineAsync();
                    await writer.FlushAsync(ct);

                    var ws = WebSocket.CreateFromStream(stream, isServer: true, subProtocol: null, keepAliveInterval: TimeSpan.FromSeconds(30));
                    await ProcessWebSocketConnectionAsync(ws, ct);
                }
                else
                {
                    // HTTP health / info response
                    string body = "{\"name\":\"MirrorCast Receiver\",\"status\":\"ready\"}";
                    byte[] bodyBytes = Encoding.UTF8.GetBytes(body);
                    await writer.WriteLineAsync("HTTP/1.1 200 OK");
                    await writer.WriteLineAsync("Content-Type: application/json");
                    await writer.WriteLineAsync($"Content-Length: {bodyBytes.Length}");
                    await writer.WriteLineAsync("Connection: close");
                    await writer.WriteLineAsync();
                    await writer.FlushAsync(ct);
                    await stream.WriteAsync(bodyBytes, ct);
                }
            }
        }
        catch (Exception ex) when (ex is not OperationCanceledException)
        {
            Log($"[SignalingServer] Client processing error: {ex.Message}");
        }
    }

    private async Task ProcessWebSocketConnectionAsync(WebSocket webSocket, CancellationToken ct)
    {
        _clientWebSocket = webSocket;
        var buffer = new byte[64 * 1024];
        Log("[SignalingServer] WebSocket connection established.");

        try
        {
            while (webSocket.State == WebSocketState.Open && !ct.IsCancellationRequested)
            {
                var result = await webSocket.ReceiveAsync(new ArraySegment<byte>(buffer), ct);
                if (result.MessageType == WebSocketMessageType.Close)
                {
                    await webSocket.CloseAsync(WebSocketCloseStatus.NormalClosure, "Closing", CancellationToken.None);
                    break;
                }

                if (result.MessageType == WebSocketMessageType.Text)
                {
                    var json = Encoding.UTF8.GetString(buffer, 0, result.Count);
                    await HandleIncomingMessageAsync(json);
                }
            }
        }
        catch (Exception ex) when (ex is not OperationCanceledException)
        {
            Log($"[SignalingServer] WebSocket communication error: {ex.Message}");
        }
        finally
        {
            if (_clientWebSocket == webSocket)
            {
                _clientWebSocket = null;
                _activeSessionId = null;
                ClientDisconnected?.Invoke("connection_closed");
            }
            Log("[SignalingServer] Client disconnected.");
        }
    }

    private async Task HandleIncomingMessageAsync(string json)
    {
        var msg = SignalingMessage.FromJson(json);
        if (msg == null) return;

        switch (msg.Type)
        {
            case "pair_request":
                var req = msg.GetPayload<PairRequestPayload>();
                if (req != null)
                {
                    _pendingPairRequest = req;

                    // Step 1: Validate PIN if PIN authentication is required and not USB loopback
                    if (RequirePinAuth && !_isCurrentClientLoopback)
                    {
                        if (!_pairingManager.ValidatePin(req.Pin))
                        {
                            var respPayload = new PairResponsePayload
                            {
                                Status = "invalid_pin",
                                ReceiverName = Environment.MachineName
                            };
                            var resp = SignalingMessage.Create("pair_response", null, respPayload);
                            await SendMessageAsync(resp);
                            Log($"[SignalingServer] Rejected pairing request from {req.DeviceName}: Invalid PIN '{req.Pin}'");
                            _pendingPairRequest = null;
                            return;
                        }
                    }

                    // Step 2: Determine if popup confirmation is needed
                    bool needsPopup = RequireConfirmationPopup;
                    if (_isCurrentClientLoopback && AutoApproveUsb)
                    {
                        needsPopup = false;
                    }

                    if (needsPopup)
                    {
                        Log($"[SignalingServer] Showing incoming request popup for {req.DeviceName} (IP: {_currentClientIp}). Waiting for user to click Allow.");
                        DevicePairingRequested?.Invoke(req.DeviceName, req.Pin, _currentClientIp);
                        // Do not complete pairing until Allow is clicked!
                    }
                    else
                    {
                        Log($"[SignalingServer] Auto-approving connection for {req.DeviceName} (IP: {_currentClientIp})");
                        await CompletePairingAsync(req);
                    }
                }
                break;

            case "offer":
                if (_pairingManager.IsSessionValid(msg.SessionId))
                {
                    var offer = msg.GetPayload<SdpPayload>();
                    if (offer != null)
                    {
                        Log("[SignalingServer] Received SDP Offer from Android");
                        OfferReceived?.Invoke(offer);
                    }
                }
                else
                {
                    Log("[SignalingServer] Dropping offer: Invalid or expired session");
                }
                break;

            case "ice_candidate":
                if (_pairingManager.IsSessionValid(msg.SessionId))
                {
                    var cand = msg.GetPayload<IceCandidatePayload>();
                    if (cand != null)
                    {
                        IceCandidateReceived?.Invoke(cand);
                    }
                }
                break;

            case "control":
                if (_pairingManager.IsSessionValid(msg.SessionId))
                {
                    var ctrl = msg.GetPayload<ControlPayload>();
                    if (ctrl != null)
                    {
                        ControlReceived?.Invoke(ctrl);
                    }
                }
                break;

            case "stats":
                if (_pairingManager.IsSessionValid(msg.SessionId))
                {
                    var stats = msg.GetPayload<StatsPayload>();
                    if (stats != null)
                    {
                        StatsReceived?.Invoke(stats);
                    }
                }
                break;

            case "disconnect":
                var disc = msg.GetPayload<DisconnectPayload>();
                _pairingManager.RevokeSession(msg.SessionId);
                _activeSessionId = null;
                ClientDisconnected?.Invoke(disc?.Reason ?? "user_stopped");
                break;
        }
    }

    public async Task SendMessageAsync(SignalingMessage message)
    {
        if (_clientWebSocket == null || _clientWebSocket.State != WebSocketState.Open) return;

        var json = message.ToJson();
        var bytes = Encoding.UTF8.GetBytes(json);

        await _sendLock.WaitAsync();
        try
        {
            if (_clientWebSocket.State == WebSocketState.Open)
            {
                await _clientWebSocket.SendAsync(
                    new ArraySegment<byte>(bytes),
                    WebSocketMessageType.Text,
                    true,
                    CancellationToken.None);
            }
        }
        catch (Exception ex)
        {
            Log($"[SignalingServer] Error sending message: {ex.Message}");
        }
        finally
        {
            _sendLock.Release();
        }
    }

    public async Task ApprovePendingPairingAsync()
    {
        if (_pendingPairRequest == null) return;
        await CompletePairingAsync(_pendingPairRequest);
        _pendingPairRequest = null;
    }

    public async Task RejectPendingPairingAsync()
    {
        if (_clientWebSocket?.State == WebSocketState.Open)
        {
            var respPayload = new PairResponsePayload
            {
                Status = "rejected",
                ReceiverName = Environment.MachineName
            };
            var resp = SignalingMessage.Create("pair_response", null, respPayload);
            await SendMessageAsync(resp);
        }
        _pendingPairRequest = null;
    }

    private async Task CompletePairingAsync(PairRequestPayload req)
    {
        var session = _pairingManager.CreateSession(req.DeviceName, preApproved: true);
        _activeSessionId = session.SessionId;

        var respPayload = new PairResponsePayload
        {
            Status = "approved",
            ReceiverName = Environment.MachineName,
            SupportedCodecs = ["H264", "VP8"],
            MaxBitrateKbps = 8000
        };

        var resp = SignalingMessage.Create("pair_response", session.SessionId, respPayload);
        await SendMessageAsync(resp);
        DeviceConnected?.Invoke(req, session.SessionId);
        Log($"[SignalingServer] Device paired successfully: {req.DeviceName} (Session: {session.SessionId})");
    }

    public async Task SendAnswerAsync(string sdp)
    {
        var payload = new SdpPayload { Sdp = sdp, Type = "answer" };
        var msg = SignalingMessage.Create("answer", _activeSessionId, payload);
        await SendMessageAsync(msg);
    }

    public async Task SendIceCandidateAsync(string candidate, string? sdpMid, int? sdpMLineIndex)
    {
        var payload = new IceCandidatePayload
        {
            Candidate = candidate,
            SdpMid = sdpMid,
            SdpMLineIndex = sdpMLineIndex
        };
        var msg = SignalingMessage.Create("ice_candidate", _activeSessionId, payload);
        await SendMessageAsync(msg);
    }

    private void Log(string message)
    {
        Debug.WriteLine(message);
        LogMessage?.Invoke(message);
    }

    public void Dispose()
    {
        Stop();
        _sendLock.Dispose();
    }
}
