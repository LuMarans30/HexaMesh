package com.lumarans30.hexamesh.mesh

/**
 * Builds a peer from a resolved mDNS record. Memory comes from optional TXT
 * attributes; a missing or malformed value stays unknown rather than becoming 0.
 */
fun discoveredPeer(
    serviceName: String,
    host: String,
    port: Int,
    nowMs: Long,
    attributes: Map<String, ByteArray> = emptyMap(),
): PeerNode =
    PeerNode(
        id = serviceName,
        name = serviceName,
        host = host,
        port = port,
        source = PeerSource.Discovered,
        stats =
            PeerStats(
                freeMemoryBytes = attributes.longValue(PeerTxt.FREE_MEMORY),
                totalMemoryBytes = attributes.longValue(PeerTxt.TOTAL_MEMORY),
                lastSeenAtMs = nowMs,
            ),
    )

/**
 * Parses the fallback-peer setting: one `host` or `host[:port]` per line, with
 * `#` comments ignored. Entries with a bad host or port are dropped; a missing
 * port defaults to [DEFAULT_RPC_PORT]. These peers are manual, so they do not
 * expire with discovery.
 */
fun parseFallbackPeers(text: String): List<PeerNode> =
    text.lineSequence()
        .map { it.substringBefore('#').trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull(::parseFallbackPeer)
        .toList()

private fun parseFallbackPeer(entry: String): PeerNode? {
    val (host, portText) = splitHostPort(entry) ?: return null
    if (host.isBlank()) return null
    val port = portText?.toIntOrNull() ?: DEFAULT_RPC_PORT
    if (port !in 1..65535) return null
    return PeerNode(
        id = "manual:$host:$port",
        name = host,
        host = host,
        port = port,
        source = PeerSource.Manual,
    )
}

/** Splits `host`, `host:port` and `[v6]:port`; null when the entry is malformed. */
internal fun splitHostPort(entry: String): Pair<String, String?>? =
    when {
        entry.startsWith("[") -> {
            val end = entry.indexOf(']')
            if (end < 0) {
                null
            } else {
                val host = entry.substring(1, end)
                when (val rest = entry.substring(end + 1)) {
                    "" -> host to null
                    else -> if (rest.startsWith(":")) host to rest.substring(1) else null
                }
            }
        }

        entry.count { it == ':' } == 1 -> entry.substringBefore(':') to entry.substringAfter(':')

        // A bare IPv6 address is ambiguous; require the bracketed form.
        ':' in entry -> null

        else -> entry to null
    }

private fun Map<String, ByteArray>.longValue(key: String): Long? =
    this[key]?.toString(Charsets.UTF_8)?.trim()?.toLongOrNull()

/**
 * TXT attributes advertising this device's spare RAM to the mesh. Unknown
 * readings fall back to 0 so the record always carries at least one attribute
 * (an empty TXT record trips a framework bug in the system NSD service).
 */
fun memoryAttributes(freeBytes: Long?, totalBytes: Long?): Map<String, ByteArray> =
    mapOf(
        PeerTxt.FREE_MEMORY to (freeBytes ?: 0L).toString().toByteArray(),
        PeerTxt.TOTAL_MEMORY to (totalBytes ?: 0L).toString().toByteArray(),
    )
