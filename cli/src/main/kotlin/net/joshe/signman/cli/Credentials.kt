package net.joshe.signman.cli

import io.ktor.http.Url

data class Credentials(val user: String?, val password: String?) {
    constructor(url: Url) : this(user = url.user, password = url.password)
}
