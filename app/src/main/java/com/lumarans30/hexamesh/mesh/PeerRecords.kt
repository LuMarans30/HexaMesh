package com.lumarans30.hexamesh.mesh

/**
 * Builds a peer from a resolved mDNS record. A missing or malformed memory TXT
 * stays unknown rather than becoming 0.
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
 * One `host` or `host[:port]` per line, `#` comments ignored. A missing port
 * defaults to [DEFAULT_RPC_PORT]; malformed entries are dropped.
 */
fun parseFallbackPeers(text: String): List<PeerNode> =
    text
        .lineSequence()
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

        entry.count { it == ':' } == 1 -> {
            entry.substringBefore(':') to entry.substringAfter(':')
        }

        // A bare IPv6 address is ambiguous; require the bracketed form.
        ':' in entry -> {
            null
        }

        else -> {
            entry to null
        }
    }

private fun Map<String, ByteArray>.longValue(key: String): Long? = this[key]?.toString(Charsets.UTF_8)?.trim()?.toLongOrNull()

private const val MEMORY_PUBLISH_STEP = 256L * 1024 * 1024

/**
 * Rounds free memory down to a coarse step so ordinary cache churn doesn't change
 * the TXT record and force an mDNS re-registration.
 */
fun quantizeMemory(bytes: Long?): Long? = bytes?.let { it / MEMORY_PUBLISH_STEP * MEMORY_PUBLISH_STEP }

/**
 * TXT attributes advertising spare RAM. Unknown readings become 0 so the record
 * always has at least one attribute (an empty TXT record trips the NSD service).
 */
fun memoryAttributes(
    freeBytes: Long?,
    totalBytes: Long?,
): Map<String, ByteArray> =
    mapOf(
        PeerTxt.FREE_MEMORY to (freeBytes ?: 0L).toString().toByteArray(),
        PeerTxt.TOTAL_MEMORY to (totalBytes ?: 0L).toString().toByteArray(),
    )
