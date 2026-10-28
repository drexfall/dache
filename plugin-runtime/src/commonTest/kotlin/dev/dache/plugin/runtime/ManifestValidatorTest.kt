package dev.dache.plugin.runtime

import dev.dache.plugin.api.AuthSpec
import dev.dache.plugin.api.FieldSpec
import dev.dache.plugin.api.ListSpec
import dev.dache.plugin.api.PagingSpec
import dev.dache.plugin.api.PluginManifest
import dev.dache.plugin.api.RequestSpec
import dev.dache.plugin.api.ResourceSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ManifestValidatorTest {
    private val valid = TestManifests.sites
    private val site = valid.resources.single()

    private fun problems(manifest: PluginManifest) = ManifestValidator.validate(manifest).map { it.toString() }

    private fun withSite(transform: ResourceSpec.() -> ResourceSpec) = valid.copy(resources = listOf(site.transform()))

    private fun withFields(transform: MutableMap<String, FieldSpec>.() -> Unit) =
        withSite { copy(fields = fields.toMutableMap().apply(transform)) }

    private fun assertProblem(
        expected: String,
        manifest: PluginManifest,
    ) {
        val actual = problems(manifest)
        assertTrue(actual.any { expected in it }, "expected \"$expected\" in $actual")
    }

    @Test
    fun acceptsAValidManifest() {
        assertEquals(emptyList(), problems(valid))
    }

    @Test
    fun rejectsBadIdentity() {
        assertProblem("manifestVersion: unsupported version 2", valid.copy(manifestVersion = 2))
        assertProblem("id: must match", valid.copy(id = "Netlify!"))
        assertProblem("version: must be MAJOR.MINOR.PATCH", valid.copy(version = "1"))
        assertProblem("capabilities[0]: unknown capability \"SITE\"", valid.copy(capabilities = listOf("SITE")))
    }

    @Test
    fun rejectsWildcardOrSchemeHosts() {
        assertProblem("hosts[0]", valid.copy(hosts = listOf("*.example.com")))
        assertProblem("hosts[0]", valid.copy(hosts = listOf("https://api.example.com")))
        assertProblem("hosts: declare at least one host", valid.copy(hosts = emptyList()))
    }

    @Test
    fun everyUrlMustPassHostGuard() {
        assertProblem(
            "resources[0].request.url: host evil.example.net is not declared",
            withSite { copy(request = request.copy(url = "https://evil.example.net/sites")) },
        )
        assertProblem(
            "validate.request.url: only https is allowed",
            valid.copy(
                validate = valid.validate.copy(request = valid.validate.request.copy(url = "http://api.example.com/u")),
            ),
        )
        assertProblem(
            "resources[0].request.url: put query parameters in \"query\"",
            withSite { copy(request = request.copy(url = "https://api.example.com/sites?x=1")) },
        )
    }

    @Test
    fun manifestCannotWriteIntoTheAuthSlot() {
        assertProblem(
            "resources[0].request.headers.Authorization: header can't be set",
            withSite { copy(request = request.copy(headers = mapOf("Authorization" to "Bearer x"))) },
        )
        assertProblem(
            "headers.Cookie",
            withSite { copy(request = request.copy(headers = mapOf("Cookie" to "a=b"))) },
        )
        val queryAuth = valid.copy(auth = AuthSpec.Query("api_key"))
        assertProblem(
            "query.api_key: collides with the auth parameter",
            queryAuth.copy(resources = listOf(site.copy(request = site.request.copy(query = mapOf("api_key" to "x"))))),
        )
        val headerAuth = valid.copy(auth = AuthSpec.Header("X-Api-Key"))
        assertProblem(
            "headers.x-api-key",
            headerAuth.copy(
                resources = listOf(site.copy(request = site.request.copy(headers = mapOf("x-api-key" to "x")))),
            ),
        )
    }

    @Test
    fun onlyGetForNow() {
        assertProblem("method: only GET is supported", withSite { copy(request = request.copy(method = "POST")) })
    }

    @Test
    fun pagingHasACap() {
        assertProblem(
            "paging.maxPages: must be 1..50",
            withSite { copy(request = request.copy(paging = PagingSpec.Page(maxPages = 500))) },
        )
        assertProblem(
            "paging: param and sizeParam must be distinct",
            withSite { copy(request = request.copy(paging = PagingSpec.Page(param = "p", sizeParam = "p"))) },
        )
        assertProblem(
            "paging: \"filter\" is also set elsewhere",
            withSite { copy(request = request.copy(paging = PagingSpec.Page(param = "filter"))) },
        )
    }

    @Test
    fun cursorAndLinkPagingAreCheckedToo() {
        assertProblem(
            "paging.next: unknown pipe",
            withSite {
                copy(
                    request = request.copy(paging = PagingSpec.Cursor(param = "until", next = "next | nope")),
                )
            },
        )
        assertProblem(
            "paging: param and sizeParam must be distinct",
            withSite {
                copy(
                    request = request.copy(paging = PagingSpec.Cursor(param = "c", next = "n", sizeParam = "c")),
                )
            },
        )
        assertProblem(
            "paging.size: must be 1..1000",
            withSite { copy(request = request.copy(paging = PagingSpec.Link(size = 0))) },
        )
        assertProblem(
            "paging: \"filter\" is also set elsewhere",
            withSite { copy(request = request.copy(paging = PagingSpec.Link(sizeParam = "filter"))) },
        )
    }

    @Test
    fun placeholdersNeedAnItemToFillThem() {
        assertProblem(
            "resources[0].request.url: placeholders need an item",
            withSite { copy(request = request.copy(url = "https://api.example.com/sites/{id}")) },
        )
        assertProblem(
            "validate.request.query.u: placeholders need an item",
            valid.copy(
                validate = valid.validate.copy(request = valid.validate.request.copy(query = mapOf("u" to "{id}"))),
            ),
        )
        assertProblem(
            "resources[0].forEach.request.url: placeholders need an item",
            withSite {
                copy(forEach = ListSpec(RequestSpec(url = "https://api.example.com/accounts/{id}")))
            },
        )
    }

    @Test
    fun fannedOutRequestsMayUsePlaceholdersInThePathAndQuery() {
        val manifest =
            withSite {
                copy(
                    forEach = ListSpec(RequestSpec(url = "https://api.example.com/accounts"), items = "result"),
                    request =
                        request.copy(
                            url = "https://api.example.com/accounts/{id}/sites",
                            query =
                                mapOf(
                                    "o" to "{name}",
                                ),
                        ),
                )
            }

        assertEquals(emptyList(), problems(manifest))
    }

    @Test
    fun placeholdersCannotChooseTheHost() {
        val parents = ListSpec(RequestSpec(url = "https://api.example.com/accounts"))
        assertProblem(
            "resources[0].request.url: placeholders can only be in the path",
            withSite { copy(forEach = parents, request = request.copy(url = "https://{host}/sites")) },
        )
        assertProblem(
            "resources[0].request.url: unmatched brace",
            withSite { copy(forEach = parents, request = request.copy(url = "https://api.example.com/{id/sites")) },
        )
        assertProblem(
            "resources[0].request.query.{id}: parameter names can't have placeholders",
            withSite { copy(forEach = parents, request = request.copy(query = mapOf("{id}" to "x"))) },
        )
    }

    @Test
    fun nestedRequestsAreCheckedLikeAnyOther() {
        val deployments = site.fields["deployments"]
        assertIs<FieldSpec.Nested>(deployments)

        fun withRequest(url: String) =
            withFields { put("deployments", deployments.copy(request = RequestSpec(url = url))) }

        assertEquals(emptyList(), problems(withRequest("https://api.example.com/sites/{id}/deploys")))
        assertProblem(
            "fields.deployments.request.url: host evil.example.net is not declared",
            withRequest("https://evil.example.net/sites/{id}/deploys"),
        )
    }

    @Test
    fun unknownKindAndMissingCapability() {
        assertProblem("resources[0].kind: unknown kind \"zone\"; supported: site", withSite { copy(kind = "zone") })
        assertProblem(
            "resources[0].kind: kind site needs capability SITES",
            valid.copy(capabilities = listOf("DEPLOYMENTS")),
        )
    }

    @Test
    fun fieldsMustFitTheModel() {
        assertProblem("fields.name: required text field", withFields { remove("name") })
        assertProblem("fields.title: unknown field", withFields { put("title", FieldSpec.Expr("x")) })
        assertProblem(
            "fields.name: only list fields take an array",
            withFields { put("name", FieldSpec.ExprList(listOf("a", "b"))) },
        )
        assertProblem(
            "fields.deployments: expects a nested mapping",
            withFields { put("deployments", FieldSpec.Expr("x")) },
        )
        assertProblem("fields.name: unknown pipe \"upper\"", withFields { put("name", FieldSpec.Expr("name | upper")) })
        assertProblem(
            "fields.customDomains[1]: invalid path segment",
            withFields {
                put("customDomains", FieldSpec.ExprList(listOf("a", "b c")))
            },
        )
    }

    @Test
    fun nestedFieldsAreCheckedToo() {
        val deployments = site.fields["deployments"]
        assertIs<FieldSpec.Nested>(deployments)

        fun nested(transform: FieldSpec.Nested.() -> FieldSpec.Nested) =
            withFields {
                put("deployments", deployments.transform())
            }

        assertProblem(
            "fields.deployments.fields.createdAt: a time field must end with the date pipe",
            nested { copy(fields = fields + ("createdAt" to FieldSpec.Expr("created_at"))) },
        )
        assertProblem(
            "fields.deployments.statusMap.ready: unknown status \"OK\"",
            nested { copy(statusMap = mapOf("ready" to "OK")) },
        )
        assertProblem("fields.deployments.statusMap: map provider states", nested { copy(statusMap = emptyMap()) })
        assertProblem("fields.deployments.fields.webUrl: required", nested { copy(fields = fields - "webUrl") })
    }

    @Test
    fun loaderReportsDecodeErrorsAsProblems() {
        val result = ManifestLoader.load("{ \"id\": 1 }")

        assertIs<LoadResult.Invalid>(result)
        assertEquals("$", result.problems.single().path)
    }

    @Test
    fun loaderAcceptsValidJson() {
        assertIs<LoadResult.Loaded>(ManifestLoader.load(TestManifests.sitesJson))
    }
}
