package com.example.data

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.example.accessibility.sendbutton.SendButtonDetectorCriteria
import com.example.accessibility.sendbutton.SendButtonStatus
import com.example.accessibility.sendbutton.SendButtonVerificationResult
import com.example.repository.SendButtonDetectorRepository

/**
 * Concrete implementation of SendButtonDetectorRepository.
 * Traverses Accessibility tree, identifies send button candidates, evaluates proximity to composer,
 * and verifies that the node is Clickable, Visible, Enabled, and Beside Composer.
 */
class SendButtonDetectorRepositoryImpl : SendButtonDetectorRepository {

    companion object {
        private val KNOWN_SEND_BUTTON_IDS = listOf(
            "com.imo.android.imoim:id/send_btn",
            "com.imo.android.imoim:id/send",
            "com.imo.android.imoim:id/btn_send",
            "com.imo.android.imoim:id/ib_send",
            "com.imo.android.imoim:id/iv_send",
            "com.imo.android.imoim:id/action_send",
            "com.imo.android.imoim:id/im_send",
            "com.imo.android.imoim:id/send_button",
            "com.imo.android.imoimlite:id/send_btn",
            "com.imo.android.imoimlite:id/send",
            "com.imo.android.imoimlite:id/btn_send",
            "send_btn",
            "send",
            "btn_send",
            "action_send"
        )

        private val SEND_TEXT_KEYWORDS = listOf(
            "send", "send message", "পাঠান", "إرسال", "kirim", "enviar"
        )
    }

    override suspend fun findAndVerifySendButton(
        rootNode: AccessibilityNodeInfo?,
        criteria: SendButtonDetectorCriteria
    ): SendButtonVerificationResult {
        if (rootNode == null) {
            return SendButtonVerificationResult(
                status = SendButtonStatus.MISSING,
                isVerified = false,
                reason = "Missing: Accessibility root node is null.",
                details = "Cannot inspect tree when root node is null."
            )
        }

        // 1. Collect potential candidate nodes across Accessibility tree
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        collectSendButtonCandidates(rootNode, candidates)

        if (candidates.isEmpty()) {
            return SendButtonVerificationResult(
                status = SendButtonStatus.MISSING,
                isVerified = false,
                candidateCount = 0,
                reason = "Missing: No candidate send button nodes found in accessibility tree.",
                details = "Searched entire UI tree. Zero send button candidates identified."
            )
        }

        val candidateCount = candidates.size
        val evaluatedCandidates = mutableListOf<SendButtonCandidateEvaluation>()

        // 2. Evaluate each candidate
        for (node in candidates) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            val resId = node.viewIdResourceName ?: ""
            val className = node.className?.toString() ?: ""
            val contentDesc = node.contentDescription?.toString() ?: ""
            val text = node.text?.toString() ?: ""

            val isClickable = node.isClickable || (node.parent?.isClickable == true)
            val isEnabled = node.isEnabled
            val isVisible = node.isVisibleToUser || (bounds.width() > 0 && bounds.height() > 0)

            // Proximity Check: Beside Message Composer
            val isBesideComposer = checkBesideComposer(bounds, criteria)

            var score = 0
            if (KNOWN_SEND_BUTTON_IDS.any { resId.contains(it, ignoreCase = true) }) score += 60
            if (SEND_TEXT_KEYWORDS.any { contentDesc.contains(it, ignoreCase = true) || text.contains(it, ignoreCase = true) }) score += 40
            if (isBesideComposer) score += 30
            if (isClickable) score += 20
            if (isEnabled) score += 10
            if (isVisible) score += 10

            evaluatedCandidates.add(
                SendButtonCandidateEvaluation(
                    node = AccessibilityNodeInfo.obtain(node),
                    bounds = bounds,
                    resId = resId,
                    className = className,
                    contentDesc = contentDesc,
                    isClickable = isClickable,
                    isEnabled = isEnabled,
                    isVisible = isVisible,
                    isBesideComposer = isBesideComposer,
                    score = score
                )
            )
        }

        // Clean up unneeded collected candidates
        candidates.forEach { it.recycle() }

        // Filter candidates that meet all 4 mandatory criteria
        val validCandidates = evaluatedCandidates.filter {
            it.isClickable && it.isVisible && it.isEnabled && it.isBesideComposer
        }

        if (validCandidates.isEmpty()) {
            // Find best candidate for details reporting
            val bestCandidate = evaluatedCandidates.maxByOrNull { it.score }
            val reasons = mutableListOf<String>()
            if (bestCandidate != null) {
                if (!bestCandidate.isClickable) reasons.add("Node is not clickable")
                if (!bestCandidate.isVisible) reasons.add("Node is not visible")
                if (!bestCandidate.isEnabled) reasons.add("Node is disabled")
                if (!bestCandidate.isBesideComposer) reasons.add("Node is not located beside message composer")
            }

            // Recycle obtained nodes
            evaluatedCandidates.forEach { it.node.recycle() }

            return SendButtonVerificationResult(
                status = SendButtonStatus.MISSING,
                isVerified = false,
                candidateCount = candidateCount,
                reason = "Missing: Candidate send button(s) detected ($candidateCount total) but failed verification checks: ${reasons.joinToString("; ")}.",
                details = "Rejection reasons: ${reasons.joinToString(", ")}"
            )
        }

        // Pick the top scoring verified candidate
        val chosenCandidate = validCandidates.maxByOrNull { it.score }!!

        // Recycle unused candidate nodes
        evaluatedCandidates.forEach {
            if (it != chosenCandidate) {
                it.node.recycle()
            }
        }

        return SendButtonVerificationResult(
            status = SendButtonStatus.VERIFIED,
            isVerified = true,
            sendButtonNode = chosenCandidate.node,
            bounds = chosenCandidate.bounds,
            nodeId = chosenCandidate.resId.ifBlank { "IMO_Send_Button_Node" },
            className = chosenCandidate.className,
            contentDescription = chosenCandidate.contentDesc,
            isClickable = chosenCandidate.isClickable,
            isVisible = chosenCandidate.isVisible,
            isEnabled = chosenCandidate.isEnabled,
            isBesideComposer = chosenCandidate.isBesideComposer,
            candidateCount = candidateCount,
            reason = "Verified: Located active IMO send button (ID: '${chosenCandidate.resId}', Bounds: ${chosenCandidate.bounds}).",
            details = "Verified Clickable=true, Visible=true, Enabled=true, BesideComposer=true."
        )
    }

    private fun checkBesideComposer(bounds: Rect, criteria: SendButtonDetectorCriteria): Boolean {
        val composerBounds = criteria.composerInputBounds
        if (composerBounds != null && composerBounds.width() > 0) {
            // Horizontal check: Button is to the right or immediately adjacent to composer
            val horizontalDistance = Math.abs(bounds.left - composerBounds.right)
            val verticalOffset = Math.abs(bounds.centerY() - composerBounds.centerY())

            val isAdjacentRight = bounds.left >= (composerBounds.right - 50) && horizontalDistance <= criteria.maxHorizontalDistancePx
            val isVerticallyAligned = verticalOffset <= criteria.maxVerticalOffsetPx

            return isAdjacentRight && isVerticallyAligned
        } else {
            // Fallback heuristic if composer bounds not explicitly passed:
            // Send button in IMO chat is at bottom right (Y > 65% height, X > 60% width)
            val isBottomScreen = bounds.top > (criteria.screenHeightPx * 0.65f)
            val isRightSide = bounds.left > (criteria.screenWidthPx * 0.55f)
            return isBottomScreen && isRightSide
        }
    }

    private fun collectSendButtonCandidates(
        node: AccessibilityNodeInfo,
        outCandidates: MutableList<AccessibilityNodeInfo>
    ) {
        val resId = node.viewIdResourceName ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        val text = node.text?.toString() ?: ""
        val className = node.className?.toString() ?: ""

        val matchesId = KNOWN_SEND_BUTTON_IDS.any { resId.contains(it, ignoreCase = true) }
        val matchesText = SEND_TEXT_KEYWORDS.any { contentDesc.contains(it, ignoreCase = true) || text.contains(it, ignoreCase = true) }

        val isIconButtonCandidate = (className.contains("Image", ignoreCase = true) ||
                className.contains("Button", ignoreCase = true) ||
                className.contains("View", ignoreCase = true)) &&
                (matchesId || matchesText)

        if (isIconButtonCandidate) {
            outCandidates.add(AccessibilityNodeInfo.obtain(node))
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectSendButtonCandidates(child, outCandidates)
            child.recycle()
        }
    }

    private data class SendButtonCandidateEvaluation(
        val node: AccessibilityNodeInfo,
        val bounds: Rect,
        val resId: String,
        val className: String,
        val contentDesc: String,
        val isClickable: Boolean,
        val isEnabled: Boolean,
        val isVisible: Boolean,
        val isBesideComposer: Boolean,
        val score: Int
    )
}
