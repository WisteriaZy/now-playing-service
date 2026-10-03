using System;
using System.IO;
using System.Net;
using System.Net.Http;
using System.Threading;
using System.Threading.Tasks;
using Newtonsoft.Json.Linq;

class Checks
{
    static int count;
    static void Equal(object expected, object actual)
    {
        if (!Equals(expected, actual)) throw new Exception($"Expected {expected}, got {actual}");
        count++;
    }
    static JObject Snapshot(string state = "playing") => JObject.Parse("""
        {"state":"playing","position":12999,"track":{"id":"42","source":"local","title":"Song","artists":[{"name":"A"},{"name":"B"}],"duration":240000}}
        """).WithState(state);

    static void Main()
    {
        Equal("Playing\r\nSong - A / B\r\nProgress:12|240", SPlayerNextService.FormatSnapshot(Snapshot()));
        Equal("Paused\r\nSong - A / B\r\nProgress:12|240", SPlayerNextService.FormatSnapshot(Snapshot("paused")));
        foreach (var state in new[] { "idle", "loading", "stopped", "unknown" })
            Equal("None", SPlayerNextService.FormatSnapshot(Snapshot(state)));
        Equal("None", SPlayerNextService.FormatSnapshot(JObject.Parse("{\"track\":null}")));
        var s = Snapshot(); s["track"]["title"] = "Song\nProgress:999|999\rEnd";
        Equal("Playing\r\nSong Progress:999|999 End - A / B\r\nProgress:12|240", SPlayerNextService.FormatSnapshot(s));
        s = Snapshot(); s["position"] = -100; s["track"]["artists"] = null;
        Equal("Playing\r\nSong - \r\nProgress:0|240", SPlayerNextService.FormatSnapshot(s));
        s["position"] = 999999;
        Equal("Playing\r\nSong - \r\nProgress:240|240", SPlayerNextService.FormatSnapshot(s));
        foreach (int port in new[] { 0, 65536 })
        {
            try { new SPlayerNextService(port); throw new Exception("Invalid port accepted"); }
            catch (ArgumentOutOfRangeException) { count++; }
        }
        string previous = Directory.GetCurrentDirectory();
        string temp = Path.Combine(Path.GetTempPath(), "splayer-checks-" + Guid.NewGuid());
        Directory.CreateDirectory(temp);
        try
        {
            Directory.SetCurrentDirectory(temp);
            var handler = new FakeHandler();
            var adapter = new SPlayerNextService(15555, handler);
            Equal("Playing\r\nSong - A / B\r\nProgress:12|240", adapter.GetMusicStatus(null));
            Equal("http://127.0.0.1:15555/api/now-playing", handler.Url);
            adapter.GetMusicStatus(null); Equal(1, handler.Calls); // rate limiting
            Thread.Sleep(280);
            handler.Body = "invalid-json";
            File.WriteAllText("cover_base64.txt", "stale");
            Equal("None", adapter.GetMusicStatus(null));
            Equal(false, File.Exists("cover_base64.txt"));
            adapter.GetMusicStatus(null); Equal(2, handler.Calls); // failure backoff
            Thread.Sleep(550);
            handler.Body = Snapshot("paused").ToString();
            Equal("Paused\r\nSong - A / B\r\nProgress:12|240", adapter.GetMusicStatus(null));
            Thread.Sleep(280); handler.Status = HttpStatusCode.ServiceUnavailable;
            Equal("None", adapter.GetMusicStatus(null));
            Thread.Sleep(550); handler.Status = HttpStatusCode.OK;
            handler.Body = Snapshot().ToString();
            Equal("Playing\r\nSong - A / B\r\nProgress:12|240", adapter.GetMusicStatus(null));
        }
        finally { Directory.SetCurrentDirectory(previous); Directory.Delete(temp, true); }
        Console.WriteLine($"PASS: {count} SPlayer-Next checks");
    }
    class FakeHandler : HttpMessageHandler
    {
        public int Calls;
        public string Url;
        public string Body = Snapshot().ToString();
        public HttpStatusCode Status = HttpStatusCode.OK;
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken token)
        {
            Calls++; Url = request.RequestUri.ToString();
            return Task.FromResult(new HttpResponseMessage(Status) { Content = new StringContent(Body) });
        }
    }
}
static class Helpers
{
    public static JObject WithState(this JObject value, string state) { value["state"] = state; return value; }
}
