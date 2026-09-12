package com.lumarans30.hexamesh.mesh

/** Merges discovered and manual peers by endpoint; a manual entry wins and lists first. */
fun mergePeers(discovered: List<PeerNode>, manual: List<PeerNode>): List<PeerNode> {
    val seen = mutableSetOf<String>()
    return buildList(manual.size + discovered.size) {
        (manual + discovered).forEach { peer ->
            if (seen.add(peer.endpoint)) add(peer)
        }
    }
}

/** Drops this device's own advertisement, matched by the registered mDNS name. */
fun withoutSelf(peers: List<PeerNode>, selfName: String?): List<PeerNode> =
    if (selfName.isNullOrEmpty()) peers else peers.filterNot { it.id == selfName }
