package net.joshe.signman.cli

import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import net.joshe.signman.client.Client

class Authenticator internal constructor(private val prompter: Prompter) {
    constructor() : this(ConsolePrompter())

    suspend fun login(client: Client, url: Url, user: String? = null): Boolean {
        var user: String? = user ?: url.user
        var pass: String? = url.password

        while (true) {
            try {
                if (user == null)
                    client.authenticate(url)
                else
                    client.login(url, user, pass)
                return true
            } catch (e: ClientRequestException) {
                if (e.response.status != HttpStatusCode.Unauthorized)
                    throw e
            }

            val newUser = if (user == null) prompter.readLine("User: ")
            else prompter.readLine("User: [%s] ", user)
            if (newUser == null)
                return false
            else if (newUser.isNotEmpty())
                user = newUser

            val newPass = prompter.readPassword("Password: ")
                ?: return false
            if (newPass.isNotEmpty())
                pass = newPass
        }
    }

    internal interface Prompter {
        fun readLine(fmt: String, vararg args: Any): String?
        fun readPassword(fmt: String, vararg args: Any): String?
    }

    private class ConsolePrompter : Prompter {
        private val console = System.console()!!
        override fun readLine(fmt: String, vararg args: Any): String? = console.readLine(fmt, *args)
        override fun readPassword(fmt: String, vararg args: Any) = console.readPassword(fmt, *args)?.concatToString()
    }
}
