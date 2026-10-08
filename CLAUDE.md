# kids — 아이용 유튜브 개인 재생목록

내가 고른 유튜브 영상만 연속 재생하는 정적 웹페이지. GitHub Pages(main 브랜치 루트)로 배포.

## 구성
- `index.html` — 전부 이 파일 하나 (YouTube IFrame Player API + oEmbed로 제목 조회, 외부 의존성 없음)
- `playlist.json` — `?list=` 로 불러오는 공유 목록 (`[{id,title}]`)
- `README.md` — 사용자용 사용법

## 배포
- 저장소: https://github.com/stepersjmj-hash/kids (공개)
- 주소: https://stepersjmj-hash.github.io/kids/
- main에 push하면 Pages가 자동 반영 (1~2분)

## 로드맵
- B(현재): PC 크롬 탭 전송으로 크롬캐스트 재생
- A(다음): Chromecast with Google TV에 WebView로 이 페이지를 띄우는 Android TV 앱(ADB 사이드로드).
  키 처리는 이미 D-pad(←/→/Enter)와 Media 키에 맞춰 둠.
- 대안 C: pychromecast YouTubeController로 TV의 공식 YouTube 앱 원격 조종 (미검증)

## Google 로그인 (방법 1)
- Google Cloud 프로젝트 `kids-player`, YouTube Data API v3, OAuth 동의 화면 **테스트 모드**
  (테스트 사용자만 로그인 가능), 범위 `youtube.readonly`
- 웹 클라이언트 ID는 `index.html` 의 `GOOGLE_CLIENT_ID` (공개 값). secret 은 쓰지 않으며 저장소에 넣지 말 것
- 승인된 JavaScript 원본: `https://stepersjmj-hash.github.io`, `http://localhost:8765` — 포트 바꾸면 콘솔에도 추가
- GIS 토큰 클라이언트(팝업). `requestAccessToken` 은 클릭 핸들러에서 동기 호출해야 팝업 차단을 피함 → GIS는 시작 시 미리 로드
- 좋아요: `videos?myRating=like`, 재생목록: `playlists?mine=true` → `playlistItems` (50개씩 페이지네이션)
- 로컬 실행: `serve.bat` (python http.server 8765, 127.0.0.1 바인딩)

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
- 앱(body.app)에서 ←/→ = 하단 바 버튼 포커스 이동(`barIdx`/`barMark`, 노란 .kfocus), 가운데 = 그 버튼 클릭. ↑↓ = 목록(맨 위에
  🔄 새로고침·🔑 재로그인 기능 줄, `tvActions`). PC 키보드는 ←/→ 이전/다음 그대로
- 앱 전체화면 = `body.full`(하단 바 숨김, ⛶ 버튼). 뒤로 순서: 목록 닫기 → 전체화면 해제 → 앱의 "한 번 더 누르면 종료"
- ←/→ 연속 누름(`ARROW_WAIT` 200ms 안): 2번 10초 · 3번 30초 · 이후 +10초 (`seekTotal`/`seekByTotal`), 한 번 누름은 200ms 지연 후 실행. 연속 seek 는
  `seekBase + seekSum` 으로 계산 (seekTo 직후 getCurrentTime 이 안 바뀌어 누적이 안 됐던 문제)
- Chromecast 리모컨엔 **메뉴 키 없음** → 로그인 화면 새 코드는 가운데 버튼
- 페이지 최신화: 앱이 `?_v=시각` 붙여 로드, 10분 넘게 백그라운드였다 돌아오면 재로드, `KidsTV.reload()`(🔄). `settings.last` 로 보던 영상부터 이어 재생
- TV용 OAuth 클라이언트(유형 "TV 및 제한된 입력 기기")의 ID/secret 은 `android-tv/local.properties`
  (`tv.clientId`, `tv.clientSecret`) — git 제외. 공개 저장소에 절대 커밋하지 말 것
- 테스트 모드는 refresh token 7일 만료 → 콘솔에서 앱 게시(프로덕션) 권장

### 빌드·설치
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
- TV 목록은 전부 그리고 선택 줄 `scrollIntoView({block:'nearest'})` (고정 줄 수로 자르면 화면 크기에 따라 마지막 줄 잘림)
- 자막: `cc_load_policy:0` 만으로는 자동 생성 자막이 켜짐 → PLAYING 때마다 `unloadModule('captions')`(`captionsOff`). 영상에 박힌 자막은 못 지움
- 광고 음소거: 앞 광고는 덮개 아래에서 소리만 남 → loadVideoById 직전 mute(`adMute`), 본영상 PLAYING 때 unMute(`adUnmute`).
  IFrame API 엔 광고 상태가 없어 중간 광고는 못 막음
- 프리미엄: 임베드 플레이어는 유튜브 로그인 쿠키가 있어야 광고 제거 → TV 앱(WebView)·아이폰 사파리는 불가, 사용자는 광고 감수(B안) 선택
- 끝화면 추천은 `rel=0`으로 못 막음 → ENDED 시 덮개(#cover)로 가리고 바로 다음 영상
- onError 100/101/150 = 비공개/임베드 금지 → `bad` 표시 후 건너뜀
- file:// 로 열면 플레이어 오류가 날 수 있음 → http(s)에서 테스트
- 커밋 메시지: 한국어 한 줄 요약
