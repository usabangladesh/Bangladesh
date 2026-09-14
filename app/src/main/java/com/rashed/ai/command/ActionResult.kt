package com.rashed.ai.command

enum class ActionResultStatus {
    SUCCESS,
    FAILED,
    UNSUPPORTED,
    PERMISSION_REQUIRED
}

data class ActionResult(
    val status: ActionResultStatus,
    val message: String,
    val details: Map<String, Any?> = emptyMap()
) {
    val isSuccess: Boolean
        get() = status == ActionResultStatus.SUCCESS
}
