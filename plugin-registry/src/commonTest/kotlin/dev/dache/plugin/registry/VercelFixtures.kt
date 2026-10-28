package dev.dache.plugin.registry

/** Trimmed Vercel API responses with made-up data. */
object VercelFixtures {
    const val TOKEN = "vercel_fake_token_for_tests_only"

    const val UNAUTHORIZED = """{"error":{"code":"forbidden","message":"Not authorized","invalidToken":true}}"""

    val user =
        """
        {
          "user": {
            "id": "Xa1fakeUserId0000000001",
            "email": "dev@example.com",
            "name": "Dev Example",
            "username": "devexample",
            "defaultTeamId": "team_fakeTeamId000000001"
          }
        }
        """.trimIndent()

    /** First page: one GitHub-linked project with a custom domain, and `next` pointing at page 2. */
    val projectsPage1 =
        """
        {
          "projects": [
            {
              "accountId": "team_fakeTeamId000000001",
              "id": "prj_fakeDocs00000000000001",
              "name": "acme-docs",
              "framework": "nextjs",
              "alias": [
                { "domain": "docs.acme.example", "environment": "production", "target": "PRODUCTION" },
                { "domain": "acme-docs.vercel.app", "environment": "production", "target": "PRODUCTION" }
              ],
              "link": { "type": "github", "org": "acme", "repo": "docs", "repoId": 1000001, "productionBranch": "main" },
              "createdAt": 1767225600000,
              "updatedAt": 1793093400000
            }
          ],
          "pagination": { "count": 1, "next": 1793000000000, "prev": null }
        }
        """.trimIndent()

    /** Last page: a project with no Git link and no deployments yet. */
    val projectsPage2 =
        """
        {
          "projects": [
            {
              "accountId": "team_fakeTeamId000000001",
              "id": "prj_fakeBlog00000000000002",
              "name": "acme-blog",
              "alias": [{ "domain": "acme-blog.vercel.app", "environment": "production", "target": "PRODUCTION" }],
              "createdAt": 1767225600000,
              "updatedAt": 1792000000000
            }
          ],
          "pagination": { "count": 1, "next": null, "prev": 1793000000000 }
        }
        """.trimIndent()

    val docsDeployments =
        """
        {
          "deployments": [
            {
              "uid": "dpl_fakeDeploy000000000002",
              "name": "acme-docs",
              "url": "acme-docs-b2c3d4.vercel.app",
              "created": 1793093400120,
              "createdAt": 1793093400120,
              "buildingAt": 1793093402000,
              "state": "BUILDING",
              "readyState": "BUILDING",
              "target": "production",
              "projectId": "prj_fakeDocs00000000000001",
              "inspectorUrl": "https://vercel.com/acme/acme-docs/fakeDeploy2",
              "meta": { "githubCommitRef": "main", "githubCommitMessage": "Add search page" }
            },
            {
              "uid": "dpl_fakeDeploy000000000001",
              "name": "acme-docs",
              "url": "acme-docs-a1b2c3.vercel.app",
              "created": 1793000000000,
              "createdAt": 1793000000000,
              "buildingAt": 1793000001000,
              "ready": 1793000060000,
              "state": "READY",
              "readyState": "READY",
              "target": "production",
              "projectId": "prj_fakeDocs00000000000001",
              "inspectorUrl": "https://vercel.com/acme/acme-docs/fakeDeploy1",
              "meta": { "githubCommitRef": "main", "githubCommitMessage": "Update install guide" }
            }
          ],
          "pagination": { "count": 2, "next": 1792999999999, "prev": null }
        }
        """.trimIndent()

    const val NO_DEPLOYMENTS = """{"deployments": [], "pagination": {"count": 0, "next": null, "prev": null}}"""
}
