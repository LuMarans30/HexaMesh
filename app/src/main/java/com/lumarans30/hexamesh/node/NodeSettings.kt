package com.lumarans30.hexamesh.node

import com.lumarans30.hexamesh.bridge.ServerRole

/** Node config the controller reads on each start, so changes apply next start. */
interface NodeSettings {
    val port: Int

    val launchArgs: List<String>

    /** [ServerRole.SERVER] to serve the API, [ServerRole.RPC] to expose devices. */
    val role: String
        get() = ServerRole.SERVER
}
