package com.m57.hermescontrol.data.bots

/** One group member and its locally resolved member session, if any. */
data class GroupMemberTarget(
    val botName: String,
    val resolvedSessionId: String?,
)

/** Outcome summary of a group fan-out. */
data class GroupFanOutReport(
    val sentBots: List<String>,
    val unresolvedBots: List<String>,
    val fencedCount: Int,
) {
    val dispatchedAny: Boolean get() = sentBots.isNotEmpty()
    val fencedAny: Boolean get() = fencedCount > 0
}

/**
 * Executes a group send across member sessions in roster order.
 *
 * Profile currency is re-checked immediately before EVERY dispatch, so a
 * connection-profile change mid fan-out fences all remaining members instead
 * of sending under new credentials. Members without a resolved session are
 * skipped with a reason — no session is ever guessed or created here.
 */
object GroupFanOut {
    fun execute(
        members: List<GroupMemberTarget>,
        isProfileCurrent: () -> Boolean,
        text: String,
        dispatch: (botName: String, sessionId: String, text: String) -> Unit,
    ): GroupFanOutReport {
        val sent = mutableListOf<String>()
        val unresolved = mutableListOf<String>()
        var fenced = 0
        for (member in members) {
            if (!isProfileCurrent()) {
                fenced += members.size - sent.size - unresolved.size
                break
            }
            val sessionId = member.resolvedSessionId?.takeIf(String::isNotBlank)
            if (sessionId == null) {
                unresolved.add(member.botName)
                continue
            }
            dispatch(member.botName, sessionId, text)
            sent.add(member.botName)
        }
        return GroupFanOutReport(sentBots = sent, unresolvedBots = unresolved, fencedCount = fenced)
    }
}
