package com.lumarans30.hexamesh.platform

/**
 * Flags the app always injects (model, host, API key, device, RPC peers, models
 * directory). Anything the user types matching one of these is stripped so the
 * app-owned value always wins. The port is not here: it lives in the args as
 * `--port` and the app parses it.
 */
val LOCKED_LAUNCH_FLAGS =
    setOf("-m", "--model", "--host", "--api-key", "--device", "--rpc", "--models-dir")

/** Editable defaults, port included. `-ngl` assumes the GPU backend. */
const val DEFAULT_LAUNCH_ARGS = "--port 8080 --slots -fa on -t 6 -ub 16 --no-warmup -ngl 99"

fun parseLaunchArgs(text: String): List<String> =
    text.split(' ', '\t', '\n', '\r').filter { it.isNotBlank() }

/** Locked flags present in [args], for validation messaging. */
fun lockedLaunchFlags(args: List<String>): List<String> =
    args.filter { it.substringBefore('=') in LOCKED_LAUNCH_FLAGS }

/** Tokens the node launches with; locked flags and their values are dropped. */
fun effectiveLaunchArgs(text: String): List<String> {
    val tokens = parseLaunchArgs(text)
    val result = mutableListOf<String>()
    var i = 0
    while (i < tokens.size) {
        val token = tokens[i]
        if (token.substringBefore('=') in LOCKED_LAUNCH_FLAGS) {
            // `flag=value` carries its own value; `flag value` consumes the next token.
            i += if ('=' in token) 1 else 2
            continue
        }
        result += token
        i++
    }
    return result
}

/**
 * The port the server will bind to: the last valid `--port`/`--port=` in [args],
 * or null when neither is present (llama.cpp's own default then applies).
 */
fun parseLaunchPort(args: List<String>): Int? {
    var port: Int? = null
    var i = 0
    while (i < args.size) {
        val token = args[i]
        val value =
            when {
                token == "--port" && i + 1 < args.size -> args[++i]
                token.startsWith("--port=") -> token.substringAfter('=')
                else -> null
            }
        value?.trim()?.toIntOrNull()?.takeIf(::isValidPort)?.let { port = it }
        i++
    }
    return port
}

/** Rewrites the port in [text], adding a `--port` pair when none is present. */
fun withPort(text: String, port: Int): String {
    val tokens = parseLaunchArgs(text).toMutableList()
    val index = tokens.indexOfLast { it == "--port" || it.startsWith("--port=") }
    if (index < 0) return (listOf("--port", port.toString()) + tokens).joinToString(" ")

    if (tokens[index] == "--port" && index + 1 < tokens.size) {
        tokens[index + 1] = port.toString()
    } else {
        tokens[index] = "--port=$port"
    }
    return tokens.joinToString(" ")
}
