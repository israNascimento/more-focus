package br.com.morefocus.blocking

sealed interface PauseExtensionTarget {
    data object InstagramReels : PauseExtensionTarget

    data class App(val packageName: String) : PauseExtensionTarget {
        init {
            require(packageName.isNotBlank())
        }
    }
}

object ExponentialPauseLockPolicy {
    const val MAX_EXTENSION_COUNT = 4

    fun lockSeconds(extensionCount: Int): Int = when (extensionCount) {
        in Int.MIN_VALUE..0 -> 0
        1 -> 3
        2 -> 9
        3 -> 27
        else -> 60
    }

    fun increment(extensionCount: Int): Int = when {
        extensionCount < 0 -> 1
        extensionCount >= MAX_EXTENSION_COUNT -> MAX_EXTENSION_COUNT
        else -> extensionCount + 1
    }
}
