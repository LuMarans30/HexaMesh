package com.lumarans30.hexamesh.platform

import java.io.File

/**
 * Flags the app always injects (host, API key, device, RPC peers, models
 * directory). Anything the user types matching one of these is stripped so the
 * app-owned value always wins. `-m`/`--model` are deliberately **not** here: a
 * user can pin a single model, which makes the router's `--models-dir` inert and
 * serves just that file. The port is not here either: it lives in the args as
 * `--port` and the app parses it.
 */
val LOCKED_LAUNCH_FLAGS =
    setOf("--host", "--api-key", "--device", "--rpc", "--models-dir")

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

/**
 * The single model the launch args pin, for the node notification when the node
 * is not in router mode. Prefers `--alias` (the name the API serves), else the
 * `-m`/`--model` filename, else the `-hf` repo. `null` means the router serves
 * every model.
 */
internal fun servedModelLabel(args: List<String>): String? {
    val model = flagValue(args, "--model") ?: flagValue(args, "-m")
    val repo = flagValue(args, "--hf-repo") ?: flagValue(args, "-hf")
    if (model.isNullOrEmpty() && repo.isNullOrEmpty()) return null

    val alias = flagValue(args, "--alias")
    if (!alias.isNullOrEmpty()) return alias

    return model?.takeIf { it.isNotEmpty() }?.let { File(it).name } ?: repo
}

/** Value of [flag] written as `flag value` or `flag=value`; null when absent. */
private fun flagValue(args: List<String>, flag: String): String? {
    args.forEachIndexed { i, token ->
        when {
            token == flag -> return args.getOrNull(i + 1)
            token.startsWith("$flag=") -> return token.substringAfter('=')
        }
    }
    return null
}
