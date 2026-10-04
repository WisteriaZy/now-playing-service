using System;
using System.Diagnostics;
using System.Linq;
using System.Net.Http;
using System.Security.Cryptography;
using System.Text;
using CSCore.CoreAudioAPI;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;

// tosu v2: one atomic snapshot per line; never download artwork or search music here.
public class TosuService : MusicService
{
    private readonly HttpClient client;
    private readonly string endpoint;
    private readonly Stopwatch clock = Stopwatch.StartNew();
    private readonly TosuSnapshotMapper mapper = new TosuSnapshotMapper();
    private long nextPoll;
    private int failures;
    private string output = "Tosu:{\"status\":\"None\"}";

    public TosuService(int port = 24050) : this(port, new HttpClientHandler { UseProxy = false, AllowAutoRedirect = false }) { }
    public TosuService(int port, HttpMessageHandler handler)
    {
        if (port < 1 || port > 65535) throw new ArgumentOutOfRangeException(nameof(port));
        client = new HttpClient(handler) { Timeout = TimeSpan.FromMilliseconds(1500), MaxResponseContentBufferSize = 4 * 1024 * 1024 };
        endpoint = $"http://127.0.0.1:{port}/json/v2";
    }
    public override string GetMusicStatus(AudioSessionManager2 sessionManager)
    {
        if (clock.ElapsedMilliseconds < nextPoll) return output;
        try
        {
            var json = JObject.Parse(client.GetStringAsync(endpoint).GetAwaiter().GetResult());
            output = "Tosu:" + mapper.Map(json, clock.ElapsedMilliseconds).ToString(Formatting.None);
            failures = 0;
            nextPoll = clock.ElapsedMilliseconds + 200;
        }
        catch (Exception ex)
        {
            mapper.Reset();
            output = "Tosu:{\"status\":\"None\"}";
            failures = Math.Min(5, failures + 1);
            nextPoll = clock.ElapsedMilliseconds + 500 * failures;
            if (failures == 1) Console.Error.WriteLine($"tosu unavailable; check osu!, tosu and port: {ex.Message}");
        }
        return output;
    }
}

public class TosuSnapshotMapper
{
    private string previousIdentity;
    private double previousPosition;
    private long lastMovement;
    public void Reset() { previousIdentity = null; previousPosition = 0; lastMovement = 0; }
    public JObject Map(JObject source, long now)
    {
        var beatmap = source["beatmap"] as JObject;
        string title = Clean(beatmap?.Value<string>("titleUnicode"));
        if (title.Length == 0) title = Clean(beatmap?.Value<string>("title"));
        string artist = Clean(beatmap?.Value<string>("artistUnicode"));
        if (artist.Length == 0) artist = Clean(beatmap?.Value<string>("artist"));
        var positionToken = beatmap?["time"]?["live"];
        if (title.Length == 0 || !IsNumber(positionToken) || source["state"]?["number"]?.Value<int>() == 3)
        {
            Reset(); return new JObject { ["status"] = "None" };
        }
        double rawPosition = positionToken.Value<double>();
        if (!double.IsFinite(rawPosition)) { Reset(); return new JObject { ["status"] = "None" }; }
        string audio = Clean(source["directPath"]?["beatmapAudio"]?.Value<string>()).Replace('\\', '/');
        if (audio.Length == 0) audio = Clean(source["folders"]?["beatmap"]?.Value<string>()) + "/" + Clean(source["files"]?["audio"]?.Value<string>());
        // Difficulty/checksum is deliberately excluded: same audio + metadata is the same song.
        string identity = Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(audio + "\n" + title + "\n" + artist)));
        bool changed = identity != previousIdentity;
        bool moved = !changed && Math.Abs(rawPosition - previousPosition) >= 1;
        if (changed || moved) lastMovement = now;
        var pause = source["game"]?["paused"];
        bool paused = pause?.Type == JTokenType.Boolean
            ? pause.Value<bool>() && !moved
            : !moved && now - lastMovement >= 650;
        double duration = Number(beatmap?["time"]?["mp3Length"]);
        if (duration <= 0) duration = Number(beatmap?["time"]?["lastObject"]);
        double position = Math.Min(int.MaxValue * 1000.0, Math.Max(0, rawPosition));
        if (duration > 0) position = Math.Min(position, duration);
        previousIdentity = identity;
        previousPosition = rawPosition;
        return new JObject {
            ["status"] = paused ? "Paused" : "Playing", ["identity"] = identity,
            ["title"] = title, ["artist"] = artist, ["positionMs"] = (long)position,
            ["durationMs"] = (long)Math.Min(duration, int.MaxValue * 1000.0),
            ["coverPath"] = Clean(source["directPath"]?["beatmapBackground"]?.Value<string>())
        };
    }
    private static bool IsNumber(JToken t) => t != null && (t.Type == JTokenType.Integer || t.Type == JTokenType.Float);
    private static double Number(JToken t) => IsNumber(t) && double.IsFinite(t.Value<double>()) ? Math.Max(0, t.Value<double>()) : 0;
    private static string Clean(string text) => (text ?? "").Replace('\r', ' ').Replace('\n', ' ').Trim();
}
