package com.seedlessds.app.emu

/** Providers cannot promise atomic replacement. Keep the old document under a
 * predictable recovery name until the new name is verified. Never copy over it. */
internal object RecoverableReplace {
    const val SUFFIX = ".seedless-previous"
    interface Files {
        fun exists(name: String): Boolean
        fun move(from: String, to: String): Boolean
        fun delete(name: String): Boolean
    }
    fun recover(files: Files, name: String): Boolean {
        val backup = name + SUFFIX
        return files.exists(name) || (files.exists(backup) && files.move(backup, name))
    }
    fun commit(files: Files, from: String, to: String): Boolean {
        if (from == to) return files.exists(from)
        val backup = to + SUFFIX
        // Recover a transaction interrupted before its replacement was named.
        if (!files.exists(to) && files.exists(backup) && !files.move(backup, to)) return false
        if (!files.exists(from)) return false
        val hadOld = files.exists(to)
        if (hadOld) {
            if (files.exists(backup) && !files.delete(backup)) return false
            if (!files.move(to, backup)) return false
        }
        if (!files.move(from, to)) {
            if (hadOld) files.move(backup, to)
            return false
        }
        // A cleanup failure leaves a recoverable old copy; the commit succeeded.
        if (hadOld) files.delete(backup)
        return true
    }
}
