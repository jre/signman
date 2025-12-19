@file:Suppress("unused")

package net.joshe.signman.zeroconf.avahi

import kotlin.enums.enumEntries

internal const val AVAHI_BUS = "org.freedesktop.Avahi"
internal const val AVAHI_SERVER_PATH = "/"

// from avahi-common/defs.h

internal enum class AvahiServerState(val value: Int) {
    AVAHI_SERVER_INVALID(0),                   // Invalid state (initial)
    AVAHI_SERVER_REGISTERING(1),               // Host RRs are being registered
    AVAHI_SERVER_RUNNING(2),                   // All host RRs have been established
    AVAHI_SERVER_COLLISION(3),                 // There is a collision with a host RR. All host RRs have been withdrawn
    AVAHI_SERVER_FAILURE(4);                   // Some fatal failure happened, the server is unable to proceed

    companion object {
        fun fromInt(value: Int) = enumEntries<AvahiServerState>().firstOrNull { it.value == value }
    }
}

internal enum class AvahiEntryGroupState(val value: Int) {
    AVAHI_ENTRY_GROUP_UNCOMMITED(0),           // The group has not yet been committed
    AVAHI_ENTRY_GROUP_REGISTERING(1),          // The entries of the group are currently being registered
    AVAHI_ENTRY_GROUP_ESTABLISHED(2),          // The entries have successfully been established
    AVAHI_ENTRY_GROUP_COLLISION(3),            // A name collision for one of the entries in the group has been detected, the entries have been withdrawn
    AVAHI_ENTRY_GROUP_FAILURE(4);              // Some kind of failure happened, the entries have been withdrawn

    companion object {
        fun fromInt(value: Int) = enumEntries<AvahiEntryGroupState>().firstOrNull { it.value == value }
    }
}

// Some flags for publishing methods
internal const val AVAHI_PUBLISH_UNIQUE = 1            // For raw records: The RRset is intended to be unique
internal const val AVAHI_PUBLISH_NO_PROBE = 2          // For raw records: Though the RRset is intended to be unique no probes shall be sent
internal const val AVAHI_PUBLISH_NO_ANNOUNCE = 4       // For raw records: Do not announce this RR to other hosts
internal const val AVAHI_PUBLISH_ALLOW_MULTIPLE = 8    // For raw records: Allow multiple local records of this type, even if they are intended to be unique
internal const val AVAHI_PUBLISH_NO_REVERSE = 16       // For address records: don't create a reverse (PTR) entry
internal const val AVAHI_PUBLISH_NO_COOKIE = 32        // For service records: do not implicitly add the local service cookie to TXT data
internal const val AVAHI_PUBLISH_UPDATE = 64           // Update existing records instead of adding new ones
internal const val AVAHI_PUBLISH_USE_WIDE_AREA = 128   // Register the record using wide area DNS (i.e. unicast DNS update)
internal const val AVAHI_PUBLISH_USE_MULTICAST = 256   // Register the record using multicast DNS

// Some flags for lookup methods
internal const val AVAHI_LOOKUP_USE_WIDE_AREA = 1      // Force lookup via wide area DNS
internal const val AVAHI_LOOKUP_USE_MULTICAST = 2      // Force lookup via multicast DNS
internal const val AVAHI_LOOKUP_NO_TXT = 4             // When doing service resolving, don't lookup TXT record
internal const val AVAHI_LOOKUP_NO_ADDRESS = 8         // When doing service resolving, don't lookup A/AAAA record

// Some flags for lookup signals
internal const val AVAHI_LOOKUP_RESULT_CACHED = 1      // This response originates from the cache
internal const val AVAHI_LOOKUP_RESULT_WIDE_AREA = 2   // This response originates from wide area DNS
internal const val AVAHI_LOOKUP_RESULT_MULTICAST = 4   // This response originates from multicast DNS
internal const val AVAHI_LOOKUP_RESULT_LOCAL = 8       // This record/service resides on and was announced by the local host
internal const val AVAHI_LOOKUP_RESULT_OUR_OWN = 16    // This service belongs to the same local client as the browser object
internal const val AVAHI_LOOKUP_RESULT_STATIC = 32     // The returned data has been defined statically by some configuration option

// from avahi-common/address.h

internal const val AVAHI_PROTO_INET = 0
internal const val AVAHI_PROTO_INET6 = 1
internal const val AVAHI_PROTO_UNSPEC = -1

internal const val AVAHI_IF_UNSPEC = -1
