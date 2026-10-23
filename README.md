# 🚄 KTX / SRT 자동예매 안드로이드 앱 (자동 로그인 지원)

Synology NAS(`http://darktype.synology.me:10111`)에서 동작하는 **KTX/SRT 멀티유저 자동예매 웹**을 안드로이드 전용 앱으로 패키징한 프로젝트입니다.

---

## ✨ 핵심 기능

1. **자동 로그인 (Auto-Login)**:
   - 앱 최초 실행 시 또는 로그인 화면 진입 시 계정(이메일, 비밀번호)을 입력하고 `[✓] 자동 로그인 및 계정 저장`을 체크하면 안전하게 저장됩니다.
   - 이후 **앱 재실행** 시 또는 **30분 세션 만료 후 재접속** 시 로그인 과정을 자동으로 완료하여 바로 메인 예매 화면으로 이동합니다.
   - 사용자가 웹 상에서 명시적으로 **[로그아웃]** 버튼을 눌렀을 경우, 즉시 다시 자동 로그인되어 계정 전환을 방해하는 현상(무한 로그인 루프)을 지능적으로 방지했습니다.

2. **안전한 암호화 저장**:
   - `AndroidX Security Crypto (EncryptedSharedPreferences)` 및 Android Keystore를 사용하여 비밀번호를 평문이 아닌 AES-256 하드웨어 암호화 상태로 저장합니다.

3. **HTTP Cleartext Traffic 완벽 지원**:
   - 안드로이드 9.0+(Pie)부터 기본 차단되는 HTTP(비암호화) 통신을 `android:usesCleartextTraffic="true"`로 허용하여 `http://darktype.synology.me:10111`에 문제없이 접속됩니다.

4. **네이티브 편의 기능**:
   - **당겨서 새로고침 (Swipe-to-Refresh)** 지원
   - **쿠키 및 세션 지속 유지 (CookieManager)**
   - **뒤로가기 제어**: 이전 웹 페이지 탐색 및 더블 탭 종료
   - **설정 다이얼로그 (우측 하단 FAB 버튼)**:
     - 자동 로그인 ON / OFF
     - 저장된 계정 삭제
     - 서버 주소 변경 (기본값: `http://darktype.synology.me:10111`)
     - 캐시/쿠키 전체 삭제

---

## 🛠 빌드 방법

### 방법 1: Android Studio에서 직접 빌드 (가장 추천)
1. Android Studio를 실행합니다.
2. **Open**을 누르고 `ktx-srt-android` 폴더를 엽니다.
3. Gradle Sync가 완료되면 상단 메뉴에서:
   - `Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)` 클릭
4. 빌드가 완료되면 `app/build/outputs/apk/debug/app-debug.apk` 파일이 생성됩니다.

### 방법 2: GitHub Actions 클라우드 빌드 (Android Studio 없을 때)
1. GitHub에 새 저장소를 생성합니다.
2. 이 `ktx-srt-android` 폴더 안의 파일들을 해당 저장소에 푸시(Push)합니다.
3. 저장소의 **Actions** 탭으로 이동하면 `.github/workflows/build-apk.yml`이 자동으로 실행되어 **약 1~2분 만에 최신 APK(KtxSrtAuto-Debug-APK)**를 다운로드할 수 있는 아티팩트로 제공합니다.
