package net.joshe.signman.cli

import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import net.joshe.signman.client.Client
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class Authenticator internal constructor(private val prompter: Prompter) {
    constructor() : this(ConsolePrompter())

    @OptIn(ExperimentalUuidApi::class)
    suspend fun login(client: Client, uuid: Uuid, credentials: Credentials? = null, user: String? = null): Boolean {
        var user: String? = user ?: credentials?.user
        var pass: String? = credentials?.password

        while (true) {
            try {
                if (user == null)
                    client.authenticate(uuid)
                else
                    client.login(uuid, user, pass)
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
