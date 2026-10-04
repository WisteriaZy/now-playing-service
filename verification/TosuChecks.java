import com.widdit.nowplaying.component.LatestOnlyLoader;
import com.widdit.nowplaying.util.TosuArtwork;
import com.widdit.nowplaying.entity.*;
import com.widdit.nowplaying.event.*;
import com.widdit.nowplaying.service.*;
import com.widdit.nowplaying.service.netease.NeteaseMusicService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import static org.mockito.Mockito.*;

public class TosuChecks {
    static int checks;
    static void check(boolean yes) { if(!yes) throw new AssertionError("check " + checks); checks++; }
    static void await(BooleanSupplier ready) throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(!ready.getAsBoolean() && System.nanoTime()<end) Thread.sleep(10);
        check(ready.getAsBoolean());
    }
    static TosuSnapshot snapshot(String id,long position) {
        return TosuSnapshot.parse("{\"status\":\"Playing\",\"identity\":\""+id+"\",\"title\":\""+id+"\",\"artist\":\"Artist\",\"positionMs\":"+position+",\"durationMs\":180000,\"coverPath\":\"\"}");
    }
    public static void main(String[] args) throws Exception {
        check(TosuArtwork.isReference(TosuArtwork.reference("A")));
        check(TosuArtwork.reference("A").equals(TosuArtwork.reference("A")));
        check(!TosuArtwork.reference("A").equals(TosuArtwork.reference("B")));
        check(!TosuArtwork.isReference("https://example.org/cover"));

        check("image/jpeg".equals(TosuArtwork.detectMime(new byte[]{(byte)0xff,(byte)0xd8,(byte)0xff,(byte)0xe0,0,16,74,70,73,70,0,1})));
        check("image/png".equals(TosuArtwork.detectMime(new byte[]{(byte)0x89,80,78,71,13,10,26,10,0,0,0,13})));
        check("image/gif".equals(TosuArtwork.detectMime("GIF89a1234567".getBytes(java.nio.charset.StandardCharsets.US_ASCII))));
        check("image/gif".equals(TosuArtwork.detectMime("GIF87a1234567".getBytes(java.nio.charset.StandardCharsets.US_ASCII))));
        check("image/webp".equals(TosuArtwork.detectMime("RIFF1234WEBP".getBytes(java.nio.charset.StandardCharsets.US_ASCII))));
        check(TosuArtwork.detectMime("<html>error</html>".getBytes(java.nio.charset.StandardCharsets.US_ASCII)) == null);
        check(TosuArtwork.detectMime("RIFF1234WAVE".getBytes(java.nio.charset.StandardCharsets.US_ASCII)) == null);
        check(TosuArtwork.detectMime(new byte[]{(byte)0xff,(byte)0xd8,(byte)0xff}) == null);
        check(TosuArtwork.detectMime(null) == null);

        check(TosuSnapshot.parse("{\"status\":\"None\"}")==null);
        check(TosuSnapshot.parse("{\"status\":\"Playing\",\"title\":\"a\"}")==null);
        TosuSnapshot path=TosuSnapshot.parse("{\"status\":\"Playing\",\"identity\":\"a\",\"title\":\"a\",\"coverPath\":\"Set name/bg #1.jpg\"}");
        check(path.coverUrl(24050).equals("http://127.0.0.1:24050/files/beatmap/Set%20name/bg%20%231.jpg"));
        path=TosuSnapshot.parse("{\"status\":\"Playing\",\"identity\":\"a\",\"title\":\"a\",\"coverPath\":\"../outside\"}");
        check(path.coverUrl(24050).isEmpty());
        AtomicInteger loads=new AtomicInteger();
        try(LatestOnlyLoader<String> loader=new LatestOnlyLoader<>("check",100,()->{})) {
            for(int i=0;i<100;i++) {final int n=i;loader.request("song"+i,()->{loads.incrementAndGet();return "song"+n;});}
            await(()->"song99".equals(loader.get("song99")));check(loads.get()==1);
        }
        CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1),newStarted=new CountDownLatch(1),newRelease=new CountDownLatch(1);
        try(LatestOnlyLoader<String> loader=new LatestOnlyLoader<>("race",0,()->{})) {
            loader.request("A",()->{started.countDown();release.await();return "old A";});check(started.await(2,TimeUnit.SECONDS));
            loader.request("B",()->{throw new AssertionError("B must be coalesced");});
            loader.request("A",()->{newStarted.countDown();newRelease.await();return "new A";});
            check(loader.get("A")==null);release.countDown();check(newStarted.await(2,TimeUnit.SECONDS));
            LatestOnlyLoader.LoadState<String> pending = loader.read("A");
            check(!pending.done && pending.value == null);
            check(loader.get("A")==null);newRelease.countDown();await(()->"new A".equals(loader.get("A")));
            LatestOnlyLoader.LoadState<String> completed = loader.read("A");
            check(completed.done && "new A".equals(completed.value));
            check(!pending.done && pending.value == null);
            check(!loader.read("B").done);
            loader.clear();check(loader.get("A")==null);
        } finally {release.countDown();newRelease.countDown();}
        AudioService audio=mock(AudioService.class); SettingsService settings=mock(SettingsService.class);
        when(audio.getCurrentPlatform()).thenReturn("tosu");when(settings.getSettingsGeneral()).thenReturn(new SettingsGeneral());
        AtomicReference<TosuSnapshot> current=new AtomicReference<>(snapshot("A",12000));
        when(audio.getTosuSnapshot()).thenAnswer(a->current.get());
        when(audio.getWindowTitle()).thenAnswer(a->current.get()==null?"":current.get().windowTitle());
        when(audio.getStatus()).thenAnswer(a->current.get()==null?"None":current.get().status);
        NeteaseMusicService netease=mock(NeteaseMusicService.class);
        NowPlayingService now=new NowPlayingService();
        ReflectionTestUtils.setField(now,"audioService",audio);ReflectionTestUtils.setField(now,"settingsService",settings);
        ReflectionTestUtils.setField(now,"neteaseMusicService",netease);
        ReflectionTestUtils.setField(now,"eventPublisher",mock(ApplicationEventPublisher.class));
        ReflectionTestUtils.setField(now,"outputService",mock(OutputService.class));
        try {
            long start=System.nanoTime();
            for(int i=0;i<100;i++){current.set(snapshot("song"+i,i*1000));now.updateMusicInfo(new MusicStatusUpdatedEvent(now,"check"));}
            check(System.nanoTime()-start<TimeUnit.SECONDS.toNanos(1));
            check(now.queryTrack().getTitle().equals("song99"));check(TosuArtwork.isReference(now.queryTrack().getCover()));check(now.queryPlayer().getSeekbarCurrentPosition()==99);
            verifyNoInteractions(netease);checks++;
            current.set(null);now.updateMusicInfo(new MusicStatusUpdatedEvent(now,"disconnect"));check(!now.queryPlayer().getHasSong());
        } finally {now.closeTosuLoader();}
        LyricService lyrics=new LyricService();SettingsLyricCommon common=new SettingsLyricCommon();common.setAutoSelectBestLyric(false);
        ReflectionTestUtils.setField(lyrics,"audioService",audio);ReflectionTestUtils.setField(lyrics,"neteaseMusicService",netease);
        ReflectionTestUtils.setField(lyrics,"settingsCommon",common);ReflectionTestUtils.setField(lyrics,"eventPublisher",mock(ApplicationEventPublisher.class));
        CountDownLatch lyricStarted=new CountDownLatch(1),lyricRelease=new CountDownLatch(1);
        when(netease.getLyric(anyString())).thenAnswer(a->{String title=a.getArgument(0);if(title.startsWith("A -")){lyricStarted.countDown();lyricRelease.await();}Lyric result=new Lyric();result.setTitle(title);return result;});
        try {
            current.set(snapshot("A",0));long start=System.nanoTime();lyrics.getLyric();check(System.nanoTime()-start<TimeUnit.MILLISECONDS.toNanos(250));
            check(lyricStarted.await(2,TimeUnit.SECONDS));current.set(snapshot("B",1));lyrics.getLyric();lyricRelease.countDown();
            await(()->"B - Artist".equals(lyrics.getLyric().getTitle()));check(!"A - Artist".equals(lyrics.getLyric().getTitle()));
        } finally {lyricRelease.countDown();lyrics.closeTosuLoader();}
        System.out.println("PASS: "+checks+" tosu snapshot, coalescing, nonblocking metadata and stale lyric checks");
    }
}
