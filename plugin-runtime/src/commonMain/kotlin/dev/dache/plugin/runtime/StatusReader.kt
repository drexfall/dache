package dev.dache.plugin.runtime

import dev.dache.core.model.ProviderError
import dev.dache.core.model.ProviderId
import dev.dache.core.model.ProviderStatus
import dev.dache.core.model.StatusComponent
import dev.dache.core.model.StatusIncident
import dev.dache.core.model.StatusLevel
import dev.dache.plugin.api.StatusListSpec
import dev.dache.plugin.api.StatusSpec
import dev.dache.plugin.runtime.mapping.Expression
import dev.dache.plugin.runtime.mapping.Value
import dev.dache.plugin.runtime.mapping.asInstant
import dev.dache.plugin.runtime.mapping.asText
import dev.dache.plugin.runtime.security.HostGuard
import dev.dache.plugin.runtime.security.HostNotAllowedException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.time.Clock

/**
 * Reads a provider's public status page. It never sees a token: the request carries none, and it may only go to
 * the status URL's own host.
 */
internal class StatusReader(
    private val spec: StatusSpec,
    private val providerId: ProviderId,
    private val client: HttpClient,
    private val clock: Clock = Clock.System,
) {
    private val url = Url(spec.url)
    private val guard = HostGuard(listOf(url.host))
    private val indicator = Expression.parse(spec.indicator)
    private val description = spec.description?.let(Expression::parse)

    suspend fun read(): ProviderStatus {
        val body = fetch()
        return ProviderStatus(
            providerId = providerId,
            level = level(indicator.evaluate(body)?.asText(), spec.levelMap),
            description = description?.evaluate(body)?.asText(),
            pageUrl = spec.pageUrl,
            components = spec.components?.let { components(it, body) }.orEmpty(),
            incidents = spec.incidents?.let { incidents(it, body) }.orEmpty(),
            fetchedAt = clock.now(),
        )
    }

    // Any failure reading a status page is a network problem as far as the dashboard is concerned.
    @Suppress("ThrowsCount")
    private suspend fun fetch(): JsonElement {
        try {
            guard.check(url)
            val response =
                client.get(url) {
                    header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                }
            if (!response.status.isSuccess()) throw ProviderError.Server(response.status.value)
            return Json.parseToJsonElement(response.bodyAsText())
        } catch (e: CancellationException) {
            throw e
        } catch (e: ProviderError) {
            throw e
        } catch (e: HostNotAllowedException) {
            throw ProviderError.Unknown(e)
        } catch (_: IOException) {
            throw ProviderError.Network()
        } catch (e: SerializationException) {
            throw ProviderError.Unknown(e)
        }
    }

    /** Components that are another's parent become groups; the rest are listed under their group's name. */
    private fun components(
        list: StatusListSpec,
        body: JsonElement,
    ): List<StatusComponent> {
        val rows =
            items(list, body).map { item ->
                Row(
                    id = field(list, "id", item),
                    name = field(list, "name", item).orEmpty(),
                    status = field(list, "status", item),
                    parent = field(list, "parent", item),
                )
            }
        val parents = rows.mapNotNull { it.parent }.toSet()
        val names = rows.filter { it.id != null }.associate { it.id to it.name }
        return rows
            .filter { it.id == null || it.id !in parents }
            .filter { it.name.isNotBlank() }
            .map { StatusComponent(it.name, level(it.status, list.statusMap), it.parent?.let(names::get)) }
    }

    private fun incidents(
        list: StatusListSpec,
        body: JsonElement,
    ): List<StatusIncident> =
        items(list, body).mapNotNull { item ->
            val name = field(list, "name", item) ?: return@mapNotNull null
            StatusIncident(
                name = name,
                status = field(list, "status", item),
                impact = level(field(list, "impact", item), list.statusMap),
                updatedAt = list.fields["updatedAt"]?.let { Expression.parse(it).evaluate(item)?.asInstant() },
                url = field(list, "url", item),
            )
        }

    private fun items(
        list: StatusListSpec,
        body: JsonElement,
    ): List<JsonElement> = (Expression.parse(list.items).evaluate(body) as? Value.Items)?.values.orEmpty()

    private fun field(
        list: StatusListSpec,
        name: String,
        item: JsonElement,
    ): String? =
        list.fields[name]?.let {
            Expression
                .parse(it)
                .evaluate(item)
                ?.asText()
                ?.ifBlank { null }
        }

    private data class Row(
        val id: String?,
        val name: String,
        val status: String?,
        val parent: String?,
    )
}

private fun level(
    value: String?,
    map: Map<String, String>,
): StatusLevel =
    value?.let(map::get)?.let { name -> StatusLevel.entries.firstOrNull { it.name == name } } ?: StatusLevel.UNKNOWN
