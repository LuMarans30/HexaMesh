package com.lumarans30.hexamesh.platform

import java.io.File

/**
 * Flags the app injects; matching user tokens are stripped so the injected value
 * wins. `--models-max` is injected only while meshing (Rust caps the router to one
 * child when `--rpc` is set). `-m` is deliberately absent: pinning a model
 * overrides `--models-dir`. The port is parsed from the args, not locked.
 */
val LOCKED_LAUNCH_FLAGS =
    setOf("--host", "--api-key", "--device", "--rpc", "--models-dir", "--models-max")

/** Editable defaults. `-ngl` assumes the GPU backend. */
const val DEFAULT_LAUNCH_ARGS = "--port 8080 --slots -fa on -t 6 -ub 16 --no-warmup -ngl 99"

fun parseLaunchArgs(text: String): List<String> = text.split(' ', '\t', '\n', '\r').filter { it.isNotBlank() }

fun lockedLaunchFlags(args: List<String>): List<String> = args.filter { it.substringBefore('=') in LOCKED_LAUNCH_FLAGS }

/** User args with app-owned flags and their values removed. */
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

/** Last valid `--port`/`--port=` in [args], or null so llama.cpp's default applies. */
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
        value
            ?.trim()
            ?.toIntOrNull()
            ?.takeIf(::isValidPort)
            ?.let { port = it }
        i++
    }
    return port
}

/**
 * The one model the launch args pin, or null when the router serves every model.
 * `--alias` wins, then the `-m`/`--model` filename, then the `-hf` repo.
 */
internal fun servedModelLabel(args: List<String>): String? {
    val model = flagValue(args, "--model") ?: flagValue(args, "-m")
    val repo = flagValue(args, "--hf-repo") ?: flagValue(args, "-hf")
    if (model.isNullOrEmpty() && repo.isNullOrEmpty()) return null

    val alias = flagValue(args, "--alias")
    if (!alias.isNullOrEmpty()) return alias

    return model?.takeIf { it.isNotEmpty() }?.let { File(it).name } ?: repo
}

/** Value of [flag] as `flag value` or `flag=value`; null when absent. */
private fun flagValue(
    args: List<String>,
    flag: String,
): String? {
    args.forEachIndexed { i, token ->
        when {
            token == flag -> return args.getOrNull(i + 1)
            token.startsWith("$flag=") -> return token.substringAfter('=')
        }
    }
    return null
}
