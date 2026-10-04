"""Run the actual Java server and shipped detector against a synthetic local tosu.
No real player, user settings or normal server port is modified. Uses two random loopback ports.
"""
import argparse, base64, binascii, json, shutil, socket, struct, subprocess, threading, time, uuid, zlib
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

parser=argparse.ArgumentParser()
parser.add_argument('--java-home',required=True)
args=parser.parse_args()
root=Path(__file__).resolve().parents[1]
work=root/'target'/('tosu-e2e-'+uuid.uuid4().hex[:8]);work.mkdir(parents=True)
shutil.copytree(root/'Assets',work/'Assets')
(work/'Settings').mkdir()
def png(red,blue):
    def chunk(t,b): return struct.pack('>I',len(b))+t+b+struct.pack('>I',binascii.crc32(t+b)&0xffffffff)
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>2I5B',1,1,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(bytes([0,red,0,blue,255])))+chunk(b'IEND',b'')
red,blue=png(255,0),png(0,255)
state={'title':'A','position':12000,'paused':False,'ok':True,'withCover':False}
cover_started=threading.Event()
requests=[]
class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path=='/json/v2':
            if not state['ok']: self.send_error(500,'osu unavailable');return
            title=state['title']
            data=json.dumps({'game':{'paused':state['paused']},'state':{'number':5},
                'beatmap':{'title':title,'artist':'Artist','time':{'live':state['position'],'mp3Length':180000}},
                'directPath':{'beatmapAudio':title+'/audio.mp3','beatmapBackground':title+'/backgroundhash' if state['withCover'] else ''}}).encode()
            content='application/json'
        elif self.path.startswith('/files/beatmap/'):
            requests.append(self.path)
            if '/A/' in self.path: cover_started.set();time.sleep(1.2);data=red
            else: data=blue
            content='application/octet-stream' if '/A/' in self.path else None
        else: self.send_error(404);return
        self.send_response(200)
        if content is not None:self.send_header('Content-Type',content)
        self.send_header('Content-Length',str(len(data)));self.end_headers()
        try:self.wfile.write(data)
        except (BrokenPipeError,ConnectionResetError):pass
    def log_message(self,*args):pass
mock=ThreadingHTTPServer(('127.0.0.1',0),Handler)
threading.Thread(target=mock.serve_forever,daemon=True).start()
with socket.socket() as s:s.bind(('127.0.0.1',0));port=s.getsockname()[1]
config=dict(deviceId='default',platform='tosu',tosuPort=mock.server_port,splayerNextPort=14558,autoLaunchHomePage=False,
    runAtStartup=False,updateCheckFreq=7,smtc=True,fallbackPlatformEnabled=False,fallbackPlatform='netease',pollInterval=100,weSingCachePath='')
(work/'Settings/settings.json').write_text(json.dumps(config),encoding='utf-8')
base='http://127.0.0.1:'+str(port)
def get(path):
    with urllib.request.urlopen(base+path,timeout=2) as r:return json.load(r)
def wait_for(predicate,timeout=12):
    end=time.monotonic()+timeout
    while time.monotonic()<end:
        if process.poll() is not None: raise AssertionError('Server exited; see '+str(work/'server.log'))
        try:
            value=predicate()
            if value:return value
        except (OSError,ValueError):pass
        time.sleep(.05)
    raise AssertionError('Timed out; see '+str(work/'server.log'))
def convert_cover(reference):
    request=urllib.request.Request(base+'/cover/convert',data=json.dumps({'cover_url':reference}).encode(),headers={'Content-Type':'application/json'})
    with urllib.request.urlopen(request,timeout=6) as response:return json.load(response)['base64Img']
conversions=ThreadPoolExecutor(max_workers=2)
log=open(work/'server.log','wb')
process=subprocess.Popen([str(Path(args.java_home)/'bin/java.exe'),'-jar',str(root/'target/now-playing-0.0.1-SNAPSHOT.jar'),
    '--server.address=127.0.0.1','--server.port='+str(port)],cwd=work,stdout=log,stderr=subprocess.STDOUT,
    creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
try:
    wait_for(lambda:get('/api/query/track')['title']=='A',30)
    state['withCover']=True
    assert cover_started.wait(3)
    first_a_url=get('/api/query/track')['cover']
    assert first_a_url.startswith('/api/cover/tosu/current?v=')
    pending_a=conversions.submit(convert_cover,first_a_url)
    # A's background is still loading. Metadata must bypass it and all obsolete intermediate songs.
    start=time.monotonic()
    for i in range(30):state['title']='skip'+str(i)
    state['title']='B';state['position']=45000
    wait_for(lambda:get('/api/query/track')['title']=='B',2)
    assert time.monotonic()-start<2
    first_b_url=get('/api/query/track')['cover']
    assert first_b_url.startswith('/api/cover/tosu/current?v=')
    assert first_a_url!=first_b_url
    pending_b=conversions.submit(convert_cover,first_b_url)
    wait_for(lambda:get('/api/query/player')['seekbarCurrentPosition']==45)
    # Legacy widgets convert the FIRST cover reference only once per title, before it is ready.
    # Both that request and a delayed old-title request must finish with the latest image.
    expected='data:image/png;base64,'+base64.b64encode(blue).decode()
    assert pending_b.result(timeout=7)==expected
    assert pending_a.result(timeout=7)==expected
    assert get('/api/query/track')['cover']==first_b_url
    with urllib.request.urlopen(base+first_b_url,timeout=6) as image:
        assert image.read()==blue
        assert image.headers.get('Content-Type')=='image/png'
        assert 'no-store' in image.headers.get('Cache-Control','')
    assert len(requests)<=3,requests
    state['paused']=True
    wait_for(lambda:get('/api/query/player')['isPaused'] is True)
    state['paused']=False;state['position']=500
    wait_for(lambda:get('/api/query/progress')['progress']==500)
    state['ok']=False
    wait_for(lambda:get('/api/query/player')['hasSong'] is False)
    assert get('/api/query/track')['title']==''
    state['ok']=True;state['title']='Recovered'
    wait_for(lambda:get('/api/query/track')['title']=='Recovered')
    print('PASS: full Java + detector chain: menu metadata, one-shot legacy cover, delayed/stale artwork, rapid selection, seek, pause, disconnect/reconnect')
    print('Isolated log:',work/'server.log')
finally:
    process.terminate()
    try:process.wait(timeout=8)
    except subprocess.TimeoutExpired:process.kill();process.wait()
    conversions.shutdown(wait=True);log.close();mock.shutdown();mock.server_close()
