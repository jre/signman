package net.joshe.signman.client

interface Resolver {
    suspend fun resolve(hostname: String): List<IP>
}
