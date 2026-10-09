# kids — 아이용 유튜브 개인 재생목록

내가 고른 유튜브 영상만 연속 재생하는 정적 웹페이지. GitHub Pages(main 브랜치 루트)로 배포.

## 구성
- `index.html` — 전부 이 파일 하나 (YouTube IFrame Player API + oEmbed로 제목 조회, 외부 의존성은 Google Fonts(Jua·Gowun Dodum)뿐)
- `ic_launcher.png` — 로고 줄 아이콘 (android-tv 의 mipmap 과 같은 그림)
- `tv.html` — TV 앱 APK 로 넘어가는 짧은 주소(`/kids/tv`, meta refresh + location.replace). Downloader 에 치기 쉽게
- `privacy.html` — 개인정보처리방침 (OAuth 동의 화면 프로덕션 게시에 필요. 브랜딩 페이지의 개인정보처리방침 URL = https://stepersjmj-hash.github.io/kids/privacy.html)
- `design_handoff_kids_1b/` — "아이 손" 리디자인 시안·핸드오프(README 에 토큰·치수 확정값). git 에는 안 올림. 화면을 고칠 땐 이 문서 기준
- `playlist.json` — `?list=` 로 불러오는 공유 목록 (`[{id,title}]`)
- `README.md` — 사용자용 사용법

## 배포
- 저장소: https://github.com/stepersjmj-hash/kids (공개)
- 주소: https://stepersjmj-hash.github.io/kids/
- main에 push하면 Pages가 자동 반영 (1~2분)

## 로드맵
- B: PC 크롬 탭 전송으로 크롬캐스트 재생
- A(구현됨, `android-tv/`): Chromecast with Google TV에 WebView로 이 페이지를 띄우는 Android TV 앱(ADB 사이드로드)
- 대안 C: pychromecast YouTubeController로 TV의 공식 YouTube 앱 원격 조종 (미검증)

## Google 로그인 (방법 1)
- Google Cloud 프로젝트 `kids-player`, YouTube Data API v3, OAuth 동의 화면 **프로덕션 게시됨(2026-10-09, 심사 안 받음)**
  → 아무 구글 계정이나 로그인 가능. `youtube.readonly` 는 민감 범위라 가져오기 팝업에 "확인되지 않은 앱" 경고가 뜨며 고급 → 이동으로 진행.
  브랜딩: 홈페이지 https://stepersjmj-hash.github.io/kids/, 개인정보처리방침 /privacy.html (로고는 올리지 말 것 — 브랜드 심사 요구됨)
- 웹 클라이언트 ID는 `index.html` 의 `GOOGLE_CLIENT_ID` (공개 값). secret 은 쓰지 않으며 저장소에 넣지 말 것
- 승인된 JavaScript 원본: `https://stepersjmj-hash.github.io`, `http://localhost:8765` — 포트 바꾸면 콘솔에도 추가
- GIS 토큰 클라이언트(팝업). `requestAccessToken` 은 클릭 핸들러에서 동기 호출해야 팝업 차단을 피함 → GIS는 시작 시 미리 로드
- 좋아요: `videos?myRating=like`, 재생목록: `playlists?mine=true` → `playlistItems` (50개씩 페이지네이션)
- 로컬 실행: `serve.bat` (python http.server 8765, 127.0.0.1 바인딩). Claude 앱 브라우저 패널은 `.claude/launch.json` 의 `kids` 로 같은 서버 실행

## 로그인 UI
- 로그인 범위: `openid email profile drive.file drive.appdata`. **youtube.readonly 는 drive.file 과 한 요청에 못 넣음**
  (invalid_request "cannot be requested together") → 유튜브 가져오기는 별도 토큰 클라이언트(`ytClient`/`ytToken`, 메모리만). 두 클라이언트 모두
  `include_granted_scopes: false` 필수 (기본값 true 면 예전에 허용한 권한이 합쳐져 같은 오류)
- 계정 표시는 userinfo(v3)
- 프로필은 토큰과 함께 localStorage `home-playlist-gprofile` (로그아웃·401 시 삭제)
- 로그아웃 = 로컬 토큰 삭제만 (revoke 안 함 → 재로그인 때 동의 화면 생략). drive base 는 유지

## 구글 드라이브 동기화
- 범위 `drive.file` (Drive API 사용 설정 필요). 내 드라이브 루트의 `우리집 재생목록 (동기화).json` = `{updatedAt, items:[{id,title}]}`
  — 같은 Cloud 프로젝트의 웹·TV 클라이언트가 같이 접근. 예전 appDataFolder/playlist.json 은 첫 동기화 때 자동 이전(웹만 drive.appdata 유지)
- 기기별 마지막 동기화 상태를 localStorage `home-playlist-drive-base` 에 두고 3-way 병합(`mergeLists`):
  한쪽만 바뀌면 그쪽을 채택, 둘 다 바뀌면 로컬 + 원격 추가분 − 원격 삭제분
- 동기화 트리거: 로그인 직후, 영상 구성/순서 변경 시 1.5초 디바운스(`save()` 의 id 서명 비교 — 제목·설정 변경은 제외),
  화면 복귀(visibilitychange), 1분 주기
- 아동용(MFK) 영상은 유튜브 재생목록 저장·좋아요 기록이 막혀 있어 이 동기화가 주 경로
- 테스트: 로그인 없이 브라우저 콘솔에서 `fetch` 를 가짜 드라이브로 바꾸고 `gToken`/`gTokenExp` 설정 후 `driveSync()` 호출

## ?add= (아이폰 단축어 · PC 북마클릿)
- `?add=URL[ 공백 URL…]` → 추가 후 URL에서 add 제거. BroadcastChannel('kids-playlist') 로 열린 탭에 먼저 넘기고
  700ms 안에 ack 오면 이 탭은 window.close() (안 닫히면 새로고침) — 오래된 목록으로 localStorage 덮어쓰기 방지
- 액세스 토큰은 만료 시각까지 localStorage `home-playlist-gtoken` 에 저장 (새 탭에서도 바로 동기화). 401이면 삭제
- 북마클릿 href 는 index.html #bookmarklet (배포 주소 하드코딩)

## TV 앱 (android-tv/)
- Kotlin 단일 Activity(`MainActivity.kt`): WebView 로 `https://stepersjmj-hash.github.io/kids/?tv=1` 로드.
  재생·목록·동기화는 전부 페이지 담당 → **페이지만 고치면 앱 재설치 없이 반영** (WebView 캐시 최대 10분)
- 로그인: WebView 안 구글 로그인은 차단됨 → OAuth **기기 코드 흐름**(QR + google.com/device), 범위
  `openid email profile drive.file` (기기 코드 흐름은 drive.appdata 불가 → "Invalid device flow scope"). refresh token 은 SharedPreferences, 액세스 토큰은
  `window.kidsTvToken(t, exp)` 로 페이지에 주입(만료 10분 전 갱신). 페이지의 login/logout 은 `window.KidsTV` 로 앱 호출
- 리모컨: WebView 기본 포커스 이동이 방향키를 먹으므로 앱 `dispatchKeyEvent` 에서 가로채 `window.kidsTvKey(key)` 로 전달.
  뒤로 키는 `window.kidsTvBack()` (목록 닫았으면 true). 페이지 키 처리는 `handleKey()` 하나로 통일
- 앱(body.app)에서 ←/→ = 바의 네 버튼(이전·재생·다음·전체화면) 포커스 이동(`barIdx`/`barMark`, 노란 링 .kfocus — `barButtons()` 는 #bar 안 **보이는** button 만 세므로
  칩 버튼은 body.app 에서 display:none, #btnFull 만 `body.app #bar #btnFull` 로 다시 살려 오른쪽 아래 원 버튼으로 absolute 배치. #btnReload 는 PC 칩·폰 원 버튼, TV 는 골라 보기 칩). 가운데 = 그 버튼 클릭. ↑↓ = 골라 보기(#tvList).
  PC 키보드는 ←/→ 이전/다음 그대로
- 골라 보기: 맨 위 칩 줄(`tvChips()`: 처음부터 다시(반복)·새로고침·(앱)로그인↔프로필 사진+로그아웃(`updateAuthUI` 가 열린 골라 보기를 다시 그림) — 셔플·전체화면 칩은 시안 2판에서 뺌, 새로고침은 사용자 요청으로 복귀) + 3×2 격자(`TV_PAGE`=6, 페이지 점). 상태 `tvSel`(-1 = 칩 줄, `tvChip` 번째) /
  `tvPage`. `tvKey()` 가 2D 이동: ↓ 다음 줄(마지막 줄이면 다음 페이지, 끝이면 칩 줄로 순환), ↑ 첫 줄에서 칩 줄, 칩 줄에서 ↑는 마지막 영상, ←/→ 는 전체 순환.
  반복·셔플 칩은 눌러도 안 닫힘(`keep`)
- 앱 전체화면 = `body.full`(#frame 을 fixed 로 화면 가득, 나머지 숨김). 바의 전체화면 버튼으로 켬. 뒤로 순서: 골라 보기 닫기 → 전체화면 해제 → 앱의 "한 번 더 누르면 종료"
- PC 로고 줄 오른쪽: #btnTv(TV 모드 토글, 바에서 이동)·#btnApp(?app=1 로 이동). body.app 에선 숨김
- PC 에서 앱 화면 흉내: `?app=1` (가짜 `KidsTV` 주입 → TVAPP=true, 로그인은 토스트만, `html.sim` 으로 창 가운데 16:9 상자(960×540u)에 그려 TV 와 같은 비율).
  실제 TV(1920×1080, 밀도 2 → CSS 960×540)와 남는 차이는 글꼴 렌더링뿐(adb screencap 으로 비교 확인). 흉내 모드는 `saveSettings()` 가 tv 값을 원래대로 저장해 PC 화면을 오염시키지 않음
- ←/→ 연속 누름(`ARROW_WAIT` 200ms 안): 2번 10초 · 3번 30초 · 이후 +10초 (`seekTotal`/`seekByTotal`), 한 번 누름은 200ms 지연 후 실행. 연속 seek 는
  `seekBase + seekSum` 으로 계산 (seekTo 직후 getCurrentTime 이 안 바뀌어 누적이 안 됐던 문제)
- Chromecast 리모컨엔 **메뉴 키 없음** → 로그인 화면 새 코드는 가운데 버튼
- 페이지 최신화: 앱이 `?_v=시각` 붙여 로드, 10분 넘게 백그라운드였다 돌아오면 재로드, `KidsTV.reload()`(🔄). `settings.last` 로 보던 영상부터 이어 재생
- TV용 OAuth 클라이언트(유형 "TV 및 제한된 입력 기기")의 ID/secret 은 `android-tv/local.properties`
  (`tv.clientId`, `tv.clientSecret`) — git 제외. 공개 저장소에 절대 커밋하지 말 것
- 프로덕션 게시 전(테스트 모드)에 받은 refresh token 은 7일 만료가 붙어 있음 → 게시 후 TV 에서 다시 로그인해 새 토큰을 받아야 만료 없음

### 빌드·설치
- **배포**: GitHub Releases (`gh release create tv-vX.Y app-release.apk#kids-tv.apk`). 고정 주소
  `https://github.com/stepersjmj-hash/kids/releases/latest/download/kids-tv.apk` 를 TV 의 Downloader 앱에 넣어 설치(README). 에셋 이름은 꼭 `kids-tv.apk` 로.
  Downloader 숫자 코드 **7491178**(aftv.news/7491178, AFTVnews 단축 서비스 → 위 고정 주소). 짧은 주소 /kids/tv 도 같은 곳
  앱 소스(`android-tv/`)가 바뀐 경우에만 새 릴리스 — 페이지 변경은 재배포 불필요. versionCode/versionName 은 `app/build.gradle.kts`
- 도구(이 PC): `C:\Users\stepe\Android\` 에 jdk17 · Sdk(platform-tools, build-tools 35, platforms 35) · gradle-8.10.2
  (`%LOCALAPPDATA%` 는 Claude 앱 샌드박스에서 가상화되므로 쓰지 말 것)
- `local.properties` 의 `sdk.dir` 은 슬래시로: `sdk.dir=C:/Users/stepe/Android/Sdk` (역슬래시 이스케이프 실수 시 빌드 실패)
- `android-tv\build-install.bat <TV IP:포트>` = 빌드 + adb 설치. release 도 debug 키로 서명(사이드로드용)
- TV(sabrina, Android 14) 연결: 개발자 옵션 → **무선 디버깅** → "페어링 코드로 기기 페어링" → `adb pair IP:페어링포트 코드`
  → `adb connect IP:연결포트` (연결 포트는 무선 디버깅 화면 상단, TV 재부팅/디버깅 재시작 시 바뀜). 5555 포트는 거부됨
- 테스트 훅: `adb shell am start -n io.github.stepersjmj.kids/.MainActivity --es url "'https://...?tv=1&add=...'"`
  화면 확인 `adb exec-out screencap -p > x.png`, 키 `adb shell input keyevent KEYCODE_DPAD_DOWN`
- **테스트 후 `adb shell pm clear io.github.stepersjmj.kids`** — 테스트 영상이 로그인 시 드라이브 목록에 합쳐지는 것 방지

## 관례·함정
- 유튜브 재생목록 연결(`settings.pl`, `?pl=`): API 키 없이 숨긴 보조 YT.Player(#plLoader)를
  `playerVars.list`로 만들고 `getPlaylist()`로 ID 목록을 얻음. **기존 플레이어에 cuePlaylist 재호출은
  목록이 안 바뀜** → 매번 destroy 후 새로 생성. 실패(비공개·잘못된 ID)는 15초 타임아웃으로 처리.
  129개 재생목록까지 확인됨(상한은 미확인, 예전 기준 200개 내외로 알려짐)
- localStorage 키: `home-playlist-v1`, `home-playlist-settings-v1` — 형식 바꾸면 키 버전 올릴 것
- 높이는 `100dvh` (iOS 사파리 100vh 는 툴바 뒤까지 포함돼 목록 마지막 줄이 가려짐). 스크롤 영역(#list, #tvItems)은 flex 자식이라 `min-height:0` 필수
- 디자인: `:root` 토큰(크림 배경 `--bg`, 빨강 `--accent`, 포커스 `--focus`…)과 글꼴 Jua(제목·칩·번호, `padding-top:2px` 로 세로 보정)·Gowun Dodum(본문).
  이전·재생·다음 버튼은 시안보다 작게(사용자 요청): PC 52/68, TV 40/50(u), 폰 36/46(전체화면·새로고침 34)
  TV 화면(body.app, #tvList)은 시안 960×540 기준이라 `--u: calc(100vh/540)` 단위로 치수를 적어 WebView 해상도가 달라도 비율 유지. TV 영상 틀은 692×398(테두리 포함)로
  높이를 직접 지정 — `aspect-ratio` 는 border-box 에 걸려 16:9 내용 영역이 안 나옴
  둥근 버튼 두께 그림자는 꼭 `inset`(바깥이면 포커스 링 안에서 처져 보임). 영상 틀 #frame(흰 테두리) 안에 #player-wrap(A/B 플레이어)
- 셔플은 UI 에서 뺌(#btnShuffle 숨김, S 키 없음, 시작 때 settings.shuffle=false 고정). 코드 경로(rebuildOrder 의 셔플)는 남아 있음
- PC·폰 전체화면 = `#frame.requestFullscreen()`(영상 틀만). 거부되거나 800ms 안에 응답 없거나 API 없음(아이폰 사파리) → `setFull(true)`(body.full CSS 전체화면) + #fsExit ✕ / Esc.
  Claude 앱 내장 브라우저는 요소 전체화면을 안 받아 항상 fallback 으로 떨어짐(실제 크롬·TV·폰에서는 정상 동작 확인됨, 2026-10-09)
- 목록 순서 변경: PC 는 HTML5 DnD(`.item[draggable]`, `moveItem`), 폰(`pointer:coarse`)은 손잡이 Pointer Events — **끄는 동안 DOM 을 옮기면(insertBefore) 포인터 캡처가 풀려
  pointerup 이 안 옴** → 행은 transform 으로만 띄우고 표시선(.drop-before/after)으로 자리 표시, 손 뗄 때 `moveItem` 한 번. 리스너는 document 에 걸어 캡처와 무관하게 받음,
  행 왼쪽 스와이프 = 삭제(`attachTouch`, `.item { touch-action:pan-y }`): 행 오른쪽 바깥에 붙은 `.swipeBg`(left:100%, z-index:-1, #list overflow-x:hidden)가 드러나고
  80px 넘으면 `.willDelete`, 놓으면 `askDelete()` 확인 레이어(#confirm) → 삭제/취소. ▲▼ 버튼은 없앰
- 진행 막대 `tickProgress` 500ms + 클릭·드래그 이동(`scrubbing` 중엔 tick 멈춤, 놓을 때 seekTo), 제목 옆 점 `renderDots`(order 기준, 25개 넘으면 숫자), TV 이전·다음 썸네일 `renderDeck`, ⏯ 아이콘은 PLAYING/BUFFERING 때 정지 모양
- 유튜브 자체 UI 숨김: `controls:0`(하단 바·설정·자막 버튼 제거) + iframe 을 위아래 `--crop`(72px)만큼 키워 틀 밖으로 밀어 마우스 올림 때 제목 줄·로고를 가림(영상은 16:9 로 가운데 맞춰져 안 잘림)
  + PAUSED 때 `showCover('일시정지', id)` 로 영상 썸네일(maxresdefault → 없으면 mqdefault) 덮개(유튜브 일시정지 추천 화면 가림). 덮개는 pointer-events:none 이라 눌러서 재개 가능.
  영상에 박힌 자막·워터마크는 못 지움
- 폰(≤820px)은 세로 한 줄: 영상 틀 → 진행 막대 → 작은 이전·재생·다음(칩 없음) → 제목·동기화 알약 → 붙여넣기 카드 → 목록. 도구는 "도구 보기" 로 접힘.
  폰은 settings.tv 가 true 여도 목록을 보여 줌. 폰 스크롤은 #side 전체(제목·카드·목록 함께, #list 는 flex:none) — 영상+버튼(#stage)만 고정
- 자막: `cc_load_policy:0` 만으로는 자동 생성 자막이 켜짐 → PLAYING 때마다 `unloadModule('captions')`(`captionsOff`). 영상에 박힌 자막은 못 지움
- 광고 음소거: 앞 광고는 덮개 아래에서 소리만 남 → loadVideoById 직전 mute(`adMute`), 본영상 PLAYING 때 unMute(`adUnmute`).
  IFrame API 엔 광고 상태가 없어 중간 광고는 못 막음. **광고는 onStateChange 를 내지 않음** → PLAYING = 본영상 시작 (아래 미리 받기도 이 가정)
- 다음 영상 미리 받기(`PRELOAD`, 앱에선 기본 켜짐·`?preload=1|0`): 플레이어 A/B 두 개를 #player-wrap 에 겹쳐 두고 `player`(활성)/`spare`(대기).
  활성이 PLAYING 되면 `preloadNext()` 가 `peekNext()` 영상을 대기 쪽에 음소거로 loadVideoById → 광고 소진 → PLAYING 오면 pause+seekTo(0) (`spareState` idle→loading→ready).
  `playIndex` 에서 대상 id 가 `spareId` 와 같으면 `swapToSpare()` 로 교대(ready 면 unMute+play, loading 이면 음소거 유지하고 PLAYING 때 해제), 다르면 기존대로 활성에 load.
  대기 쪽은 `.standby`(visibility:hidden — display:none 이면 재생 안 됨) 로 숨김. 이벤트는 `e.target === player` 로 활성/대기 구분.
  셔플 재구성·목록 편집으로 순서가 바뀌면 교대 조건이 안 맞아 자동으로 기존 경로로 떨어짐. 로컬 테스트: `?preload=1&add=영상ID 영상ID`
- 프리미엄: 임베드 플레이어는 유튜브 로그인 쿠키가 있어야 광고 제거 → TV 앱(WebView)·아이폰 사파리는 불가, 사용자는 광고 감수(B안) 선택
- 끝화면 추천은 `rel=0`으로 못 막음 → ENDED 시 덮개(#cover)로 가리고 바로 다음 영상
- onError 100/101/150 = 비공개/임베드 금지 → `bad` 표시 후 건너뜀
- file:// 로 열면 플레이어 오류가 날 수 있음 → http(s)에서 테스트
- 커밋 메시지: 한국어 한 줄 요약
