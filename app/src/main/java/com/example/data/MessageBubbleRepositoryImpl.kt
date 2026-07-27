package com.example.data

import com.example.accessibility.imo.MessageBubbleDetectionResult
import com.example.accessibility.imo.MessageBubbleModel
import com.example.accessibility.imo.MessageBubbleType
import com.example.accessibility.imo.ScannedNodeModel
import com.example.accessibility.imo.UiScanReport
import com.example.repository.MessageBubbleRepository
import com.example.repository.UiScannerRepository

/**
 * Concrete implementation of MessageBubbleRepository.
 * Analyzes the scanned Accessibility tree from Step 9, detects chat bubble nodes,
 * classifies each node deterministically into exactly one type without keyword guessing,
 * and maintains chronological top-to-bottom layout order.
 */
class MessageBubbleRepositoryImpl(
    private val uiScannerRepository: UiScannerRepository
) : MessageBubbleRepository {

    override suspend fun detectAndClassifyMessageBubbles(scanReport: UiScanReport?): MessageBubbleDetectionResult {
        val report = scanReport ?: uiScannerRepository.getLatestScanReport() ?: uiScannerRepository.scanActiveUiTree()
        val flatList = report.flatNodeList

        if (flatList.isEmpty()) {
            return MessageBubbleDetectionResult(
                bubbles = emptyList(),
                bubbleCount = 0,
                incomingCount = 0,
                outgoingCount = 0,
                stickerCount = 0,
                missedCallCount = 0,
                unknownCount = 0
            )
        }

        val bubbleNodes = extractBubbleNodes(flatList)
        val classifiedBubbles = mutableListOf<MessageBubbleModel>()

        bubbleNodes.forEach { node ->
            val type = classifyBubbleNode(node)
            val isIncoming = when (type) {
                MessageBubbleType.INCOMING_MESSAGE -> true
                MessageBubbleType.OUTGOING_MESSAGE -> false
                else -> determineIncomingState(node)
            }

            classifiedBubbles.add(
                MessageBubbleModel(
                    nodeIndex = node.nodeIndex,
                    type = type,
                    resourceId = node.resourceId,
                    className = node.className,
                    text = node.text,
                    contentDescription = node.contentDescription,
                    bounds = node.bounds,
                    isIncoming = isIncoming,
                    rawNode = node
                )
            )
        }

        // Chronological order sorting: top-to-bottom vertical screen bounds, then left-to-right
        val sortedBubbles = classifiedBubbles.sortedWith(
            compareBy<MessageBubbleModel> { it.bounds.top }.thenBy { it.bounds.left }
        )

        val totalCount = sortedBubbles.size
        val incomingCount = sortedBubbles.count { it.type == MessageBubbleType.INCOMING_MESSAGE || (it.isIncoming == true && it.type != MessageBubbleType.OUTGOING_MESSAGE) }
        val outgoingCount = sortedBubbles.count { it.type == MessageBubbleType.OUTGOING_MESSAGE || (it.isIncoming == false && it.type != MessageBubbleType.INCOMING_MESSAGE) }
        val stickerCount = sortedBubbles.count { it.type == MessageBubbleType.STICKER }
        val missedCallCount = sortedBubbles.count { it.type == MessageBubbleType.MISSED_AUDIO_CALL || it.type == MessageBubbleType.MISSED_VIDEO_CALL }
        val unknownCount = sortedBubbles.count { it.type == MessageBubbleType.UNKNOWN }

        return MessageBubbleDetectionResult(
            bubbles = sortedBubbles,
            bubbleCount = totalCount,
            incomingCount = incomingCount,
            outgoingCount = outgoingCount,
            stickerCount = stickerCount,
            missedCallCount = missedCallCount,
            unknownCount = unknownCount
        )
    }

    private fun extractBubbleNodes(flatList: List<ScannedNodeModel>): List<ScannedNodeModel> {
        val bubbleCandidates = mutableListOf<ScannedNodeModel>()

        // Filter out non-chat UI regions like input fields, header/toolbars, send/mic buttons, keyboard
        flatList.forEach { node ->
            if (!node.visible) return@forEach

            val resId = node.resourceId.lowercase()
            val className = node.className

            // Skip input fields, toolbar/headers, navigation buttons
            val isInputOrHeader = resId.contains("edit_text") || resId.contains("et_message") ||
                    resId.contains("btn_send") || resId.contains("btn_mic") ||
                    resId.contains("header") || resId.contains("toolbar") ||
                    resId.contains("btn_back") || resId.contains("input_box")

            if (isInputOrHeader) return@forEach

            val isExplicitBubbleId = resId.contains("chat_item") || resId.contains("bubble") ||
                    resId.contains("msg_layout") || resId.contains("message_body") ||
                    resId.contains("item_msg") || resId.contains("msg_content") ||
                    resId.contains("item_chat") || resId.contains("layout_msg") ||
                    resId.contains("cell_msg") || resId.contains("view_msg") ||
                    resId.contains("msg_root") || resId.contains("msg_view") ||
                    resId.contains("date_divider") || resId.contains("unread_divider") ||
                    resId.contains("system_msg") || resId.contains("sticker") ||
                    resId.contains("voice_msg") || resId.contains("call_item")

            val isSubstantialBubbleContent = (node.text.isNotBlank() || node.contentDescription.isNotBlank()) &&
                    !node.editable && node.bounds.height() > 10 && node.bounds.width() > 10

            if (isExplicitBubbleId || isSubstantialBubbleContent) {
                // Ensure we don't pick up full recycler container as individual bubble
                val isContainerClass = className.contains("RecyclerView") || className.contains("ListView")
                if (!isContainerClass) {
                    bubbleCandidates.add(node)
                }
            }
        }

        // Deduplicate child nodes if parent container node was already selected as a complete bubble
        val finalBubbles = mutableListOf<ScannedNodeModel>()
        bubbleCandidates.forEach { candidate ->
            val hasParentCandidate = bubbleCandidates.any { other ->
                other.nodeIndex != candidate.nodeIndex &&
                        other.bounds.contains(candidate.bounds) &&
                        other.bounds != candidate.bounds &&
                        (other.resourceId.contains("item") || other.resourceId.contains("layout") || other.resourceId.contains("bubble"))
            }

            if (!hasParentCandidate) {
                finalBubbles.add(candidate)
            } else if (candidate.resourceId.contains("bubble") || candidate.resourceId.contains("text") || candidate.resourceId.contains("msg")) {
                // If candidate is the specific bubble view inside the cell, prefer the specific bubble view
                finalBubbles.add(candidate)
            }
        }

        return finalBubbles.distinctBy { it.nodeIndex }
    }

    private fun classifyBubbleNode(node: ScannedNodeModel): MessageBubbleType {
        val resId = node.resourceId.lowercase()
        val className = node.className
        val desc = node.contentDescription.lowercase()

        // 1. Date Separator
        if (resId.contains("date_divider") || resId.contains("date_header") ||
            resId.contains("tv_date") || resId.contains("time_divider") ||
            resId.contains("date_separator") || resId.contains("tv_time_divider")) {
            return MessageBubbleType.DATE_SEPARATOR
        }

        // 2. Unread Separator
        if (resId.contains("unread_divider") || resId.contains("unread_separator") ||
            resId.contains("unread_line") || resId.contains("tv_unread") ||
            resId.contains("new_message_divider")) {
            return MessageBubbleType.UNREAD_SEPARATOR
        }

        // 3. System Message
        if (resId.contains("system_msg") || resId.contains("sys_msg") ||
            resId.contains("notice_msg") || resId.contains("info_msg") ||
            resId.contains("group_notice") || resId.contains("tv_system")) {
            return MessageBubbleType.SYSTEM_MESSAGE
        }

        // 4. Missed Video Call
        if (resId.contains("missed_video") || resId.contains("video_call_missed") ||
            desc.contains("missed video call") || desc.contains("video_missed")) {
            return MessageBubbleType.MISSED_VIDEO_CALL
        }

        // 5. Missed Audio Call
        if (resId.contains("missed_call") || resId.contains("call_missed") ||
            resId.contains("missed_voice_call") || desc.contains("missed call") ||
            desc.contains("missed voice call") || desc.contains("missed audio call")) {
            return MessageBubbleType.MISSED_AUDIO_CALL
        }

        // 6. Sticker
        if (resId.contains("sticker") || className.contains("StickerView") ||
            resId.contains("imo_sticker") || resId.contains("img_sticker") ||
            desc.contains("sticker")) {
            return MessageBubbleType.STICKER
        }

        // 7. Emoji
        if (resId.contains("emoji_view") || resId.contains("emoji_bubble") ||
            resId.contains("big_emoji") || desc.contains("emoji")) {
            return MessageBubbleType.EMOJI
        }

        // 8. Voice Message
        if (resId.contains("voice_msg") || resId.contains("voice_bubble") ||
            resId.contains("audio_record") || resId.contains("voice_player") ||
            resId.contains("voice_length") || desc.contains("voice message")) {
            return MessageBubbleType.VOICE
        }

        // 9. Audio
        if (resId.contains("audio_msg") || resId.contains("music_msg") ||
            resId.contains("audio_file") || resId.contains("audio_attachment")) {
            return MessageBubbleType.AUDIO
        }

        // 10. Video
        if (resId.contains("video_msg") || resId.contains("video_bubble") ||
            resId.contains("player_view") || resId.contains("video_thumbnail") ||
            desc.contains("video")) {
            return MessageBubbleType.VIDEO
        }

        // 11. Image
        if (resId.contains("img_msg") || resId.contains("photo_msg") ||
            resId.contains("iv_photo") || resId.contains("picture_msg") ||
            (className.contains("ImageView") && (resId.contains("chat") || resId.contains("bubble")))) {
            return MessageBubbleType.IMAGE
        }

        // 12. Explicit Incoming / Outgoing ID check
        if (resId.contains("incoming") || resId.contains("left_msg") ||
            resId.contains("other_msg") || resId.contains("msg_in") ||
            resId.contains("receive_msg")) {
            return MessageBubbleType.INCOMING_MESSAGE
        }

        if (resId.contains("outgoing") || resId.contains("right_msg") ||
            resId.contains("my_msg") || resId.contains("msg_out") ||
            resId.contains("send_msg")) {
            return MessageBubbleType.OUTGOING_MESSAGE
        }

        // 13. Screen bounds alignment heuristic (Left-aligned = Incoming, Right-aligned = Outgoing)
        val isIncomingByPosition = determineIncomingState(node)
        if (isIncomingByPosition != null) {
            return if (isIncomingByPosition) MessageBubbleType.INCOMING_MESSAGE else MessageBubbleType.OUTGOING_MESSAGE
        }

        return MessageBubbleType.UNKNOWN
    }

    private fun determineIncomingState(node: ScannedNodeModel): Boolean? {
        val resId = node.resourceId.lowercase()
        if (resId.contains("incoming") || resId.contains("left") || resId.contains("receive")) return true
        if (resId.contains("outgoing") || resId.contains("right") || resId.contains("send")) return false

        val bounds = node.bounds
        if (bounds.width() == 0 || bounds.height() == 0) return null

        // In standard portrait view layout:
        // Left margin < 200px indicates left alignment (Incoming)
        // Right margin near screen right edge or left bound > 300px indicates right alignment (Outgoing)
        return bounds.left < 200
    }
}
