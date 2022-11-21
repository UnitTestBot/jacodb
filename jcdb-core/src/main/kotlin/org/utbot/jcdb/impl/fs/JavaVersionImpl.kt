package org.utbot.jcdb.impl.fs

import org.utbot.jcdb.api.JavaVersion

private class JavaVersionImpl(override val majorVersion: Int) : JavaVersion

fun parseRuntimeVersion(version: String): JavaVersion {
    return when {
        version.startsWith("1.") -> JavaVersionImpl(version.substring(2, 3).toInt())
        else -> {
            val dot = version.indexOf(".")
            if (dot != -1) {
                JavaVersionImpl(version.substring(0, dot).toInt())
            } else {
                JavaVersionImpl(8)
            }
        }
    }
}