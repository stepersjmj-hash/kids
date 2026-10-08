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

## 관례·함정
- localStorage 키: `home-playlist-v1`, `home-playlist-settings-v1` — 형식 바꾸면 키 버전 올릴 것
- 끝화면 추천은 `rel=0`으로 못 막음 → ENDED 시 덮개(#cover)로 가리고 바로 다음 영상
- onError 100/101/150 = 비공개/임베드 금지 → `bad` 표시 후 건너뜀
- file:// 로 열면 플레이어 오류가 날 수 있음 → http(s)에서 테스트
- 커밋 메시지: 한국어 한 줄 요약
