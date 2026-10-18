using MirrorCast.Receiver.Security;
using Xunit;

namespace MirrorCast.Receiver.Tests;

public class PairingManagerTests
{
    [Fact]
    public void GeneratePin_ProducesFourDigitNumericString()
    {
        var manager = new PairingManager();
        var pin = manager.CurrentPin;

        Assert.NotNull(pin);
        Assert.Equal(4, pin.Length);
        Assert.True(int.TryParse(pin, out var num));
        Assert.InRange(num, 1000, 9999);
    }

    [Fact]
    public void ValidatePin_SucceedsForMatchingPin()
    {
        var manager = new PairingManager();
        var pin = manager.CurrentPin;

        var isValid = manager.ValidatePin(pin);
        Assert.True(isValid);
    }

    [Fact]
    public void ValidatePin_FailsForIncorrectPin()
    {
        var manager = new PairingManager();
        var isValid = manager.ValidatePin("000000");

        // Unless current PIN happened to be 000000 (which is excluded by range 100000..999999)
        Assert.False(isValid);
    }

    [Fact]
    public void ValidatePin_RegeneratesPinAfterMaxFailedAttempts()
    {
        var manager = new PairingManager();
        var initialPin = manager.CurrentPin;

        for (int i = 0; i < 5; i++)
        {
            manager.ValidatePin("111111");
        }

        // After 5 failed attempts, PIN is automatically invalidated and rotated
        Assert.NotEqual(initialPin, manager.CurrentPin);
    }

    [Fact]
    public void CreateSession_IssuesActiveValidSession()
    {
        var manager = new PairingManager();
        var session = manager.CreateSession("Pixel 8 Pro", preApproved: true);

        Assert.NotNull(session.SessionId);
        Assert.True(manager.IsSessionValid(session.SessionId));

        manager.RevokeSession(session.SessionId);
        Assert.False(manager.IsSessionValid(session.SessionId));
    }

    [Fact]
    public void GenerateQrPayload_ContainsRequiredParameters()
    {
        var manager = new PairingManager();
        var payload = manager.GenerateQrPayload("192.168.1.55", 8089);

        Assert.Contains("mirrorcast://pair", payload);
        Assert.Contains("ip=192.168.1.55", payload);
        Assert.Contains("port=8089", payload);
        Assert.Contains($"pin={manager.CurrentPin}", payload);
    }
}
