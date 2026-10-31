package dev.dache.plugin.cli

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import java.io.File

/**
 * Recorded responses, one file per request, under [dir]:
 * - `<host><path>?<query>.json` for one exact request, query parameters sorted by name, e.g.
 *   `api.example.com/v2/droplets?page=2&per_page=200.json`;
 * - `<host><path>.json` for every request to that path, whatever its query;
 * - either with a status before `.json` (`….403.json`) to answer with that status instead of 200.
 *
 * A request with no file gets a 404, which an optional list or a fan-out branch treats as empty.
 */
class Fixtures(
    private val dir: File,
) {
    /** The file for [url] and its status, or null. */
    fun find(url: Url): Pair<File, HttpStatusCode>? {
        val path = url.host + url.encodedPath
        val query =
            url.parameters
                .names()
                .sorted()
                .joinToString("&") { name -> "$name=${url.parameters[name]}" }
        val bases = listOfNotNull("$path?$query".takeIf { query.isNotEmpty() }, path)
        return bases.firstNotNullOfOrNull { base ->
            dir.resolve("$base.json").takeIf { it.isFile }?.let { it to HttpStatusCode.OK }
                ?: withStatus(base)
        }
    }

    private fun withStatus(base: String): Pair<File, HttpStatusCode>? {
        val file = dir.resolve(base)
        val prefix = "${file.name}."
        return file.parentFile
            ?.listFiles { candidate -> candidate.name.startsWith(prefix) && candidate.name.endsWith(".json") }
            ?.firstNotNullOfOrNull { candidate ->
                candidate.name
                    .removePrefix(prefix)
                    .removeSuffix(".json")
                    .toIntOrNull()
                    ?.let { candidate to HttpStatusCode.fromValue(it) }
            }
    }

    fun engine(): HttpClientEngine =
        MockEngine { request ->
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            val found = find(request.url)
            if (found == null) {
                respond("""{"message":"no fixture for ${request.url.encodedPath}"}""", HttpStatusCode.NotFound, json)
            } else {
                respond(found.first.readText(), found.second, json)
            }
        }
}
