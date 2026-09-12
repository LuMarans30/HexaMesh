package com.lumarans30.hexamesh.mesh

/** mDNS service type peers advertise for the RPC mesh. */
const val MESH_SERVICE_TYPE = "_hexamesh._tcp"

/** ggml-rpc-server's default port; used when a fallback entry omits one. */
const val DEFAULT_RPC_PORT = 50052

/** TXT attributes a peer may publish alongside its service record. */
object PeerTxt {
    const val FREE_MEMORY = "free_mem"
    const val TOTAL_MEMORY = "total_mem"
}

/** mDNS instance name for this device; Android suffixes it on a collision. */
fun meshServiceName(model: String): String {
    val label = model.trim().replace(Regex("[^A-Za-z0-9-]+"), "-").trim('-')
    return if (label.isEmpty()) "HexaMesh" else "HexaMesh-$label"
}
