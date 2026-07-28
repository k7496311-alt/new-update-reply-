package com.example.accessibility.imo

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.example.model.MessageType
import com.example.accessibility.AccessibilityLogger

/**
 * Represent an item detected on the IMO/IMO Lite Chat List Screen.
 */
data class ImoChatListItem(
    val contactName: String,
    val lastMessagePreview: String,
    val timestamp: String,
    val messageCount: Int,
    val hasNewMessageIndicator: Boolean,
    val contactNode: AccessibilityNodeInfo? = null // Reference to click or perform actions on
)

/**
 * Represent a message bubble detected on the Chat Conversation Screen.
 */
data class ImoConversationMessage(
    val text: String,
    val isIncoming: Boolean,
    val messageType: MessageType,
    val timestamp: String?,
    val isTranscribed: Boolean = false,
    val transcribedText: String? = null,
    val bubbleNode: AccessibilityNodeInfo? = null
)

/**
 * High-level state representing everything scanned on the current Chat Conversation Screen.
 */
data class ImoChatConversationScreenInfo(
    val contactName: String,
    val messages: List<ImoConversationMessage>,
    val inputFieldNode: AccessibilityNodeInfo? = null,
    val sendButtonNode: AccessibilityNodeInfo? = null,
    val micButtonNode: AccessibilityNodeInfo? = null,
    val backButtonNode: AccessibilityNodeInfo? = null,
    val voiceToTextButtonNode: AccessibilityNodeInfo? = null,
    val transcribedText: String? = null
)

/**
 * Scans IMO app's UI elements using advanced hierarchical traversal, heuristics, relative positioning,
 * and robust fallback strategies. Fully compatible with both IMO and IMO Lite.
 */
class IMONodeScanner {

    companion object {
        private const val TAG = "IMONodeScanner"

        // Known IMO & IMO variants package names
        const val PACKAGE_IMO = "com.imo.android.imoim"
        const val PACKAGE_IMO_BETA = "com.imo.android.imoim.beta"
        const val PACKAGE_IMO_HD = "com.imo.android.imoimhd"
        const val PACKAGE_IMO_LITE = "com.imo.android.imoimlite"

        fun isImoPackage(packageName: String): Boolean {
            if (packageName.isBlank()) return false
            return packageName.startsWith("com.imo.android", ignoreCase = true) ||
                   packageName.contains("imo", ignoreCase = true)
        }

        // View IDs (with package-relative suffixes and short IDs)
        private val CHAT_LIST_CONTACT_NAME_IDS = listOf(
            "com.imo.android.imoim:id/name",
            "com.imo.android.imoim.beta:id/name",
            "com.imo.android.imoimlite:id/name",
            "name",
            "com.imo.android.imoim:id/title",
            "com.imo.android.imoim.beta:id/title",
            "com.imo.android.imoimlite:id/title",
            "title"
        )
        private val CHAT_LIST_PREVIEW_IDS = listOf(
            "com.imo.android.imoim:id/msg",
            "com.imo.android.imoim.beta:id/msg",
            "com.imo.android.imoimlite:id/msg",
            "msg",
            "com.imo.android.imoim:id/subtitle",
            "com.imo.android.imoim.beta:id/subtitle",
            "com.imo.android.imoimlite:id/subtitle",
            "subtitle"
        )
        private val CHAT_LIST_TIME_IDS = listOf(
            "com.imo.android.imoim:id/time",
            "com.imo.android.imoim.beta:id/time",
            "com.imo.android.imoimlite:id/time",
            "time"
        )
        private val CHAT_LIST_BADGE_IDS = listOf(
            "com.imo.android.imoim:id/badge",
            "com.imo.android.imoim.beta:id/badge",
            "com.imo.android.imoimlite:id/badge",
            "badge",
            "com.imo.android.imoim:id/unread_count",
            "com.imo.android.imoim.beta:id/unread_count",
            "com.imo.android.imoimlite:id/unread_count",
            "unread_count"
        )

        private val CONV_INPUT_IDS = listOf(
            "com.imo.android.imoim:id/edit_text",
            "com.imo.android.imoim.beta:id/edit_text",
            "com.imo.android.imoimlite:id/edit_text",
            "com.imo.android.imoim:id/et_message",
            "com.imo.android.imoim.beta:id/et_message",
            "com.imo.android.imoim:id/chat_input",
            "com.imo.android.imoim.beta:id/chat_input",
            "com.imo.android.imoim:id/input_box",
            "com.imo.android.imoim.beta:id/input_box",
            "com.imo.android.imoim:id/message_edit",
            "com.imo.android.imoim.beta:id/message_edit",
            "com.imo.android.imoim:id/message_input",
            "com.imo.android.imoim.beta:id/message_input",
            "com.imo.android.imoim:id/text_input",
            "com.imo.android.imoim:id/input",
            "com.imo.android.imoim:id/msg_edit",
            "edit_text",
            "et_message",
            "chat_input",
            "input_box",
            "message_edit",
            "message_input",
            "text_input",
            "input",
            "msg_edit"
        )
        private val CONV_SEND_IDS = listOf(
            "com.imo.android.imoim:id/send",
            "com.imo.android.imoim.beta:id/send",
            "com.imo.android.imoimlite:id/send",
            "com.imo.android.imoim:id/btn_send",
            "com.imo.android.imoim.beta:id/btn_send",
            "com.imo.android.imoim:id/send_btn",
            "com.imo.android.imoim.beta:id/send_btn",
            "com.imo.android.imoim:id/iv_send",
            "com.imo.android.imoim.beta:id/iv_send",
            "com.imo.android.imoim:id/v_send",
            "com.imo.android.imoim.beta:id/v_send",
            "com.imo.android.imoim:id/image_send",
            "com.imo.android.imoim:id/send_button",
            "com.imo.android.imoim:id/send_icon",
            "com.imo.android.imoim:id/chat_send",
            "com.imo.android.imoim:id/right_btn",
            "com.imo.android.imoim.beta:id/right_btn",
            "send",
            "btn_send",
            "send_btn",
            "iv_send",
            "v_send",
            "right_btn",
            "send_button",
            "send_icon"
        )
        private val CONV_MIC_IDS = listOf(
            "com.imo.android.imoim:id/mic",
            "com.imo.android.imoim.beta:id/mic",
            "com.imo.android.imoimlite:id/mic",
            "mic",
            "com.imo.android.imoim:id/voice",
            "com.imo.android.imoim.beta:id/voice",
            "voice"
        )
        private val CONV_HEADER_NAME_IDS = listOf(
            "com.imo.android.imoim:id/chat_title",
            "com.imo.android.imoim.beta:id/chat_title",
            "com.imo.android.imoimlite:id/chat_title",
            "chat_title",
            "com.imo.android.imoim:id/title",
            "com.imo.android.imoim.beta:id/title",
            "title"
        )
        private val CONV_BACK_IDS = listOf(
            "com.imo.android.imoim:id/back",
            "com.imo.android.imoim.beta:id/back",
            "com.imo.android.imoimlite:id/back",
            "back",
            "com.imo.android.imoim:id/btn_back",
            "btn_back"
        )
        private val CONV_MESSAGE_LIST_IDS = listOf(
            "com.imo.android.imoim:id/msg_list",
            "com.imo.android.imoim:id/message_list",
            "com.imo.android.imoim:id/chat_list",
            "com.imo.android.imoim:id/recycler_view",
            "com.imo.android.imoimlite:id/msg_list",
            "com.imo.android.imoimlite:id/message_list",
            "com.imo.android.imoimlite:id/chat_list",
            "com.imo.android.imoimlite:id/recycler_view",
            "msg_list",
            "message_list",
            "chat_list",
            "recycler_view"
        )
        private val CONV_JUMP_LATEST_IDS = listOf(
            "com.imo.android.imoim:id/jump_to_latest",
            "com.imo.android.imoim:id/jump_to_bottom",
            "com.imo.android.imoim:id/btn_jump",
            "com.imo.android.imoim:id/fab_jump",
            "com.imo.android.imoim:id/go_to_bottom",
            "com.imo.android.imoim:id/scroll_down_btn",
            "com.imo.android.imoim:id/scroll_to_bottom",
            "com.imo.android.imoim:id/unread_btn",
            "com.imo.android.imoim:id/btn_latest",
            "com.imo.android.imoimlite:id/jump_to_latest",
            "com.imo.android.imoimlite:id/jump_to_bottom",
            "com.imo.android.imoimlite:id/btn_jump",
            "com.imo.android.imoimlite:id/fab_jump",
            "com.imo.android.imoimlite:id/go_to_bottom",
            "com.imo.android.imoimlite:id/scroll_down_btn",
            "com.imo.android.imoimlite:id/scroll_to_bottom",
            "com.imo.android.imoimlite:id/unread_btn",
            "com.imo.android.imoimlite:id/btn_latest",
            "jump_to_latest",
            "jump_to_bottom",
            "btn_jump",
            "fab_jump",
            "go_to_bottom",
            "scroll_down_btn",
            "scroll_to_bottom",
            "unread_btn",
            "btn_latest"
        )
    }

    /**
     * Scan the chat list screen for messages, names, counts, timestamps.
     */
    fun scanChatListScreen(root: AccessibilityNodeInfo?): List<ImoChatListItem> {
        val items = mutableListOf<ImoChatListItem>()
        if (root == null) return items

        // Traverses the tree to find list items (often inside a RecyclerView or ListView)
        traverseChatList(root, items)
        AccessibilityLogger.d(TAG, "Chat list scan complete: found ${items.size} contacts")
        return items
    }

    /**
     * Scan the active chat conversation screen to capture context, input fields, bubbles, types, etc.
     */
    fun scanChatConversationScreen(root: AccessibilityNodeInfo?): ImoChatConversationScreenInfo? {
        if (root == null) return null

        val inputField = findInputField(root)
        val sendButton = findSendButton(root)
        val micButton = findMicButton(root)
        val backButton = findBackButton(root)
        val headerName = findHeaderName(root) ?: "Unknown IMO Chat"
        
        // Find Voice-to-Text "A" button and transcript if any
        val voiceToTextButton = findVoiceToTextButton(root)
        val transcribedText = findTranscribedText(root)

        // Find conversation message bubbles
        val messages = mutableListOf<ImoConversationMessage>()
        traverseConversationBubbles(root, messages)

        return ImoChatConversationScreenInfo(
            contactName = headerName,
            messages = messages,
            inputFieldNode = inputField,
            sendButtonNode = sendButton,
            micButtonNode = micButton,
            backButtonNode = backButton,
            voiceToTextButtonNode = voiceToTextButton,
            transcribedText = transcribedText
        )
    }

    /**
     * Determines whether the active screen is the Chat Conversation Screen based on input field presence.
     */
    fun isOnChatScreen(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        val pkg = root.packageName?.toString() ?: ""
        if (pkg != PACKAGE_IMO && pkg != PACKAGE_IMO_LITE) return false
        
        val input = findInputField(root)
        val hasInput = input != null
        input?.recycle()
        return hasInput
    }

    /**
     * Determines whether the active screen is the Chat List Screen.
     */
    fun isOnChatListScreen(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        val pkg = root.packageName?.toString() ?: ""
        if (pkg != PACKAGE_IMO && pkg != PACKAGE_IMO_LITE) return false

        // Not on chat screen, and has standard chat list markers (no chat edit text but names or badges)
        if (isOnChatScreen(root)) return false
        
        // Ensure there is some list-like structure or title
        val names = findNodesByMultipleIds(root, CHAT_LIST_CONTACT_NAME_IDS)
        val hasNames = names.isNotEmpty()
        names.forEach { it.recycle() }
        return hasNames
    }

    private fun traverseChatList(node: AccessibilityNodeInfo, items: MutableList<ImoChatListItem>) {
        // Typically, each chat item in the list is a relative/linear layout containing sub-views
        // Let's identify nodes that might represent a container or a contact name
        val className = node.className?.toString() ?: ""
        
        // If this node represents a cell container, let's look at its children
        if (node.childCount > 0 && (className.contains("RelativeLayout") || className.contains("ViewGroup") || className.contains("LinearLayout"))) {
            var contactNameNode: AccessibilityNodeInfo? = null
            var previewNode: AccessibilityNodeInfo? = null
            var timeNode: AccessibilityNodeInfo? = null
            var badgeNode: AccessibilityNodeInfo? = null

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                
                // Identify by ID
                val resId = child.viewIdResourceName ?: ""
                when {
                    CHAT_LIST_CONTACT_NAME_IDS.any { resId.contains(it) } -> {
                        contactNameNode = AccessibilityNodeInfo.obtain(child)
                    }
                    CHAT_LIST_PREVIEW_IDS.any { resId.contains(it) } -> {
                        previewNode = AccessibilityNodeInfo.obtain(child)
                    }
                    CHAT_LIST_TIME_IDS.any { resId.contains(it) } -> {
                        timeNode = AccessibilityNodeInfo.obtain(child)
                    }
                    CHAT_LIST_BADGE_IDS.any { resId.contains(it) } -> {
                        badgeNode = AccessibilityNodeInfo.obtain(child)
                    }
                }
                
                // Fallback heuristic based on text / class
                if (contactNameNode == null && child.text != null && child.className?.contains("TextView") == true) {
                    // Simple heuristic: if it's the first text view in a relative layout, it could be the name
                    contactNameNode = AccessibilityNodeInfo.obtain(child)
                }

                child.recycle()
            }

            if (contactNameNode != null) {
                val name = contactNameNode.text?.toString() ?: ""
                if (name.isNotEmpty()) {
                    val preview = previewNode?.text?.toString() ?: ""
                    val time = timeNode?.text?.toString() ?: ""
                    
                    // Parse badge count
                    val badgeStr = badgeNode?.text?.toString() ?: ""
                    val badgeCount = badgeStr.toIntOrNull() ?: if (badgeNode != null) 1 else 0
                    
                    // Check if there is a green dot / unread indicator
                    val hasIndicator = badgeCount > 0 || checkUnreadDot(node)

                    items.add(
                        ImoChatListItem(
                            contactName = name,
                            lastMessagePreview = preview,
                            timestamp = time,
                            messageCount = badgeCount,
                            hasNewMessageIndicator = hasIndicator,
                            contactNode = AccessibilityNodeInfo.obtain(node)
                        )
                    )
                }
            }

            contactNameNode?.recycle()
            previewNode?.recycle()
            timeNode?.recycle()
            badgeNode?.recycle()
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseChatList(child, items)
            child.recycle()
        }
    }

    private fun checkUnreadDot(container: AccessibilityNodeInfo): Boolean {
        // IMO sometimes uses a small custom view or ImageView without text for unread indicator
        // Let's check child counts of container for small elements near the right edge
        for (i in 0 until container.childCount) {
            val child = container.getChild(i) ?: continue
            val className = child.className?.toString() ?: ""
            val contentDesc = child.contentDescription?.toString() ?: ""
            if (className.contains("ImageView") && (contentDesc.contains("unread") || contentDesc.contains("new message"))) {
                child.recycle()
                return true
            }
            child.recycle()
        }
        return false
    }

    private fun traverseConversationBubbles(node: AccessibilityNodeInfo, messages: MutableList<ImoConversationMessage>) {
        val className = node.className?.toString() ?: ""
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        val resId = node.viewIdResourceName ?: ""

        // Detect if this node is a message bubble
        // Standard bubble heuristics in IMO:
        // - RelativeLayout/LinearLayout containing text + status icon
        // - Contains resource-id with "msg", "bubble", "content"
        val isBubble = resId.contains("chat_item") || resId.contains("bubble") || resId.contains("msg_layout") || resId.contains("message_body")
        
        if (isBubble || text.isNotEmpty() || contentDesc.isNotEmpty()) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            
            // Heuristic to determine if incoming or outgoing based on screen horizontal position
            // Center of screen is typically around 540px for a 1080px wide screen.
            val isIncoming = bounds.left < 200 // Incoming are usually left-aligned

            // Classify message type
            val type = when {
                resId.contains("voice") || contentDesc.contains("voice", ignoreCase = true) -> MessageType.VOICE_MESSAGE
                resId.contains("image") || contentDesc.contains("photo", ignoreCase = true) || contentDesc.contains("image", ignoreCase = true) -> MessageType.IMAGE
                resId.contains("sticker") || contentDesc.contains("sticker", ignoreCase = true) -> MessageType.STICKER
                resId.contains("gif") || contentDesc.contains("gif", ignoreCase = true) -> MessageType.GIF
                resId.contains("video") || contentDesc.contains("video", ignoreCase = true) -> MessageType.VIDEO
                else -> MessageType.PLAIN_TEXT
            }

            if (text.isNotEmpty() && !CONV_INPUT_IDS.any { resId.contains(it) }) {
                // Prevent duplicate entries for parent containers vs children text
                if (messages.none { it.text == text }) {
                    messages.add(
                        ImoConversationMessage(
                            text = text,
                            isIncoming = isIncoming,
                            messageType = type,
                            timestamp = null,
                            bubbleNode = AccessibilityNodeInfo.obtain(node)
                        )
                    )
                }
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseConversationBubbles(child, messages)
            child.recycle()
        }
    }

    // --- Dynamic Finder Helpers ---

    fun findInputField(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        return findNodeByMultipleIds(root, CONV_INPUT_IDS) 
            ?: findFirstByCriteria(root) { node ->
                val className = node.className?.toString() ?: ""
                val resId = node.viewIdResourceName ?: ""
                val text = node.text?.toString() ?: ""
                val desc = node.contentDescription?.toString() ?: ""

                val isEditClass = node.isEditable || className.contains("EditText", ignoreCase = true)
                val isEditId = resId.contains("edit", ignoreCase = true) || 
                               resId.contains("input", ignoreCase = true) || 
                               resId.contains("msg", ignoreCase = true)
                val isMessageHint = text.contains("Message", ignoreCase = true) || 
                                    text.contains("মেসেজ", ignoreCase = true) || 
                                    desc.contains("Message", ignoreCase = true)

                (isEditClass || (isEditId && isMessageHint)) && node.isEnabled
            }
    }

    fun findSendButton(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        
        // 1. Check known send IDs
        val directMatch = findNodeByMultipleIds(root, CONV_SEND_IDS)
        if (directMatch != null) return directMatch

        // 2. Check text or content description
        val textMatch = findFirstByCriteria(root) { node ->
            val text = node.text?.toString() ?: ""
            val desc = node.contentDescription?.toString() ?: ""
            val resId = node.viewIdResourceName ?: ""
            val isClickableOrParent = node.isClickable || node.parent?.isClickable == true
            isClickableOrParent && (
                text.contains("Send", ignoreCase = true) ||
                text.contains("পাঠান", ignoreCase = true) ||
                desc.contains("Send", ignoreCase = true) ||
                desc.contains("পাঠান", ignoreCase = true) ||
                resId.contains("send", ignoreCase = true) ||
                resId.contains("v_send", ignoreCase = true)
            )
        }
        if (textMatch != null) return textMatch

        // 3. Relative positioning fallback: Find button situated to the right of the input field in the bottom bar
        val inputField = findInputField(root)
        if (inputField != null) {
            val inputBounds = Rect()
            inputField.getBoundsInScreen(inputBounds)
            inputField.recycle()

            if (inputBounds.width() > 0) {
                return findFirstByCriteria(root) { node ->
                    val isClickableOrParent = node.isClickable || (node.parent?.isClickable == true)
                    if (!isClickableOrParent) return@findFirstByCriteria false
                    
                    val nodeBounds = Rect()
                    node.getBoundsInScreen(nodeBounds)

                    // Must be situated to the right of the input field or near right edge of the bottom row
                    val isToRight = nodeBounds.left >= (inputBounds.right - 120) || nodeBounds.centerX() > inputBounds.centerX()
                    val isInSameRow = nodeBounds.top >= (inputBounds.top - 200) && nodeBounds.bottom <= (inputBounds.bottom + 200)
                    val isSmallIcon = nodeBounds.width() in 15..400 && nodeBounds.height() in 15..400

                    isToRight && isInSameRow && isSmallIcon
                }
            }
        }

        return null
    }

    fun findMicButton(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        return findNodeByMultipleIds(root, CONV_MIC_IDS)
            ?: findFirstByCriteria(root) { it.isClickable && (it.contentDescription?.toString()?.contains("mic", ignoreCase = true) == true || it.contentDescription?.toString()?.contains("record", ignoreCase = true) == true) }
    }

    fun findBackButton(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        return findNodeByMultipleIds(root, CONV_BACK_IDS)
            ?: findFirstByCriteria(root) { it.isClickable && (it.contentDescription?.toString()?.contains("back", ignoreCase = true) == true || it.contentDescription?.toString()?.contains("navigate up", ignoreCase = true) == true) }
    }

    fun findMessageListContainer(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        // 1. Direct ID match
        val directMatch = findNodeByMultipleIds(root, CONV_MESSAGE_LIST_IDS)
        if (directMatch != null) return directMatch

        // 2. Class name heuristic match (RecyclerView, ListView, AbsListView, ScrollView)
        return findFirstByCriteria(root) { node ->
            val className = node.className?.toString() ?: ""
            val resId = node.viewIdResourceName ?: ""
            val isListClass = className.contains("RecyclerView", ignoreCase = true) ||
                    className.contains("ListView", ignoreCase = true) ||
                    className.contains("ScrollView", ignoreCase = true)
            val isListResId = resId.contains("list", ignoreCase = true) ||
                    resId.contains("recycler", ignoreCase = true) ||
                    resId.contains("chat", ignoreCase = true)
            (isListClass || (node.isScrollable && isListResId)) && node.isVisibleToUser
        }
    }

    fun findScrollableContainer(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        val listContainer = findMessageListContainer(root)
        if (listContainer != null && listContainer.isScrollable) {
            return listContainer
        }
        listContainer?.recycle()

        return findFirstByCriteria(root) { node ->
            node.isScrollable && node.isVisibleToUser
        }
    }

    fun findHeaderName(root: AccessibilityNodeInfo?): String? {
        if (root == null) return null
        val node = findNodeByMultipleIds(root, CONV_HEADER_NAME_IDS)
        val name = node?.text?.toString()
        node?.recycle()
        return name
    }

    fun findVoiceToTextButton(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        // IMO voice-to-text button is typically an "A" icon or a button near the audio bubble
        return findFirstByCriteria(root) { 
            it.isClickable && (it.text?.toString() == "A" || it.contentDescription?.toString()?.contains("transcribe", ignoreCase = true) == true || it.viewIdResourceName?.contains("voice_to_text") == true)
        }
    }

    fun findJumpToLatestButton(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null

        // 1. Check known view ID matches
        val directMatch = findNodeByMultipleIds(root, CONV_JUMP_LATEST_IDS)
        if (directMatch != null && directMatch.isVisibleToUser) {
            return directMatch
        }
        directMatch?.recycle()

        // 2. Search entire accessibility tree using node properties:
        // AccessibilityNodeInfo, ContentDescription, Clickable, Visible, Bounds
        return findFirstByCriteria(root) { node ->
            if (!node.isVisibleToUser) return@findFirstByCriteria false

            val desc = node.contentDescription?.toString() ?: ""
            val text = node.text?.toString() ?: ""
            val resId = node.viewIdResourceName ?: ""
            val className = node.className?.toString() ?: ""

            val descMatch = desc.contains("jump", ignoreCase = true) ||
                    desc.contains("latest", ignoreCase = true) ||
                    desc.contains("bottom", ignoreCase = true) ||
                    desc.contains("scroll down", ignoreCase = true) ||
                    desc.contains("new message", ignoreCase = true) ||
                    desc.contains("unread", ignoreCase = true)

            val textMatch = text.contains("jump", ignoreCase = true) ||
                    text.contains("latest", ignoreCase = true) ||
                    text.contains("bottom", ignoreCase = true) ||
                    text.contains("down", ignoreCase = true) ||
                    text.contains("unread", ignoreCase = true)

            val idMatch = resId.contains("jump", ignoreCase = true) ||
                    resId.contains("bottom", ignoreCase = true) ||
                    resId.contains("latest", ignoreCase = true) ||
                    resId.contains("scroll_down", ignoreCase = true) ||
                    resId.contains("unread", ignoreCase = true)

            val isClickableOrParent = node.isClickable || (node.parent?.isClickable == true)

            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            // Floating Jump button bounds heuristic: valid non-empty rect in lower half/center
            val hasValidBounds = bounds.width() in 15..500 && bounds.height() in 15..500 && bounds.top > 100

            val isFloatingClass = className.contains("FloatingActionButton", ignoreCase = true) ||
                    className.contains("ImageButton", ignoreCase = true) ||
                    className.contains("ImageView", ignoreCase = true) ||
                    className.contains("Button", ignoreCase = true)

            isClickableOrParent && hasValidBounds && (descMatch || textMatch || idMatch || (isFloatingClass && (idMatch || descMatch)))
        }
    }

    fun findTranscribedText(root: AccessibilityNodeInfo?): String? {
        if (root == null) return null
        val node = findFirstByCriteria(root) {
            it.viewIdResourceName?.contains("transcribe") == true || it.viewIdResourceName?.contains("text_layout") == true
        }
        val text = node?.text?.toString()
        node?.recycle()
        return text
    }

    private fun findNodeByMultipleIds(root: AccessibilityNodeInfo, ids: List<String>): AccessibilityNodeInfo? {
        for (id in ids) {
            val list = root.findAccessibilityNodeInfosByViewId(id)
            if (list != null && list.isNotEmpty()) {
                val found = AccessibilityNodeInfo.obtain(list[0])
                list.forEach { it.recycle() }
                return found
            }
        }
        return null
    }

    private fun findNodesByMultipleIds(root: AccessibilityNodeInfo, ids: List<String>): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()
        for (id in ids) {
            val list = root.findAccessibilityNodeInfosByViewId(id)
            if (list != null && list.isNotEmpty()) {
                results.addAll(list)
            }
        }
        return results
    }

    private fun findFirstByCriteria(root: AccessibilityNodeInfo, criteria: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (criteria(root)) {
            return AccessibilityNodeInfo.obtain(root)
        }
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val found = findFirstByCriteria(child, criteria)
            child.recycle()
            if (found != null) {
                return found
            }
        }
        return null
    }
}
