"""Read live tosu and verify the real detector -> Java -> artwork pipeline in an isolated server.
Only GET requests are made to tosu. Does not control osu! or use the normal Now Playing port/settings.
"""
import argparse,base64,hashlib,json,shutil,socket,subprocess,time,uuid
import urllib.request,urllib.parse
from pathlib import Path
p=argparse.ArgumentParser()
p.add_argument('--java-home',required=True)
p.add_argument('--tosu-port',type=int,default=24050)
a=p.parse_args()
root=Path(__file__).resolve().parents[1]
tosu='http://127.0.0.1:'+str(a.tosu_port)
def read_json(url):
    with urllib.request.urlopen(url,timeout=3) as r:return json.load(r)
assert read_json(tosu+'/json/v2').get('beatmap'), 'tosu has no beatmap'
work=root/'target'/('tosu-live-artwork-'+uuid.uuid4().hex[:8]);work.mkdir()
shutil.copytree(root/'Assets',work/'Assets');(work/'Settings').mkdir()
(work/'Settings/settings.json').write_text(json.dumps(dict(platform='tosu',tosuPort=a.tosu_port,
    deviceId='default',autoLaunchHomePage=False,runAtStartup=False,fallbackPlatformEnabled=False,
    fallbackPlatform='netease',updateCheckFreq=7,smtc=True,pollInterval=100,splayerNextPort=14558,weSingCachePath='')),encoding='utf-8')
with socket.socket() as s:s.bind(('127.0.0.1',0));port=s.getsockname()[1]
log=open(work/'server.log','wb')
process=subprocess.Popen([str(Path(a.java_home)/'bin/java.exe'),'-jar',str(root/'target/now-playing-0.0.1-SNAPSHOT.jar'),
    '--server.address=127.0.0.1','--server.port='+str(port)],cwd=work,stdout=log,stderr=subprocess.STDOUT,
    creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
try:
    deadline=time.monotonic()+35
    last_error='waiting for artwork'
    while time.monotonic()<deadline:
        if process.poll() is not None:raise AssertionError('Server exited; see '+str(work/'server.log'))
        try:
            track=read_json('http://127.0.0.1:'+str(port)+'/api/query/track')
            cover=track.get('cover','')
            if not cover.startswith('/api/cover/tosu/current'):time.sleep(.2);continue
            request=urllib.request.Request('http://127.0.0.1:'+str(port)+'/cover/convert',
                data=json.dumps({'cover_url':cover}).encode(),headers={'Content-Type':'application/json'})
            with urllib.request.urlopen(request,timeout=6) as result:cover=json.load(result)['base64Img']
            if not cover.startswith('data:image/'):time.sleep(.2);continue
            live=read_json(tosu+'/json/v2')
            expected_title=live['beatmap'].get('titleUnicode') or live['beatmap'].get('title')
            if track.get('title')!=expected_title:time.sleep(.2);continue
            path=live['directPath']['beatmapBackground'].replace('\\','/')
            url=tosu+'/files/beatmap/'+urllib.parse.quote(path,safe='/')
            with urllib.request.urlopen(url,timeout=3) as response:
                expected=response.read();header=response.headers.get('Content-Type')
            actual=base64.b64decode(cover.split(',',1)[1],validate=True)
            if actual!=expected:time.sleep(.2);continue
            print('PASS: live '+str(live.get('client'))+' artwork reaches the actual Java API')
            print('tosu Content-Type:',repr(header),'output:',cover.split(';',1)[0],'bytes:',len(actual))
            print('SHA256:',hashlib.sha256(actual).hexdigest())
            print('Isolated log:',work/'server.log')
            break
        except (OSError,ValueError,KeyError) as error:last_error=str(error)
        time.sleep(.2)
    else:raise AssertionError('Live artwork not verified: '+last_error+'; see '+str(work/'server.log'))
finally:
    process.terminate()
    try:process.wait(timeout=8)
    except subprocess.TimeoutExpired:process.kill();process.wait()
    log.close()
