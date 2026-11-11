package quoi.api.customtriggers

private val subareaPrefix = Regex("(?U)^[\uE067\uE020]\\s+")

internal fun String.subareaName() = trim().replaceFirst(subareaPrefix, "")
