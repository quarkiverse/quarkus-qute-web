# Build-time image resolution for the Roq image plugin

Date: 2026-11-05 (updated after rebasing `roq-image-plugin` on `upstream/main` `d8770be1`)
Status: approved, implementation plan in `plan.md`
Repos: `quarkus-qute-web` (image extension, branch `qute-image`) and `quarkus-roq` (image plugin, frontmatter, branch `roq-image-plugin`)
POC: `ImagePoc.java` next to this file (JBang, run against a Roq checkout)

## Intent

Today the Roq image plugin converts most images at render time. The qute-image
scanner only pre-processes `{#img}` and `{img:url()}` usages whose `src` is a
string literal. In Roq the common usages are expressions (`page.image` in a
layout, `post.image` in a card tag), so they fall through to the
`quarkus-qute-web-image-dynamic` resolver, which converts on first render,
writes to a temp dir and feeds the generated paths back into the static site
generator.

Roq knows at build time everything the scanner lacks: every page's frontmatter,
attachments, URL, layout chain and collection. This design makes Roq evaluate
the image expressions at build time against that data, so every image a Roq
site needs is converted during the build, and the dynamic path is removed
from Roq entirely.

A throwaway POC (`/Users/adamevin/sandbox/tmp/roq-image-poc/ImagePoc.java`)
validated the approach on the Roq blog: both expression usages in the blog and
default theme resolve for all 35 posts that have an image, elvis operators and
virtual methods with literal arguments resolve, and runtime-only expressions
fail cleanly.

## Goals

- Every image referenced through a Roq page or site expression is converted at
  build time and served as a generated static resource.
- No image conversion happens at render time in a Roq site.
- Users need no new configuration for the common cases: `page.image`,
  `page.image('name')`, `site.image`, `post.image` in a tag, and expressions
  combining them with Qute operators.
- Failures are reported at build time with the template, the expression and a
  hint, in the style of existing `RoqException` errors.

## Non-goals

- Evaluating `{#for}` iterables or data-file driven images at build time. The
  existing `quarkus.qute.image.generate` globs remain the answer for those.
  This is a natural follow-up using the same mechanism.
- Rewriting markdown or AsciiDoc inline images (`![alt](photo.jpg)`) to use the
  image pipeline. Separate follow-up.
- Changing the generated file layout, preset model, or `<picture>` output.

The dynamic module has already been removed from `quarkus-qute-web` (commits
`c40596f` and `b793b1a` on `qute-image`); its sources live in `../dynamic`.

## Anti-goals

- A silent fallback. If a usage cannot be resolved, the build or the render
  must fail loudly. A bare `<img src="...">` rendered in place of a processed
  image is a bug, not a feature.
- A second expression language. All evaluation goes through Qute core.
- A qute-web SPI that knows about pages, layouts or collections.

## Constraints

- The runtime lookup is `Images.get(templateId, src, preset)` where `src` is
  the string value of the evaluated `src` expression. It falls back to the
  wildcard template id `*`. Build-time registration must therefore produce a
  `src` string byte-identical to what the runtime expression yields.
- `RoqUrl.toString()` returns the root-prefixed, URI-encoded path
  (`StringPaths.join(root.rootPath(), resourcePath())`, then encoded). The
  build-time model must return real `RoqUrl` values built with the real
  `RootUrl` so the strings match, including for file names with spaces or
  accents.
- Since upstream #1312, step 5 (`RoqFrontMatterStep5RecordProcessor`) builds
  a `SerializedPage` per published page, serializes the lists to classpath
  resources through `LargeRecords` / `LargeRecordBuildItem`, and the recorder
  rebuilds pages at startup with `SerializedPage.toPage()`. The
  `Supplier<List<NormalPage>>` and `Supplier<List<DocumentPage>>` held by the
  recorded build items are bytecode-recorder proxies and are not usable at
  build time. `SerializedPage.toPage()` is however plain Java, so the real
  `NormalPage` and `DocumentPage` objects can be built at build time from the
  exact same `SerializedPage` values, and `Site` and `RoqCollections` from
  them, which is what Part 2 does.
- After removing `PageImageCustomizer`, the only Arc dependency on the image
  path is `Page.site()`, which `Page.image()` uses to fall back to site
  images. `DocumentPage.collection()` and `Page.content()` also use Arc but
  are not needed for image expressions and fail cleanly if an expression
  touches them.
- Quarkus Qute resolves Vert.x `JsonObject` properties through
  `io.quarkus.qute.runtime.jsonobject.JsonObjectValueResolver` at runtime.
  A build-time engine must register the same resolver for `page.data.cover`
  style expressions to resolve.
- Qute's internal `ResolutionContextImpl` is package-private. Evaluation uses
  the public API: `engine.parse("{" + expr + "}").data(...).render()`.
- Roq's static site generator only copies paths that were selected
  (`SelectedPathBuildItem` or config). A `GeneratedStaticResourceBuildItem`
  alone is served by Vert.x but not written to the generated site. The removed
  dynamic listener used to enqueue each converted image for this reason, so
  build-time images need an equivalent (see 3e).
- Raw layout sources (`layouts/...`, `theme-layouts/...`) have their content
  filtered to empty during Qute's validation parse
  (`isLayoutSourceTemplate` in `RoqFrontMatterStep6BindProcessor`). A usage in
  a layout therefore appears exactly once in the analysis, under Roq's
  generated id `roq-templates/<layout path>`. Page usages appear under
  `roq-templates/content/<page path>`. Tags and partials keep their own Qute
  ids (`tags/roq/postCard`).
- Upstream restructured the themes: the post card tag now lives in
  `roq-theme/blog-common/src/main/resources/templates/tags/roq/postCard.html`
  (already switched to `{img:url(post.image, "roq-post-card")}` on the
  branch), the base layouts live under `roq-frontmatter` as
  `theme-layouts/roq-base/*`, and the default theme's `post.html` still holds
  the `{#img src=page.image preset='roq-hero' ...}` usage.
- The generator now serializes its selection and static files through
  `LargeRecordBuildItem` (#1340), but its input is still
  `SelectedPathBuildItem` plus `GeneratedStaticResourceBuildItem`, so 3e is
  unchanged. The branch currently carries the queue-based generator changes
  (`ConfiguredPathsProvider.enqueuePath`, `addStaticFile`, the drain loop in
  `RoqGenerator`) from the dynamic design; they are removed in Part 3.
- In dev mode, Qute template edits trigger a restart by default
  (`restartNeeded = true` unless `quarkus.qute.dev-mode.no-restart-templates`
  matches), and Roq watches its content and template dirs with restart. Build
  steps re-run on every edit, so the build-time index is never stale.

## Design

### Part 1: qute-web image (generic contract)

Two new build items in `quarkus-qute-web-image-deployment`, plus two small
behaviour changes. Nothing in qute-web learns about Roq.

#### 1a. `QuteImageExpressionBuildItem` (scanner output)

Produced by `QuteImageScanProcessor` for every `{#img}` section and
`{img:url()}` / `{img:tag()}` expression whose `src` is **not** a literal.
Today these are silently dropped (`return null`).

Fields:

- `templateId`: the Qute template id the usage lives in.
- `srcExpression`: `Expression.toOriginalString()` of the `src` expression.
- `presetExpression`: `toOriginalString()` of the preset expression, or the
  literal `"default"` when absent. Hosts evaluate it the same way as `src`;
  a literal preset is simply a literal expression.
- `rootTypeInfo`: `getTypeInfo()` of the first part of the `src` expression,
  may be null. Informational, for hosts that want to narrow contexts by type.
- `origin`: a short description for error messages (`{#img}` or
  `{img:url(...)}` plus line, if available from `Origin`).

Literal usages keep flowing through the existing `ImageRef` path unchanged.

#### 1b. `QuteImageToProcessBuildItem` (processor input)

A `MultiBuildItem` any extension can produce to request a conversion:

- `declaredPath`: the exact `src` string the runtime lookup will use.
- `presetName`: resolved against `quarkus.qute.image.presets` by the
  processor; unknown names fail the build with the existing message.
- `source`: either a `Path` or a `byte[]` plus a name, matching what
  `ResolvedSourceImage` needs.

`QuteImageProcessor.processTemplatesWithImages` consumes the list and calls
the existing static `processImage` with a null `templateId`, so the tag is
registered under the wildcard key and the image is deduplicated by digest in
`ImagesBuilder`. The glob feature (`quarkus.qute.image.generate`) is
functionally a built-in producer of this item; refactoring it onto the item is
optional and left to the builder.

#### 1c. Strict lookup

New config `quarkus.qute.image.strict` (build and runtime fixed, default
`false`). When true, a lookup miss in `ImageSectionHelperFactory` and
`ImageNamespaceResolver` throws a `TemplateException` naming the template,
`src` and preset instead of rendering `qute-image/fallback-image.html`. Roq
sets it to `true` through its runtime `application.properties`.

#### 1d. Remote URL pass-through

A `src` starting with `http://` or `https://` is never looked up or
converted. The tag renders a plain `<img>` with the given attributes and the
namespace `url` returns the value untouched. This applies regardless of
strict mode and is the only case where `fallback-image.html` is used when
strict is on. The scanner also skips literal `http(s)` sources.

#### 1e. Configurable output URL path

`ImageUtils.computeOutputPath` hardcodes `/static/images/generated`. New
config `quarkus.qute.image.output-path` (build time, default
`/static/images/generated`) replaces it. The value is the URL prefix under
which generated variants are served and selected for generation; the
`{digest}/{basename}-{width}-{hash}.{ext}` layout below it is unchanged. The
dynamic module's route and resolver use the same config so the two modules
stay consistent for non-Roq users.

#### 1f. Configurable build directory, persisted across builds

`QuteImageScanProcessor.initTargetDir` writes to
`<build output>/qute-image/<profile>` and deletes it on every build. New
config `quarkus.qute.image.build-dir` (build time, default `qute-image`)
names where generated files are stored. A relative value resolves against
`OutputTargetBuildItem.getOutputDirectory()` (the Maven `target` dir), so the
default stays inside `target` and `mvn clean` removes it. An absolute value
allows a shared or CI-cached directory. The per-profile subdirectory is
dropped: the content addressing below makes sharing between dev, test and
prod builds correct and desirable.

The directory is created if missing and never wiped by the processor.

#### 1g. Skip conversion when the output already exists

Before calling the converter, the processor checks whether the target file
exists in the build dir and, if so, only produces the
`GeneratedStaticResourceBuildItem` for it. The dynamic resolver already does
this; the build-time processor gains the same check.

This is correct without a separate hash table because every input that
changes the output bytes is already part of the path:

- source bytes: the `{digest}` directory (`ImageUtils.digest(contents)`);
- width and format: in the file name and extension;
- crop ratio, crop position and quality: in the `{hash}` segment
  (`GeneratedImageOptions.settings()`).

Two additions make the key complete:

- the converter identity (`ImageIO` or `vips`, plus a converter version
  string if available) is appended to `settings()`, so switching converters
  regenerates instead of serving files produced by the other one;
- the source digest stays at 4 bytes of SHA-1 (8 hex chars). A collision only
  matters between two images with the same file name, since the digest is the
  directory and the file name carries the base name: one chance in four
  billion per such pair, which is negligible (decided during implementation,
  the draft asked for 8 bytes).

A manifest or hash table is **not** introduced. It would only serve to prune
stale files, and `mvn clean` already covers that for the default location.
A user pointing `build-dir` at a persistent cache accepts that stale files
accumulate there. Pruning can be a follow-up if it proves needed.

#### 1h. Rename `img:url` to `img:path`

The namespace method returns the generated image path (root-relative, no
scheme or host), so it is renamed `img:path` in `ImageNamespaceResolver`,
the scanner's origin label, the tests and `image.adoc`. `img:tag` is
unchanged. Roq follows in `postCard.html` and the blog's plugin page.

Roq keeps `img:url` with a different meaning: it returns a `RoqUrl` built
from the generated path (so `.absolute` and `.relative` work and the HTTP
root path is handled once, by `RoqUrl`). It is a `NamespaceResolver` on the
`img` namespace in the image plugin's runtime module, registered with a
lower priority than qute-web's resolver; Qute falls through to the next
resolver on `Results.NotFound`, so `path` and `tag` are answered by
qute-web and `url` by Roq. It performs the same `Images` lookup as
`img:path` (strict mode applies, remote sources pass through as an external
`RoqUrl`). Without the plugin, `RoqImageFallbackNamespaceResolver` answers
`url` with the original's `RoqUrl` unchanged. The scanner treats any `img:`
method as a usage, so `img:url(expr, preset)` is evaluated at build time
like the others.

#### 1i. Vector and unconvertible sources pass through, blank sources render nothing

Two kinds of source are registered with the original only (no generated
variants):

- Vector sources, decided by the declared path's extension (`svg`), with
  every converter and in every mode, and without a warning. This is by
  design: a vector stays a vector, rasterizing it would make it larger and
  blurrier. The converter is not consulted for them (vips can read SVG, so
  without this rule the behaviour would depend on the installed converter).
- Raster sources the converter cannot read (`UnsupportedFormatException`
  from `readInfo`: WebP with ImageIO only, exotic formats). The processor
  logs one warning naming the path and the converter, and suggesting the
  vips module.

At render time such a tag renders the original path as `src`, with no
`srcset`, `<picture>`, `width` or `height`; `img:path` returns the original
path. Hosts that unpublish originals must leave these published. A `src`
that evaluates to null or blank renders an empty string in both modes.

Consequence for Roq's SEO tag: a page whose `image` is an SVG gets the SVG
as `og:image`, which most social networks do not render. This is documented
(use a raster page image for the card), not worked around.

### Part 2: roq-frontmatter (build-time site model)

No parallel model and no extracted helper: the build-time evaluation uses the
real `NormalPage`, `DocumentPage` and `Site` classes, so the `image()` logic
and the resulting `RoqUrl` are the same objects at build time and at
runtime by construction. roq-frontmatter owns their construction in both
worlds: the recorder for runtime, a build item for build-time consumers.

Runtime module:

- `PageImageCustomizer` and its lookups in `Page.image(Object)` and
  `Site.image(Object)` are removed. They existed only to feed raw names to
  the dynamic resolver, and they never handled page-local originals anyway.
  Image originals are ordinary attachments: a file next to a page or under
  `public/images/` is scanned, listed in `page.files()` and resolved by the
  upstream `Page.image()` / `Site.image()` rules unchanged. The `_` prefix
  convention and `content/_images/` are not used by the image plugin.
- `Page` gets an explicit site binding: `public void bindSite(Site site)` and
  `Page.site()` returns the bound site when set, otherwise the Arc lookup as
  today. `DocumentPage.collection()` uses `site().collections()` instead of
  its own Arc lookup. The binding is documented as intended for build-time
  evaluation and tests.

Deployment module. The record step already turns every published page into
a `SerializedPage` before recording it; those lists are exposed on the
existing recorded build items instead of being rebuilt:

- `RoqFrontMatterRecordedNormalPagesBuildItem` gains
  `List<SerializedPage> serialized()`, filled by `bindPages` from the list it
  already serializes.
- `RoqFrontMatterRecordedCollectionBuildItem` gains
  `List<SerializedPage> serialized()`. `bindCollections` fills it from the
  list it serializes per collection; for a derived collection it collects the
  serialized pages of the source documents by id (it already validates every
  id against the published documents).

A new `SimpleBuildItem` `RoqFrontMatterSiteModelBuildItem(Site site,
List<NormalPage> pages, Map<String, RoqCollection> collections)` is produced
by a new non-recording step `buildTimeSiteModel` in
`RoqFrontMatterStep5RecordProcessor`, consuming `RoqFrontMatterRootUrlBuildItem`,
`RoqSiteConfig`, the recorded normal pages item and the recorded collection
items. It returns null when there is no site index (same condition under
which `bindSite` produces no site bean). Otherwise it:

- calls `toPage()` once per page id, so a document shared by its source and
  a derived collection is one instance, as `createDerivedDocuments` does at
  runtime;
- builds `RoqCollection(collection, documents)` per recorded collection item,
  `RoqCollections(Map.copyOf(...))`, and `Site(indexPage.url(),
  config.imagesPath(), indexPage.data(), pages, collections)` exactly as
  `createRoqCollections` and `createSite` do;
- calls `bindSite(site)` on every page and document.

Going through `SerializedPage` guarantees the build-time objects are built
from the same values the runtime deserializes, and nothing is recorded
twice: the item is a plain build-time object that is never serialized. Any
plugin needing page data at build time consumes this item.

Unpublishing originals. A new `MultiBuildItem`
`RoqFrontMatterUnpublishedFileBuildItem(String resourcePath)` lets any
extension exclude a scanned static file from the output. `bindStaticFiles`
in `RoqFrontMatterStep6BindProcessor` skips every
`RoqFrontMatterStaticFileBuildItem` whose `link()` is listed, so it produces
neither the `GeneratedStaticResourceBuildItem` nor the `RoqPathBuildItem`:
the file is not served in dev and not generated. The file stays in
`page.files()` and `page.image()` still resolves it, because the URL is the
lookup key for its generated variants. There is no build cycle: the image
plugin's steps depend on the Qute analysis and the step-5 model, never on
step 6's static resources.

`tags/seoImage.html` switches from `{page.image.absolute}` to the image
pipeline, `{img:url(page.image, 'roq-og').absolute}` (the root path must
appear exactly once). Without the image plugin the
`RoqImageFallbackNamespaceResolver` returns the original's `RoqUrl`, so the
tag keeps working for sites that do not use the plugin.

### Part 3: roq-plugin-image

#### 3a. Dependencies, beans and the generator queue

- Remove the dependency on `quarkus-qute-web-image-dynamic` (runtime and
  deployment) and on `quarkus-qute-web-image-dynamic-deployment`.
- Delete `RoqRuntimeImageListener` and `RoqImageCustomizer`.
- Revert the queue-based generator changes carried by the branch:
  `ConfiguredPathsProvider` goes back to `setStaticFiles` with a plain map,
  `enqueuePath`, `enqueueAll` and `pathQueue` are removed, and `RoqGenerator`
  goes back to upstream's single pass over the build selection.
- `registerImageDirs` registers only `public/` and `static/` (local dir and
  classpath), with a root URL prefix so a literal `/images/x.jpg` resolves
  to `public/images/x.jpg`. The `content/_images/` registration is dropped.
- New config `quarkus.roq.plugin.image.publish-originals` (build time,
  boolean, default `false`). When false, every original converted at build
  time is unpublished (see 3e). When true, originals are published as today.
- Add `quarkus.qute.image.strict=true` and a `roq-og` preset (single width
  of 1200, jpg only, crop ratio 1.91:1 keeping the center, quality 80) to
  the plugin's `application.properties`. No `og:image:width`/`height` metas.

#### 3b. Build-time model: the real runtime objects

The plugin consumes `RoqFrontMatterSiteModelBuildItem` from Part 2. It holds
the real pages and site built from the serialized published pages, so
drafts, future posts and otherwise unpublished pages are never evaluated and
never emit images into the site output. Because these are the runtime
classes, `page.image()` returns the same `RoqUrl` the runtime returns, and
its `toString()` is byte-identical.

The engine registers Qute's `JsonObjectValueResolver` next to the
`ReflectionValueResolver` so `page.data.cover` resolves as it does at
runtime. Roq's generated `@TemplateExtension` methods are not available to
a plain engine; an expression relying on one fails evaluation, which is
reported under 3d.

#### 3c. Evaluation step

One build step in `RoqPluginImageProcessor`:

Inputs: `List<QuteImageExpressionBuildItem>`,
`RoqFrontMatterSiteModelBuildItem` (pages, documents, site),
`List<RoqFrontMatterPageTemplateBuildItem>` (each page's resolved layout id via
`raw().layout()`, matched to the model pages by `source().id()`),
`List<RoqFrontMatterLayoutTemplateBuildItem>` (layout parent chain via
`raw().id()` and `raw().layout()`, Qute template id via
`raw().templateSource().generatedQuteTemplateId()`),
`RoqSiteConfig`, `RoqProjectBuildItem`.
Output: `QuteImageToProcessBuildItem` per unique (`declaredPath`, preset).

Build order is acyclic: Roq steps 1 to 6 produce the page items, the site
model and the `TemplatePathBuildItem`s; Qute analyses them; the qute-image
scan step emits expression items; this step consumes them together with the
site model; `QuteImageProcessor.processTemplatesWithImages` consumes the
output.

Engine: `Engine.builder().addDefaults().addValueResolver(new
ReflectionValueResolver()).addValueResolver(new JsonObjectValueResolver())
.strictRendering(true).timeout(...)`, built once per build.

Context selection per expression item, by template id:

| template id | contexts |
|---|---|
| `roq-templates/content/...` (a page template) | that page only |
| `roq-templates/...` (a layout) | every page whose layout chain includes it |
| anything else (tags, partials, includes) | root variable `page` or `site`: all pages; any other name (`post`, `doc`, ...): all collection documents |

The layout chain is computed from `RoqFrontMatterPageTemplateBuildItem.raw().layout()`
and `RoqFrontMatterLayoutTemplateBuildItem.raw().layout()` using the same
local-first resolution as the frontmatter pipeline (`RoqFrontMatterLayoutUtils`).

For each context: bind the root variable name, `page` and `site`; render
`{srcExpression}` and `{presetExpression}`; on `TemplateException` record the
context as unresolved; on an empty result record "no image" (the runtime
`{#if}` guard skips it); otherwise map the string back to its file through
`List<RoqFrontMatterStaticFileBuildItem>`, indexed by
`RoqUrl.fromRoot(rootUrl, link()).toString()`, which covers page attachments
and `public/` files alike; a string with no match fails under 3d; produce the
item with the file's `Path` or bytes.

Deduplicate on (`declaredPath`, preset) before producing.

#### 3d. Failure rules

Build fails with a `RoqException` (template id, expression, preset, hint)
when:

- an expression item resolves in **zero** contexts and all contexts failed
  evaluation (nothing could be resolved and it is not a "no image" case);
- a resolved `src` points to a file that does not exist (`Page.image` already
  throws `RoqStaticFileException` with page source info).

A build-time `INFO` log summarises counts: usages, contexts, images queued,
contexts with no image. A `DEBUG` log lists each evaluation.

At render time, strict mode turns any lookup miss into a `TemplateException`.
This should only happen if context selection guessed wrong for a specific
page, which is a plugin bug to fix, not a user error.

#### 3e. Selecting generated images for the generator

A second build step consumes qute-image's `ImagesBuildItem` (a
`SimpleBuildItem` holding every image and its generated variants, including
those from literal usages and globs) and produces one `SelectedPathBuildItem`
per `GeneratedImage.outputPath()`. This is what makes the generator copy the
converted files into the site output. It replaces the `enqueuePath` call of
the removed runtime listener and also fixes the current situation where
build-time images from literal usages are served in dev mode but are not
selected for generation.

The same step unpublishes originals when `publish-originals` is false.
`ImagesBuilder` exposes the source identities it indexed (the absolute path
or classpath index path given to `getImageId`, and the content digest). For
each `RoqFrontMatterStaticFileBuildItem`, a local file is matched by absolute
path and a classpath file by digest of its bytes; each match produces a
`RoqFrontMatterUnpublishedFileBuildItem(link())`. Literal and expression
usages are covered by this one mechanism. Images that were passed through
without variants (1i) are never unpublished, since their original is what
the tag renders.

### Lookup key contract (the one thing that must not drift)

```
build time:  QuteImageToProcessBuildItem.declaredPath
          == page.image(...).toString() on a build-time NormalPage/DocumentPage
runtime:     Images.get("*", srcObj.toString(), preset)
          where srcObj is the runtime Page.image(...) RoqUrl
```

Both sides run the same `Page.image` code on the same classes with the same
`RootUrl`, so the only way to break this is to construct the build-time
objects with different arguments than the recorder. The integration test
below guards it.

## Testing expectations

- `roq-plugin/image/integration-tests` keeps all existing assertions and
  adds: a frontmatter image attached to the page dir (`_image.jpg`), a page
  using a theme layout with `page.image`, a listing using a tag with
  `post.image`, a `page.data.cover ?: page.image` expression, and a remote
  `https://` image. Assertions check the rendered `srcset` paths exist under
  the build output **before** any HTTP request, and that no request produces
  a bare fallback `<img>`.
- The `roq-and-roll` test additionally asserts that every
  `/static/images/generated/...` path referenced by a rendered page exists as
  a file in the generated site output directory, not only that the running
  app serves it.
- A negative test: an expression over a runtime-only namespace fails the
  build with the expected message.
- A draft page with a frontmatter image: its image must not appear in the
  generated output.
- Originals: with the default config a converted original returns 404 and is
  absent from the generated site, while an unreferenced file in the same
  page directory is still served; with `publish-originals=true` the original
  is served. The `og:image` meta of a page with an image points to a
  generated variant and is an absolute URL with the root path once.
- `blog/` builds and generates with no render-time failure (strict mode on).
- qute-web image deployment tests cover the two build items and strict mode in
  isolation, with a minimal host producing `QuteImageToProcessBuildItem`.

## Decisions already made (by the user)

- Dynamic conversion is removed from Roq; unresolvable usages fail.
- The generated image URL path and the build directory are configurable, and
  generated files are kept across builds with an exists-check before
  converting.
- Evaluation and context selection live in the Roq plugin; qute-web only
  exposes the two build items.
- Originals are ordinary attachments; whether they are published is a
  plugin config (`publish-originals`, default false) implemented through a
  frontmatter build item, not a `_` naming convention.
- `seoImage.html` goes through `img:url(page.image, 'roq-og').absolute`.
- Qute core is used for evaluation, not a hand-written resolver.

## Calls made in this document (review these)

- Preset expressions are evaluated like `src` rather than requiring a literal.
- Remote `http(s)` URLs pass through everywhere, including strict mode.
- `PageImageCustomizer` is removed rather than kept dormant.
- Strict mode is a qute-web config defaulting to false; Roq turns it on.
- Zero resolved contexts fails the build; partial resolution does not.
- Config names `quarkus.qute.image.output-path` and
  `quarkus.qute.image.build-dir`, with the build dir relative to the Maven
  output dir by default and the per-profile subdirectory dropped.
- No manifest or hash table: the content-addressed path is the cache key,
  with the converter identity added to the settings hash and a longer source
  digest.
- Only published pages are evaluated, so unpublished drafts never leak
  converted variants into the output.
- Build-time pages come from the `SerializedPage` lists the record step
  already builds, exposed as accessors on the recorded build items, rather
  than from re-reading the step-4 publish items.
- Generated image paths are selected for the generator by the Roq plugin
  from `ImagesBuildItem`, rather than by making the generator copy every
  `GeneratedStaticResourceBuildItem`.

## Left to the builder

- Exact class and package names, and whether the glob feature is refactored
  onto `QuteImageToProcessBuildItem`.
- Engine timeout value and whether the engine is shared across build steps.
- How layout chain resolution is shared with `RoqFrontMatterLayoutUtils`
  (reuse or a small duplicate in the plugin).
- Whether the build-time `INFO` summary is one line or one line per preset.

## Follow-ups (out of scope)

- Evaluate `{#for}` iterables at build time so data-driven images need no
  glob config. The Qute analysis exposes the section chain and the iterable
  expression; Roq data files are build-time known.
- Rewrite markdown and AsciiDoc inline images into the image pipeline.
- A preset option to rasterize vector sources (for OG cards built from an
  SVG page image). Vectors pass through today by design (1i).
- Use `rootTypeInfo` to narrow tag contexts by declared type.
- qute-web renders generated paths without the HTTP root path (`imgSrc`
  returns `/static/images/generated/...`), so `{#img}` output is wrong under
  a non-root `quarkus.http.root-path`. Roq's `img:url` sidesteps it, but
  qute-web should prefix the root path itself.
