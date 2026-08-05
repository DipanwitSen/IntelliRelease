# SAP Commerce Change Knowledge Base

**Purpose:** Deterministic change-impact intelligence for SAP Commerce Cloud (Composable Storefront / Spartacus) implementations.
**Consumer:** IntelliRelease SAP Commerce Context Engine (and any AI agent performing release impact analysis).
**Contract:** This document describes *architecture, responsibility, and relationships only*. It contains no source code, no business logic, no proprietary algorithms, and no credentials.

**Companion file:** `sap_context.json` — the machine-readable form of everything below, indexed for lookup by changed file path.

---

## 0. How to Use This Knowledge Base

Given a set of changed files in a pull request, an AI should:

1. **Classify** each changed file into an *artifact type* (Section 4) using the path/name heuristics in Section 3.4.
2. **Resolve** the artifact's position in the layer model (Section 2) and its declared dependencies (Section 5).
3. **Propagate** impact downstream using the Change Impact Matrix (Section 6) — every artifact type declares what breaks when it changes.
4. **Score** deployment risk using the risk model (Section 7).
5. **Recommend** regression areas from the regression map (Section 8).
6. **Explain** the change in business terms using the *business capability* attached to each artifact.

The rules are intentionally **generalized**. Client-specific class names have been normalized (e.g. `<Client>CheckoutFacade` → *Checkout Facade — client-specific implementation detected*) so the same engine works for any SAP Commerce customer.

---

## 1. Platform Baseline

| Dimension | Value | Change significance |
|---|---|---|
| SAP Commerce Suite | 2211 (JDK 21 line) | Platform upgrade = full regression |
| Deployment model | SAP Commerce Cloud (CCv2), `manifest.json` driven | Manifest change = infrastructure-level risk |
| Storefront model | Headless — Composable Storefront (Spartacus), Angular | Backend and frontend deploy independently |
| Angular / Spartacus | Angular 21.x line / Spartacus 2211.x line | Framework bump = frontend-wide regression |
| Search | Solr Cloud (managed by CCv2) | Index definition change = reindex required |
| Integration backbone | SAP Cloud Platform Integration (SCPI) + S/4HANA Order Management + Integration API (inbound/outbound services) | Contract change = cross-system regression |
| Identity | External OIDC/IdP provider + SAML SSO for Backoffice | Auth change = access-outage risk |
| Commerce model | B2B (B2B Units, org hierarchy, account-based ordering) | B2B org model touches pricing, assortment, and checkout simultaneously |
| Aspects deployed | `backoffice`, `accstorefront`, `api`, `backgroundProcessing` | Aspect assignment determines *which nodes restart* |

### 1.1 Runtime Aspects and What They Serve

| Aspect | Serves | Restart impact |
|---|---|---|
| `api` | OCC v2 REST API, authorization server, SSO webapp, personalization/permission web services | Storefront outage — highest blast radius |
| `backoffice` | Backoffice, HAC, SmartEdit + CMS web services, media | Content authoring and admin outage |
| `accstorefront` | Media delivery for storefront | Media/asset outage |
| `backgroundProcessing` | CronJobs, integration node group, HAC | Batch/integration outage; no user-facing impact until data staleness |

**Rule:** an artifact's *deployment risk* is partly a function of which aspects load it. Anything in the core/facade layer loads in **all** aspects.

---

## 2. Layer Model

SAP Commerce is a strictly layered platform. Impact almost always propagates **upward** (toward the client) and **outward** (toward integrations). This is the canonical propagation order used by the impact engine:

```
                       ┌──────────────────────────────────────┐
   FRONTEND            │  Angular / Spartacus Storefront      │
                       │  components · modules · adapters ·   │
                       │  connectors · routes · i18n · styles │
                       └───────────────▲──────────────────────┘
                                       │ HTTP (OCC v2 REST)
                       ┌───────────────┴──────────────────────┐
   API / WEB           │  OCC Controllers · WsDTOs ·          │
                       │  field-set mappings · filters ·      │
                       │  validators · OCC web-spring         │
                       └───────────────▲──────────────────────┘
                                       │ Data objects (DTO)
                       ┌───────────────┴──────────────────────┐
   FACADE              │  Facades · Converters · Populators · │
                       │  Data beans · email contexts         │
                       └───────────────▲──────────────────────┘
                                       │ Models
                       ┌───────────────┴──────────────────────┐
   SERVICE             │  Services · Strategies · Hooks ·     │
                       │  Business processes · Actions ·      │
                       │  Events · Listeners · Jobs           │
                       └───────────────▲──────────────────────┘
                                       │ FlexibleSearch / ModelService
                       ┌───────────────┴──────────────────────┐
   PERSISTENCE         │  DAOs · Interceptors · items.xml     │
                       │  type system · indexes · relations   │
                       └───────────────▲──────────────────────┘
                                       │
                       ┌───────────────┴──────────────────────┐
   PLATFORM / DATA     │  ImpEx · Solr config · CMS content ·  │
                       │  Patches · properties · CronJob defs │
                       └──────────────────────────────────────┘

   CROSS-CUTTING:  Integration Objects (inbound/outbound) · SCPI/ERP destinations ·
                   Backoffice config · SmartEdit · Security & permissions
```

### 2.1 Propagation Rules

| Rule | Statement |
|---|---|
| **R1 — Upward propagation** | A change at layer *N* can affect every layer above *N*. It rarely affects layers below. |
| **R2 — Type system is the floor** | `items.xml` changes propagate to *every* layer, plus the database, plus Solr, plus integration payloads. Highest fan-out of any artifact. |
| **R3 — Spring is the wiring** | A Spring XML change can silently redirect an entire behaviour chain without any Java file changing. Always treat Spring XML as a first-class change. |
| **R4 — Alias override is invisible substitution** | An `<alias>` that repoints a platform bean name to a custom class changes behaviour for every caller of the platform name, including out-of-the-box extensions the team never touched. |
| **R5 — Populator lists are shared** | Populators added to a converter's list affect *every* consumer of that converter — OCC, emails, exports, Backoffice. |
| **R6 — Contract symmetry** | OCC field-set mappings and Angular OCC endpoint field lists must move together. Changing one without the other produces silent data loss in the UI. |
| **R7 — Solr needs a rebuild** | Any change to indexed properties, value providers, or indexer queries requires a full reindex before it is observable. |
| **R8 — ImpEx is environment state** | ImpEx does not "deploy" — it *executes*. Re-running it mutates live data. Idempotency is the property that determines risk. |

---

## 3. Extension Inventory and Dependency Map

### 3.1 Custom Extension Roles (Generalized)

| Extension (role) | Client name pattern | Layer | Business capability |
|---|---|---|---|
| **Core extension** | `<client>core` | Service + Persistence | Type system, services, DAOs, strategies, business processes, events, jobs, interceptors, ERP outbound helpers, email service, system setup |
| **Facades extension** | `<client>facades` | Facade | Facades, converters, populators, data beans, email contexts, Solr field value providers |
| **OCC/web extension** | `<client>commerceocc` | API/Web | REST controllers, WsDTOs, field-set mappings, request filters, OCC validators, inbound persistence hooks, ERP payload/response services |
| **Identity extension** | `<client>auth0` (IdP-specific) | Service (cross-cutting) | External identity provider integration — token verification, user provisioning, invitation and deactivation payloads |
| **Initial/base data extension** | `<client>initialdata` | Data | System setup entrypoint, core/sample data import services, catalog sync job service, first-wave store data |
| **Rollout data extensions** | `<client>phase<N>data` | Data | Per-market/per-wave store, site, catalog, CMS and Solr data. One extension per rollout phase. |
| **Backoffice extension** | `<client>backoffice` | Admin | Custom Backoffice widgets, editors, renderers, role/site-scoped security, authentication provider |
| **Patch extension** | `<client>patches` | Data (versioned) | Versioned, release-scoped, idempotent data migrations — ImpEx import, Groovy execution, catalog sync, Solr indexing, Excel import |
| **Test extension** | `<client>test` | Test | Integration test harness |

> **Generalization note:** classes named `<Client>XxxFacade`, `<Client>XxxService`, `Default<Client>XxxDao` etc. are **client-specific implementations of standard SAP Commerce roles**. The engine should strip the client prefix and reason about the *role*, not the name.

### 3.2 Custom Extension Dependency Graph

```
                          ┌─────────────────────┐
                          │  <client>commerceocc│  (API layer)
                          └──────────┬──────────┘
                                     │ requires
                    ┌────────────────┴────────────────┐
                    │                                 │
          ┌─────────▼─────────┐            ┌──────────▼──────────┐
          │  <client>facades  │            │  OCC platform ext.  │
          └─────────┬─────────┘            │  b2bocc, cmsocc,    │
                    │ requires             │  swaggerintegration │
          ┌─────────▼─────────┐            └─────────────────────┘
          │   <client>core    │◄──────────── <client>initialdata
          └─────────┬─────────┘◄──────────── <client>phase1data
                    │ requires               <client>phase2data
                    │                        <client>phase4data
     ┌──────────────┼──────────────┬──────────────┐
     │              │              │              │
┌────▼────┐  ┌──────▼──────┐ ┌─────▼─────┐ ┌──────▼───────┐
│ B2B     │  │ SAP CPI /   │ │ Identity  │ │ Accelerator  │
│ commerce│  │ S4 OM       │ │ extension │ │ CMS / facades│
│ stack   │  │ integration │ │           │ │              │
└─────────┘  └─────────────┘ └───────────┘ └──────────────┘
```

**Reverse-dependency rule (critical for impact analysis):**

| If this changes | These must be re-validated |
|---|---|
| Core extension | Facades, OCC, all data extensions, Backoffice, Patches, everything |
| Facades extension | OCC, email rendering, Solr indexing (value providers live here), exports |
| OCC extension | Angular storefront, any external API consumer, Swagger contract |
| Identity extension | Core (user provisioning), OCC (login/token endpoints), storefront auth flow, invitation/deactivation jobs |
| Data extensions | Only the markets/stores they own — *unless* they touch shared `common/` data |
| Patch extension | The specific environments where the patch has not yet run |

### 3.3 Platform Extension Groups in Scope

Changes to `localextensions.xml` implicitly change the behaviour of every group below.

| Group | Purpose | Impact when the group is added/removed |
|---|---|---|
| Core commerce | Platform, base commerce services | Total |
| Backoffice + platformbackoffice + solrsearch | Admin UI | Admin outage |
| SmartEdit stack (`smartedit`, `cmssmartedit`, `cmssmarteditwebservices`, `cmswebservices`, `previewwebservices`, `permissionswebservices`, `personalization*`) | In-context content authoring | Content authoring outage; storefront unaffected |
| OCC stack (`commercewebservices`, `cmsocc`, `acceleratorocc`, `b2bocc`, `swaggerintegration`) | Headless REST API | Storefront total outage |
| Solr stack (`solrserver`, `solrfacetsearch`, `adaptivesearch*`) | Search & merchandising | Search/PLP outage |
| B2B stack (`b2bcommerce`, `b2bapprovalprocess`, `b2bacceleratorservices/facades`, `b2bcommercefacades`) | Org hierarchy, approvals, account ordering | Checkout + org admin outage |
| Order Management (`warehousing*`, `ordermanagementfacades`, `ysap*ordermanagement`, `ysaporderfulfillment`) | Fulfilment, consignments | Post-order processing |
| SCPI / integration (`integrationservices`, `inboundservices`, `outboundservices`, `odata2*`, `outboundsync`, `webhookservices`, `sapcpi*`, `saps4om*`) | ERP data exchange | Master data + order flow to ERP |
| Customer service (`ticketsystem`, `customerticketingocc`, `assistedservice*`, `customersupportbackoffice`) | Support tickets, ASM | Support flows |
| Security (`samlsinglesignon`, `authorizationserver`, `oauth2commons`, `resourceserver`) | Auth | Login outage across all channels |
| Patches (`patchesbackoffice`, `<client>patches`) | Versioned data migration | Release data state |

### 3.4 File Classification Heuristics

The engine classifies a changed file by matching, in order:

| Pattern | Artifact type |
|---|---|
| `**/resources/*-items.xml` | Type system definition |
| `**/resources/*-beans.xml` | DTO/Data bean definition |
| `**/resources/*-spring.xml` | Spring service wiring |
| `**/resources/**/web/spring/*-web-spring.xml` | OCC web wiring / field-set mapping |
| `**/resources/*-backoffice-config.xml` | Backoffice UI configuration |
| `**/resources/*-backoffice-spring.xml` / `-widgets.xml` | Backoffice wiring / widgets |
| `**/extensioninfo.xml` | Extension dependency declaration |
| `**/config/localextensions.xml` | Extension activation set |
| `manifest.json` / `config.json` | Cloud deployment descriptor |
| `**/src/**/controllers/*.java` | OCC REST controller |
| `**/src/**/facades/**/*Facade*.java` | Facade |
| `**/src/**/*Service*.java` (service pkg) | Service |
| `**/src/**/dao/**/*Dao*.java` | DAO |
| `**/src/**/populator/**` or `*Populator.java` | Populator |
| `**/src/**/*Converter*.java` | Converter |
| `**/src/**/interceptors/*.java` | Persistence interceptor |
| `**/src/**/validators/*.java` | Validator |
| `**/src/**/strategies/**` | Strategy |
| `**/src/**/hook/**` or `*Hook.java` | Method/persistence hook |
| `**/src/**/event/**` | Event or event listener |
| `**/src/**/actions/*.java` | Business-process action |
| `**/src/**/job/**` or `*Job.java` | CronJob performable |
| `**/src/**/filter/*.java` | Servlet/OCC filter |
| `**/src/**/**ValueProvider.java` | Solr field value provider |
| `**/src/**/setup/*SystemSetup.java` | System setup / data bootstrap |
| `**/resources/**/processes/*.xml` | Business process definition |
| `**/resources/**/import/**/*.impex` | ImpEx data |
| `**/resources/**/stores/*/solr*.impex` | Solr index configuration |
| `**/resources/**/*ContentCatalog/*.impex` | CMS content |
| `**/resources/**/emails/*.vm` | Email template |
| `**/resources/**/releases/**/*` | Patch data |
| `**/project.properties`, `**/local.properties`, `config/ccv2/**` | Configuration |
| `**/*.module.ts` | Angular module / CMS mapping |
| `**/*.component.ts|html|scss` | Angular component |
| `**/*.adapter.ts` / `*.connector.ts` | Frontend OCC binding |
| `**/*.service.ts` | Frontend service |
| `**/*.guard.ts` / `*.interceptor.ts` | Frontend routing/HTTP cross-cutting |
| `**/configs/*.ts` | Frontend OCC endpoint & site context config |
| `public/translations/**` | Frontend i18n |

---

## 4. Artifact Catalog

Each artifact below is documented with the ten required dimensions. **General role** describes SAP Commerce in the abstract; **client implementation** describes what was detected in this codebase, generalized; **generalized meaning** is the reusable rule.

---

### 4.1 `items.xml` — Type System Definition

| Dimension | Detail |
|---|---|
| **General SAP Commerce role** | Declares item types, attributes, enums, collections, relations, deployment tables/typecodes and indexes. Drives code generation (`gensrc` models), DDL, and the persistence schema. |
| **Client implementation** | *Client-specific implementation detected.* Extends platform types (Order/OrderEntry, Address, B2B Customer, B2B Unit, Product, Base Store, Price Row, Discount Row, CS Ticket, SAP Configuration, CMS component types) with commerce-specific and ERP-alignment attributes; introduces custom types for assortment control, profit centre, payment terms, shipping configuration, document-type configuration, stock notifications, temporary customers, FAQ/forms CMS components, customer/price grouping user groups, invitation & deactivation cron job types, and cron job run results. |
| **Generalized meaning** | *Type system change.* The single highest-fan-out artifact in SAP Commerce. |
| **Business capability** | Product & pricing model, customer/organisation hierarchy, order structure, assortment entitlement, service ticketing, content model. |
| **Dependencies (upstream)** | Platform type system; types declared in required extensions. |
| **Dependencies (downstream)** | Generated models → DAOs → services → facades → populators → WsDTOs → OCC responses → Angular models. Also: Solr indexed properties, integration object attributes, ImpEx headers, Backoffice editors, SmartEdit component definitions. |
| **Potential impact** | Database DDL change; model regeneration; every consumer of the changed attribute; Solr schema drift; integration payload contract; ImpEx scripts referencing the attribute; Backoffice list/editor configuration. |
| **Regression areas** | Full smoke: PDP, PLP, cart, checkout, order history, account, admin. Plus targeted regression on the specific domain touched. |
| **Deployment risk** | **CRITICAL.** Requires system update. Removing/renaming attributes or changing `persistence`/`modifiers`/type is potentially destructive. New typecodes and deployment tables are irreversible in practice. |
| **Related configuration** | `localextensions.xml` load order; `extensioninfo.xml` dependencies; system-update settings. |
| **Related integrations** | Integration Object item attributes for inbound/outbound; ERP field mapping; Solr indexed property definitions. |

**Change sub-classification** (the engine should distinguish these — risk differs by an order of magnitude):

| Sub-change | Risk | Notes |
|---|---|---|
| Add optional attribute to existing type | Medium | Additive; still needs system update + model regen |
| Add new item type with new typecode/table | High | New table; typecode is permanent |
| Change attribute type or persistence | Critical | Potential data loss |
| Remove attribute / type | Critical | Destructive; breaks all consumers |
| Add/change relation | High | New relation table; cardinality affects queries |
| Add/change index | Medium–High | Rebuild cost on large tables; can be a performance *fix* or a lock risk |
| Extend enum with new values | Medium | Dynamic enums are data-driven; consumers may not handle new values |

---

### 4.2 Spring XML (`*-spring.xml`) — Service Wiring

| Dimension | Detail |
|---|---|
| **General SAP Commerce role** | Declares and wires all beans; overrides platform behaviour via `<alias>`, `parent=`, and list-merge directives; registers interceptor mappings, process definition resources, and value providers. |
| **Client implementation** | *Client-specific implementation detected.* The core Spring context overrides a large set of platform bean aliases — order service, cart service and cart factory, add-to-cart and update-cart-entry strategies, calculation service, customer account service, user service, delivery service, catalog version service, email service, ticket service/DAO, password policy service, key generators for order/cart codes, price criteria/query provider/post-matcher, delivery-address lookup strategies, cart validation strategy, and the search Solr query populator. It also registers persistence interceptor mappings, business process definition resources, email generation actions, and ERP outbound/simulation helper beans. The facade context registers converters, populator list mutations, email contexts, Solr field value providers, image-format mappings, and address-format mappings. |
| **Generalized meaning** | *Behavioural wiring.* Changing Spring XML can change system behaviour with zero Java diff. |
| **Business capability** | Everything — Spring is the substitution mechanism for all customisation. |
| **Dependencies (upstream)** | Bean definitions in required extensions; property placeholders. |
| **Dependencies (downstream)** | Every caller of the aliased bean name, including untouched out-of-the-box extensions. |
| **Potential impact** | Silent behaviour substitution; startup failure on unresolvable references; duplicate/ambiguous bean ids; changed population order altering output. |
| **Regression areas** | Determined by the bean role. An `orderService`/`cartService`/`calculationService` alias change ⇒ full order-to-cash regression. A populator list change ⇒ every API response using that converter. |
| **Deployment risk** | **HIGH.** Context load failure takes down all nodes in every aspect. No partial rollout. |
| **Related configuration** | `project.properties` (placeholders), `local.properties`, `*.application-context` property. |
| **Related integrations** | Outbound REST template strategy lists, destination services, payload creators/processors, order conversion & mapper services. |

**Highest-risk Spring patterns to flag:**

| Pattern | Why it is dangerous |
|---|---|
| `<alias name="customBean" alias="platformBeanName"/>` | Global behavioural substitution — affects all consumers |
| `parent="listMergeDirective"` adding to a filter chain or hook list | Changes request pipeline / order lifecycle for all traffic |
| `parent="modifyPopulatorList"` | Alters every DTO built through that converter |
| `InterceptorMapping` registration | Fires on every save of that type, including ImpEx and integration writes |
| `ProcessDefinitionResource` | Alters the order/email business process graph |
| Key generator redefinition | Changes order/cart code format — affects ERP correlation and support tooling |

---

### 4.3 `*-beans.xml` — Data/DTO Bean Definitions

| Dimension | Detail |
|---|---|
| **General SAP Commerce role** | Generates Data (facade DTO) and WsDTO (API DTO) classes; extends platform DTOs with additional fields. |
| **Client implementation** | *Client-specific implementation detected.* Facade beans extend order, order-entry, cart, order-history, product, variant-option, address, customer and search DTOs, and define account-summary, shipping-configuration, temporary-customer, FAQ and sales-document-type data objects. Web-service beans extend the equivalent WsDTOs and define ERP account/order/invoice result DTOs. |
| **Generalized meaning** | *API/data contract definition.* |
| **Business capability** | The shape of every payload the storefront and external consumers receive. |
| **Dependencies (upstream)** | `items.xml` attributes being surfaced; populators that fill the fields. |
| **Dependencies (downstream)** | Populators, converters, field-set mappings, Angular models, Swagger contract. |
| **Potential impact** | Field added but never populated (null in API); field removed breaks the frontend; renaming breaks all clients. |
| **Regression areas** | Every API endpoint returning the DTO; corresponding Angular model and component bindings. |
| **Deployment risk** | **MEDIUM–HIGH.** Additive = safe. Rename/remove = breaking API change requiring coordinated frontend release. |
| **Related configuration** | OCC field-set level mappings (`BASIC`/`DEFAULT`/`FULL`); Angular OCC endpoint `fields=` parameters. |
| **Related integrations** | ERP response DTOs mirror the external contract — a supplier-side change lands here. |

---

### 4.4 Facade

| Dimension | Detail |
|---|---|
| **General SAP Commerce role** | Orchestration boundary between the web/API layer and the service layer. Converts models to data objects, coordinates multiple services, enforces session/user context. Contains no persistence and (properly) no business rules. |
| **Client implementation** | *Client-specific implementations detected.* Cart facade, order facade, B2B checkout facade, saved-cart facade, product facade, customer facade, B2B unit facade, account-summary facade, assortment facade, configuration facade, notification ("notify me") facade, customer-ticketing facade, FAQ search facade, suggestion facade. Several are aliased over platform facade bean names. |
| **Generalized meaning** | *Orchestration layer.* |
| **Business capability** | Cart & checkout, order history & details, product browsing, account & organisation management, financial account summary, entitlement/assortment, support ticketing, content search. |
| **Dependencies (upstream)** | Services, strategies, converters, session/user service, base-store & base-site services. |
| **Dependencies (downstream)** | OCC controllers; email contexts; any Backoffice/ASM flow using the same facade. |
| **Potential impact** | Changed response shape or population set; changed error behaviour; changed pagination/sorting; session-context leaks between users. |
| **Regression areas** | The capability's end-to-end journey plus adjacent journeys sharing the facade (e.g. cart facade ⇒ mini-cart, cart page, checkout, saved carts, reorder). |
| **Deployment risk** | **MEDIUM–HIGH.** Facades are aliased over platform names — impact reaches untouched code paths. |
| **Related configuration** | Spring alias in the facades context; converter wiring; field-set mappings that expose the result. |
| **Related integrations** | Order/account facades call ERP-backed services synchronously — latency and failure modes propagate to the UI. |

---

### 4.5 Service

| Dimension | Detail |
|---|---|
| **General SAP Commerce role** | Business capability implementation over the persistence layer. Transaction boundary. Called by facades, jobs, processes, interceptors, and other services. |
| **Client implementation** | *Client-specific implementations detected.* Cart service (platform + commerce variants), place-order cart service, reorder validation service, calculation service, customer service & customer account service, user service, B2B unit service, assortment service, account-summary service, notification service, ticket service & ticket attachment service, delivery service, email service, catalog version service, password policy service, suggestion service, identity-provider customer service, ERP order service and ERP outbound service, ERP order conversion/mapper/payload/response services. |
| **Generalized meaning** | *Business capability implementation.* |
| **Business capability** | Ordering, pricing & calculation, cart lifecycle, entitlement, customer & organisation lifecycle, notifications, ticketing, ERP exchange. |
| **Dependencies (upstream)** | DAOs, model service, FlexibleSearch, session/user service, configuration service, integration/REST clients. |
| **Dependencies (downstream)** | Facades, jobs, business-process actions, event listeners, interceptors, other services. |
| **Potential impact** | Data correctness; totals/pricing; transactional integrity; performance (services aliased over platform names run on every request); ERP payload correctness. |
| **Regression areas** | The capability plus any batch job or process invoking the same service. Calculation/cart/order services ⇒ full order-to-cash. |
| **Deployment risk** | **HIGH** when the service is aliased over a platform bean (`cartService`, `calculationService`, `orderService`, `userService`, `emailService`, `catalogVersionService`, `customerAccountService`) — these run on essentially every request in every aspect. |
| **Related configuration** | Spring wiring & aliases; feature-flag and threshold properties (e.g. quantity limits, page sizes, retry counts). |
| **Related integrations** | ERP outbound/inbound services, identity provider APIs, destinations and credentials. |

---

### 4.6 DAO

| Dimension | Detail |
|---|---|
| **General SAP Commerce role** | Encapsulates FlexibleSearch queries and paged search. The only layer that should contain query text. |
| **Client implementation** | *Client-specific implementations detected.* Customer DAO, customer-invitation DAO, ticket DAO (paged), account-summary DAO, assortment DAO, notification DAO, suggestion DAO, plus price/PDT criteria and query-provider components that build price-lookup queries dynamically. |
| **Generalized meaning** | *Query layer.* |
| **Business capability** | Retrieval for every capability above. |
| **Dependencies (upstream)** | Type system (`items.xml`) — query text references types/attributes by name; search restrictions; catalog version context. |
| **Dependencies (downstream)** | Services → facades → API → UI. Also Solr indexer queries when the DAO backs an index. |
| **Potential impact** | Wrong/missing results; **performance regressions** (missing index, cartesian relation joins); search-restriction bypass causing data leakage across organisations. |
| **Regression areas** | Listing/search/detail flows for the entity; pagination and sorting; multi-org data isolation. |
| **Deployment risk** | **MEDIUM**, escalating to **HIGH** where the query runs per-request or over large tables. Query changes are a leading cause of production latency incidents. |
| **Related configuration** | Index definitions in `items.xml`; search restrictions; page-size properties. |
| **Related integrations** | Indirect — DAOs feed the payloads sent outbound. |

> **Security rule:** any DAO change that alters or disables search restrictions must be flagged as a **data-isolation risk** in a B2B multi-organisation model.

---

### 4.7 Populator

| Dimension | Detail |
|---|---|
| **General SAP Commerce role** | Copies/derives fields from a source (model) to a target (data object). Registered in ordered lists on converters. |
| **Client implementation** | *Client-specific implementations detected.* Populators for cart, cart entry, cart payment address, order, order entry, order history, grouped order entries, ERP order and ERP order entry, ERP order result, product, product carousel, variant options, variant full, search results (product and price), address, customer, B2B customer, B2B unit, temporary customer, shipping configuration, FAQ & FAQ search page, ticket details/events/parameters, account summary info, saved-cart parameters, facet-search query grouping and exclusion filters, and Solr query decoding. |
| **Generalized meaning** | *Field mapping / projection.* |
| **Business capability** | Determines what the storefront can actually display for every domain object. |
| **Dependencies (upstream)** | Model attributes (`items.xml`), price/stock/media services, i18n & currency services. |
| **Dependencies (downstream)** | Every converter list the populator is registered in, and therefore every API response, email, and export built from that converter. |
| **Potential impact** | Missing or stale fields in the UI; **N+1 query and latency regressions** (populators run per entity in a collection); ordering conflicts when two populators write the same field. |
| **Regression areas** | Every surface using the converter — often much wider than the developer intended. Cart/order entry populators appear in cart, checkout, order history, order detail, confirmation emails and CSV exports simultaneously. |
| **Deployment risk** | **MEDIUM.** Low deploy risk, high *silent* functional risk. |
| **Related configuration** | Converter definitions and populator-list merge directives; field-set mappings that decide whether the populated field is ever serialized. |
| **Related integrations** | ERP-result populators translate the external contract into internal models — supplier contract changes surface here. |

---

### 4.8 Converter

| Dimension | Detail |
|---|---|
| **General SAP Commerce role** | Owns a target class and an ordered populator list; produces data objects. |
| **Client implementation** | *Client-specific implementations detected.* Converters for shipping configuration, ERP order and ERP order details, FAQ search response and FAQ data, Solr search query decoding, search-query pageable, temporary customer, saved-cart parameters, and carousel products. Several platform converters have populator lists mutated rather than being replaced. |
| **Generalized meaning** | *DTO assembly definition.* |
| **Business capability** | Same as populators, at aggregate level. |
| **Dependencies (upstream)** | Target data bean (`beans.xml`); the populators in its list. |
| **Dependencies (downstream)** | Facades, controllers, email contexts. |
| **Potential impact** | Population order changes; target class change breaks all consumers; removing a populator silently blanks fields. |
| **Regression areas** | All consumers of the converter. |
| **Deployment risk** | **MEDIUM.** |
| **Related configuration** | Spring populator lists; `beans.xml` target class. |
| **Related integrations** | Same as populators. |

---

### 4.9 Interceptor

| Dimension | Detail |
|---|---|
| **General SAP Commerce role** | Hooks into the persistence lifecycle (`prepare`, `validate`, `load`, `remove`, `init`) for a registered type. Runs on **every** save of that type — from the UI, ImpEx, integration inbound, jobs, and Backoffice. |
| **Client implementation** | *Client-specific implementations detected.* A prepare interceptor and a validate interceptor on the B2B customer type, a validate interceptor on the B2B unit type, and a change interceptor on the stock-level type that participates in back-in-stock notification. |
| **Generalized meaning** | *Persistence lifecycle guard / derivation.* |
| **Business capability** | Data quality enforcement, derived-field maintenance, event triggering on data change. |
| **Dependencies (upstream)** | The intercepted type; services the interceptor calls. |
| **Dependencies (downstream)** | Every write path for that type: storefront, OCC, Backoffice, ImpEx import, integration inbound, cron jobs, patches. |
| **Potential impact** | **Import failures at scale** (a validate interceptor rejecting rows fails a whole ImpEx/integration batch); performance degradation on bulk writes; infinite loops when an interceptor triggers a save on the same type. |
| **Regression areas** | Data import (ImpEx, hot folders, integration inbound), Backoffice editing, storefront write flows, patch execution. |
| **Deployment risk** | **HIGH.** Interceptors are the most common cause of "the deploy succeeded but data loads now fail". |
| **Related configuration** | `InterceptorMapping` Spring bean; type code. |
| **Related integrations** | Directly gates inbound integration writes — an ERP feed can be silently rejected. |

---

### 4.10 Validator

| Dimension | Detail |
|---|---|
| **General SAP Commerce role** | Request/payload validation at the API boundary; rejects invalid input before it reaches services. |
| **Client implementation** | *Client-specific implementation detected.* A B2B place-order cart validator extending the platform validator, wired over the platform validator bean name. |
| **Generalized meaning** | *API input contract enforcement.* |
| **Business capability** | Checkout integrity and error messaging. |
| **Dependencies (upstream)** | Cart/order model state; base-store configuration. |
| **Dependencies (downstream)** | Controller responses; frontend error handling and messaging. |
| **Potential impact** | Loosened validation ⇒ invalid orders reach ERP. Tightened validation ⇒ legitimate orders blocked; frontend may not have a message for the new error code. |
| **Regression areas** | Checkout happy path **and** every negative path; frontend error message coverage; i18n keys for new error codes. |
| **Deployment risk** | **HIGH** for checkout validators — directly gates revenue. |
| **Related configuration** | Spring alias; localized message bundles for error codes. |
| **Related integrations** | Prevents malformed orders reaching the ERP order interface. |

---

### 4.11 CronJob / Job Performable

| Dimension | Detail |
|---|---|
| **General SAP Commerce role** | Scheduled or triggered background execution. Defined as a job type in `items.xml`, a Spring `JobPerformable` bean, a `ServicelayerJob` + `CronJob` + `Trigger` in ImpEx. Runs on the background-processing node group. |
| **Client implementation** | *Client-specific implementations detected.* An identity-provider **customer invitation** job and a **customer deactivation** job (both with dedicated cron job types carrying connection, tenancy, audience, TTL and rate-limit parameters, and a run-result item type for audit); platform-derived **ticket retention** and **ticket stagnation** cleanup jobs; **Solr indexing** triggers per store; catalog **synchronisation** jobs. |
| **Generalized meaning** | *Scheduled background capability.* |
| **Business capability** | User lifecycle automation, data retention/compliance, search freshness, catalog publication. |
| **Dependencies (upstream)** | Services, DAOs, external APIs, cron job configuration data. |
| **Dependencies (downstream)** | Data state consumed by the storefront (index freshness, user access, catalog online version). |
| **Potential impact** | Mass user access change (invitation/deactivation at scale); mass data deletion (retention rules); index staleness or index corruption; node-group saturation. |
| **Regression areas** | Job execution in a non-production environment with production-like volume; rate-limit and error-path behaviour; audit/result recording; idempotency on re-run. |
| **Deployment risk** | **HIGH.** Jobs act on the whole dataset with no user in the loop. A retention rule change can be irreversibly destructive. Trigger activation state and cron expression changes are as risky as code changes. |
| **Related configuration** | ImpEx-defined trigger schedule and `active` flag; rate limits; cluster node group assignment (`backgroundProcessing`); external endpoint & credential properties. |
| **Related integrations** | Identity provider management API (rate-limited); Solr; ERP. |

> **Rule:** if the diff touches a `Trigger` row, a retention rule query, or a job's batch size, classify as **HIGH** even if no Java changed.

---

### 4.12 Business Process Definition and Action

| Dimension | Detail |
|---|---|
| **General SAP Commerce role** | XML-defined state machine (`<action>`/`<transition>`/`<wait>`/`<end>`) executed by the process engine; actions are Spring beans. Used for order lifecycle and transactional email. |
| **Client implementation** | *Client-specific implementations detected.* An **ERP order process** (order check → payment authorisation check → send-to-ERP → send-status → confirmation-status, with failure branches and a notification action) and a family of **email processes**: customer registration, order confirmation, order update, delivery sent, order cancelled, order refunded, order partially cancelled, order partially refunded, uncollected-consignment cancelled, and back-in-stock notification. Each email process follows generate → send → cleanup. |
| **Generalized meaning** | *Asynchronous orchestration graph.* |
| **Business capability** | Order-to-ERP transmission; all transactional customer communication. |
| **Dependencies (upstream)** | Action beans; email templates and contexts; ERP outbound services; process parameter helper. |
| **Dependencies (downstream)** | Order status visible in the storefront and Backoffice; customer inbox; ERP order creation. |
| **Potential impact** | Orders stuck in a non-terminal state; duplicate or missing customer emails; orders never reaching ERP; in-flight processes orphaned when a node is removed from a definition. |
| **Regression areas** | Full order-to-cash including error branches; email rendering in all supported locales; process restart/retry behaviour. |
| **Deployment risk** | **CRITICAL** for the order process — it is the revenue path, and **in-flight process instances continue against the new definition**. Changing node ids can strand running processes. |
| **Related configuration** | `ProcessDefinitionResource` Spring beans; email template names bound to generate-email actions; process engine properties. |
| **Related integrations** | ERP order interface; SMTP. |

---

### 4.13 Event and Event Listener

| Dimension | Detail |
|---|---|
| **General SAP Commerce role** | Decoupled publish/subscribe within the platform. Listeners are singletons; site-aware listeners extend the accelerator site event listener base. Commonly used to start business processes. |
| **Client implementation** | *Client-specific implementations detected.* Events and listeners for order submitted, order confirmation, order cancelled, order refunded, order completed, order partially cancelled, order partially refunded, delivery message, uncollected-consignment cancelled, customer registration, customer welcome email, order update email, recently-viewed product, recently-ordered product, product viewed, and product back-in-stock. |
| **Generalized meaning** | *Asynchronous side-effect trigger.* |
| **Business capability** | Transactional communications, personalization signals (recently viewed/ordered), stock-availability notifications. |
| **Dependencies (upstream)** | Publishing services/strategies; business process service; model service. |
| **Dependencies (downstream)** | Business processes → emails → customer inbox; personalization data on the customer record. |
| **Potential impact** | Duplicate or missing notifications; unbounded listener work on high-frequency events causing throughput degradation; transaction-boundary surprises (listener work committing independently). |
| **Regression areas** | Every notification scenario; high-volume event paths (product view, order submit) under load. |
| **Deployment risk** | **MEDIUM–HIGH.** Listener errors are often swallowed — failures are silent until a customer reports a missing email. |
| **Related configuration** | Spring listener registration and parent base class (site-aware vs. plain); feature-flag properties gating event production. |
| **Related integrations** | SMTP; downstream analytics. |

---

### 4.14 Hook (Method Hook / Inbound Persistence Hook)

| Dimension | Detail |
|---|---|
| **General SAP Commerce role** | Extension points registered into platform hook lists — commerce method hooks (before/after place order, add-to-cart, update-cart-entry) and integration-services pre/post-persist hooks for inbound payloads. |
| **Client implementation** | *Client-specific implementations detected.* A place-order method hook; inbound pre-persist hooks for B2B customer, B2B unit, employee, price row and stock level; an inbound order hook; and a goods-issue persistence hook for the OMS/ERP flow. |
| **Generalized meaning** | *Pipeline extension point.* |
| **Business capability** | Order placement side-effects; normalisation and enrichment of inbound master data from ERP. |
| **Dependencies (upstream)** | Integration object definitions; the payload contract; services used for lookup/enrichment. |
| **Dependencies (downstream)** | Persisted master data → pricing, assortment, stock display, customer access. |
| **Potential impact** | Inbound feed rejection or silent data corruption at scale; order placement failure; duplicate records when key resolution changes. |
| **Regression areas** | Replay a representative inbound payload set per integration object; order placement; pricing after a price-row load; stock display after a stock load. |
| **Deployment risk** | **HIGH.** Inbound hooks sit in the ERP data path with no human review. |
| **Related configuration** | Hook Spring registration; integration object item attributes; inbound channel configuration and auth type. |
| **Related integrations** | All inbound ERP objects (customer, org unit, employee, product/variant, price row, discount row, stock level, order) and the outbound order object. |

---

### 4.15 Strategy

| Dimension | Detail |
|---|---|
| **General SAP Commerce role** | Pluggable algorithm behind a service — replaces a specific decision point without replacing the service. |
| **Client implementation** | *Client-specific implementations detected.* Cart validation strategy, add-to-cart strategy, update-cart-entry strategy, delivery-address lookup strategies (B2B and customer variants), external tax determination strategy, ticket-event email strategy, and price/PDT criteria & query-provider strategies. |
| **Generalized meaning** | *Pluggable decision point.* |
| **Business capability** | Cart integrity, quantity limits, address eligibility, tax determination, price resolution, ticket notification. |
| **Dependencies (upstream)** | The owning service; configuration properties (limits, thresholds); B2B unit/user context. |
| **Dependencies (downstream)** | Every operation routed through the owning service. |
| **Potential impact** | Cart contents silently changed on validation; add-to-cart limits changed; wrong delivery addresses offered; **wrong price selected** — the highest-severity functional defect class in B2B commerce. |
| **Regression areas** | Cart & checkout across multiple organisations, price lists and currencies; address selection; tax totals. |
| **Deployment risk** | **HIGH.** Price and cart strategies are financial-correctness code. |
| **Related configuration** | Quantity-limit and force-in-stock properties; base-store flags (price restriction, max quantity); price-list and customer-group configuration. |
| **Related integrations** | ERP price/simulation calls where pricing is externalised. |

---

### 4.16 OCC REST Controller

| Dimension | Detail |
|---|---|
| **General SAP Commerce role** | Spring MVC endpoints under the OCC v2 web module, secured with role annotations, documented via OpenAPI annotations, and optionally overriding platform endpoints via request-mapping override with a priority property. |
| **Client implementation** | *Client-specific implementations detected.* Controllers for: identity-provider login/success/logout; SSO token exchange; account summary, statement PDF and sales document types; bill-to/sold-to addresses; cart retrieval and deletion (platform overrides); CMS component retrieval and FAQ search; site/GA/cookie/shipping configuration; customer creation, removal, login-status and organisation-unit switching; invoice PDF; order history (user and organisation scopes), order types and B2B order placement (platform override); cart update/place-order; product detail (platform override), recently-viewed products, and max order quantity; reorder / cart-from-order (platform override); saved-cart entry deletion; external ticket list/create/read and attachment download; and stock notification subscription. |
| **Generalized meaning** | *Public API surface.* |
| **Business capability** | The complete contract the storefront and any external client depends on. |
| **Dependencies (upstream)** | Facades, WsDTOs, field-set mappings, validators, filters, security roles. |
| **Dependencies (downstream)** | Angular adapters/connectors and OCC endpoint configuration; external API consumers; Swagger/OpenAPI contract. |
| **Potential impact** | Breaking API change; authorisation regression (role annotation change); endpoint-override priority conflict causing the wrong handler to win; response-shape change breaking the UI. |
| **Regression areas** | Contract tests for the endpoint; the Angular feature calling it; authentication/authorisation matrix per role; multi-baseSite behaviour. |
| **Deployment risk** | **HIGH.** Backend and frontend deploy independently — a breaking API change without a coordinated frontend release causes an immediate production defect. |
| **Related configuration** | Request-mapping override **priority properties** in `project.properties` (a numeric change here silently swaps which controller serves a path); CORS filter configuration; OAuth/resource-server settings. |
| **Related integrations** | Controllers that proxy ERP (account summary, statement/invoice PDF, order history, order simulation) inherit ERP availability and latency. |

> **Critical rule — endpoint override priority:** changing a `*.priority` property changes which controller handles a URL with no code diff. Always classify as an API-contract change.

---

### 4.17 OCC Field-Set Level Mapping (`*-web-spring.xml`)

| Dimension | Detail |
|---|---|
| **General SAP Commerce role** | Declares which DTO fields are serialized at `BASIC`, `DEFAULT` and `FULL` levels for each WsDTO class; also declares field mappers between data and WsDTO classes. |
| **Client implementation** | *Client-specific implementation detected.* Field-set mappings are defined for product, variant option, product/category search page, order, order entry, order history, cart, user, B2B unit, address, ticket, ticket attachment, media, account summary, sales document type, FAQ component/segment/entry and FAQ search page DTOs. |
| **Generalized meaning** | *Response projection contract.* |
| **Business capability** | Determines whether a populated field is actually visible to the client. |
| **Dependencies (upstream)** | `beans.xml` DTO definitions; populators that fill the fields. |
| **Dependencies (downstream)** | Angular OCC endpoint `fields=` strings; any external consumer relying on a level. |
| **Potential impact** | Removing a field from a level silently blanks it in the UI; adding heavy sub-graphs (e.g. `FULL` nested collections) causes **payload size and latency regressions**. |
| **Regression areas** | Every screen using the endpoint at that level; payload-size/performance checks on list endpoints. |
| **Deployment risk** | **MEDIUM–HIGH.** Zero-code, high-visibility breakage. Must be released in lock-step with the frontend field lists. |
| **Related configuration** | Angular OCC endpoint configuration — this is the *matching half of the same contract*. |
| **Related integrations** | External API consumers pinned to a level. |

> **Rule R6 restated:** a change to a field-set mapping without a corresponding change to the frontend `fields=` list (or vice versa) is a **contract-drift finding** and should always be reported.

---

### 4.18 OCC Filter

| Dimension | Detail |
|---|---|
| **General SAP Commerce role** | Servlet filters merged into the OCC filter chain; establish request context (user, currency, language, site, session attributes) before controllers run. |
| **Client implementation** | *Client-specific implementations detected.* An assortment/entitlement filter and a user-context filter, both merged into the OCC v2 filter chain. |
| **Generalized meaning** | *Request pipeline cross-cutting concern.* |
| **Business capability** | Per-request entitlement scoping and user/locale/currency context — the mechanism that makes B2B data isolation work. |
| **Dependencies (upstream)** | User service, i18n service, session service. |
| **Dependencies (downstream)** | **Every OCC request**, without exception. |
| **Potential impact** | Cross-organisation data leakage; wrong currency/language on all responses; per-request latency added to 100% of traffic; session contamination between requests on a shared thread pool. |
| **Regression areas** | Full API smoke across multiple users, organisations, sites, currencies and locales; concurrency/session-isolation testing. |
| **Deployment risk** | **CRITICAL.** Filters affect every request and are a classic source of security defects. |
| **Related configuration** | Filter chain merge directives; CORS configuration; session properties. |
| **Related integrations** | Context established here flows into ERP calls (sold-to/ship-to, currency). |

---

### 4.19 Solr Configuration (indexed types, properties, value providers, queries, triggers)

| Dimension | Detail |
|---|---|
| **General SAP Commerce role** | ImpEx-defined facet-search configuration: server config, index config, search config, indexed types, indexed properties, value providers, indexer queries (full/update), sorts, value ranges, search query templates and query properties, plus indexing cron triggers. Bound to base site and base store. |
| **Client implementation** | *Client-specific implementation detected.* Two indexed types per store — a **product** index and an **FAQ/content** index — sharing one facet search configuration. Indexed properties cover identity and merchandising fields (code, name, summary, description, keywords, EAN, unit, manufacturer, URL), category hierarchy fields, multiple image-format fields, stock fields, price and currency fields, and a set of **B2B entitlement/pricing dimension fields** (assortment include/exclude, price list, customer group, customer hierarchy, sold-to, ship-to, incoterm, sales district, division, user group, generic-price flag). Value providers for these dimensions are implemented in the facades extension. Full and update indexer queries plus per-store indexing triggers are defined. |
| **Generalized meaning** | *Search index contract.* |
| **Business capability** | Product discovery, category browsing, faceted navigation, **entitlement-aware search** (customers only see what they may buy), price-aware search, and content/FAQ search. |
| **Dependencies (upstream)** | `items.xml` attributes exposed to the index; value provider beans (facades layer); catalog versions; base store/site binding. |
| **Dependencies (downstream)** | PLP, search results, facets, autocomplete, category pages, FAQ search, carousels driven by search. |
| **Potential impact** | Index schema mismatch until reindex; **entitlement leakage** if an assortment/user-group field is broken (customers see products they are not entitled to); facet and sort breakage; index size and query latency growth; per-store divergence when only some stores are updated. |
| **Regression areas** | Search and PLP for **every** affected store/locale/currency; facet counts; entitlement checks with at least two distinct organisations; autocomplete; FAQ search. |
| **Deployment risk** | **HIGH.** Requires a **full reindex** to take effect — this is an operational step, not a code deploy, and is time-proportional to catalog size. Partial rollout across stores creates inconsistent behaviour. |
| **Related configuration** | Solr server mode and endpoint config; index name prefix per store; indexing trigger schedules; `solr-cloud` config in the CCv2 manifest. |
| **Related integrations** | Product, price, stock and assortment data all arrive from ERP — index freshness depends on inbound integration health. |

> **Rule R7 restated:** any diff under `**/stores/*/solr*.impex` or any `*ValueProvider.java` ⇒ emit a **"full reindex required"** deployment action.

---

### 4.20 CMS Component and Content

| Dimension | Detail |
|---|---|
| **General SAP Commerce role** | Content types defined in `items.xml` (extending CMS component base types), content instances imported via ImpEx into content catalogs, exposed through the CMS OCC API, rendered by frontend components, and authored in SmartEdit/Backoffice. |
| **Client implementation** | *Client-specific implementations detected.* Custom CMS component types for an **FAQ container**, **FAQ segment**, **FAQ entry** and a **forms container** (related to banner components), plus an extension of the standard product-carousel component. Content catalogs exist per market/store, with localized content ImpEx per supported language. |
| **Generalized meaning** | *Content model + content instances.* |
| **Business capability** | Marketing content, navigation, footer, banners, self-service help content, page composition. |
| **Dependencies (upstream)** | `items.xml` component type; CMS OCC DTO adapters; content catalog and catalog version; page templates and content slots. |
| **Dependencies (downstream)** | Frontend CMS component mapping (Angular `cmsComponents` config); SmartEdit authoring; CMS OCC response shape; layout slot configuration. |
| **Potential impact** | Unmapped component type renders nothing; content catalog requires **synchronisation (Staged → Online)** to become visible; per-market content divergence; broken SmartEdit authoring for the type. |
| **Regression areas** | Page rendering per template and per market; SmartEdit authoring for the changed type; content sync; localized content in every supported language of the affected store. |
| **Deployment risk** | **MEDIUM–HIGH.** New component types need frontend mapping **and** catalog sync **and** SmartEdit configuration. Missing any one produces a visible content defect. |
| **Related configuration** | Content catalog/version definitions; layout slot configuration in the frontend; CMS component adapters in the OCC layer; page template definitions. |
| **Related integrations** | None directly; media assets and image conversion. |

---

### 4.21 ImpEx

| Dimension | Detail |
|---|---|
| **General SAP Commerce role** | Declarative data import/update language. Executed at system setup (essential/project data), from patches, from hot folders, and manually via HAC. It **mutates data**, not schema. |
| **Client implementation** | *Client-specific implementation detected.* Organised into: **common essential data** (countries, delivery modes, themes, user groups, cron jobs, email templates, integration setup), **security** (user groups, access rights, security roles — heavily localized and phase-scoped), **integration** (integration objects and channel configuration for inbound customer, employee, product/variant, price row, stock level, listings/exclusions, order update, and the outbound order object), **per-store data** (site, store, Solr, indexing triggers), **catalog data** (product and content catalogs per market), **CMS content** (per market, per language) and **email content**. Rollout data is partitioned across a base data extension and per-phase data extensions. |
| **Generalized meaning** | *Environment data state.* |
| **Business capability** | Every configuration-driven capability: sites, stores, catalogs, search config, security, integration wiring, notifications, content. |
| **Dependencies (upstream)** | Type system (header attributes must exist); referenced items must exist or be created in the same run; catalog version context. |
| **Dependencies (downstream)** | Runtime behaviour of everything the data configures. |
| **Potential impact** | **Non-idempotent ImpEx can destroy or duplicate live data.** `INSERT` vs `INSERT_UPDATE` vs `UPDATE` vs `REMOVE` semantics determine reversibility. Header attribute references break when `items.xml` changes. Localized files can be applied inconsistently across languages. Encoding/delimiter issues corrupt values. |
| **Regression areas** | Re-run the same ImpEx twice (idempotency check); verify every store/language variant of the file family; verify dependent runtime behaviour (search, security, integration, content). |
| **Deployment risk** | **HIGH → CRITICAL** depending on operation type and target. `REMOVE`, `UPDATE` on shared/common data, and security/access-rights files are the highest-risk category. Data changes are frequently **not** covered by application rollback. |
| **Related configuration** | System setup annotations that decide *when* a file runs (essential vs project data); patch definitions; hot-folder converters; `system.setup.create.data.fail.on` behaviour. |
| **Related integrations** | Integration object and channel configuration ImpEx defines the entire inbound/outbound contract surface. |

**Sub-classification by directory (risk ordering):**

| Location | Scope | Risk |
|---|---|---|
| `import/security/**` | Access rights, roles, user groups | **CRITICAL** — access outage or privilege escalation |
| `import/common/global-integration-setup.impex` | Destinations, credentials, channels | **CRITICAL** — integration outage; credential material |
| `import/common/essentialdata-Inbound*/Outbound*` | Integration object contracts | **HIGH** |
| `import/common/cronjobs.impex` | Job schedule and retention rules | **HIGH** |
| `stores/*/solr*.impex` | Search configuration | **HIGH** + reindex |
| `stores/*/site.impex`, `store.impex` | Site/store wiring | **HIGH** |
| `*ContentCatalog/**` | CMS content | **MEDIUM** + catalog sync |
| `*ProductCatalog/**` | Catalog/category/product base data | **MEDIUM** + catalog sync |
| `import/common/countries|delivery-modes|themes` | Reference data | **LOW–MEDIUM** |

---

### 4.22 Patch (Versioned Data Migration)

| Dimension | Detail |
|---|---|
| **General SAP Commerce role** | Release-scoped, ordered, idempotent data migration framework. Each patch declares a release, an id, and a set of actions — ImpEx import, Groovy execution, catalog synchronisation, Solr indexing, Excel import. Execution state is tracked so a patch runs once per environment. |
| **Client implementation** | *Client-specific implementation detected.* Multiple releases containing patches for content catalog updates, per-market content, global header changes, product catalog updates, shipping configuration per store, Solr configuration per store, FAQ naming and redirection, language updates, OCC/OAuth configuration, SmartEdit core configuration, consent component cleanup, out-of-the-box data cleanup, media conversion, digital payment, account summary, and catalog synchronisation updates. Shared patch action base classes cover sync, import, Groovy, Solr indexing and Excel import. |
| **Generalized meaning** | *Versioned, environment-tracked data migration.* |
| **Business capability** | Controlled evolution of configuration and content across environments without re-initialising. |
| **Dependencies (upstream)** | Patch action beans; ImpEx/Groovy resources; catalogs; Solr configuration. |
| **Dependencies (downstream)** | Environment data state; whatever the patch configures. |
| **Potential impact** | A patch runs **once per environment** — an incorrect patch that has already executed in production cannot be re-run to fix itself; a compensating patch is required. Ordering between patches matters. A patch that triggers catalog sync or Solr indexing has a long, resource-intensive execution window. |
| **Regression areas** | Execute the patch in a production-like environment first; verify the specific configuration it changes; verify patch-state tracking; confirm no double-execution. |
| **Deployment risk** | **HIGH.** Irreversible-by-default. Patches that perform sync or indexing extend deployment duration significantly. |
| **Related configuration** | Patch framework enablement in the cloud descriptor; release/patch id ordering; patch backoffice extension for visibility. |
| **Related integrations** | Patches that rewrite integration configuration change live endpoints. |

---

### 4.23 System Setup / Data Import Service

| Dimension | Detail |
|---|---|
| **General SAP Commerce role** | Annotated setup classes that run at initialization/update and import essential data (always) and project data (on demand). Core and sample data import services resolve which files to load per store. |
| **Client implementation** | *Client-specific implementations detected.* A core system setup that imports essential reference data, security data, cron jobs, integration setup and CMS updates; project-data setup that imports integration object definitions and security data; and per-extension initial/phase data setups with customised core-data and sample-data import services and a customised catalog sync job service. |
| **Generalized meaning** | *Bootstrap orchestration.* |
| **Business capability** | Environment provisioning and repeatability. |
| **Dependencies (upstream)** | ImpEx resources; catalog and sync configuration. |
| **Dependencies (downstream)** | The entire environment data state. |
| **Potential impact** | Adding a file to essential data makes it run on **every update**, in every environment — a frequent cause of unintended production data changes. Ordering between imports determines success. |
| **Regression areas** | Full system update on a production-like copy; idempotency across repeated updates. |
| **Deployment risk** | **HIGH.** Essential-data changes execute automatically during update with no explicit approval step. |
| **Related configuration** | Import path properties; `system.setup.create.data.fail.on`. |
| **Related integrations** | Integration object definitions are imported here. |

---

### 4.24 Integration Object / Inbound Channel / Outbound Destination

| Dimension | Detail |
|---|---|
| **General SAP Commerce role** | Integration API definitions: an integration object maps item types and attributes into an OData-exposed structure; inbound channel configuration exposes it for writes with an authentication type; outbound destinations, consumed credentials and endpoints define where data is sent. |
| **Client implementation** | *Client-specific implementation detected.* Inbound objects for **B2B unit, B2B customer, customer update, employee, variant product, price row, discount row, stock level, listings/exclusions and OMS order**; an outbound object for **order** (order management → order management system); an integration service account and client-credentials registration; ERP endpoints for customer replication, order creation, order simulation, order history, account/statement and billing; ERP logical system, sales organisation, plant, distribution-channel and division mappings; and a delivery-mode mapping. |
| **Generalized meaning** | *Cross-system data contract.* |
| **Business capability** | Master data synchronisation (customers, organisations, products, prices, stock, entitlement), order transmission and order status/history, financial documents (invoices, statements), price simulation. |
| **Dependencies (upstream)** | `items.xml` attributes exposed; pre-persist hooks; credentials and destination properties. |
| **Dependencies (downstream)** | Every downstream consumer of the synchronised data: pricing, assortment, stock display, customer access, order history, account summary. |
| **Potential impact** | **Bidirectional breakage** — a change here can break the middleware/ERP side as well as Commerce. Adding a required attribute rejects existing payloads. Removing an attribute silently drops data. Credential or destination changes cause immediate integration outage. Authentication type changes break the caller. |
| **Regression areas** | Replay representative payloads for every affected object (positive and negative); verify outbound payload against the ERP contract; verify error handling and retry; end-to-end order placement to ERP; order history and account summary retrieval. |
| **Deployment risk** | **CRITICAL.** Requires coordination with the middleware/ERP release train. Failures are often asynchronous and detected late. Credential material lives in this configuration surface. |
| **Related configuration** | Endpoint URL, client id/secret and OAuth URL properties (environment-specific); logical system and organisational mappings; outbound sync jobs; webhook configuration; REST template strategy lists. |
| **Related integrations** | The integration layer *is* the integration. |

> **Security rule:** any diff touching integration credential rows, destination URLs, or client-credentials registrations must be flagged for **secret-handling review** and must never be echoed into generated documentation.

---

### 4.25 Hot Folder / Batch Import Configuration

| Dimension | Detail |
|---|---|
| **General SAP Commerce role** | Spring-integration file pipelines that watch a folder (cloud storage in CCv2), convert CSV rows into ImpEx via converter beans, and import them. |
| **Client implementation** | *Client-specific implementation detected.* A shared media/catalog converter set (media, media container, product media) plus per-store hot-folder pipelines. |
| **Generalized meaning** | *Batch data ingestion pipeline.* |
| **Business capability** | Bulk catalog, media and master-data loading. |
| **Dependencies (upstream)** | ImpEx header templates embedded in converters; catalog version; media formats. |
| **Dependencies (downstream)** | Catalog content, product imagery, anything the feed loads. |
| **Potential impact** | Malformed generated ImpEx fails whole batches; changed media formats orphan existing media; per-store pipelines diverge. |
| **Regression areas** | Run a representative file per pipeline; verify media formats resolve; verify failure/retry and error-file handling. |
| **Deployment risk** | **MEDIUM–HIGH.** Runs unattended on the background-processing node. |
| **Related configuration** | Cloud hot-folder feature flag in the deployment descriptor; storage container configuration; media format definitions. |
| **Related integrations** | Upstream PIM/DAM feeds. |

---

### 4.26 Backoffice Configuration and Widgets

| Dimension | Detail |
|---|---|
| **General SAP Commerce role** | `*-backoffice-config.xml` defines list/editor/search views per type; widget definitions and Spring wiring add custom actions, editors and renderers; security services scope visibility. |
| **Client implementation** | *Client-specific implementations detected.* A custom fulfilment action widget, a principal-permission action widget, a custom enum editor, a currency list renderer, a site-scoped Backoffice role/user-details/authentication service set, and a site-assignment helper tied to a role↔site relation. |
| **Generalized meaning** | *Administrative UI and admin access control.* |
| **Business capability** | Content, catalog, order, customer and configuration administration; **who can administer which markets**. |
| **Dependencies (upstream)** | `items.xml` types and attributes; SSO/SAML authentication; role↔site relation. |
| **Dependencies (downstream)** | Business-user workflows; no storefront impact. |
| **Potential impact** | Admin lockout or over-privileging (site-scoping bugs let an operator administer another market); missing editors for new attributes; widget errors breaking a perspective. |
| **Regression areas** | Admin login (SSO), per-role and per-site visibility, editing each affected type, the custom actions. |
| **Deployment risk** | **MEDIUM**, rising to **HIGH** for security/role/site-scoping changes. Backoffice aspect restart only. |
| **Related configuration** | SSO group-mapping properties; Backoffice cache/reset properties; role↔site data. |
| **Related integrations** | Identity provider group mapping. |

---

### 4.27 SmartEdit Configuration

| Dimension | Detail |
|---|---|
| **General SAP Commerce role** | In-context content authoring. Requires the SmartEdit extension set, CMS/SmartEdit web services, preview and permission services, and correct CORS/allow-origin and application configuration. |
| **Client implementation** | *Client-specific implementation detected.* SmartEdit and personalization webapps are deployed on the Backoffice and API aspects; SmartEdit core configuration is applied through the patch framework; the storefront enables the Spartacus SmartEdit module. |
| **Generalized meaning** | *In-context authoring channel.* |
| **Business capability** | Business-user content editing without developer involvement. |
| **Dependencies (upstream)** | CMS web services, preview web services, permission web services, CMS component types, storefront SmartEdit module, CORS configuration. |
| **Dependencies (downstream)** | Content authoring productivity; no runtime storefront impact. |
| **Potential impact** | Authoring outage (blank canvas, unauthorised errors) — typically caused by CORS, allow-origin, or preview-ticket configuration, not by content code. New component types not decorated correctly are not editable. |
| **Regression areas** | Open a page in SmartEdit per market; edit each customised component type; verify preview and personalization. |
| **Deployment risk** | **MEDIUM.** Business-user impact only, but highly visible and often diagnosed late. |
| **Related configuration** | CORS filters for SmartEdit/CMS web services; allow-origin lists; webapp context paths in the deployment descriptor; storefront SmartEdit module configuration. |
| **Related integrations** | None. |

---

### 4.28 Security, Roles and Permissions

| Dimension | Detail |
|---|---|
| **General SAP Commerce role** | User groups, access rights (type/attribute-level permissions), OCC security roles on controllers, OAuth client registrations, search restrictions, and password policy. |
| **Client implementation** | *Client-specific implementation detected.* Extensive ImpEx-driven user groups, access rights and security roles, partitioned per rollout phase and heavily localized; custom customer/price/product grouping user-group types; a customised password policy service and a native password policy; role annotations on all custom OCC endpoints; identity-provider and SSO configuration for storefront and Backoffice. |
| **Generalized meaning** | *Authorisation model.* |
| **Business capability** | Data isolation between organisations and markets; least-privilege administration; customer self-service boundaries. |
| **Dependencies (upstream)** | Type system; identity provider group claims; SSO attribute mapping. |
| **Dependencies (downstream)** | Every read and write path in every channel. |
| **Potential impact** | **Privilege escalation** or **access outage**. Both are severity-1 classes. Access-rights ImpEx changes are global and immediate. |
| **Regression areas** | Authorisation matrix per role across OCC, Backoffice and SmartEdit; cross-organisation data isolation; login for each identity type. |
| **Deployment risk** | **CRITICAL.** Always require explicit human review. |
| **Related configuration** | SSO mapping properties; password policy properties; OAuth client registrations; CORS. |
| **Related integrations** | Identity provider; SAML metadata and keystore. |

---

### 4.29 Properties and Environment Configuration

| Dimension | Detail |
|---|---|
| **General SAP Commerce role** | Layered configuration: extension `project.properties` (defaults), `local.properties` (local/dev), and CCv2 `ccv2/local.properties` plus per-persona and per-aspect property files resolved by the deployment descriptor. |
| **Client implementation** | *Client-specific implementation detected.* Properties govern OCC endpoint override priorities, quantity limits, page sizes, recently-viewed/ordered limits, key generator formats for order and cart codes, Solr server mode, facet limits, CORS allow-lists per web service, mail transport, SSO attribute and group mapping, password policy rules, ERP/integration endpoints and credentials, identity-provider language fallbacks per market, audit toggles, and logging levels. |
| **Generalized meaning** | *Runtime configuration surface.* |
| **Business capability** | Feature toggles, limits, formats, endpoints, and cross-origin policy. |
| **Dependencies (upstream)** | None — properties are leaves. |
| **Dependencies (downstream)** | Any bean or controller reading the key; behaviour differs per environment. |
| **Potential impact** | Behaviour that passes in lower environments and fails in production because the property differs; CORS misconfiguration blocking the storefront; endpoint priority flips; key-format changes breaking ERP correlation; credential exposure if placed in the wrong file. |
| **Regression areas** | Verify the property is set in **every** persona/aspect file it needs to be; verify defaults; verify CORS from the real storefront origin. |
| **Deployment risk** | **MEDIUM**, rising to **CRITICAL** for CORS, security, credential and endpoint-override properties. Property changes usually require a restart of the affected aspects. |
| **Related configuration** | `manifest.json` `useConfig.properties` persona/aspect mapping. |
| **Related integrations** | Endpoint URLs and credentials for ERP and identity provider. |

---

### 4.30 Cloud Deployment Descriptor (`manifest.json`) and Project Descriptor (`config.json`)

| Dimension | Detail |
|---|---|
| **General SAP Commerce role** | `manifest.json` declares the Commerce version, extension packs, additional extensions, Solr configuration location, property file mapping per persona/aspect, aspect definitions and the webapps/context paths each aspect serves. `config.json` drives local/CI project setup and feature flags. |
| **Client implementation** | *Client-specific implementation detected.* Four aspects are defined with explicit webapp/context-path lists; property files are mapped per persona (development/staging/production) and per aspect; Solr uses a checked-in cloud configuration; language packs are declared as a property. |
| **Generalized meaning** | *Infrastructure and topology definition.* |
| **Business capability** | Determines what runs where, and therefore availability and scaling characteristics. |
| **Dependencies (upstream)** | Extension set in `localextensions.xml`. |
| **Dependencies (downstream)** | Build, deploy, routing, and runtime topology of every environment. |
| **Potential impact** | Build failure; an endpoint becoming unreachable because its webapp was removed from an aspect; Solr configuration mismatch; a property no longer resolved for a persona; platform version change triggering a full-platform regression. |
| **Regression areas** | Full build; deploy to a lower environment; reachability check for every declared context path; environment-specific property resolution. |
| **Deployment risk** | **CRITICAL.** This is infrastructure. Errors are discovered at deploy time and affect whole environments. |
| **Related configuration** | All property files it references; `localextensions.xml`; Solr configuration directory. |
| **Related integrations** | Aspect assignment determines which nodes host integration and background processing. |

---

### 4.31 Frontend — Angular/Spartacus Feature Module and CMS Mapping

| Dimension | Detail |
|---|---|
| **General SAP Commerce role** | Angular modules that map backend CMS component types to Angular components (`cmsComponents` config) and register lazily-loaded feature modules. This mapping is the frontend's contract with the CMS. |
| **Client implementation** | *Client-specific implementation detected.* Roughly two dozen lazily-loaded feature modules map CMS component types across: home page (carousel, rotating images, account summary, order history), product listing and grid, search results, product detail (base product), cart (cart, totals, clear cart, saved-cart dialog), saved carts (list and detail), quick order and import/export order entries, checkout (single-step checkout, delivery address, delivery mode, order summary, place order, paragraph), order confirmation, order history and order detail (items, overview, totals, reorder), address book, profile, account summary, support tickets (list, create, update), FAQ, forms, sitemap, navigation, footer, breadcrumb, manage account and customer login. |
| **Generalized meaning** | *CMS→UI binding and code-splitting boundary.* |
| **Business capability** | Which UI renders for each authored content component. |
| **Dependencies (upstream)** | CMS component type codes (backend `items.xml` + content catalogs); CMS OCC responses. |
| **Dependencies (downstream)** | Rendered page; lazy chunk boundaries and initial bundle size. |
| **Potential impact** | A component type present in content but unmapped renders nothing (blank slot). A mapping registered in two places causes ambiguity. Moving a component between eager and lazy modules changes bundle size and time-to-interactive. |
| **Regression areas** | Visual regression per page template and per market; lazy-chunk loading; bundle size budget. |
| **Deployment risk** | **MEDIUM.** Frontend deploys independently; a bad mapping is visible immediately but only on pages that use the component. |
| **Related configuration** | Layout slot configuration; feature toggle level; SmartEdit decoration. |
| **Related integrations** | None directly. |

---

### 4.32 Frontend — OCC Endpoint & Site Context Configuration

| Dimension | Detail |
|---|---|
| **General SAP Commerce role** | Declares the OCC base URL, the endpoint template for every backend call (including the `fields=` projection), and the site context (base sites, currencies, languages, URL parameters), plus i18n chunk loading and feature level. |
| **Client implementation** | *Client-specific implementation detected.* Endpoint overrides are defined for product search, product detail variants, cart and carts, invoice PDF, notification subscription, saved-cart entry deletion, bill-to and sold-to addresses, sold-to list, ticket categories/create/detail/messages/attachments, external tickets, order history, organisation orders, order types, home account summary, cart update, shipping configuration, organisation-unit switching, max order quantity, recently-viewed products and site configuration. Site context enumerates all supported base sites, currencies and languages for the rollout. |
| **Generalized meaning** | *Frontend half of the API contract.* |
| **Business capability** | Every network call the storefront makes, and the market/locale/currency matrix it supports. |
| **Dependencies (upstream)** | OCC controllers and field-set mappings (the backend half of the contract). |
| **Dependencies (downstream)** | Every frontend feature. |
| **Potential impact** | **Contract drift** — a `fields=` list requesting a field the backend no longer serializes yields silent nulls; a new base site or currency not added here is unreachable; a wrong endpoint path produces 404s at runtime only. |
| **Regression areas** | Smoke every endpoint touched; verify each newly added site/currency/language end to end; verify `fields=` alignment against backend field-set levels. |
| **Deployment risk** | **HIGH.** Single-file, wide blast radius, and the most common location of frontend/backend contract drift. |
| **Related configuration** | Backend field-set mappings; base-site and base-store ImpEx; i18n translation chunks. |
| **Related integrations** | Indirect. |

> **Rollout rule:** adding a market means changes in **at least five places** — a phase data extension (site/store/catalog/Solr), security ImpEx for the market's groups, CMS content catalog, identity-provider locale fallback properties, and the frontend site-context configuration. A diff touching only some of these is an **incomplete rollout** finding.

---

### 4.33 Frontend — Adapter, Connector and Service

| Dimension | Detail |
|---|---|
| **General SAP Commerce role** | Spartacus data-access layering: an **adapter** performs the HTTP call using configured endpoints; a **connector** is the injectable seam; a **service/facade** exposes state to components (often via NgRx). Adapters are swapped by DI providers. |
| **Client implementation** | *Client-specific implementations detected.* Adapter/connector/service triples for order history, home-page account summary, quick order, cart update, shipping configuration, organisation orders, saved carts, logout, base product, support tickets, sold-to selection, max order quantity, address book and chatbot. |
| **Generalized meaning** | *Frontend data access layer.* |
| **Business capability** | Data retrieval and mutation for each frontend feature. |
| **Dependencies (upstream)** | OCC endpoint configuration; backend controller and response shape; frontend models. |
| **Dependencies (downstream)** | Components and NgRx state. |
| **Potential impact** | Response-shape mismatch producing runtime undefined errors; missing error handling surfacing raw errors; changed DI provider swapping the implementation globally. |
| **Regression areas** | The owning feature end to end, including error and empty states. |
| **Deployment risk** | **MEDIUM.** |
| **Related configuration** | OCC endpoint config; DI provider registrations in the features module. |
| **Related integrations** | Inherits ERP latency for ERP-backed endpoints. |

---

### 4.34 Frontend — Component

| Dimension | Detail |
|---|---|
| **General SAP Commerce role** | Presentation and interaction; bound to CMS component data and/or feature services. |
| **Client implementation** | *Client-specific implementations detected.* A large custom component set covering home page, PLP/search, PDP (including scaled-price table, quantity, cart actions, product card/table views), cart and saved carts, single-step checkout (address, delivery method, summary, place order), order confirmation, order history and detail (including reorder dialog and item actions), account summary (header, filters, table, pagination), address book, profile, support tickets, FAQ, forms, sitemap, navigation, footer, breadcrumb, customer login, multiple sold-to selection, quick order (form, table, import/export), export button, spinner, chatbot, and global/inline error surfaces. |
| **Generalized meaning** | *UI unit.* |
| **Business capability** | The user-visible product. |
| **Dependencies (upstream)** | Services/connectors; CMS data; i18n keys; shared components; styles. |
| **Dependencies (downstream)** | Other components importing it; visual regression baselines. |
| **Potential impact** | Visual and behavioural regression; accessibility regression; missing i18n keys showing raw keys; performance regression on list-heavy views. |
| **Regression areas** | The screen plus every screen reusing the component (shared/reusable components have wide reach); responsive breakpoints; supported locales. |
| **Deployment risk** | **LOW–MEDIUM** for leaf components; **MEDIUM–HIGH** for shared/reusable components and checkout components. |
| **Related configuration** | i18n translation chunks; layout slots; feature module registration. |
| **Related integrations** | None directly. |

---

### 4.35 Frontend — Guard, Interceptor, Route and i18n

| Dimension | Detail |
|---|---|
| **General SAP Commerce role** | Cross-cutting frontend concerns: route guards control navigation/auth; HTTP interceptors handle errors and headers globally; route configuration defines URL structure; i18n chunks provide localized text. |
| **Client implementation** | *Client-specific implementations detected.* An authentication-state service and a login-redirect guard; a global error HTTP interceptor; route overrides for the product route (mapping the product path parameter to the base product) and a single-step checkout route; an application initializer establishing site context before bootstrap; and per-language translation chunk configuration with a fallback language. |
| **Generalized meaning** | *Frontend cross-cutting concerns.* |
| **Business capability** | Access control, error experience, URL/SEO structure, localization. |
| **Dependencies (upstream)** | Auth state; OCC responses; translation assets. |
| **Dependencies (downstream)** | **Every route and every HTTP call.** |
| **Potential impact** | Redirect loops or lockout (guards); swallowed or double-handled errors (interceptors); **broken URLs and lost SEO/bookmarks** (route path changes); untranslated text or raw keys (i18n). |
| **Regression areas** | Authenticated and anonymous navigation across all guarded routes; error scenarios (401/403/500/network); deep links and legacy URLs; every supported locale. |
| **Deployment risk** | **HIGH.** These affect 100% of frontend traffic. Route path changes are effectively permanent public contract. |
| **Related configuration** | Site context URL parameters; OCC base URL meta tag; translation chunk config. |
| **Related integrations** | None directly. |

---

## 5. Consolidated Dependency Map

### 5.1 Vertical Chain (a single field, end to end)

```
items.xml attribute
   └─> generated model
        └─> DAO query / FlexibleSearch
             └─> Service
                  └─> Populator ──> Converter ──> Data bean (beans.xml)
                       └─> Facade
                            └─> OCC Controller
                                 └─> WsDTO (beans.xml)
                                      └─> Field-set level mapping (BASIC/DEFAULT/FULL)
                                           └─> Frontend OCC endpoint `fields=` list
                                                └─> Frontend model
                                                     └─> Angular component template
```

**Engine rule:** if a change enters this chain at any node, every node *below it in this listing* is a candidate impact. If the change is at `items.xml`, additionally fan out to Solr, integration objects, ImpEx and Backoffice.

### 5.2 Horizontal Chains (cross-cutting)

```
items.xml attribute ──┬──> Solr indexed property ──> value provider ──> index ──> PLP/facets
                      ├──> Integration object attribute ──> inbound/outbound payload ──> ERP
                      ├──> ImpEx header ──> data load scripts ──> patches ──> hot folders
                      └──> Backoffice editor/list config ──> admin UI

Spring alias ─────────┬──> all callers of the platform bean name (including untouched extensions)
                      └──> behaviour in every aspect

Interceptor ──────────┬──> storefront writes
                      ├──> OCC writes
                      ├──> ImpEx / hot-folder imports
                      ├──> integration inbound
                      └──> Backoffice edits and patches

Event ────────────────> listener ──> business process ──> action ──> email template ──> customer
```

### 5.3 The Order-to-Cash Critical Path

The single most important chain to protect. Any change touching a node here should default to elevated risk:

```
Product search (Solr + assortment/entitlement filter)
  → PDP (product facade + price strategy)
    → Add to cart (add-to-cart strategy + cart service + calculation service)
      → Cart (cart facade + populators + field-set mapping)
        → Checkout (delivery-address lookup strategy, delivery/shipping configuration,
                    cart validation strategy, place-order validator)
          → Place order (place-order method hook + order service + key generator)
            → ERP order process (payment authorisation check → outbound payload creator
                                 → destination → SCPI/ERP → response processor)
              → Order confirmation event → email process → customer notification
                → Order history / order detail (ERP-backed order facade + populators)
                  → Invoice / statement retrieval (ERP-backed)
```

---

## 6. Change Impact Matrix

Read as: *"If X changes, these are affected."*

| Changed artifact | Direct impact | Indirect / non-obvious impact | Default risk |
|---|---|---|---|
| `items.xml` | Model regeneration, DDL, all consumers | Solr schema, integration payloads, ImpEx headers, Backoffice editors, SmartEdit component definitions | CRITICAL |
| `*-beans.xml` | Generated DTO, populators | Field-set mappings, frontend models, Swagger contract | HIGH |
| `*-spring.xml` (alias/override) | The overridden behaviour everywhere | Untouched OOTB extensions calling the platform bean name | HIGH |
| `*-spring.xml` (populator list merge) | The converter | Every API response, email, and export using that converter | MEDIUM |
| `*-spring.xml` (interceptor mapping) | Writes for that type | ImpEx, hot folders, integration inbound, patches, Backoffice | HIGH |
| `*-spring.xml` (process definition) | Process graph | **In-flight process instances** | CRITICAL |
| `*-web-spring.xml` (field-set) | API response projection | Frontend `fields=` alignment; payload size/latency | HIGH |
| OCC Controller | Endpoint contract, auth | Frontend adapters, Swagger, endpoint override priority conflicts | HIGH |
| OCC Filter | Every request | Data isolation, locale/currency correctness, latency on 100% of traffic | CRITICAL |
| Validator | Input rejection rules | Frontend error messaging and i18n keys | HIGH |
| Facade | Orchestration, response shape | Every channel using the facade (OCC, email contexts, ASM) | HIGH |
| Service (aliased over platform bean) | The capability | Every request in every aspect; batch jobs and processes | HIGH |
| Service (custom only) | The capability | Jobs/processes calling it | MEDIUM |
| DAO | Query results | Performance; **search restrictions / data isolation** | MEDIUM–HIGH |
| Populator | Field values in DTO | Every converter list membership — often 3–5 surfaces | MEDIUM |
| Converter | DTO assembly | All facades using it | MEDIUM |
| Interceptor | Persistence lifecycle | **Bulk import and integration inbound failures** | HIGH |
| Strategy | The decision point | Pricing/cart correctness across organisations | HIGH |
| Hook (commerce) | Order/cart lifecycle | Order placement success rate | HIGH |
| Hook (inbound persist) | Inbound payload handling | ERP feed acceptance, master-data quality | HIGH |
| Event / Listener | Side effects | Emails, personalization data, throughput on hot events | MEDIUM–HIGH |
| Business process XML | Process graph | In-flight instances; orders stuck mid-flow | CRITICAL |
| Process Action bean | A process node | The whole process outcome | HIGH |
| CronJob performable / trigger | Batch behaviour | Mass data change; node-group load; retention/deletion | HIGH |
| Solr `*.impex` / value provider | Index definition | **Requires full reindex**; entitlement leakage; facet/sort breakage; per-store divergence | HIGH |
| CMS component type | Content model | Frontend `cmsComponents` mapping; SmartEdit; content catalog sync | MEDIUM–HIGH |
| CMS content ImpEx | Content instances | Catalog sync required; per-market/per-language consistency | MEDIUM |
| ImpEx (security/access) | Permissions | Access outage or privilege escalation, immediately and globally | CRITICAL |
| ImpEx (integration setup) | Destinations/credentials/channels | Integration outage; secret material | CRITICAL |
| ImpEx (reference/catalog) | Data state | Idempotency; catalog sync | MEDIUM |
| Patch | Environment data state | **Runs once per environment**; sync/index duration; ordering | HIGH |
| System setup class | Bootstrap import set | Essential data runs on **every** update in every environment | HIGH |
| Integration object / channel | Cross-system contract | Bidirectional breakage; middleware/ERP release coordination | CRITICAL |
| Hot folder converter | Batch ingestion | Whole-batch failures; media orphaning | MEDIUM–HIGH |
| Backoffice config/widget | Admin UI | Admin lockout / over-privilege on site scoping | MEDIUM–HIGH |
| SmartEdit config | Authoring channel | CORS/preview/permission chain | MEDIUM |
| `project.properties` (priority/CORS/security) | Runtime behaviour | Endpoint override flips; storefront blocked by CORS | MEDIUM–CRITICAL |
| `local.properties` / ccv2 persona properties | Environment behaviour | Works in dev, fails in production | MEDIUM–HIGH |
| `manifest.json` / `config.json` | Topology | Build/deploy failure; endpoint unreachable; version regression | CRITICAL |
| `localextensions.xml` | Extension set | Bean graph, type system, webapp availability | CRITICAL |
| Angular feature module / CMS mapping | Rendering | Blank slots; bundle size; lazy chunk boundaries | MEDIUM |
| Angular OCC endpoint config | All network calls | **Contract drift** with backend field-sets; unreachable new markets | HIGH |
| Angular adapter/connector | Feature data access | Runtime undefined errors; error UX | MEDIUM |
| Angular component (leaf) | One screen | Visual/accessibility regression | LOW–MEDIUM |
| Angular component (shared/reusable) | Many screens | Wide visual regression | MEDIUM–HIGH |
| Angular guard / interceptor / route | 100% of frontend traffic | Lockout, redirect loops, broken deep links/SEO | HIGH |
| i18n translations | Displayed text | Raw keys visible; layout overflow in long-text locales | LOW–MEDIUM |
| Email template (`.vm`) | Customer communication | Rendering per locale; context variable availability | MEDIUM |
| Extension dependency (`extensioninfo.xml`) | Build & bean graph | Load order, circular dependency, unavailable beans | HIGH |

---

## 7. Deployment Risk Model

### 7.1 Risk Bands

| Band | Meaning | Required controls |
|---|---|---|
| **CRITICAL** | Can cause an outage, data loss, security breach, or a cross-system contract break. Frequently irreversible. | Human approval; production-like rehearsal; explicit rollback/compensation plan; coordination with ERP/middleware and frontend release trains |
| **HIGH** | Wide blast radius or requires an operational step (reindex, sync, patch run, restart) | Full regression of the affected capability; staged rollout; operational runbook step documented |
| **MEDIUM** | Contained to a capability; recoverable by redeploy | Targeted regression |
| **LOW** | Cosmetic or additive; trivially reversible | Smoke test |

### 7.2 Risk Amplifiers (raise the band by one)

| Amplifier | Reason |
|---|---|
| Change is in a Spring `<alias>` over a platform bean | Silent global substitution |
| Change affects the order-to-cash critical path (§5.3) | Revenue impact |
| Change requires a **full Solr reindex** | Long operational window; stale results meanwhile |
| Change requires a **catalog synchronisation** | Long operational window; partial visibility |
| Change is an **ImpEx `REMOVE` or `UPDATE` on shared data** | Destructive and often unbacked by rollback |
| Change is in a **patch already executed in production** | Cannot be re-run; needs a compensating patch |
| Change touches **security, access rights, or roles** | Outage or escalation |
| Change touches **integration objects, destinations or credentials** | Requires external release coordination |
| Change touches **only one side of a contract pair** (field-set vs. frontend `fields=`, OCC vs. adapter, items.xml vs. Solr) | Contract drift — silent failure |
| Change touches **only some markets** in a per-store artifact family | Inconsistent behaviour across the estate |
| Change affects an **interceptor or inbound hook** | Bulk-load failures discovered only at import time |
| Change modifies a **business process node id** | Strands in-flight process instances |
| Change modifies a **key generator format** | Breaks ERP/support correlation on existing identifiers |

### 7.3 Required Operational Actions (emit these as deployment steps)

| Trigger | Action |
|---|---|
| `items.xml` changed | System update with the affected extension selected; verify model regeneration; review DDL |
| Solr configuration or value provider changed | Import Solr ImpEx, then **full reindex** per affected store |
| CMS content or catalog ImpEx changed | Run **catalog synchronisation** (Staged → Online) for affected catalogs |
| Patch added | Confirm patch id/release ordering; run patches; verify execution state per environment |
| Essential-data ImpEx changed | Confirm behaviour on **every** system update, not just this release |
| Property changed | Confirm the key exists in every persona/aspect file that needs it; restart affected aspects |
| Integration object/destination changed | Coordinate with middleware/ERP; replay payload set; verify credentials rotated safely |
| OCC contract changed | Coordinate backend + frontend release; regenerate/publish API documentation |
| Extension set changed in `localextensions.xml` or `manifest.json` | Full build + full environment deploy; verify every declared context path is reachable |
| Job trigger changed | Verify schedule, `active` flag, and node group; verify behaviour at production volume |

---

## 8. Regression Recommendation Map

| Capability | Trigger artifacts | Regression scope |
|---|---|---|
| **Product discovery** | Solr config, value providers, search populators/decoders, assortment filter/service, product facade/populators, search components | PLP, search, facets, sorting, autocomplete, entitlement isolation across ≥2 organisations, per store/locale/currency |
| **Product detail** | Product facade/populators, variant populators, price strategy, stock, PDP components, product endpoint config | PDP for base and variant products, scaled/tiered prices, stock states, images, max-quantity, notify-me |
| **Cart** | Cart service/facade/factory, add-to-cart & update-entry strategies, calculation service, cart populators, cart components, cart endpoints | Add/update/remove, quantity limits, totals, multi-organisation, saved-cart interactions, mini-cart |
| **Checkout** | Delivery-address lookup strategies, delivery/shipping configuration, cart validation strategy, place-order validator, checkout components/route | Full checkout incl. all negative paths, address selection, delivery method availability by cutoff/day configuration, error messaging per locale |
| **Order placement → ERP** | Place-order hook, order service, key generator, order process XML and actions, payload creator, destination config, response processor, partner/entry contributors | End-to-end order to ERP; payment-authorisation branch; failure/notification branches; order code format; retry behaviour |
| **Order history & detail** | Order facade, ERP order services, order/entry/history populators, field-set mappings, order components and endpoints | List, filter, pagination, detail, reorder, invoice/statement download, order types |
| **Account & organisation** | Customer/B2B unit services and facades, customer populators, customer controller, address controller, sold-to components | Profile, address book, sold-to switching, organisation-unit switching, user creation/deactivation, role visibility |
| **Financial account summary** | Account summary service/DAO/facade/populators, account summary controller, ERP account endpoints, account summary components | Summary, filters, pagination, document types, statement PDF, currency handling |
| **Support ticketing** | Ticket service/DAO/facade, ticket populators, ticket controller, attachment service, ticket email strategy, ticket components | Create/list/read ticket, message posting, attachment upload/download, category handling, notification emails, retention/stagnation jobs |
| **Notifications & email** | Events/listeners, process XML, email contexts, `.vm` templates, email service | Every transactional email, per locale, with correct context variables and links |
| **Content & CMS** | CMS component types, content ImpEx, CMS OCC adapters, frontend CMS mappings, layout slots | Page rendering per template and market, SmartEdit authoring, catalog sync, localized content |
| **Master data inbound** | Integration objects, inbound channels, pre-persist hooks, interceptors, hot folders | Replay payload set per object (positive/negative), volume test, resulting storefront behaviour (price, stock, entitlement, access) |
| **Authentication & authorisation** | Identity extension, SSO config, security ImpEx, OCC role annotations, frontend guards/interceptors | Login per identity type and channel, role matrix, session handling, logout, token refresh, cross-organisation isolation |
| **Administration** | Backoffice config/widgets/security, role↔site data | Admin login, per-role/per-site visibility, custom actions, editing each customised type |

---

## 9. Heuristics for the Impact Engine

1. **Never analyse a file in isolation** — resolve its artifact type first, then apply the type's declared fan-out.
2. **Spring XML and ImpEx are code.** A release with no Java changes can still be CRITICAL.
3. **Look for missing halves.** Contract pairs that must move together:
   - backend field-set mapping ↔ frontend `fields=` list
   - OCC controller ↔ frontend adapter/endpoint config
   - `items.xml` attribute ↔ Solr indexed property ↔ integration object attribute
   - new CMS component type ↔ frontend `cmsComponents` mapping ↔ content ImpEx ↔ SmartEdit
   - new market ↔ phase data extension + security ImpEx + content catalog + identity locale properties + frontend site context
   - new validation error ↔ frontend message ↔ i18n key in every locale
4. **Per-store artifact families must be complete.** If a file exists per store (Solr, site, store, content catalog, shipping configuration) and only some were changed, report the omission.
5. **Localized file families must be complete.** If `x_en` changed but `x_de`, `x_fr`, … did not, report the omission.
6. **Escalate anything that reaches the order-to-cash path** (§5.3).
7. **Emit operational steps, not just risk.** Reindex, catalog sync, system update, patch run, aspect restart, and external coordination are the deliverables the release manager actually needs.
8. **Redact secrets.** Integration credentials, client secrets and keystore material appear in configuration ImpEx and property files. Flag their presence; never reproduce their values.
9. **Prefer the role over the name.** Strip client prefixes before reasoning so the same rules apply to any SAP Commerce customer.
10. **Absence of tests is a risk signal.** If a changed service/facade/controller has no corresponding test file in `testsrc`, raise the regression recommendation accordingly.

---

## 10. Glossary (for AI-generated explanations)

| Term | Plain-language meaning |
|---|---|
| Item type | A business object definition (and its database table) |
| Type system update | Applying item-type changes to the database schema |
| Facade | The layer that assembles what an API returns |
| Populator | Fills one part of an API response |
| Converter | The recipe that assembles a full API response object |
| Interceptor | A rule that runs every time a record is saved |
| Strategy | A swappable decision (e.g. how price is chosen) |
| Hook | An extension point in a standard platform operation |
| OCC | The headless REST API the storefront calls |
| Field-set level | How much detail an API response includes (BASIC/DEFAULT/FULL) |
| ImpEx | A data script that adds or changes records |
| Patch | A one-time, tracked data migration for a release |
| Catalog synchronisation | Publishing staged catalog/content changes live |
| Reindex | Rebuilding the search index so changes become searchable |
| Aspect | A group of servers running a specific role (API, admin, background) |
| Integration object | The agreed data shape exchanged with the ERP |
| Base site / base store | A market's storefront configuration and its commercial settings |
| B2B unit | A customer organisation in the account hierarchy |
| Assortment | The set of products a customer is entitled to buy |
| Business process | A long-running workflow (e.g. sending an order to the ERP) |

---

*Generated as an architecture and change-intelligence artifact. Contains no source code, business logic, algorithms, or credentials. Client-specific implementations are described by role and generalized for reuse across SAP Commerce customers.*