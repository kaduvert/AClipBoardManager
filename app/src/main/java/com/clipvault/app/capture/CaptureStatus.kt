package com.clipvault.app.capture

/**
 * Flavor-agnostic snapshot of "is capture actually working right now, and if not,
 * what should the user do about it".
 *
 * There are two implementations of [CaptureCoordinator] that produce this - one
 * under app/src/xposed, one under app/src/root - and exactly one of them is ever
 * compiled into a given build (see the sourceSets block in app/build.gradle.kts).
 * SettingsScreen only ever sees this common shape, so it never needs to know or
 * branch on which capture path it's actually looking at.
 */
data class CaptureStatus(
    val active: Boolean,
    val statusText: String,
    val hintText: String? = null,
)
