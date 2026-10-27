package dev.dache.core.model

/** Unified status shown by the status chips; shared by deployments and CI runs. */
enum class DeployStatus {
    SUCCESS,
    BUILDING,
    FAILED,
    CANCELLED,
    UNKNOWN,
}
