package net.joshe.signman.zeroconf

fun serviceTypeValid(type: String)
        = type.count { it == '.' } == 1 &&
        type.count { it == '_' } == 2 &&
        type.startsWith('_') &&
        !type.startsWith("_.") &&
        "._" in type &&
        !type.endsWith('_')
