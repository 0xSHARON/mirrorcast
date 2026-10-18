using System.Text.Json.Serialization;

namespace MirrorCast.Receiver.Signaling;

public class PairRequestPayload
{
    [JsonPropertyName("deviceName")]
    public string DeviceName { get; set; } = "Android Device";

    [JsonPropertyName("pin")]
    public string Pin { get; set; } = string.Empty;

    [JsonPropertyName("androidVersion")]
    public int AndroidVersion { get; set; } = 14;

    [JsonPropertyName("screenWidth")]
    public int ScreenWidth { get; set; } = 1080;

    [JsonPropertyName("screenHeight")]
    public int ScreenHeight { get; set; } = 2400;

    [JsonPropertyName("preferredFps")]
    public int PreferredFps { get; set; } = 60;

    [JsonPropertyName("preferredCodec")]
    public string PreferredCodec { get; set; } = "H264";
}

public class PairResponsePayload
{
    [JsonPropertyName("status")]
    public string Status { get; set; } = "approved"; // "approved", "rejected", "invalid_pin"

    [JsonPropertyName("receiverName")]
    public string ReceiverName { get; set; } = Environment.MachineName;

    [JsonPropertyName("supportedCodecs")]
    public string[] SupportedCodecs { get; set; } = ["H264", "VP8"];

    [JsonPropertyName("maxBitrateKbps")]
    public int MaxBitrateKbps { get; set; } = 8000;
}

public class SdpPayload
{
    [JsonPropertyName("sdp")]
    public string Sdp { get; set; } = string.Empty;

    [JsonPropertyName("type")]
    public string Type { get; set; } = string.Empty; // "offer" or "answer"
}

public class IceCandidatePayload
{
    [JsonPropertyName("sdpMid")]
    public string? SdpMid { get; set; }

    [JsonPropertyName("sdpMLineIndex")]
    public int? SdpMLineIndex { get; set; }

    [JsonPropertyName("candidate")]
    public string Candidate { get; set; } = string.Empty;
}

public class ControlPayload
{
    [JsonPropertyName("action")]
    public string Action { get; set; } = "resume"; // "pause", "resume", "set_quality", "rotate"

    [JsonPropertyName("rotation")]
    public int? Rotation { get; set; }

    [JsonPropertyName("targetFps")]
    public int? TargetFps { get; set; }

    [JsonPropertyName("targetBitrate")]
    public int? TargetBitrate { get; set; }
}

public class StatsPayload
{
    [JsonPropertyName("fps")]
    public double Fps { get; set; }

    [JsonPropertyName("bitrateKbps")]
    public double BitrateKbps { get; set; }

    [JsonPropertyName("packetsLost")]
    public long PacketsLost { get; set; }

    [JsonPropertyName("rttMs")]
    public double RttMs { get; set; }

    [JsonPropertyName("width")]
    public int Width { get; set; }

    [JsonPropertyName("height")]
    public int Height { get; set; }
}

public class DisconnectPayload
{
    [JsonPropertyName("reason")]
    public string Reason { get; set; } = "user_stopped";
}
