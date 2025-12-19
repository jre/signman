package net.joshe.signman.zeroconf

import at.asitplus.cidre.IpAddress

data class Service(val name: String,
                   val hostname: String,
                   val address: IpAddress<*,*>,
                   val port: Int,
                   val params: Map<String,String>)
