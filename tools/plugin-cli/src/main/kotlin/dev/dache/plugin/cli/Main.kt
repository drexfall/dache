package dev.dache.plugin.cli

import dev.dache.plugin.runtime.LoadResult
import dev.dache.plugin.runtime.ManifestLoader
import dev.dache.plugin.runtime.http.createPluginHttpClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File
import kotlin.system.exitProcess

private val USAGE =
    """
    usage: dache-plugin validate <manifest.json>...
           dache-plugin test <manifest.json> --fixtures <dir> [--setting key=value]...
           dache-plugin run <manifest.json> --token-env <VAR> [--setting key=value]...
           dache-plugin schema

    run sends real requests with the token from environment variable <VAR>. Pass the token that way, never as an
    argument, so it stays out of shell history and process lists.
    """.trimIndent()

/** The token `test` sends; fixtures don't check it. */
private const val FIXTURE_TOKEN = "fixture-token"

fun main(args: Array<String>) {
    val outcome =
        when (args.firstOrNull()) {
            "validate" -> args.drop(1).takeIf { it.isNotEmpty() }?.let(::validateAll)
            "test" -> Arguments.parse(args.drop(1), "--fixtures")?.let(::test)
            "schema" -> Outcome(schemaText(), ok = true).takeIf { args.size == 1 }
            "run" -> Arguments.parse(args.drop(1), "--token-env")?.let { run(it, System::getenv) }
            else -> null
        }
    if (outcome == null) {
        System.err.println(USAGE)
        exitProcess(2)
    }
    println(outcome.text)
    exitProcess(if (outcome.ok) 0 else 1)
}

/** The schema as committed under `schema/`: two-space indent, so diffs stay readable. */
@OptIn(ExperimentalSerializationApi::class)
fun schemaText(): String =
    Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }.encodeToString(JsonObject.serializer(), ManifestSchema.generate())

private fun validateAll(paths: List<String>): Outcome {
    val reports = paths.map { validate(File(it)) }
    return Outcome(reports.joinToString("\n"), reports.all { it.valid })
}

private fun test(arguments: Arguments): Outcome {
    val dir = File(arguments.option)
    if (!dir.isDirectory) return Outcome("${dir.path}: not a directory", ok = false)
    val client = createPluginHttpClient(Fixtures(dir).engine())
    return runBlocking { refresh(arguments.manifest, client, FIXTURE_TOKEN, arguments.settings) }
}

/** Against the live API. [env] reads the environment, so tests can supply their own. */
fun run(
    arguments: Arguments,
    env: (String) -> String?,
): Outcome {
    val token = env(arguments.option)?.trim()
    if (token.isNullOrEmpty()) return Outcome("environment variable ${arguments.option} is not set", ok = false)
    return runBlocking { refresh(arguments.manifest, createPluginHttpClient(), token, arguments.settings) }
}

/** `<manifest> <flag> <value> [--setting key=value]...`, in any order after the manifest. */
class Arguments(
    val manifest: File,
    val option: String,
    val settings: Map<String, String>,
) {
    companion object {
        fun parse(
            args: List<String>,
            flag: String,
        ): Arguments? {
            val manifest = args.firstOrNull()?.takeUnless { it.startsWith("--") }
            val pairs = args.drop(1).chunked(2)
            val option = pairs.singleOrNull { it.first() == flag }?.getOrNull(1)
            val settings =
                pairs.filter { it.first() == "--setting" }.map {
                    it
                        .getOrNull(
                            1,
                        ).orEmpty()
                        .split('=', limit = 2)
                }
            val wellFormed =
                pairs.all { it.size == 2 && (it.first() == flag || it.first() == "--setting") } &&
                    settings.all { it.size == 2 }
            return if (manifest == null || option == null || !wellFormed) {
                null
            } else {
                Arguments(File(manifest), option, settings.associate { it[0] to it[1] })
            }
        }
    }
}

class Report(
    val file: String,
    val problems: List<String>,
) {
    val valid: Boolean get() = problems.isEmpty()

    override fun toString(): String =
        if (valid) {
            "$file: ok"
        } else {
            (
                listOf(
                    "$file: ${problems.size} problem(s)",
                ) + problems.map { "  $it" }
            ).joinToString("\n")
        }
}

fun validate(file: File): Report {
    if (!file.isFile) return Report(file.path, listOf("file not found"))
    val problems =
        when (val result = ManifestLoader.load(file.readText())) {
            is LoadResult.Loaded -> emptyList()
            is LoadResult.Invalid -> result.problems.map { it.toString() }
        }
    return Report(file.path, problems)
}
