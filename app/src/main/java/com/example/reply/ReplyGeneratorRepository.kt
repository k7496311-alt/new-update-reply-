package com.example.reply

interface ReplyGeneratorRepository {
    /**
     * Retrieves the last reply timestamp for a specific rule.
     */
    suspend fun getLastReplyTimestamp(ruleId: Long): Long?

    /**
     * Retrieves total reply count globally for a specific rule.
     */
    suspend fun getGlobalReplyCount(ruleId: Long): Int

    /**
     * Retrieves reply count for a rule since a specific timestamp.
     */
    suspend fun getReplyCountSince(ruleId: Long, sinceTimestamp: Long): Int

    /**
     * Retrieves the persistent sequential index for rotating replies on a rule.
     */
    suspend fun getSequentialIndex(ruleId: Long): Int

    /**
     * Saves/updates the persistent sequential index for rotating replies on a rule.
     */
    suspend fun saveSequentialIndex(ruleId: Long, index: Int)

    /**
     * Retrieves the default/fallback reply setting.
     */
    suspend fun getDefaultReplySetting(): String?

    /**
     * Saves the default/fallback reply setting.
     */
    suspend fun saveDefaultReplySetting(reply: String)
}
