using System.Text.Json;
using System.Text.Json.Serialization;

namespace MirrorCast.Receiver.Signaling;

public class SignalingMessage
{
    [JsonPropertyName("version")]
    public string Version { get; set; } = "1.0";

    [JsonPropertyName("type")]
    public string Type { get; set; } = string.Empty;

    [JsonPropertyName("sessionId")]
    public string? SessionId { get; set; }

    [JsonPropertyName("payload")]
    public JsonElement? Payload { get; set; }

    public static SignalingMessage Create<T>(string type, string? sessionId, T payload)
    {
        var json = JsonSerializer.SerializeToElement(payload);
        return new SignalingMessage
        {
            Version = "1.0",
            Type = type,
            SessionId = sessionId,
            Payload = json
        };
    }

    public T? GetPayload<T>()
    {
        if (Payload == null) return default;
        return JsonSerializer.Deserialize<T>(Payload.Value.GetRawText());
    }

    public string ToJson()
    {
        return JsonSerializer.Serialize(this, new JsonSerializerOptions
        {
            DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull
        });
    }

    public static SignalingMessage? FromJson(string json)
    {
        return JsonSerializer.Deserialize<SignalingMessage>(json);
    }
}
