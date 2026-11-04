#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
mp3 도우미 — PC 에서 하이쮸 플레이어의 "mp3 다운로드" 버튼이 부르는 작은 로컬 서버 (NAS 의 Reelsnap /audio 와 같은 API, NAS 가 안 될 때의 예비).
  GET /ping                                  → {"ok": true}
  GET /audio?url=영상ID또는URL&start=초&fn=이름 → yt-dlp + ffmpeg 로 소리만 mp3(128k) 로 뽑아 파일로 응답 (start 초부터 끝까지, 0 이면 전체)
  GET /mp3?id=영상ID&start=초                 → 같은 동작 (예전 주소)
127.0.0.1 에만 묶여 있어 이 PC 의 브라우저만 접근할 수 있다. 실행: mp3.bat (또는 python mp3-server.py)
필요: yt-dlp, ffmpeg 가 PATH 에 있어야 함 (winget install yt-dlp.yt-dlp / Gyan.FFmpeg)
"""
import json, os, re, shutil, subprocess, sys, tempfile, threading, urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = 8770
ID_RE = re.compile(r'^[A-Za-z0-9_-]{11}$')
URL_ID_RE = re.compile(r'(?:v=|youtu\.be/|shorts/|embed/)([A-Za-z0-9_-]{11})')

def find(exe):
    p = shutil.which(exe)
    if not p:
        raise RuntimeError(f'{exe} 를 찾을 수 없습니다. 설치 후 PATH 에 넣어 주세요 (winget install {"yt-dlp.yt-dlp" if exe == "yt-dlp" else "Gyan.FFmpeg"})')
    return p

def extract(vid, start, fn=''):
    """yt-dlp 로 mp3 를 만들어 (경로, 파일명) 을 돌려준다. 임시 폴더는 호출한 쪽이 지운다."""
    ytdlp = find('yt-dlp'); find('ffmpeg')
    tmp = tempfile.mkdtemp(prefix='kids-mp3-')
    safe = re.sub(r'[\\/:*?"<>|\r\n]+', ' ', fn).strip()[:120]
    # 1) yt-dlp 로 원본 오디오(m4a)만 받고 2) ffmpeg 로 자르기+mp3 128k 변환 (NAS 의 Reelsnap /audio 와 같은 방식)
    cmd = [ytdlp, '--no-playlist', '-f', 'bestaudio[ext=m4a]/bestaudio',
           '-o', os.path.join(tmp, 'a.%(ext)s'), '--no-mtime', f'https://www.youtube.com/watch?v={vid}']
    print('>', ' '.join(cmd), flush=True)
    r = subprocess.run(cmd, capture_output=True, text=True, encoding='utf-8', errors='replace')
    src = [f for f in os.listdir(tmp) if f.startswith('a.')] if r.returncode == 0 else []
    if not src:
        shutil.rmtree(tmp, ignore_errors=True)
        raise RuntimeError((r.stderr or r.stdout).strip().splitlines()[-1] if (r.stderr or r.stdout).strip() else 'yt-dlp 실패')
    name = (safe or vid) + '.mp3'
    ff = ['ffmpeg', '-y', '-hide_banner', '-loglevel', 'error']
    if start > 0:
        ff += ['-ss', str(start)]   # 시작 시각부터 끝까지
    ff += ['-i', os.path.join(tmp, src[0]), '-vn', '-codec:a', 'libmp3lame', '-b:a', '128k', os.path.join(tmp, name)]
    r = subprocess.run(ff, capture_output=True, text=True, encoding='utf-8', errors='replace')
    if r.returncode != 0 or not os.path.exists(os.path.join(tmp, name)):
        shutil.rmtree(tmp, ignore_errors=True)
        raise RuntimeError('mp3 변환 실패 (ffmpeg): ' + r.stderr.strip()[-200:])
    return tmp, name

class H(BaseHTTPRequestHandler):
    def cors(self):
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Expose-Headers', 'Content-Disposition')

    def send_json(self, code, obj):
        body = json.dumps(obj, ensure_ascii=False).encode('utf-8')
        self.send_response(code); self.cors()
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(body))); self.end_headers(); self.wfile.write(body)

    def do_OPTIONS(self):
        self.send_response(204); self.cors(); self.end_headers()

    def do_GET(self):
        u = urllib.parse.urlsplit(self.path); q = urllib.parse.parse_qs(u.query)
        if u.path == '/ping':
            return self.send_json(200, {'ok': True})
        if u.path not in ('/mp3', '/audio'):
            return self.send_json(404, {'error': 'not found'})
        vid = (q.get('id') or q.get('url') or [''])[0].strip()
        m = URL_ID_RE.search(vid)
        if m: vid = m.group(1)
        fn = (q.get('fn') or [''])[0]
        try: start = max(0, int(float((q.get('start') or ['0'])[0])))
        except ValueError: start = 0
        if not ID_RE.match(vid):
            return self.send_json(400, {'error': '영상 ID 형식이 아닙니다'})
        try:
            tmp, name = extract(vid, start, fn)
        except Exception as e:
            print('!', e, flush=True)
            return self.send_json(500, {'error': str(e)})
        path = os.path.join(tmp, name)
        try:
            size = os.path.getsize(path)
            self.send_response(200); self.cors()
            self.send_header('Content-Type', 'audio/mpeg')
            self.send_header('Content-Length', str(size))
            self.send_header('Content-Disposition', "attachment; filename*=UTF-8''" + urllib.parse.quote(name))
            self.end_headers()
            with open(path, 'rb') as f:
                shutil.copyfileobj(f, self.wfile)
            print(f'  보냄: {name} ({size // 1024} KB, start={start})', flush=True)
        finally:
            shutil.rmtree(tmp, ignore_errors=True)

    def log_message(self, fmt, *args):   # 기본 로그는 조용히
        pass

if __name__ == '__main__':
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except Exception:
        pass
    for exe in ('yt-dlp', 'ffmpeg'):
        try: find(exe)
        except RuntimeError as e: print('!', e); sys.exit(1)
    srv = ThreadingHTTPServer(('127.0.0.1', PORT), H)
    print(f'mp3 도우미 실행 중: http://127.0.0.1:{PORT}  (하이쮸 플레이어 PC 화면의 "mp3 다운로드" 가 여기로 옵니다. 끝내려면 Ctrl+C)', flush=True)
    try: srv.serve_forever()
    except KeyboardInterrupt: pass
