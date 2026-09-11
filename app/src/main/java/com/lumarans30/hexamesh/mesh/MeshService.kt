package com.lumarans30.hexamesh.mesh

/** mDNS service type peers advertise for the RPC mesh. */
const val MESH_SERVICE_TYPE = "_hexamesh._tcp"

/** ggml-rpc-server's default listen port, used when a fallback entry omits one. */
const val DEFAULT_RPC_PORT = 50052

/** TXT attributes a peer may publish alongside its service record. */
object PeerTxt {
    const val FREE_MEMORY = "free_mem"
    const val TOTAL_MEMORY = "total_mem"
}

/**
 * Builds the mDNS instance name this device advertises, from the model string.
 * Android appends a suffix when the name collides on the network.
 */
fun meshServiceName(model: String): String {
    val label = model.trim().replace(Regex("[^A-Za-z0-9-]+"), "-").trim('-')
    return if (label.isEmpty()) "HexaMesh" else "HexaMesh-$label"
}
