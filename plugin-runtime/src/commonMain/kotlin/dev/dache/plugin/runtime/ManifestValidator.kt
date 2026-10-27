package dev.dache.plugin.runtime

import dev.dache.core.model.Capability
import dev.dache.core.model.DeployStatus
import dev.dache.plugin.api.AuthSpec
import dev.dache.plugin.api.FieldSpec
import dev.dache.plugin.api.ManifestJson
import dev.dache.plugin.api.PagingSpec
import dev.dache.plugin.api.PluginManifest
import dev.dache.plugin.api.RequestSpec
import dev.dache.plugin.runtime.mapping.Expression
import dev.dache.plugin.runtime.mapping.ExpressionException
import dev.dache.plugin.runtime.mapping.FieldDef
import dev.dache.plugin.runtime.mapping.FieldType
import dev.dache.plugin.runtime.mapping.ManifestKind
import dev.dache.plugin.runtime.mapping.Pipe
import dev.dache.plugin.runtime.security.HostGuard

/** One thing wrong with a manifest. [path] points into the JSON, e.g. `resources[0].fields.name`. */
data class ManifestProblem(
    val path: String,
    val message: String,
) {
    override fun toString(): String = "$path: $message"
}

/**
 * Checks everything about a decoded manifest that the type system can't: ids, hosts, that every URL passes
 * [HostGuard], that the auth slot can't be overwritten by declared headers or query parameters, paging
 * limits, and that field mappings fit the target model and parse.
 */
object ManifestValidator {
    const val MAX_PAGES = 50
    const val MAX_PAGE_SIZE = 1000

    private val idPattern = Regex("[a-z][a-z0-9-]{1,39}")
    private val versionPattern = Regex("[0-9]+\\.[0-9]+\\.[0-9]+")
    private val headerNamePattern = Regex("[A-Za-z0-9-]+")

    /** Headers a manifest may not set itself: secrets only travel through the auth slot. */
    private val reservedHeaders = setOf("authorization", "proxy-authorization", "cookie", "host", "content-length")

    fun validate(manifest: PluginManifest): List<ManifestProblem> = Checker(manifest).run()

    private class Checker(
        private val manifest: PluginManifest,
    ) {
        private val problems = mutableListOf<ManifestProblem>()
        private val guard = HostGuard(manifest.hosts)

        private fun problem(
            path: String,
            message: String,
        ) {
            problems += ManifestProblem(path, message)
        }

        fun run(): List<ManifestProblem> {
            checkHeader()
            checkAuth()
            checkRequest("validate.request", manifest.validate.request)
            checkExpression("validate.displayName", manifest.validate.displayName)
            if (manifest.resources.isEmpty()) problem("resources", "declare at least one resource")
            manifest.resources.forEachIndexed { index, resource ->
                val path = "resources[$index]"
                val kind = ManifestKind.named(resource.kind)
                if (kind == null) {
                    problem(
                        "$path.kind",
                        "unknown kind \"${resource.kind}\"; supported: ${ManifestKind.entries.joinToString {
                            it.kindName
                        }}",
                    )
                } else {
                    if (kind.capability.name !in manifest.capabilities) {
                        problem("$path.kind", "kind ${kind.kindName} needs capability ${kind.capability.name}")
                    }
                    checkFields("$path.fields", resource.fields, kind.fields)
                    checkStatusMap(path, resource.statusMap, resource.fields, kind.fields)
                }
                checkRequest("$path.request", resource.request)
                resource.items?.let { checkExpression("$path.items", it) }
            }
            return problems
        }

        private fun checkHeader() {
            if (manifest.manifestVersion != ManifestJson.CURRENT_VERSION) {
                problem(
                    "manifestVersion",
                    "unsupported version ${manifest.manifestVersion}; this build reads ${ManifestJson.CURRENT_VERSION}",
                )
            }
            if (!idPattern.matches(manifest.id)) problem("id", "must match ${idPattern.pattern}")
            if (manifest.name.isBlank()) problem("name", "must not be blank")
            if (!versionPattern.matches(manifest.version)) problem("version", "must be MAJOR.MINOR.PATCH")
            if (manifest.hosts.isEmpty()) problem("hosts", "declare at least one host")
            manifest.hosts.forEachIndexed { index, host ->
                if (!HostGuard.isValidHost(host)) {
                    problem("hosts[$index]", "\"$host\" is not a lowercase hostname (no scheme, port or wildcard)")
                }
            }
            if (manifest.capabilities.isEmpty()) problem("capabilities", "declare at least one capability")
            manifest.capabilities.forEachIndexed { index, name ->
                if (Capability.entries.none { it.name == name }) {
                    problem("capabilities[$index]", "unknown capability \"$name\"")
                }
            }
        }

        private fun checkAuth() {
            when (val auth = manifest.auth) {
                AuthSpec.Bearer -> Unit
                is AuthSpec.Header ->
                    if (!headerNamePattern.matches(auth.name) || auth.name.lowercase() in setOf("host", "cookie")) {
                        problem("auth.name", "\"${auth.name}\" can't carry a token")
                    }
                is AuthSpec.Query -> if (auth.name.isBlank()) problem("auth.name", "must not be blank")
            }
        }

        private fun checkRequest(
            path: String,
            request: RequestSpec,
        ) {
            if (request.method != "GET") problem("$path.method", "only GET is supported")
            guard.violation(request.url)?.let { problem("$path.url", it) }
            if ('?' in request.url) problem("$path.url", "put query parameters in \"query\"")
            request.headers.keys.forEach { name ->
                val lower = name.lowercase()
                if (!headerNamePattern.matches(name) ||
                    lower in reservedHeaders ||
                    lower == manifest.auth.headerName()
                ) {
                    problem("$path.headers.$name", "header can't be set by a manifest")
                }
            }
            request.query.keys
                .filter { it == manifest.auth.queryName() }
                .forEach { problem("$path.query.$it", "collides with the auth parameter") }
            request.paging?.let { checkPaging("$path.paging", it, request) }
        }

        private fun checkPaging(
            path: String,
            paging: PagingSpec,
            request: RequestSpec,
        ) {
            when (paging) {
                is PagingSpec.Page -> {
                    if (paging.maxPages !in 1..MAX_PAGES) problem("$path.maxPages", "must be 1..$MAX_PAGES")
                    if (paging.size !in 1..MAX_PAGE_SIZE) problem("$path.size", "must be 1..$MAX_PAGE_SIZE")
                    if (paging.start < 0) problem("$path.start", "must not be negative")
                    val params = listOfNotNull(paging.param, paging.sizeParam)
                    if (params.any { it.isBlank() } || params.size != params.toSet().size) {
                        problem(path, "param and sizeParam must be distinct and not blank")
                    }
                    params
                        .filter { it in request.query || it == manifest.auth.queryName() }
                        .forEach { problem(path, "\"$it\" is also set elsewhere") }
                }
            }
        }

        private fun checkFields(
            path: String,
            fields: Map<String, FieldSpec>,
            schema: Map<String, FieldDef>,
        ) {
            schema
                .filter { (name, def) -> def.required && name !in fields }
                .forEach { (name, def) -> problem("$path.$name", "required ${def.type.name.lowercase()} field") }
            fields.forEach { (name, spec) ->
                val def = schema[name]
                if (def == null) {
                    problem("$path.$name", "unknown field; expected one of ${schema.keys.joinToString()}")
                } else {
                    checkField("$path.$name", spec, def)
                }
            }
        }

        private fun checkField(
            path: String,
            spec: FieldSpec,
            def: FieldDef,
        ) {
            when (spec) {
                is FieldSpec.Expr -> {
                    if (def.type == FieldType.RECORDS) {
                        problem(path, "expects a nested mapping object")
                        return
                    }
                    val expression = checkExpression(path, spec.expr) ?: return
                    if (def.type == FieldType.TIME && expression.pipes.lastOrNull()?.pipe != Pipe.DATE) {
                        problem(path, "a time field must end with the date pipe")
                    }
                }
                is FieldSpec.ExprList ->
                    if (def.type == FieldType.TEXT_LIST) {
                        spec.exprs.forEachIndexed { index, expr -> checkExpression("$path[$index]", expr) }
                    } else {
                        problem(path, "only list fields take an array of expressions")
                    }
                is FieldSpec.Nested ->
                    if (def.type == FieldType.RECORDS) {
                        checkExpression("$path.from", spec.from)
                        checkFields("$path.fields", spec.fields, def.nested)
                        checkStatusMap(path, spec.statusMap, spec.fields, def.nested)
                    } else {
                        problem(path, "only record fields take a nested mapping")
                    }
            }
        }

        private fun checkStatusMap(
            path: String,
            statusMap: Map<String, String>,
            fields: Map<String, FieldSpec>,
            schema: Map<String, FieldDef>,
        ) {
            val mapsStatus = fields.keys.any { schema[it]?.type == FieldType.STATUS }
            if (mapsStatus && statusMap.isEmpty()) problem("$path.statusMap", "map provider states to a status")
            if (!mapsStatus && statusMap.isNotEmpty()) problem("$path.statusMap", "no status field to apply it to")
            statusMap.forEach { (from, to) ->
                if (DeployStatus.entries.none { it.name == to }) {
                    problem(
                        "$path.statusMap.$from",
                        "unknown status \"$to\"; use one of ${DeployStatus.entries.joinToString { it.name }}",
                    )
                }
            }
        }

        private fun checkExpression(
            path: String,
            source: String,
        ): Expression? =
            try {
                Expression.parse(source)
            } catch (e: ExpressionException) {
                problem(path, e.message.orEmpty())
                null
            }
    }
}

/** The lowercase header that carries the token, if any. */
private fun AuthSpec.headerName(): String? =
    when (this) {
        AuthSpec.Bearer -> "authorization"
        is AuthSpec.Header -> name.lowercase()
        is AuthSpec.Query -> null
    }

private fun AuthSpec.queryName(): String? = (this as? AuthSpec.Query)?.name
