using System;
using System.Net;
using System.Net.Http;
using System.Threading;
using System.Threading.Tasks;
using Newtonsoft.Json.Linq;
class Checks {
    static int count;
    static void Equal(object a, object b) { if (!Equals(a,b)) throw new Exception($"Expected {a}, got {b}"); count++; }
    static JObject Frame() => JObject.Parse("""
        {"game":{"paused":false},"state":{"number":5},"beatmap":{"title":"Song","artist":"Artist","checksum":"diff1","version":"Easy","time":{"live":12000,"mp3Length":180000,"lastObject":150000}},"directPath":{"beatmapAudio":"Set/audio.mp3","beatmapBackground":"Set/bg.jpg"}}
        """);
    static void Main() {
        var mapper = new TosuSnapshotMapper(); var frame = Frame();
        var first = mapper.Map(frame,0);
        Equal("Playing",first.Value<string>("status")); Equal(12000L,first.Value<long>("positionMs")); Equal(180000L,first.Value<long>("durationMs"));
        frame["beatmap"]["checksum"]="diff2";frame["beatmap"]["version"]="Hard";
        Equal(first.Value<string>("identity"),mapper.Map(frame,200).Value<string>("identity"));
        frame["directPath"]["beatmapAudio"]="Set/other.mp3";
        Equal(false,first.Value<string>("identity")==mapper.Map(frame,400).Value<string>("identity"));
        frame=Frame();mapper.Reset();frame["beatmap"]["time"]["live"]=-500;
        Equal(0L,mapper.Map(frame,0).Value<long>("positionMs"));
        frame["beatmap"]["time"]["live"]=999999;
        Equal(180000L,mapper.Map(frame,200).Value<long>("positionMs"));
        frame=Frame();frame["game"]["paused"]=true;mapper.Reset();
        Equal("Paused",mapper.Map(frame,0).Value<string>("status"));
        frame["beatmap"]["time"]["live"]=14000;
        Equal("Playing",mapper.Map(frame,200).Value<string>("status")); // advancing clock wins stale pause flag
        frame=Frame();frame.Remove("game");mapper.Reset();
        Equal("Playing",mapper.Map(frame,0).Value<string>("status"));
        Equal("Paused",mapper.Map(frame,700).Value<string>("status"));
        frame["beatmap"]["time"]["live"]=200;
        Equal("Playing",mapper.Map(frame,900).Value<string>("status")); // seek/retry
        frame=Frame();frame["state"]["number"]=0;
        Equal("Playing",mapper.Map(frame,1000).Value<string>("status"));
        frame["state"]["number"]=3;Equal("None",mapper.Map(frame,1200).Value<string>("status"));
        Equal("None",mapper.Map(new JObject(),1400).Value<string>("status"));
        frame=Frame();((JObject)frame["beatmap"]["time"]).Remove("mp3Length");
        Equal(150000L,mapper.Map(frame,1600).Value<long>("durationMs"));
        frame["beatmap"]["titleUnicode"]="Unicode\nTitle";
        Equal("Unicode Title",mapper.Map(frame,1800).Value<string>("title"));
        foreach (int port in new[]{0,65536}) { try {new TosuService(port);throw new Exception("port accepted");} catch(ArgumentOutOfRangeException){count++;} }
        var handler=new Handler();var service=new TosuService(23456,handler);
        Equal(true,service.GetMusicStatus(null).StartsWith("Tosu:"));
        Equal("http://127.0.0.1:23456/json/v2",handler.Url);
        service.GetMusicStatus(null);Equal(1,handler.Calls);
        Thread.Sleep(230);handler.Status=HttpStatusCode.InternalServerError;
        Equal("Tosu:{\"status\":\"None\"}",service.GetMusicStatus(null));
        service.GetMusicStatus(null);Equal(2,handler.Calls);
        Thread.Sleep(530);handler.Status=HttpStatusCode.OK;
        Equal("Playing",JObject.Parse(service.GetMusicStatus(null).Substring(5)).Value<string>("status"));
        Thread.Sleep(230);handler.Body="invalid";
        Equal("Tosu:{\"status\":\"None\"}",service.GetMusicStatus(null));
        Console.WriteLine($"PASS: {count} tosu detector checks");
    }
    class Handler:HttpMessageHandler {
        public int Calls;public string Url;public HttpStatusCode Status=HttpStatusCode.OK;public string Body=Frame().ToString();
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage r,CancellationToken c) {
            Calls++;Url=r.RequestUri.ToString();return Task.FromResult(new HttpResponseMessage(Status){Content=new StringContent(Body)});
        }
    }
}
