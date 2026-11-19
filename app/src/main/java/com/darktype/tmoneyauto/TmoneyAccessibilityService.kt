package com.darktype.tmoneyauto

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TmoneyAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "TmoneyAccService"
        var isServiceRunning = false
            private set
        var lastResultStatus: String = "대기 중"
            private set
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var lastActionTime = 0L
    private var hasHandledToday = false
    private val handler = Handler(Looper.getMainLooper())
    private var scheduledCheck: Runnable? = null
    private var scrollCount = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        Log.i(TAG, "모바일티머니 자동출석 접근성 서비스 연결됨")
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        scheduledCheck?.let { handler.removeCallbacks(it) }
        Log.i(TAG, "모바일티머니 자동출석 접근성 서비스 종료됨")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: ""
        if (!packageName.contains("tmoney")) return

        val now = System.currentTimeMillis()
        val elapsed = now - lastActionTime

        if (elapsed < 1200) {
            // 디바운스 대기 중: 화면 전환 또는 렌더링이 진행 중일 수 있으므로 이전 예약 취소 후 재스케줄링
            scheduledCheck?.let { handler.removeCallbacks(it) }
            val check = Runnable {
                val currentRoot = rootInActiveWindow ?: return@Runnable
                try {
                    processScreen(currentRoot, System.currentTimeMillis())
                } catch (e: Exception) {
                    Log.e(TAG, "지연 화면 처리 중 예외 발생: ${e.message}", e)
                }
            }
            scheduledCheck = check
            handler.postDelayed(check, (1200 - elapsed) + 200)
            return
        }

        val rootNode = rootInActiveWindow ?: return
        try {
            processScreen(rootNode, now)
        } catch (e: Exception) {
            Log.e(TAG, "onAccessibilityEvent 처리 중 예외 발생: ${e.message}", e)
        }
    }

    private fun processScreen(rootNode: AccessibilityNodeInfo, now: Long) {
        // 1. 출석 성공/결과 팝업 확인 ("오늘 미션 참여 완료!", "출석 미션 완료!" 또는 "적립되었습니다")
        val popupTitleNodes = rootNode.findAccessibilityNodeInfosByViewId("com.lgt.tmoney:id/txt_title")
        val closeBtn = rootNode.findAccessibilityNodeInfosByViewId("com.lgt.tmoney:id/btn_close").firstOrNull()
            ?: findNodeByTextOrDesc(rootNode, "닫기")

        val isPopupVisible = (popupTitleNodes.isNotEmpty() && closeBtn != null) ||
                findNodeByTextOrDesc(rootNode, "출석 미션 완료") != null ||
                findNodeByTextOrDesc(rootNode, "미션 참여 완료") != null ||
                findNodeByTextOrDesc(rootNode, "적립되었습니다") != null

        if (isPopupVisible && closeBtn != null) {
            lastActionTime = now
            handleResultPopup(rootNode)
            return
        }

        // 2. 메인 안내 팝업 또는 이벤트 배너 닫기 ("닫기" 또는 "오늘 다시 보지 않기")
        val closeDialogNodes = rootNode.findAccessibilityNodeInfosByViewId("com.lgt.tmoney:id/dialog_close")
        if (closeDialogNodes.isNotEmpty()) {
            lastActionTime = now
            Log.i(TAG, "메인 배너 팝업 감지 -> [닫기] 클릭")
            clickNodeOrGesture(closeDialogNodes[0])
            schedulePostActionCheck(1400)
            return
        }

        // 3. 간편비밀번호 로그인 화면 (PayLoginActivity): 둘러보기 버튼이 있는 경우
        val lookAroundBtn = rootNode.findAccessibilityNodeInfosByViewId("com.lgt.tmoney:id/btn_look_around").firstOrNull()
            ?: findNodeByTextOrDesc(rootNode, "둘러보기")
        if (lookAroundBtn != null) {
            lastActionTime = now
            Log.i(TAG, "로그인 화면 둘러보기 버튼 감지 -> 클릭")
            clickNodeOrGesture(lookAroundBtn)
            schedulePostActionCheck(1400)
            return
        }

        // 4. AttendActivity: '출석하기' 버튼 탐색 및 자동 탭
        val attendButton = findNodeByTextOrDesc(rootNode, "출석하기")
        if (attendButton != null) {
            lastActionTime = now
            Log.i(TAG, "출석체크 화면 감지 -> [출석하기] 버튼 클릭 시도")
            lastResultStatus = "출석하기 클릭 중..."
            val clicked = clickNodeOrGesture(attendButton)
            Log.i(TAG, "출석 버튼 클릭 결과: $clicked")
            schedulePostActionCheck(1500)
            return
        }

        // 5. AttendActivity: 이미 출석 완료된 상태 감지 ("출석체크 완료" 스탬프 확인)
        val completedStamp = findNodeByTextOrDesc(rootNode, "출석체크 완료")
        if (completedStamp != null && !hasHandledToday) {
            val hasAttendBtn = findNodeByTextOrDesc(rootNode, "출석하기") != null
            if (!hasAttendBtn) {
                lastActionTime = now
                hasHandledToday = true
                lastResultStatus = "오늘 출석 이미 완료됨"
                Log.i(TAG, "오늘자 출석이 이미 완료되어 있습니다.")

                val prefs = getSharedPreferences("tmoney_auto_prefs", Context.MODE_PRIVATE)
                val nasUrl = prefs.getString("nas_url", "https://darktype.synology.me:7878") ?: ""
                val userId = prefs.getString("nas_user_id", "") ?: ""
                val password = prefs.getString("nas_password", "") ?: ""
                serviceScope.launch {
                    NasReporter.report(nasUrl, userId, password, "already_done", "오늘 모바일티머니 출석체크가 이미 완료되어 있습니다.")
                }
                return
            }
        }

        // 6. MainActivity (메인 홈 화면 감지 -> 🎁 이벤트 아이콘 클릭)
        // 메인 홈 화면의 고유 위젯들을 확인하여 홈 화면임을 정확히 판별
        val eventIconNodes = rootNode.findAccessibilityNodeInfosByViewId("com.lgt.tmoney:id/main_home_header_event")
        val hasHomeCharge = rootNode.findAccessibilityNodeInfosByViewId("com.lgt.tmoney:id/main_home_tmoney_charge").isNotEmpty()
        val hasHomeMileage = rootNode.findAccessibilityNodeInfosByViewId("com.lgt.tmoney:id/main_home_header_mileage").isNotEmpty()

        if (eventIconNodes.isNotEmpty() || hasHomeCharge || hasHomeMileage) {
            val targetEventBtn = if (eventIconNodes.isNotEmpty()) {
                eventIconNodes[0]
            } else {
                findNodeByTextOrDesc(rootNode, "이벤트")
            }

            if (targetEventBtn != null) {
                lastActionTime = now
                scrollCount = 0
                Log.i(TAG, "🏠 메인 홈 화면 감지 -> 상단 🎁 [이벤트] 버튼 클릭")
                lastResultStatus = "메인 홈 -> 이벤트 버튼 클릭"
                clickNodeOrGesture(targetEventBtn)
                schedulePostActionCheck(1400)
                return
            }
        }

        // 7. MainEventActivity (이벤트 목록 화면 감지 -> "모바일티머니 출석체크" 배너 클릭)
        val eventListNodes = rootNode.findAccessibilityNodeInfosByViewId("com.lgt.tmoney:id/main_event_list")
        val isEventListScreen = eventListNodes.isNotEmpty() ||
                (rootNode.findAccessibilityNodeInfosByViewId("com.lgt.tmoney:id/tv_title").any { it.text?.contains("이벤트") == true } && !hasHomeCharge)

        if (isEventListScreen) {
            val attendEventItem = findNodeByTextOrDesc(rootNode, "출석체크")
            if (attendEventItem != null) {
                val itemText = (attendEventItem.text?.toString() ?: "") + (attendEventItem.contentDescription?.toString() ?: "")
                if (!itemText.contains("완료")) {
                    lastActionTime = now
                    Log.i(TAG, "📋 이벤트 목록에서 [모바일티머니 출석체크] 배너 감지 -> 클릭")
                    lastResultStatus = "이벤트 목록 -> 출석체크 배너 클릭"
                    clickNodeOrGesture(attendEventItem)
                    schedulePostActionCheck(1500)
                    return
                }
            } else if (scrollCount < 8) {
                // 목록에 보이지 않으면 아래로 스크롤하여 탐색 (최대 8회)
                lastActionTime = now
                scrollCount++
                Log.i(TAG, "이벤트 목록 스크롤 탐색 중... ($scrollCount/8)")
                lastResultStatus = "이벤트 목록 스크롤 중 ($scrollCount/8)"
                scrollDown()
                schedulePostActionCheck(1500)
                return
            }
        }

        // 8. "확인" 또는 "닫기" 일반 다이얼로그 닫기
        val dialogRight = rootNode.findAccessibilityNodeInfosByViewId("com.lgt.tmoney:id/dialog_right").firstOrNull()
        if (dialogRight != null && dialogRight.text?.contains("확인") == true) {
            val content = rootNode.findAccessibilityNodeInfosByViewId("com.lgt.tmoney:id/dialog_content").firstOrNull()?.text?.toString() ?: ""
            if (content.contains("다시 시작") || content.contains("로그인")) {
                lastActionTime = now
                Log.i(TAG, "재시작/로그인 안내 팝업 감지 -> 확인 클릭")
                clickNodeOrGesture(dialogRight)
                schedulePostActionCheck(1400)
                return
            }
        }
    }

    private fun schedulePostActionCheck(delayMs: Long) {
        scheduledCheck?.let { handler.removeCallbacks(it) }
        val check = Runnable {
            val root = rootInActiveWindow ?: return@Runnable
            try {
                processScreen(root, System.currentTimeMillis())
            } catch (e: Exception) {
                Log.e(TAG, "스케줄된 화면 처리 중 예외 발생: ${e.message}", e)
            }
        }
        scheduledCheck = check
        handler.postDelayed(check, delayMs)
    }

    private fun handleResultPopup(root: AccessibilityNodeInfo) {
        val contentNodes = root.findAccessibilityNodeInfosByViewId("com.lgt.tmoney:id/txt_contents1")
        var rewardText = "1M 적립되었습니다."
        if (contentNodes.isNotEmpty()) {
            rewardText = contentNodes[0].text?.toString() ?: rewardText
        }

        Log.i(TAG, "🎉 출석 성공 팝업 확인: $rewardText")
        lastResultStatus = "출석 성공: $rewardText"
        hasHandledToday = true

        // 닫기 버튼 클릭
        val closeNodes = root.findAccessibilityNodeInfosByViewId("com.lgt.tmoney:id/btn_close")
        if (closeNodes.isNotEmpty()) {
            clickNodeOrGesture(closeNodes[0])
        } else {
            val closeBtn = findNodeByTextOrDesc(root, "닫기")
            if (closeBtn != null) clickNodeOrGesture(closeBtn)
        }

        val prefs = getSharedPreferences("tmoney_auto_prefs", Context.MODE_PRIVATE)
        val nasUrl = prefs.getString("nas_url", "https://darktype.synology.me:7878") ?: ""
        val userId = prefs.getString("nas_user_id", "") ?: ""
        val password = prefs.getString("nas_password", "") ?: ""
        val statusType = if (rewardText.contains("내일도") || rewardText.contains("이미") || rewardText.contains("완료")) "already_done" else "checked_in"
        val points = if (statusType == "checked_in") 1 else 0
        serviceScope.launch {
            NasReporter.report(nasUrl, userId, password, statusType, rewardText, points)
        }
    }

    private fun scrollDown() {
        val path = Path().apply {
            moveTo(540f, 1600f)
            lineTo(540f, 600f)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun clickNodeOrGesture(node: AccessibilityNodeInfo): Boolean {
        // 1. 화면 실제 좌표(중심점)로 터치 제스처 디스패치 (가장 확실한 터치)
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.width() > 0 && bounds.height() > 0) {
            val cx = bounds.centerX().toFloat()
            val cy = bounds.centerY().toFloat()
            val path = Path().apply { moveTo(cx, cy) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
                .build()
            dispatchGesture(gesture, null, null)
        }

        // 2. 접근성 ACTION_CLICK 동시 호출
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }

        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            parent = parent.parent
        }

        return true
    }

    private fun findNodeByTextOrDesc(node: AccessibilityNodeInfo, query: String): AccessibilityNodeInfo? {
        val desc = node.contentDescription?.toString() ?: ""
        val text = node.text?.toString() ?: ""
        if (desc.contains(query, ignoreCase = true) || text.contains(query, ignoreCase = true)) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByTextOrDesc(child, query)
            if (found != null) return found
        }
        return null
    }

    override fun onInterrupt() {
        Log.w(TAG, "접근성 서비스 중단됨")
    }
}
