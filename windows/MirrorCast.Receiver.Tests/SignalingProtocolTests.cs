using System.Text.Json;
using MirrorCast.Receiver.Signaling;
using Xunit;

namespace MirrorCast.Receiver.Tests;

public class SignalingProtocolTests
{
    [Fact]
    public void PairRequestMessage_SerializesAndDeserializesCorrectly()
    {
        var req = new PairRequestPayload
        {
            DeviceName = "Samsung Galaxy S24",
            Pin = "847291",
            AndroidVersion = 14,
            ScreenWidth = 1080,
            ScreenHeight = 2340,
            PreferredFps = 60,
            PreferredCodec = "H264"
        };

        var msg = SignalingMessage.Create("pair_request", null, req);
        var json = msg.ToJson();

        var restored = SignalingMessage.FromJson(json);
        Assert.NotNull(restored);
        Assert.Equal("1.0", restored.Version);
        Assert.Equal("pair_request", restored.Type);

        var restoredPayload = restored.GetPayload<PairRequestPayload>();
        Assert.NotNull(restoredPayload);
        Assert.Equal("Samsung Galaxy S24", restoredPayload.DeviceName);
        Assert.Equal("847291", restoredPayload.Pin);
        Assert.Equal(60, restoredPayload.PreferredFps);
    }

    [Fact]
    public void SdpOfferAndAnswer_PreservesSdpPayload()
    {
        var sdp = "v=0\r\no=- 12345 2 IN IP4 127.0.0.1\r\ns=-\r\nt=0 0\r\n";
        var offerPayload = new SdpPayload { Sdp = sdp, Type = "offer" };
        var msg = SignalingMessage.Create("offer", "sess-12345", offerPayload);

        var json = msg.ToJson();
        var restored = SignalingMessage.FromJson(json);
        Assert.NotNull(restored);
        Assert.Equal("sess-12345", restored.SessionId);

        var payload = restored.GetPayload<SdpPayload>();
        Assert.NotNull(payload);
        Assert.Equal(sdp, payload.Sdp);
        Assert.Equal("offer", payload.Type);
    }

    [Fact]
    public void IceCandidatePayload_SerializesCorrectly()
    {
        var cand = new IceCandidatePayload
        {
            Candidate = "candidate:1 1 UDP 2130706431 192.168.1.100 50000 typ host",
            SdpMid = "video",
            SdpMLineIndex = 0
        };

        var msg = SignalingMessage.Create("ice_candidate", "sess-abc", cand);
        var json = msg.ToJson();

        var restored = SignalingMessage.FromJson(json);
        Assert.NotNull(restored);

        var candRestored = restored.GetPayload<IceCandidatePayload>();
        Assert.NotNull(candRestored);
        Assert.Equal("candidate:1 1 UDP 2130706431 192.168.1.100 50000 typ host", candRestored.Candidate);
        Assert.Equal("video", candRestored.SdpMid);
        Assert.Equal(0, candRestored.SdpMLineIndex);
    }
}
