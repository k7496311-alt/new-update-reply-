package com.example.data

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.example.accessibility.input.MessageInputFinderCriteria
import com.example.accessibility.input.MessageInputStatus
import com.example.accessibility.input.MessageInputVerificationResult
import com.example.repository.MessageInputFinderRepository

/**
 * Concrete implementation of MessageInputFinderRepository.
 * Traverses the Accessibility tree, identifies editable candidates, filters out search bars and non-composer fields,
 * and verifies that the selected node is an active IMO chat message composer.
 */
class MessageInputFinderRepositoryImpl : MessageInputFinderRepository {

    companion object {
        private val KNOWN_CHAT_INPUT_IDS = listOf(
            "com.imo.android.imoim:id/edit_text",
            "com.imo.android.imoim:id/et_message",
            "com.imo.android.imoim:id/chat_input",
            "com.imo.android.imoim:id/input_box",
            "com.imo.android.imoim:id/message_edit",
            "com.imo.android.imoim:id/message_input",
            "com.imo.android.imoim:id/text_input",
            "com.imo.android.imoim:id/input",
            "com.imo.android.imoim:id/msg_edit",
            "com.imo.android.imoimlite:id/edit_text",
            "com.imo.android.imoimlite:id/et_message",
            "com.imo.android.imoimlite:id/input_box",
            "com.imo.android.imoimlite:id/chat_input",
            "com.imo.android.imoimlite:id/input",
            "edit_text",
            "input",
            "msg_edit",
            "chat_input"
        )

        private val SEARCH_KEYWORD_IDS = listOf(
            "search", "find", "query", "filter", "search_src_text", "search_bar"
        )
    }

    override suspend fun findAndVerifyMessageInput(
        rootNode: AccessibilityNodeInfo?,
        criteria: MessageInputFinderCriteria
    ): MessageInputVerificationResult {
        if (rootNode == null) {
            return MessageInputVerificationResult(
                status = MessageInputStatus.INPUT_MISSING,
                isVerified = false,
                reason = "Input Missing: Accessibility root node is null.",
                details = "Cannot search tree when root node is null."
            )
        }

        // 1. Traverse tree and collect all candidate nodes
        val candidateNodes = mutableListOf<AccessibilityNodeInfo>()
        collectCandidateInputNodes(rootNode, candidateNodes)

        if (candidateNodes.isEmpty()) {
            return MessageInputVerificationResult(
                status = MessageInputStatus.INPUT_MISSING,
                isVerified = false,
                candidateNodesCount = 0,
                reason = "Input Missing: No editable input field found in accessibility tree.",
                details = "Scanned entire UI tree. Zero candidate EditText nodes detected."
            )
        }

        val candidateCount = candidateNodes.size
        val wrongInputReasons = mutableListOf<String>()
        val validComposerCandidates = mutableListOf<InputEvaluationCandidate>()

        // 2. Evaluate each candidate node
        for (node in candidateNodes) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            val resId = node.viewIdResourceName ?: ""
            val className = node.className?.toString() ?: ""
            val pkgName = node.packageName?.toString() ?: ""
            val text = node.text?.toString() ?: ""
            val contentDesc = node.contentDescription?.toString() ?: ""

            val isEditable = node.isEditable || className.contains("EditText", ignoreCase = true)
            val isEnabled = node.isEnabled
            val isVisible = node.isVisibleToUser || (bounds.width() > 0 && bounds.height() > 0)
            val isFocusable = node.isFocusable || node.isFocused

            val belongsToImo = pkgName.isBlank() ||
                    pkgName.startsWith("com.imo.android", ignoreCase = true) ||
                    pkgName.contains("imo", ignoreCase = true)

            // Rejection Heuristic 1: Is it a search box or filter bar?
            val isSearchBox = SEARCH_KEYWORD_IDS.any { resId.contains(it, ignoreCase = true) } ||
                    text.contains("Search", ignoreCase = true) ||
                    contentDesc.contains("Search", ignoreCase = true) ||
                    text.contains("খুঁজুন", ignoreCase = true)

            if (isSearchBox) {
                wrongInputReasons.add("Rejected node '$resId' ($className) as search box.")
                continue
            }

            // Rejection Heuristic 2: Position check (is it at the top of the screen where search bars reside?)
            val isTopScreenSearchPosition = bounds.top < (criteria.screenHeightPx * 0.25f) &&
                    !KNOWN_CHAT_INPUT_IDS.any { resId.contains(it) }

            if (isTopScreenSearchPosition) {
                wrongInputReasons.add("Rejected node '$resId' at Y=${bounds.top} as top search bar/header input.")
                continue
            }

            // Verify the 5 mandatory conditions
            if (!isEditable) {
                wrongInputReasons.add("Node '$resId' is not editable.")
                continue
            }
            if (!isEnabled) {
                wrongInputReasons.add("Node '$resId' is disabled.")
                continue
            }
            if (!isVisible) {
                wrongInputReasons.add("Node '$resId' is not visible to user.")
                continue
            }
            if (!isFocusable) {
                wrongInputReasons.add("Node '$resId' is not focusable.")
                continue
            }
            if (!belongsToImo) {
                wrongInputReasons.add("Node '$resId' belongs to non-IMO package '$pkgName'.")
                continue
            }

            // High score for matching known IDs or lower screen position
            var score = 0
            if (KNOWN_CHAT_INPUT_IDS.any { resId.contains(it) }) score += 50
            if (bounds.top > (criteria.screenHeightPx * criteria.bottomScreenThresholdRatio)) score += 30
            if (node.isFocused) score += 20

            validComposerCandidates.add(
                InputEvaluationCandidate(
                    node = AccessibilityNodeInfo.obtain(node),
                    bounds = bounds,
                    resId = resId,
                    className = className,
                    pkgName = pkgName,
                    isEditable = isEditable,
                    isEnabled = isEnabled,
                    isVisible = isVisible,
                    isFocusable = isFocusable,
                    belongsToImo = belongsToImo,
                    score = score
                )
            )
        }

        // Clean up unneeded obtained candidates
        candidateNodes.forEach { it.recycle() }

        if (validComposerCandidates.isEmpty()) {
            return MessageInputVerificationResult(
                status = MessageInputStatus.WRONG_INPUT,
                isVerified = false,
                candidateNodesCount = candidateCount,
                wrongInputReasons = wrongInputReasons,
                reason = "Wrong Input: Candidate node(s) were found ($candidateCount total) but all were rejected as non-chat fields (e.g. search bars, disabled, or non-visible).",
                details = wrongInputReasons.joinToString("; ")
            )
        }

        // Pick the best valid message composer input candidate
        val bestCandidate = validComposerCandidates.maxByOrNull { it.score }!!

        // Recycle all other valid candidates except chosen one
        validComposerCandidates.forEach {
            if (it != bestCandidate) {
                it.node.recycle()
            }
        }

        val finalStatus = if (candidateCount > 1) {
            MessageInputStatus.MULTIPLE_INPUTS
        } else {
            MessageInputStatus.INPUT_VERIFIED
        }

        return MessageInputVerificationResult(
            status = finalStatus,
            isVerified = true,
            inputNode = bestCandidate.node,
            bounds = bestCandidate.bounds,
            nodeId = bestCandidate.resId.ifBlank { "IMO_Message_Input_Node" },
            className = bestCandidate.className,
            packageName = bestCandidate.pkgName,
            isEditable = bestCandidate.isEditable,
            isEnabled = bestCandidate.isEnabled,
            isVisible = bestCandidate.isVisible,
            isFocusable = bestCandidate.isFocusable,
            belongsToImoChat = bestCandidate.belongsToImo,
            candidateNodesCount = candidateCount,
            wrongInputReasons = wrongInputReasons,
            reason = "Input Verified: Located active IMO message composer input field (ID: '${bestCandidate.resId}', Bounds: ${bestCandidate.bounds}).",
            details = "Verified Editable=true, Enabled=true, Visible=true, Focusable=true, BelongsToImo=true."
        )
    }

    private fun collectCandidateInputNodes(
        node: AccessibilityNodeInfo,
        outCandidates: MutableList<AccessibilityNodeInfo>
    ) {
        val className = node.className?.toString() ?: ""
        val resId = node.viewIdResourceName ?: ""

        if (node.isEditable ||
            className.contains("EditText", ignoreCase = true) ||
            resId.contains("edit_text", ignoreCase = true) ||
            resId.contains("input", ignoreCase = true)
        ) {
            outCandidates.add(AccessibilityNodeInfo.obtain(node))
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectCandidateInputNodes(child, outCandidates)
            child.recycle()
        }
    }

    private data class InputEvaluationCandidate(
        val node: AccessibilityNodeInfo,
        val bounds: Rect,
        val resId: String,
        val className: String,
        val pkgName: String,
        val isEditable: Boolean,
        val isEnabled: Boolean,
        val isVisible: Boolean,
        val isFocusable: Boolean,
        val belongsToImo: Boolean,
        val score: Int
    )
}
