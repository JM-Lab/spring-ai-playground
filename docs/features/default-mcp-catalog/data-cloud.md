description: Default MCP Servers - Data & Cloud reference: 17 preset MCP connections with transport, auth defaults, required env, and full description per card.

# Default MCP Servers - Data & Cloud

Object storage, managed databases (relational / analytical / document), and hosting platforms. Google Cloud entries route through Google OAuth; Microsoft entries through Azure AD tenants; the rest carry the vendor's own OAuth issuer URI. BigQuery carries the `pipeline` tag - it's the only entry on this page used as both a query target and a long-running ETL endpoint.

## Entries (17)

Click any card to expand the full spec inline - transport (Streamable HTTP / STDIO), authentication shape (OAuth 2.1 / API key / Bearer / none), required environment variables, vendor URL or stdio command, and the upstream docs link.

<div class="tcg-grid" markdown>

<div class="tcg-card tcg-card--clickable t-google" id="Google-Drive" data-tool-id="Google-Drive" data-tool-title="Google Drive" markdown>
<div class="tcg-name"><span class="tcg-name__text">Google Drive</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Google Drive](https://cdn.simpleicons.org/googledrive){ width="40" .tcg-favicon }</div>
<div class="tcg-type">storage · global · preview <span class="risk risk-l3">preview</span> <span class="rl rl-l2">L2</span></div>
<div class="tcg-body" markdown>
List, read, and upload files in Google Drive, manage permissions, shared drives, and folder structure. Google Workspace MCP (Preview).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Google · T1 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** - Google (vendor-official)

**Transport** - Streamable HTTP

**URL** - `https://drivemcp.googleapis.com/mcp/v1`

**Auth** - OAuth 2.1

**OAuth 2.1** - runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.

**Stability** - PREVIEW · **Tier** - Tier 1

**Required env** - -

**Tags** - global · preview

**Tools** - 8 tools published by the vendor (per its [MCP docs](https://developers.google.com/workspace/guides/configure-mcp-servers)); no static per-tool levels - the live set varies by plan / scopes / release, so confirm it and the recomputed levels on the [Inspector](../mcp-server/inspector.md#tools):

??? note "Tools (8) - search_files · read_file_content · download_file_content · get_file_metadata · get_file_permissions · create_file · copy_file · list_recent_files"
    - `search_files`
    - `read_file_content`
    - `download_file_content`
    - `get_file_metadata`
    - `get_file_permissions`
    - `create_file`
    - `copy_file`
    - `list_recent_files`

**Description**

List, read, and upload files in Google Drive, manage permissions, shared drives, and folder structure. Google Workspace MCP (Preview).


**Docs** - [https://developers.google.com/workspace/guides/configure-mcp-servers](https://developers.google.com/workspace/guides/configure-mcp-servers)

</div>
</div>

<div class="tcg-card tcg-card--clickable t-microsoft" id="OneDrive-SharePoint" data-tool-id="OneDrive-SharePoint" data-tool-title="OneDrive & SharePoint" markdown>
<div class="tcg-name"><span class="tcg-name__text">OneDrive & SharePoint</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>:material-microsoft-onedrive:</div>
<div class="tcg-type">storage · global <span class="risk risk-l0">ga</span> <span class="rl rl-l2">L2</span></div>
<div class="tcg-body" markdown>
Browse and edit OneDrive personal files and SharePoint sites, lists, document libraries via Microsoft 365 Agent365. Requires MS_TENANT_ID.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Microsoft · T1 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** - Microsoft (vendor-official)

**Transport** - Streamable HTTP

**URL** - `https://agent365.svc.cloud.microsoft/agents/tenants/${MS_TENANT_ID}/servers/mcp_ODSPRemoteServer`

**Auth** - OAuth 2.1

**OAuth 2.1** - runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.

**Stability** - GA · **Tier** - Tier 1

**Required env** - `MS_TENANT_ID`

**Tags** - global

**Tools** - published by the vendor and discovered live on connect; its [MCP docs](https://learn.microsoft.com/en-us/microsoft-agent-365/mcp-server-reference/) don't enumerate tool names, so open the [Inspector](../mcp-server/inspector.md#tools) after Save & Connect for the live tools and their recomputed levels.

**Description**

Browse and edit OneDrive personal files and SharePoint sites, lists, document libraries via Microsoft 365 Agent365. Requires MS_TENANT_ID.


**Docs** - [https://github.com/microsoft/mcp](https://github.com/microsoft/mcp)

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="Cloudflare" data-tool-id="Cloudflare" data-tool-title="Cloudflare" markdown>
<div class="tcg-name"><span class="tcg-name__text">Cloudflare</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Cloudflare](https://cdn.simpleicons.org/cloudflare){ width="40" .tcg-favicon }</div>
<div class="tcg-type">cloud · global <span class="risk risk-l0">ga</span> <span class="rl rl-l2">L2</span></div>
<div class="tcg-body" markdown>
Cloudflare umbrella covering Workers, R2, KV, D1, Workers AI, Browser Rendering, Hyperdrive, Queues, Logs, and observability.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Cloudflare · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** - Cloudflare (vendor-official)

**Transport** - Streamable HTTP

**URL** - `https://mcp.cloudflare.com/mcp`

**Auth** - OAuth 2.1

**OAuth 2.1** - runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.

**Stability** - GA · **Tier** - Tier 2

**Required env** - -

**Tags** - global

**Tools** - published by the vendor and discovered live on connect; its [MCP docs](https://developers.cloudflare.com/agents/model-context-protocol/mcp-servers-for-cloudflare/) don't enumerate tool names, so open the [Inspector](../mcp-server/inspector.md#tools) after Save & Connect for the live tools and their recomputed levels. (15 separate MCP servers; only the Documentation server's search_cloudflare_documentation is documented by name.)

**Description**

Cloudflare umbrella covering Workers, R2, KV, D1, Workers AI, Browser Rendering, Hyperdrive, Queues, Logs, and observability.


**Docs** - [https://developers.cloudflare.com/agents/model-context-protocol/mcp-servers-for-cloudflare/](https://developers.cloudflare.com/agents/model-context-protocol/mcp-servers-for-cloudflare/)

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="Vercel" data-tool-id="Vercel" data-tool-title="Vercel" markdown>
<div class="tcg-name"><span class="tcg-name__text">Vercel</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Vercel](https://cdn.simpleicons.org/vercel){ width="40" .tcg-favicon }</div>
<div class="tcg-type">cloud · global <span class="risk risk-l0">ga</span> <span class="rl rl-l2">L2</span></div>
<div class="tcg-body" markdown>
Inspect and manage Vercel projects, deployments, domains, environment variables, and observability via OAuth.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Vercel · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** - Vercel (vendor-official)

**Transport** - Streamable HTTP

**URL** - `https://mcp.vercel.com`

**Auth** - OAuth 2.1

**OAuth 2.1** - runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.

**Stability** - GA · **Tier** - Tier 2

**Required env** - -

**Tags** - global

**Tools** - published by the vendor and discovered live on connect; its [MCP docs](https://vercel.com/docs/mcp) don't enumerate tool names, so open the [Inspector](../mcp-server/inspector.md#tools) after Save & Connect for the live tools and their recomputed levels.

**Description**

Inspect and manage Vercel projects, deployments, domains, environment variables, and observability via OAuth.


**Docs** - [https://vercel.com/docs/mcp](https://vercel.com/docs/mcp)

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="Netlify" data-tool-id="Netlify" data-tool-title="Netlify" markdown>
<div class="tcg-name"><span class="tcg-name__text">Netlify</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Netlify](https://cdn.simpleicons.org/netlify){ width="40" .tcg-favicon }</div>
<div class="tcg-type">cloud · global <span class="risk risk-l0">ga</span> <span class="rl rl-l2">L2</span></div>
<div class="tcg-body" markdown>
Manage Netlify sites, deploys, build hooks, edge functions, and form submissions via OAuth.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Netlify · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** - Netlify (vendor-official)

**Transport** - Streamable HTTP

**URL** - `https://netlify-mcp.netlify.app/mcp`

**Auth** - OAuth 2.1

**OAuth 2.1** - runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.

**Stability** - GA · **Tier** - Tier 2

**Required env** - -

**Tags** - global

**Tools** - published by the vendor and discovered live on connect; its [MCP docs](https://docs.netlify.com/welcome/build-with-ai/netlify-mcp-server/) don't enumerate tool names, so open the [Inspector](../mcp-server/inspector.md#tools) after Save & Connect for the live tools and their recomputed levels.

**Description**

Manage Netlify sites, deploys, build hooks, edge functions, and form submissions via OAuth.


**Docs** - [https://docs.netlify.com/build/build-with-ai/mcp/](https://docs.netlify.com/build/build-with-ai/mcp/)

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="Heroku" data-tool-id="Heroku" data-tool-title="Heroku" markdown>
<div class="tcg-name"><span class="tcg-name__text">Heroku</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>:material-cloud-cog-outline:</div>
<div class="tcg-type">cloud · global <span class="risk risk-l0">ga</span> <span class="rl rl-l2">L2</span></div>
<div class="tcg-body" markdown>
Inspect Heroku apps, dynos, releases, config vars, addons, and logs via OAuth.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Heroku · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** - Heroku (vendor-official)

**Transport** - Streamable HTTP

**URL** - `https://mcp.heroku.com/mcp`

**Auth** - OAuth 2.1

**OAuth 2.1** - runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.

**Stability** - GA · **Tier** - Tier 2

**Required env** - -

**Tags** - global

**Tools** - 31 tools published by the vendor (per its [MCP docs](https://github.com/heroku/heroku-mcp-server)); no static per-tool levels - the live set varies by plan / scopes / release, so confirm it and the recomputed levels on the [Inspector](../mcp-server/inspector.md#tools):

??? note "Tools (10 of 31) - list_apps · get_app_info · create_app · deploy_to_heroku · deploy_one_off_dyno · ps_scale · ps_restart · pg_psql · pipelines_promote · get_app_logs"
    - `list_apps`
    - `get_app_info`
    - `create_app`
    - `deploy_to_heroku`
    - `deploy_one_off_dyno`
    - `ps_scale`
    - `ps_restart`
    - `pg_psql`
    - `pipelines_promote`
    - `get_app_logs`

**Description**

Inspect Heroku apps, dynos, releases, config vars, addons, and logs via OAuth.


**Docs** - [https://devcenter.heroku.com/articles/heroku-mcp-server](https://devcenter.heroku.com/articles/heroku-mcp-server)

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="Render" data-tool-id="Render" data-tool-title="Render" markdown>
<div class="tcg-name"><span class="tcg-name__text">Render</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Render](https://cdn.simpleicons.org/render){ width="40" .tcg-favicon }</div>
<div class="tcg-type">cloud · global <span class="risk risk-l0">ga</span> <span class="rl rl-l2">L2</span></div>
<div class="tcg-body" markdown>
Manage Render web services, background workers, cron jobs, and deploys via OAuth.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Render · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** - Render (vendor-official)

**Transport** - Streamable HTTP

**URL** - `https://mcp.render.com/mcp`

**Auth** - OAuth 2.1

**OAuth 2.1** - runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.

**Stability** - GA · **Tier** - Tier 2

**Required env** - -

**Tags** - global

**Tools** - 24 tools published by the vendor (per its [MCP docs](https://github.com/render-oss/render-mcp-server)); no static per-tool levels - the live set varies by plan / scopes / release, so confirm it and the recomputed levels on the [Inspector](../mcp-server/inspector.md#tools):

??? note "Tools (10 of 24) - list_services · get_service · create_web_service · create_static_site · create_cron_job · update_environment_variables · list_deploys · list_logs · get_metrics · query_render_postgres"
    - `list_services`
    - `get_service`
    - `create_web_service`
    - `create_static_site`
    - `create_cron_job`
    - `update_environment_variables`
    - `list_deploys`
    - `list_logs`
    - `get_metrics`
    - `query_render_postgres`

**Description**

Manage Render web services, background workers, cron jobs, and deploys via OAuth.


**Docs** - [https://render.com/docs/mcp-server](https://render.com/docs/mcp-server)

</div>
</div>

<div class="tcg-card tcg-card--clickable t-google" id="Google-Cloud-Run" data-tool-id="Google-Cloud-Run" data-tool-title="Google Cloud Run" markdown>
<div class="tcg-name"><span class="tcg-name__text">Google Cloud Run</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Google Cloud Run](https://cdn.simpleicons.org/googlecloud){ width="40" .tcg-favicon }</div>
<div class="tcg-type">cloud · global <span class="risk risk-l0">ga</span> <span class="rl rl-l2">L2</span></div>
<div class="tcg-body" markdown>
Deploy and manage Cloud Run services, revisions, traffic splits, and jobs. Uses Google OAuth with the cloud-platform scope.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Google · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** - Google (vendor-official)

**Transport** - Streamable HTTP

**URL** - `https://run.googleapis.com/mcp`

**Auth** - OAuth 2.1

**OAuth 2.1** - runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.

**Stability** - GA · **Tier** - Tier 2

**Required env** - -

**Tags** - global

**Tools** - 5 tools published by the vendor (per its [MCP docs](https://docs.cloud.google.com/run/docs/reference/mcp)); no static per-tool levels - the live set varies by plan / scopes / release, so confirm it and the recomputed levels on the [Inspector](../mcp-server/inspector.md#tools):

??? note "Tools (5) - list_services · get_service · deploy_service_from_image · deploy_service_from_archive · deploy_service_from_file_contents"
    - `list_services`
    - `get_service`
    - `deploy_service_from_image`
    - `deploy_service_from_archive`
    - `deploy_service_from_file_contents`

**Description**

Deploy and manage Cloud Run services, revisions, traffic splits, and jobs. Uses Google OAuth with the cloud-platform scope.


**Docs** - [https://docs.cloud.google.com/mcp/supported-products](https://docs.cloud.google.com/mcp/supported-products)

</div>
</div>

<div class="tcg-card tcg-card--clickable t-google" id="Google-Cloud-Storage" data-tool-id="Google-Cloud-Storage" data-tool-title="Google Cloud Storage" markdown>
<div class="tcg-name"><span class="tcg-name__text">Google Cloud Storage</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Google Cloud Storage](https://cdn.simpleicons.org/googlecloud){ width="40" .tcg-favicon }</div>
<div class="tcg-type">storage · global <span class="risk risk-l0">ga</span> <span class="rl rl-l2">L2</span></div>
<div class="tcg-body" markdown>
Manage GCS buckets and objects - list, upload, download, IAM, lifecycle rules. Uses Google OAuth.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Google · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** - Google (vendor-official)

**Transport** - Streamable HTTP

**URL** - `https://storage.googleapis.com/storage/mcp`

**Auth** - OAuth 2.1

**OAuth 2.1** - runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.

**Stability** - GA · **Tier** - Tier 2

**Required env** - -

**Tags** - global

**Tools** - 7 tools published by the vendor (per its [MCP docs](https://docs.cloud.google.com/storage/docs/reference/mcp)); no static per-tool levels - the live set varies by plan / scopes / release, so confirm it and the recomputed levels on the [Inspector](../mcp-server/inspector.md#tools):

??? note "Tools (7) - create_bucket · list_buckets · list_objects · get_object_metadata · read_object · read_text · write_text"
    - `create_bucket`
    - `list_buckets`
    - `list_objects`
    - `get_object_metadata`
    - `read_object`
    - `read_text`
    - `write_text`

**Description**

Manage GCS buckets and objects - list, upload, download, IAM, lifecycle rules. Uses Google OAuth.


**Docs** - [https://docs.cloud.google.com/mcp/supported-products](https://docs.cloud.google.com/mcp/supported-products)

</div>
</div>

<div class="tcg-card tcg-card--clickable t-google" id="BigQuery" data-tool-id="BigQuery" data-tool-title="BigQuery" markdown>
<div class="tcg-name"><span class="tcg-name__text">BigQuery</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![BigQuery](https://cdn.simpleicons.org/googlebigquery){ width="40" .tcg-favicon }</div>
<div class="tcg-type">database · global · pipeline <span class="risk risk-l0">ga</span> <span class="rl rl-l2">L2</span></div>
<div class="tcg-body" markdown>
Query datasets, manage tables and views, and run jobs in Google BigQuery. Google OAuth with the bigquery scope.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Google · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** - Google (vendor-official)

**Transport** - Streamable HTTP

**URL** - `https://bigquery.googleapis.com/mcp`

**Auth** - OAuth 2.1

**OAuth 2.1** - runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.

**Stability** - GA · **Tier** - Tier 2

**Required env** - -

**Tags** - global · pipeline

**Tools** - 6 tools published by the vendor (per its [MCP docs](https://docs.cloud.google.com/bigquery/docs/use-bigquery-mcp)); no static per-tool levels - the live set varies by plan / scopes / release, so confirm it and the recomputed levels on the [Inspector](../mcp-server/inspector.md#tools):

??? note "Tools (6) - list_dataset_ids · list_table_ids · get_dataset_info · get_table_info · execute_sql · execute_sql_readonly"
    - `list_dataset_ids`
    - `list_table_ids`
    - `get_dataset_info`
    - `get_table_info`
    - `execute_sql`
    - `execute_sql_readonly`

**Description**

Query datasets, manage tables and views, and run jobs in Google BigQuery. Google OAuth with the bigquery scope.


**Docs** - [https://docs.cloud.google.com/mcp/supported-products](https://docs.cloud.google.com/mcp/supported-products)

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="Neon" data-tool-id="Neon" data-tool-title="Neon" markdown>
<div class="tcg-name"><span class="tcg-name__text">Neon</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Neon](https://cdn.simpleicons.org/neon){ width="40" .tcg-favicon }</div>
<div class="tcg-type">database · global <span class="risk risk-l0">ga</span> <span class="rl rl-l2">L2</span></div>
<div class="tcg-body" markdown>
Provision and query Neon serverless Postgres - branches, roles, schema migrations, query analysis. OAuth.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Neon · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** - Neon (vendor-official)

**Transport** - Streamable HTTP

**URL** - `https://mcp.neon.tech/mcp`

**Auth** - OAuth 2.1

**OAuth 2.1** - runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.

**Stability** - GA · **Tier** - Tier 2

**Required env** - -

**Tags** - global

**Tools** - 22 tools published by the vendor (per its [MCP docs](https://neon.com/docs/ai/neon-mcp-server)); no static per-tool levels - the live set varies by plan / scopes / release, so confirm it and the recomputed levels on the [Inspector](../mcp-server/inspector.md#tools):

??? note "Tools (10 of 22) - list_projects · create_project · create_branch · get_connection_string · run_sql · run_sql_transaction · get_database_tables · prepare_database_migration · complete_database_migration · list_slow_queries"
    - `list_projects`
    - `create_project`
    - `create_branch`
    - `get_connection_string`
    - `run_sql`
    - `run_sql_transaction`
    - `get_database_tables`
    - `prepare_database_migration`
    - `complete_database_migration`
    - `list_slow_queries`

**Description**

Provision and query Neon serverless Postgres - branches, roles, schema migrations, query analysis. OAuth.


**Docs** - [https://neon.tech/docs/ai/neon-mcp-server](https://neon.tech/docs/ai/neon-mcp-server)

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="Supabase" data-tool-id="Supabase" data-tool-title="Supabase" markdown>
<div class="tcg-name"><span class="tcg-name__text">Supabase</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Supabase](https://cdn.simpleicons.org/supabase){ width="40" .tcg-favicon }</div>
<div class="tcg-type">database · global <span class="risk risk-l0">ga</span> <span class="rl rl-l2">L2</span></div>
<div class="tcg-body" markdown>
Manage Supabase projects end-to-end - Postgres queries, Auth users, Storage buckets, Edge Functions. OAuth.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Supabase · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** - Supabase (vendor-official)

**Transport** - Streamable HTTP

**URL** - `https://mcp.supabase.com/mcp`

**Auth** - OAuth 2.1

**OAuth 2.1** - runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.

**Stability** - GA · **Tier** - Tier 2

**Required env** - -

**Tags** - global

**Tools** - 33 tools published by the vendor (per its [MCP docs](https://supabase.com/docs/guides/getting-started/mcp)); no static per-tool levels - the live set varies by plan / scopes / release, so confirm it and the recomputed levels on the [Inspector](../mcp-server/inspector.md#tools):

??? note "Tools (10 of 33) - list_projects · create_project · list_tables · apply_migration · execute_sql · get_logs · get_advisors · deploy_edge_function · create_branch · search_docs"
    - `list_projects`
    - `create_project`
    - `list_tables`
    - `apply_migration`
    - `execute_sql`
    - `get_logs`
    - `get_advisors`
    - `deploy_edge_function`
    - `create_branch`
    - `search_docs`

**Description**

Manage Supabase projects end-to-end - Postgres queries, Auth users, Storage buckets, Edge Functions. OAuth.


**Docs** - [https://supabase.com/docs/guides/getting-started/mcp](https://supabase.com/docs/guides/getting-started/mcp)

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="PlanetScale" data-tool-id="PlanetScale" data-tool-title="PlanetScale" markdown>
<div class="tcg-name"><span class="tcg-name__text">PlanetScale</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![PlanetScale](https://cdn.simpleicons.org/planetscale){ width="40" .tcg-favicon }</div>
<div class="tcg-type">database · global <span class="risk risk-l0">ga</span> <span class="rl rl-l2">L2</span></div>
<div class="tcg-body" markdown>
Query PlanetScale MySQL databases, manage branches, deploy schema changes via OAuth.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; PlanetScale · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** - PlanetScale (vendor-official)

**Transport** - Streamable HTTP

**URL** - `https://mcp.pscale.dev/mcp/planetscale`

**Auth** - OAuth 2.1

**OAuth 2.1** - runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.

**Stability** - GA · **Tier** - Tier 2

**Required env** - -

**Tags** - global

**Tools** - 16 tools published by the vendor (per its [MCP docs](https://planetscale.com/docs/connect/mcp)); no static per-tool levels - the live set varies by plan / scopes / release, so confirm it and the recomputed levels on the [Inspector](../mcp-server/inspector.md#tools):

??? note "Tools (8 of 16) - planetscale_list_databases · planetscale_get_database · planetscale_list_branches · planetscale_get_branch_schema · planetscale_execute_read_query · planetscale_execute_write_query · planetscale_get_insights · planetscale_search_documentation"
    - `planetscale_list_databases`
    - `planetscale_get_database`
    - `planetscale_list_branches`
    - `planetscale_get_branch_schema`
    - `planetscale_execute_read_query`
    - `planetscale_execute_write_query`
    - `planetscale_get_insights`
    - `planetscale_search_documentation`

**Description**

Query PlanetScale MySQL databases, manage branches, deploy schema changes via OAuth.


**Docs** - [https://planetscale.com/docs/concepts/planetscale-mcp](https://planetscale.com/docs/concepts/planetscale-mcp)

</div>
</div>

<div class="tcg-card tcg-card--clickable t-google" id="Cloud-SQL" data-tool-id="Cloud-SQL" data-tool-title="Google Cloud SQL" markdown>
<div class="tcg-name"><span class="tcg-name__text">Google Cloud SQL</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Google Cloud SQL](https://cdn.simpleicons.org/googlecloud){ width="40" .tcg-favicon }</div>
<div class="tcg-type">database · global <span class="risk risk-l0">ga</span> <span class="rl rl-l2">L2</span></div>
<div class="tcg-body" markdown>
Administer Google Cloud SQL instances (MySQL, Postgres, SQL Server) - databases, users, backups, and queries.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Google · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** - Google (vendor-official)

**Transport** - Streamable HTTP

**URL** - `https://sqladmin.googleapis.com/mcp`

**Auth** - OAuth 2.1

**OAuth 2.1** - runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.

**Stability** - GA · **Tier** - Tier 2

**Required env** - -

**Tags** - global

**Tools** - 19 tools published by the vendor (per its [MCP docs](https://googleapis.github.io/genai-toolbox/reference/prebuilt-tools/)); no static per-tool levels - the live set varies by plan / scopes / release, so confirm it and the recomputed levels on the [Inspector](../mcp-server/inspector.md#tools). MCP Toolbox for Databases (MySQL/Postgres/SQL Server); names below are the MySQL set.

??? note "Tools (10 of 19) - execute_sql · list_tables · list_databases · list_instances · create_instance · create_database · create_user · get_query_plan · get_system_metrics · create_backup"
    - `execute_sql`
    - `list_tables`
    - `list_databases`
    - `list_instances`
    - `create_instance`
    - `create_database`
    - `create_user`
    - `get_query_plan`
    - `get_system_metrics`
    - `create_backup`

**Description**

Administer Google Cloud SQL instances (MySQL, Postgres, SQL Server) - databases, users, backups, and queries.


**Docs** - [https://docs.cloud.google.com/mcp/supported-products](https://docs.cloud.google.com/mcp/supported-products)

</div>
</div>

<div class="tcg-card tcg-card--clickable t-google" id="Spanner" data-tool-id="Spanner" data-tool-title="Google Cloud Spanner" markdown>
<div class="tcg-name"><span class="tcg-name__text">Google Cloud Spanner</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Google Cloud Spanner](https://cdn.simpleicons.org/googlecloud){ width="40" .tcg-favicon }</div>
<div class="tcg-type">database · global <span class="risk risk-l0">ga</span> <span class="rl rl-l2">L2</span></div>
<div class="tcg-body" markdown>
Query Google Cloud Spanner instances and databases - globally distributed, strong consistency, SQL.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Google · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** - Google (vendor-official)

**Transport** - Streamable HTTP

**URL** - `https://spanner.googleapis.com/mcp`

**Auth** - OAuth 2.1

**OAuth 2.1** - runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.

**Stability** - GA · **Tier** - Tier 2

**Required env** - -

**Tags** - global

**Tools** - 11 tools published by the vendor (per its [MCP docs](https://docs.cloud.google.com/spanner/docs/reference/mcp)); no static per-tool levels - the live set varies by plan / scopes / release, so confirm it and the recomputed levels on the [Inspector](../mcp-server/inspector.md#tools):

??? note "Tools (9 of 11) - list_instances · create_instance · list_databases · create_database · get_database_ddl · execute_sql · execute_sql_readonly · commit · get_operation"
    - `list_instances`
    - `create_instance`
    - `list_databases`
    - `create_database`
    - `get_database_ddl`
    - `execute_sql`
    - `execute_sql_readonly`
    - `commit`
    - `get_operation`

**Description**

Query Google Cloud Spanner instances and databases - globally distributed, strong consistency, SQL.


**Docs** - [https://docs.cloud.google.com/mcp/supported-products](https://docs.cloud.google.com/mcp/supported-products)

</div>
</div>

<div class="tcg-card tcg-card--clickable t-google" id="Firestore" data-tool-id="Firestore" data-tool-title="Google Cloud Firestore" markdown>
<div class="tcg-name"><span class="tcg-name__text">Google Cloud Firestore</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Google Cloud Firestore](https://cdn.simpleicons.org/firebase){ width="40" .tcg-favicon }</div>
<div class="tcg-type">database · global <span class="risk risk-l0">ga</span> <span class="rl rl-l2">L2</span></div>
<div class="tcg-body" markdown>
Query and manage Firestore documents, collections, composite indexes, and security rules via Google OAuth.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Google · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** - Google (vendor-official)

**Transport** - Streamable HTTP

**URL** - `https://firestore.googleapis.com/mcp`

**Auth** - OAuth 2.1

**OAuth 2.1** - runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.

**Stability** - GA · **Tier** - Tier 2

**Required env** - -

**Tags** - global

**Tools** - 8 tools published by the vendor (per its [MCP docs](https://googleapis.github.io/genai-toolbox/reference/prebuilt-tools/)); no static per-tool levels - the live set varies by plan / scopes / release, so confirm it and the recomputed levels on the [Inspector](../mcp-server/inspector.md#tools). MCP Toolbox for Databases.

??? note "Tools (8) - get_documents · add_documents · update_document · delete_documents · list_collections · query_collection · get_rules · validate_rules"
    - `get_documents`
    - `add_documents`
    - `update_document`
    - `delete_documents`
    - `list_collections`
    - `query_collection`
    - `get_rules`
    - `validate_rules`

**Description**

Query and manage Firestore documents, collections, composite indexes, and security rules via Google OAuth.


**Docs** - [https://docs.cloud.google.com/mcp/supported-products](https://docs.cloud.google.com/mcp/supported-products)

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="SQLite" data-tool-id="SQLite" data-tool-title="SQLite" markdown>
<div class="tcg-name"><span class="tcg-name__text">SQLite</span> <span class="cost">🛠</span></div>
<div class="tcg-art" markdown>![SQLite](https://cdn.simpleicons.org/sqlite){ width="40" .tcg-favicon }</div>
<div class="tcg-type">database · global · community <span class="risk risk-l0">ga</span> <span class="rl rl-l3">L3</span></div>
<div class="tcg-body" markdown>
[macOS] Query a local SQLite database - SELECT, schema introspection, plus INSERT/UPDATE/DELETE when permitted. The activated form is pre-filled to run: uvx mcp-server-sqlite --db-path...
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; modelcontextprotocol/servers-archived · T2 community</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;STDIO</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** - modelcontextprotocol/servers-archived (community-maintained)

**Transport** - STDIO

**Command** - `uvx`

**Args** - `mcp-server-sqlite --db-path /absolute/path/to/your.db`

**OS variants** - mac · linux · win (catalog picks the entry matching the host OS automatically; macOS / Linux use `npx` or `uvx`; Windows uses `npx.cmd`).

**Auth** - STDIO

**Stability** - GA · **Tier** - Tier 2

**Required env** - -

**Tags** - global · community

**Tools** - discovered on connect; community-trust STDIO with no per-tool descriptors, so each tool composes to <span class="rl rl-l3">L3 - Moderate</span> on its own connection's [Inspector](../mcp-server/inspector.md#tools). `write_query` runs INSERT / UPDATE / DELETE, but the *tool name* carries no irreversible verb so no floor fires - the SQL it carries is what to review:

??? abstract "Tools (6) - read_query · write_query · create_table · list_tables · describe-table · append_insight"
    - **`read_query`** - run a SELECT and return rows. <span class="rl rl-l3">L3 - Moderate</span>
    - **`write_query`** - run an INSERT / UPDATE / DELETE statement. <span class="rl rl-l3">L3 - Moderate</span>
    - **`create_table`** - create a new table. <span class="rl rl-l3">L3 - Moderate</span>
    - **`list_tables`** - list all tables in the database. <span class="rl rl-l3">L3 - Moderate</span>
    - **`describe-table`** - show a table's schema (hyphen, not underscore - verbatim from the server). <span class="rl rl-l3">L3 - Moderate</span>
    - **`append_insight`** - append a business insight to the memo resource. <span class="rl rl-l3">L3 - Moderate</span>

**Description**

[macOS] Query a local SQLite database - SELECT, schema introspection, plus INSERT/UPDATE/DELETE when permitted.

Prereq: install uv on macOS.
  brew install uv

The activated form is pre-filled to run:
  uvx mcp-server-sqlite --db-path /absolute/path/to/your.db

Required arg:
  --db-path <path>                  # absolute path to the .db file

Security: writes are allowed by default - point at a copy of the DB or a read-only mount if you don't want the agent modifying production data. Reference server is archived; community forks active.


**Docs** - [https://github.com/modelcontextprotocol/servers-archived/tree/main/src/sqlite](https://github.com/modelcontextprotocol/servers-archived/tree/main/src/sqlite)

</div>
</div>

</div>

## Workflow combinations { #combinations }

The 17 entries on this page span object storage, managed databases, and hosting platforms. They compose naturally as **data-pipeline assemblies**:

- **Analytics report → dashboard upload** - `BigQuery` (run the query) + `Google Cloud Storage` (drop the CSV) + `Cloudflare` (purge the cache so the next dashboard hit is fresh).
- **Cross-cloud DB read** - `Supabase` (Postgres-flavoured) + `Neon` (serverless Postgres) + `PlanetScale` (Vitess-on-MySQL). Same SQL surface, three providers - useful for cost / latency comparisons.
- **Deploy + verify** - `Vercel` (deploy) + `Netlify` (alternative target for the same build) + `Render` (worker tier). Pick whichever matches the project, but keep all three active if you maintain mixed-host fleets.
- **Google-stack data flow** - `Google Cloud Run` (job) + `Google Cloud SQL` (transactional store) + `BigQuery` (warehouse) + `Google Cloud Storage` (artifacts). One OAuth issuer (`accounts.google.com`) covers all four.
- **OneDrive / SharePoint document ingest** - `OneDrive & SharePoint` + downstream `Notion` (Productivity page) for staging into a doc store.

## Auth & secrets { #auth-secrets }

Every remote entry uses OAuth 2.1. One stdio entry needs no env at all.

| Connection family | OAuth issuer | Extra env |
|---|---|---|
| Google Drive / Cloud Run / Cloud Storage / Cloud SQL / Cloud Spanner / Cloud Firestore / BigQuery | `https://accounts.google.com` (per-product scopes) | - |
| OneDrive & SharePoint | `https://login.microsoftonline.com/${MS_TENANT_ID}/v2.0` | `MS_TENANT_ID` |
| Cloudflare | Cloudflare OAuth | - |
| Vercel | Vercel OAuth | - |
| Netlify | Netlify OAuth | - |
| Heroku | Heroku OAuth | - |
| Render | Render OAuth | - |
| Neon | Neon OAuth | - |
| Supabase | Supabase OAuth | - |
| PlanetScale | PlanetScale OAuth | - |
| SQLite (stdio) | None | Node.js 18+; pass the database file path as the first arg |

Google Cloud entries each request a specific scope - `bigquery.read` for BigQuery, `cloud-platform` for Cloud Run, `cloud-spanner.data` for Spanner. Spring AI Playground stores the granted refresh token under `~/spring-ai-playground/mcp/oauth-tokens/` so you don't reauthorise on every restart.

## Picking guide { #picking-guide }

| If you need... | Reach for |
|---|---|
| Hot OLAP / dataset queries | `BigQuery` |
| Transactional Postgres, serverless | `Neon` · `Supabase` |
| Transactional MySQL, sharded | `PlanetScale` |
| Long-tail document DB | `Google Cloud Firestore` |
| Cheap blob store | `Google Cloud Storage` |
| Same store with versioning + ACLs | `OneDrive & SharePoint` (`MS_TENANT_ID` required) |
| Edge / CDN | `Cloudflare` |
| Static-first hosting | `Vercel` · `Netlify` |
| Container-on-managed-runtime | `Google Cloud Run` · `Render` · `Heroku` |

