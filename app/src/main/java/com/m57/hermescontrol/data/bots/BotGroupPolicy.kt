package com.m57.hermescontrol.data.bots

/** Roster validity for a local phone group chat (v1: 2–6 distinct bots). */
enum class BotGroupRosterValidity {
    OK,
    TOO_FEW,
    TOO_MANY,
    DUPLICATE,
}

object BotGroupPolicy {
    const val MIN_MEMBERS: Int = 2
    const val MAX_MEMBERS: Int = 6

    /**
     * A group roster must carry [MIN_MEMBERS]..[MAX_MEMBERS] distinct,
     * non-blank bot profile names. No blank names: membership keys the
     * per-bot session and the merged transcript sender.
     */
    fun validate(members: Collection<String>): BotGroupRosterValidity =
        when {
            members.count { it.isNotBlank() } < MIN_MEMBERS -> BotGroupRosterValidity.TOO_FEW
            members.size > MAX_MEMBERS -> BotGroupRosterValidity.TOO_MANY
            members.any { it.isBlank() } -> BotGroupRosterValidity.TOO_FEW
            members.distinct().size != members.size -> BotGroupRosterValidity.DUPLICATE
            else -> BotGroupRosterValidity.OK
        }
}
