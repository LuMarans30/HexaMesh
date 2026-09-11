package com.lumarans30.hexamesh.node

/**
 * Node configuration the controller reads fresh each time a model is loaded, so
 * settings changes take effect on the next start without restarting the process.
 */
interface NodeSettings {
    val port: Int
}
