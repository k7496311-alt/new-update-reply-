package com.example.repository

/**
 * Screen position state for bottom verification in chat.
 */
data class BottomCheckState(
    val isAtBottom: Boolean,
    val visibleMessageCount: Int,
    val currentPosition: String,
    val canScrollForward: Boolean,
    val hasJumpButton: Boolean
)

/**
 * Clean Architecture repository interface for checking and scrolling to newest message bottom position.
 */
interface BottomVerificationRepository {
    /**
     * Inspects active window nodes to determine current scroll position and bottom state.
     */
    suspend fun checkBottomState(): BottomCheckState

    /**
     * Performs a single controlled downward scroll gesture/action on the chat list container.
     */
    suspend fun performControlledScrollDown(): Boolean
}
