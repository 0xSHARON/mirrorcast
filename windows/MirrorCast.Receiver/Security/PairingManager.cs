using System.Security.Cryptography;
using QRCoder;

namespace MirrorCast.Receiver.Security;

public class PairingSession
{
    public string SessionId { get; set; } = Guid.NewGuid().ToString("N");
    public string ClientDeviceName { get; set; } = string.Empty;
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    public DateTime LastActiveAt { get; set; } = DateTime.UtcNow;
    public bool IsApproved { get; set; }
}

public class PairingManager
{
    private readonly object _lock = new();
    private string _currentPin = string.Empty;
    private int _failedAttempts = 0;
    private const int MaxFailedAttempts = 5;
    private readonly Dictionary<string, PairingSession> _activeSessions = new();

    public string CurrentPin
    {
        get
        {
            lock (_lock)
            {
                if (string.IsNullOrEmpty(_currentPin))
                {
                    RegeneratePin();
                }
                return _currentPin;
            }
        }
    }

    public string RegeneratePin()
    {
        lock (_lock)
        {
            // 4-digit cryptographic random PIN (matching UI mockup design)
            _currentPin = RandomNumberGenerator.GetInt32(1000, 10000).ToString("D4");
            _failedAttempts = 0;
            return _currentPin;
        }
    }

    public bool ValidatePin(string candidatePin)
    {
        lock (_lock)
        {
            if (_failedAttempts >= MaxFailedAttempts)
            {
                // Auto regenerate to prevent brute force
                RegeneratePin();
                return false;
            }

            if (!string.IsNullOrEmpty(candidatePin) &&
                string.Equals(candidatePin.Trim(), _currentPin.Trim(), StringComparison.Ordinal))
            {
                _failedAttempts = 0;
                return true;
            }

            _failedAttempts++;
            if (_failedAttempts >= MaxFailedAttempts)
            {
                RegeneratePin();
            }
            return false;
        }
    }

    public PairingSession CreateSession(string clientDeviceName, bool preApproved = true)
    {
        lock (_lock)
        {
            var session = new PairingSession
            {
                ClientDeviceName = clientDeviceName,
                IsApproved = preApproved
            };
            _activeSessions[session.SessionId] = session;
            return session;
        }
    }

    public bool IsSessionValid(string? sessionId)
    {
        if (string.IsNullOrEmpty(sessionId)) return false;

        lock (_lock)
        {
            if (_activeSessions.TryGetValue(sessionId, out var session))
            {
                session.LastActiveAt = DateTime.UtcNow;
                return session.IsApproved;
            }
            return false;
        }
    }

    public void RevokeSession(string? sessionId)
    {
        if (string.IsNullOrEmpty(sessionId)) return;

        lock (_lock)
        {
            _activeSessions.Remove(sessionId);
        }
    }

    public void ClearAllSessions()
    {
        lock (_lock)
        {
            _activeSessions.Clear();
        }
    }

    public string GenerateQrPayload(string ip, int port)
    {
        return $"mirrorcast://pair?ip={ip}&port={port}&pin={CurrentPin}&name={Uri.EscapeDataString(Environment.MachineName)}";
    }

    public byte[] GenerateQrCodePng(string ip, int port)
    {
        var payload = GenerateQrPayload(ip, port);
        using var qrGenerator = new QRCodeGenerator();
        using var qrCodeData = qrGenerator.CreateQrCode(payload, QRCodeGenerator.ECCLevel.M);
        using var qrCode = new PngByteQRCode(qrCodeData);
        return qrCode.GetGraphic(20);
    }
}
