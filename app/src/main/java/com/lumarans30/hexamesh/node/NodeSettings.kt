package com.lumarans30.hexamesh.node

import com.lumarans30.hexamesh.bridge.ServerRole

/**
 * Node configuration the controller reads fresh each time a model is loaded, so
 * settings changes take effect on the next start without restarting the process.
 */
interface NodeSettings {
    val port: Int

    val launchArgs: List<String>

    /** [ServerRole.SERVER] to serve the API, [ServerRole.RPC] to expose devices. */
    val role: String
        get() = ServerRole.SERVER
}
