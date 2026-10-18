using System.Diagnostics;
using System.Net;
using System.Net.Sockets;
using System.Text;
using Makaretu.Dns;

namespace MirrorCast.Receiver.Discovery;

public class MdnsDiscoveryService : IDisposable
{
    private MulticastService? _mdns;
    private ServiceDiscovery? _sd;
    private ServiceProfile? _profile;
    private UdpClient? _udpResponder;
    private CancellationTokenSource? _udpCts;
    private bool _isAdvertising;

    public bool IsAdvertising => _isAdvertising;

    public void Start(int port, string instanceName = "MirrorCast-PC")
    {
        if (_isAdvertising) return;

        try
        {
            _mdns = new MulticastService();
            _sd = new ServiceDiscovery(_mdns);

            // Advertise service: _mirrorcast._tcp
            _profile = new ServiceProfile(instanceName, "_mirrorcast._tcp", (ushort)port);
            _profile.AddProperty("txtvers", "1");
            _profile.AddProperty("name", Environment.MachineName);
            _profile.AddProperty("ver", "1.0");

            _mdns.Start();
            _sd.Advertise(_profile);
            _isAdvertising = true;
            Debug.WriteLine($"[mDNS] Advertising service _mirrorcast._tcp on port {port}");
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"[mDNS] Failed to start mDNS advertisement: {ex.Message}");
        }

        // Start UDP broadcast responder for instant Wi-Fi subnet discovery
        StartUdpBeacon(port);
    }

    private void StartUdpBeacon(int port)
    {
        try
        {
            _udpCts = new CancellationTokenSource();
            _udpResponder = new UdpClient(new IPEndPoint(IPAddress.Any, 8091));
            _udpResponder.EnableBroadcast = true;
            var ct = _udpCts.Token;

            Task.Run(async () =>
            {
                while (!ct.IsCancellationRequested)
                {
                    try
                    {
                        var result = await _udpResponder.ReceiveAsync(ct);
                        var req = Encoding.UTF8.GetString(result.Buffer);
                        if (req.StartsWith("MIRRORCAST_DISCOVER"))
                        {
                            var response = Encoding.UTF8.GetBytes($"MIRRORCAST_OFFER|{Environment.MachineName}|{port}");
                            await _udpResponder.SendAsync(response, response.Length, result.RemoteEndPoint);
                        }
                    }
                    catch (OperationCanceledException) { break; }
                    catch { }
                }
            }, ct);
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"[UDP Beacon] Warning: {ex.Message}");
        }
    }

    public void Stop()
    {
        if (!_isAdvertising) return;

        try
        {
            _udpCts?.Cancel();
            _udpResponder?.Dispose();
            _udpResponder = null;

            if (_profile != null && _sd != null)
            {
                _sd.Unadvertise(_profile);
            }
            _mdns?.Stop();
            _mdns?.Dispose();
            _sd?.Dispose();
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"[mDNS] Error stopping mDNS: {ex.Message}");
        }
        finally
        {
            _mdns = null;
            _sd = null;
            _profile = null;
            _isAdvertising = false;
        }
    }

    public void Dispose()
    {
        Stop();
    }
}
