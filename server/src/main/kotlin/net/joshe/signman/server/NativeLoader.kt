package net.joshe.signman.server

import org.slf4j.LoggerFactory
import java.io.File

class NativeLoader {
    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
        private val base: File = File(this::class.java.protectionDomain.codeSource.location.toURI()).parentFile

        fun load(name: String) {
            val full = File(base,
                if (System.getProperty("os.name").startsWith("Windows"))
                    "$name.dll" else "lib$name.so").absolutePath
            try {
                System.load(full)
                log.debug("Successfully loaded library \"$name\" from $full")
            } catch (_: UnsatisfiedLinkError) {
                log.debug("Trying normal load of library \"$name\" after failing to load from $full")
                System.loadLibrary(name)
            }
        }
    }
}
