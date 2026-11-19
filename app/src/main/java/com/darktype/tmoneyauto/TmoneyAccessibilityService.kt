package com.darktype.tmoneyauto

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
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

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        Log.i(TAG, "모바일티머니 자동출석 접근성 서비스 연결됨")
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        Log.i(TAG, "모바일티머니 자동출석 접근성 서비스 종료됨")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val rootNode = rootInActiveWindow ?: return
        val packageName = event?.packageName?.toString() ?: ""

        if (!packageName.contains("tmoney")) return

        val now = System.currentTimeMillis()
        if (now - lastActionTime < 1200) return // 디바운스

        // 1. 배너 팝업 닫기 ("닫기" 또는 "오늘 다시 보지 않기")
        val closeDialogNodes = rootNode.findAccessibilityNodeInfosByViewId("com.lgt.tmoney:id/dialog_close")
        if (closeDialogNodes.isNotEmpty()) {
            lastActionTime = now
            Log.i(TAG, "메인 배너 팝업 감지 -> 닫기 클릭")
            clickNodeOrGesture(closeDialogNodes[0])
            return
        }

        // 2. 출석 성공 팝업 확인 ("출석 미션 완료!" 또는 "적립되었습니다")
        val popupTitleNodes = rootNode.findAccessibilityNodeInfosByViewId("com.lgt.tmoney:id/txt_title")
        val isPopupVisible = popupTitleNodes.any { it.text?.contains("출석") == true } ||
                findNodeByTextOrDesc(rootNode, "출석 미션 완료") != null

        if (isPopupVisible) {
            lastActionTime = now
            handleResultPopup(rootNode)
            return
        }

        // 3. AttendActivity: '출석하기' 버튼 탐색 및 자동 탭
        val attendButton = findNodeByTextOrDesc(rootNode, "출석하기")
        if (attendButton != null) {
            lastActionTime = now
            Log.i(TAG, "출석하기 버튼 감지됨 -> 클릭 시도")
            lastResultStatus = "출석하기 클릭 시도 중..."
            val clicked = clickNodeOrGesture(attendButton)
            Log.i(TAG, "출석 버튼 클릭 결과: $clicked")
            return
        }

        // 4. AttendActivity: 이미 출석 완료된 상태 감지 ("출석체크 완료" 스탬프 확인)
        val completedStamp = findNodeByTextOrDesc(rootNode, "출석체크 완료")
        val attendHeader = findNodeByTextOrDesc(rootNode, "출석체크")
        if (completedStamp != null && attendHeader != null && !hasHandledToday) {
            val hasAttendBtn = findNodeByTextOrDesc(rootNode, "출석하기") != null
            if (!hasAttendBtn) {
                lastActionTime = now
                hasHandledToday = true
                lastResultStatus = "오늘 출석 이미 완료됨"
                Log.i(TAG, "오늘자 출석이 이미 완료되어 있습니다.")

                val prefs = getSharedPreferences("tmoney_auto_prefs", MODE_PRIVATE)
                val nasUrl = prefs.getString("nas_url", "https://darktype.synology.me:7878") ?: ""
                serviceScope.launch {
                    NasReporter.report(nasUrl, "already_done", "오늘 모바일티머니 출석체크가 이미 완료되어 있습니다.")
                }
                return
            }
        }

        // 5. MainEventActivity (이벤트 목록 화면): "모바일티머니 출석체크" 항목 클릭
        val eventListHeader = findNodeByTextOrDesc(rootNode, "이벤트")
        if (eventListHeader != null) {
            val attendEventItem = findNodeByTextOrDesc(rootNode, "출석체크")
            if (attendEventItem != null && !attendEventItem.text.toString().contains("완료")) {
                lastActionTime = now
                Log.i(TAG, "이벤트 목록에서 출석체크 배너 감지 -> 클릭")
                clickNodeOrGesture(attendEventItem)
                return
            } else {
                // 목록에 안 보이면 아래로 스크롤
                lastActionTime = now
                Log.i(TAG, "이벤트 목록 스크롤 탐색")
                scrollDown()
                return
            }
        }

        // 6. MainActivity (홈 화면): 이벤트 아이콘(🎁) 클릭
        val eventIconNodes = rootNode.findAccessibilityNodeInfosByViewId("com.lgt.tmoney:id/main_home_header_event")
        if (eventIconNodes.isNotEmpty()) {
            lastActionTime = now
            Log.i(TAG, "메인 홈 화면 감지 -> 이벤트(🎁) 버튼 클릭")
            clickNodeOrGesture(eventIconNodes[0])
            return
        }
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

        val prefs = getSharedPreferences("tmoney_auto_prefs", MODE_PRIVATE)
        val nasUrl = prefs.getString("nas_url", "https://darktype.synology.me:7878") ?: ""
        serviceScope.launch {
            NasReporter.report(nasUrl, "checked_in", rewardText, 1)
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

        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.width() > 0 && bounds.height() > 0) {
            val cx = bounds.centerX().toFloat()
            val cy = bounds.centerY().toFloat()
            val path = Path().apply { moveTo(cx, cy) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
                .build()
            return dispatchGesture(gesture, null, null)
        }

        return false
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
