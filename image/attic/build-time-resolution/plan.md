# Build-time image resolution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every image referenced through a Roq page or site expression is converted at build time; the Roq image plugin no longer converts anything at render time, and originals are unpublished by a build item instead of a `_` naming convention.

**Architecture:** qute-web's image extension gains two generic build items (expression usages out, images to process in), strict lookups, remote pass-through, configurable output path and persistent build dir, and renames `img:url` to `img:path`. roq-frontmatter exposes a build-time site model built from the pages it already serializes, a `bindSite` hook on `Page`, and an unpublish build item honoured by the static file step. The Roq image plugin evaluates the expressions with Qute core against the real pages, feeds the resolved files to qute-web, selects the generated paths for the generator and unpublishes converted originals.

**Tech Stack:** Quarkus build steps and recorders, Qute core (`Engine`, `ReflectionValueResolver`, `JsonObjectValueResolver`), QuarkusUnitTest with build chain customizers, QuarkusTest with `@RoqAndRoll`, Maven.

**Spec:** `design.md` next to this file (`image/attic/build-time-resolution/design.md` on `quarkus-qute-web`, branch `qute-image`).

Repos and shorthands used below:

- `$QW` = `/Users/adamevin/workspace/redhat/quarkiverse/quarkus-qute-web` (branch `qute-image`). Builds install into `~/.m2`.
- `$ROQ` = `/Users/adamevin/workspace/roq-features/pr-1001-roq-image/quarkus-roq` (branch `roq-image-plugin`). Maven reads `.mvn/maven.config` (feature `.m2` with `~/.m2` as tail), so qute-web snapshots installed in `$QW` are visible.
- Java packages: qute-web deployment `io.quarkiverse.qute.web.image.deployment` (items under `.items`), qute-web runtime `io.quarkiverse.qute.web.image.runtime`; roq-frontmatter deployment `io.quarkiverse.roq.frontmatter.deployment` (items under `.items.<step>`), runtime model `io.quarkiverse.roq.frontmatter.runtime.model`; plugin `io.quarkiverse.roq.plugin.image.{runtime,deployment}`.

## Global Constraints

- Lookup key contract: `QuteImageToProcessBuildItem.declaredPath` must equal `page.image(...).toString()` of the build-time page, which must equal the runtime `RoqUrl.toString()` (root-prefixed, URI-encoded). Tags registered by the plugin use the wildcard template id (`templateId == null`).
- Config names, exact: `quarkus.qute.image.strict` (default `false`), `quarkus.qute.image.output-path` (default `/static/images/generated`), `quarkus.qute.image.build-dir` (default `qute-image`, relative to the build output directory), `quarkus.roq.plugin.image.publish-originals` (default `false`).
- Namespace method rename in qute-web: `img:url` becomes `img:path`; `img:tag` unchanged; the preset option `direct-url` is unrelated and unchanged. Roq defines its own `img:url` returning a `RoqUrl`, in the image plugin runtime, with the frontmatter fallback answering it without the plugin.
- Source digest stays 4 bytes of SHA-1 (8 hex chars); the settings hash includes the converter id.
- No em dashes anywhere (code comments, docs, commit messages). No `Co-Authored-By` lines. One commit per task, in the repo the task touches.
- Both repos run a formatter during `install`; run the module build before committing so formatting is applied.
- Dev mode and tests: never run `mvn clean` while a dev server runs.

## Review Focus

1. Attachment names with spaces or accents (`Foé photo.jpg`): the registered key must be the encoded URL exactly as the runtime renders it. Pinned in Task 14 (`testEncodedAttachmentName`).
2. A non-root `quarkus.http.root-path`: the `og:image` absolute URL from `img:url(...).absolute` must contain the root path exactly once, with and without the plugin. Pinned in Task 12 (`testOgImageAbsoluteWithRootPath`) and Task 14 (`testImgUrlAbsoluteWithRootPath`).
3. The same source file referenced by two presets and from two pages: one conversion per variant, both tags registered. Pinned in Task 14 (`testSharedImageAcrossPresets`).
4. A layout containing an image expression but used by no page: must not fail the build (zero contexts is not "all contexts failed"). Pinned in Task 14 (`unused-layout` in the integration site).
5. A persistent build dir reused with a changed preset quality must regenerate instead of serving the old file. Pinned in Task 6 (`GeneratedImageOptionsTest.testQualityChangesSettings`).
6. A page image the converter cannot read (SVG, or WebP without vips) must render as the original and stay published, since `seoImage.html` now sends every page image through the pipeline. Pinned in Task 6b (`testVectorSourcePassesThrough`) and Task 15 (`testUnconvertedOriginalStaysPublished`).
7. An image tag whose `src` evaluates to null or blank (an unguarded `{#img src=page.image/}` on a page without an image) must render nothing, not a broken `<img src="">` and not a strict-mode error. Pinned in Task 4 (`testBlankSrcRendersNothing`).

---

## Part A: quarkus-qute-web (`$QW`, branch `qute-image`)

Build and test commands for this part:

```bash
cd $QW && mvn -q install -DskipTests -f image/runtime/pom.xml      # after runtime changes
cd $QW && mvn -q test -f image/deployment/pom.xml -Dtest=<TestClass>
cd $QW && mvn -q install -DskipTests -f image/pom.xml               # whole image tree
```

### Task 1: Rename `img:url` to `img:path`

**Files:**
- Modify: `image/runtime/src/main/java/io/quarkiverse/qute/web/image/runtime/ImageNamespaceResolver.java` (`case "url"` at line 54)
- Modify: `image/deployment/src/test/resources/web/templates/pub/images.html` (`#ns-url` div)
- Modify: `image/deployment/src/test/java/io/quarkiverse/qute/web/image/deployment/QuteImageTest.java` (`testNamespaceUrl`)
- Modify: `docs/modules/ROOT/pages/image.adoc` (every `img:url`)

**Interfaces:**
- Produces: `{img:path(src, preset)}` returning the generated fallback path string; `img:url` is no longer resolved (returns `Results.NotFound`).

- [ ] **Step 1: Change the test template and test**

In `pub/images.html` replace `{img:url('/static/images/white_1920_1080.png', 'small')}` with `{img:path('/static/images/white_1920_1080.png', 'small')}`. Rename `testNamespaceUrl` to `testNamespacePath`; assertions unchanged.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -f image/deployment/pom.xml -Dtest=QuteImageTest#testNamespacePath`
Expected: FAIL (the `#ns-url` text does not contain `/static/images/generated/`).

- [ ] **Step 3: Rename the case in `ImageNamespaceResolver.resolve`**

`case "url"` becomes `case "path"`; rename `resolveUrl` to `resolvePath`. Update `image.adoc` occurrences.

- [ ] **Step 4: Install runtime, run the whole test class**

Run: `mvn -q install -DskipTests -f image/runtime/pom.xml && mvn -q test -f image/deployment/pom.xml -Dtest=QuteImageTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A image docs && git commit -m "Rename img:url to img:path"
```

### Task 2: `QuteImageExpressionBuildItem` from the scanner

**Files:**
- Create: `image/deployment/src/main/java/io/quarkiverse/qute/web/image/deployment/items/QuteImageExpressionBuildItem.java`
- Modify: `image/deployment/src/main/java/io/quarkiverse/qute/web/image/deployment/QuteImageScanProcessor.java` (`scanRuntimeQuteTemplates`, `imageTagSectionMapper`, `imageNamespaceExprMapper`)
- Create: `image/deployment/src/test/resources/web/templates/pub/expressions.html`
- Create: `image/deployment/src/test/java/io/quarkiverse/qute/web/image/deployment/QuteImageExpressionScanTest.java`

**Interfaces:**
- Produces: `public final class QuteImageExpressionBuildItem extends MultiBuildItem` with constructor `(String templateId, String srcExpression, String presetExpression, String rootTypeInfo, String origin)` and matching accessors. `presetExpression` is `"'default'"` (a Qute string literal) when the usage has no preset. `origin` is `"{#img} at line N"` or `"{img:path(...)} at line N"` using `Origin.getLine()`.

- [ ] **Step 1: Write the failing test**

Template `pub/expressions.html`:

```
{#img src=item.cover preset='small' alt='Cover'/}
{img:path(item.cover ?: item.fallback, item.preset)}
{#img 'https://example.com/remote.jpg' alt='Remote'/}
```

Test class pattern: copy the `Customiser`/`MyBuildStep` structure of `QuteImageTest` but the build step consumes instead of produces: `buildChainBuilder.addBuildStep(step).consumes(QuteImageExpressionBuildItem.class).produces(ReportBuildItem...)` is not needed; simply `.consumes(QuteImageExpressionBuildItem.class).build()` and in `execute` call `context.consumeMulti(QuteImageExpressionBuildItem.class)` and write one line per item as `templateId|srcExpression|presetExpression|rootTypeInfo` to `target/qute-image-expressions.txt` (the test JVM and the augmentation classloader do not share statics). Add `expressions.html` by `.addAsResource("web")` as the existing tests do.

Assertions (`testExpressionsAreReported`): file has exactly 2 lines; line 1 is `pub/expressions|item.cover|'small'|...`; line 2 is `pub/expressions|item.cover ?: item.fallback|item.preset|...`; no line mentions `example.com`. Check the exact `templateId` the Qute analysis gives this template by printing it on the first run; it is the analysis id (`pub/expressions`) and the test pins it.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -f image/deployment/pom.xml -Dtest=QuteImageExpressionScanTest`
Expected: FAIL, class `QuteImageExpressionBuildItem` not found.

- [ ] **Step 3: Implement the build item and the scanner change**

In `scanRuntimeQuteTemplates` add `BuildProducer<QuteImageExpressionBuildItem> expressionProducer`. Change both mappers to take the producer: when `src` is not a literal, produce `new QuteImageExpressionBuildItem(templateId, srcExpr.toOriginalString(), presetExpr == null ? "'default'" : presetExpr.toOriginalString(), rootTypeInfo, origin)` and return null; when `src` is a string literal starting with `http://` or `https://`, return null without producing anything. `rootTypeInfo` is `srcExpr.getParts().get(0).getTypeInfo()` (may be null).

- [ ] **Step 4: Run the new test and the existing class**

Run: `mvn -q test -f image/deployment/pom.xml -Dtest='QuteImageExpressionScanTest,QuteImageTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A image && git commit -m "Report non-literal image usages as QuteImageExpressionBuildItem"
```

### Task 3: `QuteImageToProcessBuildItem` consumed by the processor

**Files:**
- Create: `image/deployment/src/main/java/io/quarkiverse/qute/web/image/deployment/items/QuteImageToProcessBuildItem.java`
- Modify: `image/deployment/src/main/java/io/quarkiverse/qute/web/image/deployment/QuteImageProcessor.java` (`processTemplatesWithImages`, `processGlobPatterns`)
- Modify: `image/deployment/src/main/java/io/quarkiverse/qute/web/image/deployment/items/model/ImagesBuilder.java`
- Create: `image/deployment/src/test/java/io/quarkiverse/qute/web/image/deployment/QuteImageToProcessTest.java`

**Interfaces:**
- Produces: `public final class QuteImageToProcessBuildItem extends MultiBuildItem` with static factories `of(String declaredPath, String presetName, Path file)` and `of(String declaredPath, String presetName, String fileName, byte[] contents)`; accessors `declaredPath()`, `presetName()`, `file()` (nullable), `fileName()`, `contents()` (loads the file lazily when `file()` is set).
- Produces: `ImagesBuilder.imageIdsByPath()` returning an unmodifiable `Map<String, ImageId>` (the `path` argument given to `getImageId`: absolute local path, classpath index path, or `bytes:<declaredPath>`). `toResolvedImage` keys a local `ProjectFile` by `file.file().toAbsolutePath().toString()` instead of `indexPath()` so hosts can match by path (`Images.image(String)` has no runtime caller, so the recorded key change is safe).
- Produces: `static PresetConfig resolvePreset(ImageConfig config, String presetName, String context)` in `QuteImageProcessor`, shared by the glob loop and the new loop, throwing the existing "Unknown preset" message.

- [ ] **Step 1: Write the failing test**

`QuteImageToProcessTest`: same scaffolding as `QuteImageTest` (endpoint with `/rest/dynamic?src=`, `dynamic.html` uses preset `small`). The build step produces `QuteImageToProcessBuildItem.of("/virtual/hero.png", "small", Path.of("target/test-classes/roq-public/Foé_1920_1081.png"))` and `QuteImageToProcessBuildItem.of("/virtual/bytes.png", "small", "bytes.png", <bytes of web/static/images/white_1920_1080.png>)`.

```java
@Test
public void testFileSourceIsProcessed() {
    Document doc = Jsoup.parse(RestAssured.get("/rest/dynamic?src=/virtual/hero.png").then().statusCode(200).extract().asString());
    String srcset = doc.select("img").first().attr("srcset");
    assertThat(srcset, containsString("320w"));
    assertThat(srcset, containsString("640w"));
    assertThat(doc.select("img").first().attr("src"), containsString("/static/images/generated/"));
}
@Test
public void testBytesSourceIsProcessed() { /* same for /virtual/bytes.png */ }
@Test
public void testUnknownPresetFails() /* separate test class with .assertException(t -> assertThat(t.getMessage(), containsString("Unknown preset 'nope'"))) producing of("/x.png","nope",...) */
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -f image/deployment/pom.xml -Dtest=QuteImageToProcessTest`
Expected: FAIL, class not found.

- [ ] **Step 3: Implement**

`processTemplatesWithImages` gains `List<QuteImageToProcessBuildItem> toProcess`; the early return triggers only when templates, globs and `toProcess` are all empty. For each item: `PresetConfig preset = resolvePreset(imageConfig, item.presetName(), "QuteImageToProcessBuildItem " + item.declaredPath())`; `String pathKey = item.file() != null ? item.file().toAbsolutePath().toString() : "bytes:" + item.declaredPath()`; `ImageId id = images.builder().getImageId(pathKey, item.fileName(), item.contents())`; `processImage(staticResourceProducer, converter, images, null, item.declaredPath(), item.presetName(), preset, new ResolvedSourceImage(item.declaredPath(), id, item.contents()), targetDir.path, imageConfig.slugifyOutput())`. Refactor the glob loop onto `resolvePreset`.

- [ ] **Step 4: Run tests**

Run: `mvn -q test -f image/deployment/pom.xml -Dtest='QuteImageToProcessTest,QuteImageTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A image && git commit -m "Accept images to process from other extensions"
```

### Task 4: Strict lookups and remote URL pass-through

**Files:**
- Modify: `image/runtime/src/main/java/io/quarkiverse/qute/web/image/runtime/ImageConfig.java`
- Modify: `image/runtime/src/main/java/io/quarkiverse/qute/web/image/runtime/ImageSectionHelperFactory.java`
- Modify: `image/runtime/src/main/java/io/quarkiverse/qute/web/image/runtime/ImageNamespaceResolver.java`
- Create: `image/deployment/src/test/resources/web/templates/pub/remote.html`
- Create: `image/deployment/src/test/java/io/quarkiverse/qute/web/image/deployment/QuteImageStrictTest.java`
- Modify: `QuteImageTest.java` (add `testRemoteLiteralPassesThrough`)

**Interfaces:**
- Produces: `boolean strict()` on `ImageConfig` (`@WithDefault("false")`).
- Produces: `static boolean isRemote(String src)` in `ImageUtils` (true for `http://` and `https://` prefixes, case-insensitive).
- A null or blank `src` renders an empty string in `{#img}`, `img:path` and `img:tag`, in both modes, before any lookup.

- [ ] **Step 1: Write the failing tests**

`pub/remote.html`: `{#img 'https://example.com/remote.jpg' alt='Remote'/}` and `<div id="p">{img:path('https://example.com/remote.jpg')}</div>`.

In `QuteImageTest`:
```java
@Test
public void testRemoteLiteralPassesThrough() {
    Document doc = fetchAndParse("/remote.html");
    Element img = doc.select("img").first();
    assertThat(img.attr("src"), is("https://example.com/remote.jpg"));
    assertThat(img.attr("srcset"), is(""));
    assertThat(doc.getElementById("p").text(), is("https://example.com/remote.jpg"));
}
```

`QuteImageStrictTest`: same scaffolding as `QuteImageTest` plus `.overrideConfigKey("quarkus.qute.image.strict", "true")`.
```java
@Test
public void testMissFailsRendering() {
    RestAssured.get("/rest/dynamic?src=/unknown/image.jpg").then().statusCode(500);
}
@Test
public void testRemoteStillPassesThrough() {
    String body = RestAssured.get("/rest/dynamic?src=https://example.com/a.jpg").then().statusCode(200).extract().asString();
    assertThat(body, containsString("<img src=\"https://example.com/a.jpg\""));
}
@Test
public void testHitStillRenders() {
    RestAssured.get("/images.html").then().statusCode(200);
}
@Test
public void testBlankSrcRendersNothing() {
    String body = RestAssured.get("/rest/dynamic?src=").then().statusCode(200).extract().asString();
    assertThat(body, not(containsString("<img")));
}
```
Add the same `testBlankSrcRendersNothing` to `QuteImageTest` (non-strict).

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q test -f image/deployment/pom.xml -Dtest='QuteImageStrictTest,QuteImageTest#testRemoteLiteralPassesThrough'`
Expected: `testMissFailsRendering` fails (200 with fallback) and both `testBlankSrcRendersNothing` fail (`<img src="">`); the remote tests already pass because Task 2 made the scanner skip remote literals and the fallback renders them.

- [ ] **Step 3: Implement**

Inject `ImageConfig config` in both runtime beans (`@Inject` field; keep the existing constructors compiling). If `src` is null or blank, return an empty result before the lookup. On a miss (`imageTag == null`): if `ImageUtils.isRemote(src)` render the fallback as today; else if `config.strict()` throw `new TemplateException("Image not found for src '%s' with preset '%s' in template '%s' (quarkus.qute.image.strict=true). Add the image to a scanned directory or produce it at build time.".formatted(src, preset, templateId))`; else fallback as today.

- [ ] **Step 4: Run tests**

Run: `mvn -q install -DskipTests -f image/runtime/pom.xml && mvn -q test -f image/deployment/pom.xml -Dtest='QuteImageStrictTest,QuteImageTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A image && git commit -m "Add strict image lookups and remote URL pass-through"
```

### Task 5: Configurable output path

**Files:**
- Modify: `ImageConfig.java` (`outputPath()`), `ImageUtils.java` (`computeOutputPath` overloads), `image/deployment/.../items/model/ImageBuilder.java` (`addGeneratedImage`), `QuteImageProcessor.java` (`processImage`, callers)
- Create: `image/deployment/src/test/java/io/quarkiverse/qute/web/image/deployment/QuteImageOutputPathTest.java`
- Modify: `image/runtime/src/test/...` or `image/deployment/src/test/.../ImageUtilsOutputPathTest.java` (unit test, place next to `ImageUtilsIndexKeyTest`)

**Interfaces:**
- Produces: `String outputPath()` on `ImageConfig` (`@WithDefault("/static/images/generated")`); validated in `processTemplatesWithImages` to start with `/` and not end with `/` (throw `IllegalArgumentException` naming the key).
- Produces: `ImageUtils.computeOutputPath(String outputPrefix, ImageId id, Integer width, String settingsHash, String ext, boolean slugify)`; the old overloads are removed. `ImageBuilder.addGeneratedImage(String outputPrefix, GeneratedImageOptions options, boolean slugifyOutput, Consumer<GeneratedImage> consumer)`. `processImage(..., ImageConfig imageConfig)` replaces the trailing `boolean slugifyOutput` parameter.

- [ ] **Step 1: Write the failing tests**

Unit: `assertEquals("/img/gen/abcd1234/photo-640-h.jpg", ImageUtils.computeOutputPath("/img/gen", new ImageId("abcd1234","photo","jpg"), 640, "h", "jpg", false))`.

`QuteImageOutputPathTest` with `.overrideConfigKey("quarkus.qute.image.output-path", "/img/gen")`:
```java
@Test
public void testGeneratedPathsUseConfiguredPrefix() {
    Document doc = fetchAndParse("/images.html");
    String src = doc.select("img").get(1).attr("src");
    assertThat(src, startsWith("/img/gen/"));
    RestAssured.get(src).then().statusCode(200);
}
```

- [ ] **Step 2: Run tests to verify they fail**

Expected: compile failure on the new overload; the integration test fails on `startsWith`.

- [ ] **Step 3: Implement**, threading `imageConfig.outputPath()` from `processImage` into `addGeneratedImage` and `computeOutputPath`.

- [ ] **Step 4: Run tests**

Run: `mvn -q install -DskipTests -f image/runtime/pom.xml && mvn -q test -f image/deployment/pom.xml`
Expected: PASS for all deployment tests.

- [ ] **Step 5: Commit**

```bash
git add -A image && git commit -m "Make the generated image output path configurable"
```

### Task 6: Persistent build dir, exists check, converter id

**Files:**
- Modify: `ImageConfig.java` (`buildDir()`), `QuteImageScanProcessor.java` (`initTargetDir`), `ImageUtils.java` (`digest`), `image/deployment/.../converter/ImageConverter.java` (`id()`), `ImageIOConverter.java`, `image/vips/deployment/.../VipsConverter.java`, `GeneratedImageOptions.java`, `QuteImageProcessor.java` (`processImage`)
- Create: `image/deployment/src/test/java/io/quarkiverse/qute/web/image/deployment/ImageUtilsDigestTest.java`, `GeneratedImageOptionsTest.java`, `QuteImageBuildDirTest.java`

**Interfaces:**
- Produces: `String buildDir()` on `ImageConfig` (`@WithDefault("qute-image")`). `initTargetDir(ImageConfig, OutputTargetBuildItem)`: `Path.of(buildDir)` if absolute, else `outputTarget.getOutputDirectory().resolve(buildDir)`; `Files.createDirectories`; no delete; `LaunchModeBuildItem` and the profile subdirectory are dropped.
- Produces: `default String id()` on `ImageConverter` returning `"imageio"`; `VipsConverter.id()` returns `"vips"`.
- Produces: `GeneratedImageOptions(int width, int height, String format, PresetConfig.Crop crop, int quality, String converterId)`; `settings()` appends `"-" + converterId`.
- `ImageUtils.digest` stays at 8 hex characters (pinned by a test).

- [ ] **Step 1: Write the failing tests**

```java
// ImageUtilsDigestTest
assertEquals(8, ImageUtils.digest("hello".getBytes(UTF_8)).length());
assertEquals(ImageUtils.digest("a"), ImageUtils.digest("a"));
// GeneratedImageOptionsTest
var a = new GeneratedImageOptions(100, 50, "jpg", null, 80, "imageio");
assertEquals("80-imageio", a.settings());
assertNotEquals(a.settings(), new GeneratedImageOptions(100, 50, "jpg", null, 70, "imageio").settings()); // testQualityChangesSettings
assertNotEquals(a.settings(), new GeneratedImageOptions(100, 50, "jpg", null, 80, "vips").settings());
```

`QuteImageBuildDirTest`: a `static { }` block declared before the `@RegisterExtension` field creates `target/qute-image-keep/keep.txt`; config `.overrideConfigKey("quarkus.qute.image.build-dir", Path.of("target/qute-image-keep").toAbsolutePath().toString())` (absolute, so the test does not depend on what `OutputTargetBuildItem` resolves to under QuarkusUnitTest). Add a second class `QuteImageRelativeBuildDirTest` with `"qute-image-relative"` asserting that `OutputTargetBuildItem`'s directory (print it once from a build step into `target/output-dir.txt`) contains `qute-image-relative` with at least one `.jpg`.
```java
@Test
public void testBuildDirIsNotWiped() throws IOException {
    assertTrue(Files.exists(Path.of("target/qute-image-keep/keep.txt")));
    try (var s = Files.walk(Path.of("target/qute-image-keep"))) {
        assertTrue(s.anyMatch(p -> p.toString().endsWith(".jpg")));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Expected: `settings()` has no converter id, `keep.txt` deleted, relative build dir missing.

- [ ] **Step 3: Implement**

In `processImage`, inside the `addGeneratedImage` consumer: `if (Files.exists(outputPath)) { LOGGER.debugf("Reusing %s", outputPath); } else { converter.process(...); }`, then always produce the `GeneratedStaticResourceBuildItem`. Pass `converter.id()` into every `GeneratedImageOptions`.

- [ ] **Step 4: Run all image tests, including vips**

Run: `mvn -q install -f image/pom.xml` (runs unit and deployment tests; vips tests need libvips, skip them with `-Dtest='!QuteVipsImageTest'` only if libvips is absent and say so in the task report).
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A image && git commit -m "Keep generated images across builds in a configurable build dir"
```

### Task 6b: Vector and unconvertible sources pass through

**Files:**
- Modify: `QuteImageProcessor.java` (`processImage`), `ImageIOConverter.java` (confirm `readInfo` throws `UnsupportedFormatException` when no reader accepts the bytes; make it so if it returns null)
- Modify: `image/runtime/.../ImageTemplateExtension.java`, `ImageAttrs.java`, `templates/qute-image/image.html` as needed for an `Image` with no generated variants
- Create: `image/deployment/src/test/resources/web/static/images/logo.svg`, `web/templates/pub/vector.html`
- Modify: `QuteImageTest.java` (add `testVectorSourcePassesThrough`)
- Modify: the vips deployment test (`image/vips/deployment/src/test`): same `logo.svg` + `vector.html` fixture and the same assertions, proving the SVG passes through even though vips can read it

**Interfaces:**
- `processImage` first checks the declared path's extension: `svg` is registered with `OriginalInfo("svg", 0, 0)` and returns without calling the converter and without a warning (vectors pass through by design, with every converter).
- Otherwise `processImage` catches `UnsupportedFormatException` from `converter.readInfo`: logs one WARN naming the declared path and the converter (and suggesting the vips extension), sets `OriginalInfo(extensionOf(declaredPath), 0, 0)`, registers the tag and returns without generating variants.
- At render time an `Image` with no generated variants renders `src` equal to `declaredPath` (`selectBest` already falls back to it), no `srcset`, no `<picture>`, no `width`/`height`; `img:path` returns `declaredPath`. `ImageAttrs.from` and `imgSrcset` must not dereference `info()` dimensions or the first generated image when the list is empty.

- [ ] **Step 1: Write the failing test**

`pub/vector.html`: `{#img '/static/images/logo.svg' alt='Logo'/}` and `<div id="p">{img:path('/static/images/logo.svg')}</div>`; `logo.svg` is any small valid SVG.
```java
@Test
public void testVectorSourcePassesThrough() {
    Document doc = fetchAndParse("/vector.html");
    Element img = doc.select("img").first();
    assertThat(img.attr("src"), is("/static/images/logo.svg"));
    assertThat(img.attr("srcset"), is(""));
    assertThat(doc.select("picture").size(), is(0));
    assertThat(doc.getElementById("p").text(), is("/static/images/logo.svg"));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -f image/deployment/pom.xml -Dtest=QuteImageTest#testVectorSourcePassesThrough`
Expected: FAIL (build error from `readInfo`, or an exception rendering). The vips variant fails differently: it generates raster variants, so `srcset` is not empty.

- [ ] **Step 3: Implement** as in Interfaces.

- [ ] **Step 4: Run** `mvn -q install -DskipTests -f image/runtime/pom.xml && mvn -q test -f image/deployment/pom.xml && mvn -q test -f image/vips/deployment/pom.xml`. Expected: PASS (skip the vips run if libvips is not installed locally, and say so).

- [ ] **Step 5: Commit**

```bash
git add -A image && git commit -m "Pass vector and unconvertible image sources through unchanged"
```

### Task 7: qute-web docs and attic notes

**Files:**
- Modify: `docs/modules/ROOT/pages/image.adoc`
- Modify: `image/attic/dynamic/README.md` (line 35 points at a Roq docs path that does not exist; point at `../build-time-resolution/design.md`; mention `img:path`)

- [ ] **Step 1: Document** in `image.adoc`: `img:path`; SVG sources always pass through (vectors stay vectors), unreadable raster sources pass through with a warning; a blank `src` renders nothing; `quarkus.qute.image.strict`; `quarkus.qute.image.output-path`; `quarkus.qute.image.build-dir` with the reuse rule (a variant is regenerated only when its path changes: source bytes, width, format, crop, quality or converter); remote URLs pass through; a short "Integrating from another extension" section naming `QuteImageExpressionBuildItem` and `QuteImageToProcessBuildItem` with one sentence each. The generated config reference picks up the new keys from the javadoc on `ImageConfig`.

- [ ] **Step 2: Build docs module** if the repo builds docs (`mvn -q install -DskipTests -f docs/pom.xml`); otherwise just `mvn -q install -DskipTests -f image/pom.xml` so Roq sees the final snapshots.

- [ ] **Step 3: Commit**

```bash
git add -A docs image/attic && git commit -m "Document build-time image integration and new config"
```

---

## Part B: quarkus-roq (`$ROQ`, branch `roq-image-plugin`)

Build and test commands for this part:

```bash
cd $ROQ && mvn -q install -DskipTests -pl <module>            # e.g. roq-frontmatter/runtime
cd $ROQ && mvn -q test -pl <deployment-module> -Dtest=<Class>
cd $ROQ && mvn -q verify -pl roq-plugin/image/integration-tests  # QuarkusTest ITs
```

### Task 8: Revert the queue-based generator changes

**Files:**
- Modify (restore from upstream): `roq-generator/runtime/src/main/java/io/quarkiverse/roq/generator/runtime/ConfiguredPathsProvider.java`, `RoqGenerator.java`, `RoqGeneratorRecorder.java`

- [ ] **Step 1: Restore the three files**

```bash
git checkout upstream/main -- roq-generator/runtime/src/main/java/io/quarkiverse/roq/generator/runtime/ConfiguredPathsProvider.java roq-generator/runtime/src/main/java/io/quarkiverse/roq/generator/runtime/RoqGenerator.java roq-generator/runtime/src/main/java/io/quarkiverse/roq/generator/runtime/RoqGeneratorRecorder.java
git diff upstream/main --stat -- roq-generator   # expected: empty
```

- [ ] **Step 2: Build and run generator tests**

Run: `mvn -q install -DskipTests -pl roq-generator/runtime && mvn -q test -pl roq-generator/deployment`
Expected: PASS. (`roq-plugin/image` does not compile yet; that is expected until Task 13.)

- [ ] **Step 3: Commit**

```bash
git add -A roq-generator && git commit -m "Revert generator queue changes from the dynamic image design"
```

### Task 9: `Page.bindSite` and removal of `PageImageCustomizer`

**Files:**
- Modify: `roq-frontmatter/runtime/src/main/java/io/quarkiverse/roq/frontmatter/runtime/model/Page.java` (lines 213 to 236)
- Modify (restore from upstream): `.../runtime/model/Site.java`
- Delete: `.../runtime/model/PageImageCustomizer.java`
- Modify: `.../runtime/model/DocumentPage.java` (`collection()` at line 43)
- Create: `roq-frontmatter/runtime/src/test/java/io/quarkiverse/roq/frontmatter/runtime/model/PageBindSiteTest.java`

**Interfaces:**
- Produces: `public void bindSite(Site site)` on `Page`; `public Site site()` returns the bound site when non-null, else the Arc lookup as today. `DocumentPage.collection()` becomes `site().collections().resolveCollection(this)`.

- [ ] **Step 1: Write the failing test**

Build a directory page and a site index without Arc (see `runtime/src/test/.../model` tests for how `TemplateSource`, `PageSource` and `RootUrl` are constructed; `PageSource(templateSource, draft, dateString, files, false)` with `files = new PageFiles(List.of("cover.png"), false)`; index files `List.of("images/logo.png")`):

```java
@Test
void imageResolvesThroughBoundSite() {
    RootUrl root = new RootUrl("https://example.com", "/");
    NormalPage index = /* site index at "/" */;
    NormalPage post = /* dir page at "/posts/hello/" with data {"image":"cover.png"} and files ["cover.png"] */;
    Site site = new Site(index.url(), "images/", index.data(), List.of(index, post), new RoqCollections(Map.of()));
    index.bindSite(site); post.bindSite(site);
    assertEquals("/posts/hello/cover.png", post.image().toString());
    assertEquals("/images/logo.png", index.image("logo.png").toString());
    assertSame(site, post.site());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -pl roq-frontmatter/runtime -Dtest=PageBindSiteTest`
Expected: FAIL, `bindSite` undefined.

- [ ] **Step 3: Implement**

`git checkout upstream/main -- roq-frontmatter/runtime/src/main/java/io/quarkiverse/roq/frontmatter/runtime/model/Site.java`; delete `PageImageCustomizer.java`; in `Page` remove the customizer block in `image(Object)`, add `private Site site;`, `bindSite`, and the `site()` fallback; change `DocumentPage.collection()`. Javadoc on `bindSite`: intended for build-time evaluation and tests.

- [ ] **Step 4: Run runtime tests**

Run: `mvn -q install -pl roq-frontmatter/runtime`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A roq-frontmatter/runtime && git commit -m "Add Page.bindSite and remove PageImageCustomizer"
```

### Task 10: Build-time site model build item

**Files:**
- Modify: `roq-frontmatter/deployment/src/main/java/io/quarkiverse/roq/frontmatter/deployment/items/record/RoqFrontMatterRecordedNormalPagesBuildItem.java`, `RoqFrontMatterRecordedCollectionBuildItem.java`
- Create: `.../items/record/RoqFrontMatterSiteModelBuildItem.java`
- Modify: `.../RoqFrontMatterStep5RecordProcessor.java` (`bindPages`, `bindCollections`, new `buildTimeSiteModel`)
- Create: `roq-frontmatter/deployment/src/test/java/io/quarkiverse/roq/frontmatter/deployment/apptest/RoqFrontMatterSiteModelTest.java`

**Interfaces:**
- Produces: `List<SerializedPage> serialized()` on both recorded items (constructor gains the list; `bindCollections` keeps a `Map<String, SerializedPage> byId` so a derived collection's list is built from `i.documentIds()`).
- Produces: `public final class RoqFrontMatterSiteModelBuildItem extends SimpleBuildItem` with `Site site()`, `List<NormalPage> pages()`, `Map<String, RoqCollection> collections()`, `List<DocumentPage> documents()` (all documents, each id once), `List<Page> allPages()` (pages then documents).
- Produces: `@BuildStep RoqFrontMatterSiteModelBuildItem buildTimeSiteModel(RoqFrontMatterRootUrlBuildItem rootUrlItem, RoqSiteConfig config, RoqFrontMatterRecordedNormalPagesBuildItem normalPagesItem, List<RoqFrontMatterRecordedCollectionBuildItem> collectionItems)`: returns null under the same conditions `bindSite` returns null or has no site index; otherwise `toPage()` once per page id, `RoqCollection(collection, docs)` per item, `RoqCollections(Map.copyOf(...))`, `Site(indexPage.url(), config.imagesPath(), indexPage.data(), pages, collections)`, `bindSite(site)` on every page.

- [ ] **Step 1: Write the failing test**

Pattern: `RoqFrontMatterApiModificationTest` (build chain customizer on a `QuarkusUnitTest`). Site resources: `files-site`. The build step consumes `RoqFrontMatterSiteModelBuildItem` and writes `target/site-model.txt` with lines `pages=<n>`, `documents=<n>`, `siteImage=<site.image("cover.png")>`, `postCollection=<first document's collection().id()>`, `bound=<pages.get(0).site() == site>`.

```java
@Test
void modelMatchesSite() {
    Map<String,String> m = read("target/site-model.txt");
    assertEquals("/images/cover.png", m.get("siteImage"));
    assertEquals("true", m.get("bound"));
    assertEquals("posts", m.get("postCollection"));
    assertTrue(Integer.parseInt(m.get("pages")) >= 3);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -pl roq-frontmatter/deployment -Dtest=RoqFrontMatterSiteModelTest`
Expected: FAIL, class not found.

- [ ] **Step 3: Implement** as in Interfaces.

- [ ] **Step 4: Run the frontmatter deployment tests**

Run: `mvn -q install -pl roq-frontmatter/deployment`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A roq-frontmatter && git commit -m "Expose a build-time site model from the serialized pages"
```

### Task 11: `RoqFrontMatterUnpublishedFileBuildItem`

**Files:**
- Create: `roq-frontmatter/deployment/src/main/java/io/quarkiverse/roq/frontmatter/deployment/items/data/RoqFrontMatterUnpublishedFileBuildItem.java`
- Modify: `.../RoqFrontMatterStep6BindProcessor.java` (`bindStaticFiles`, line 247)
- Create: `.../apptest/RoqFrontMatterUnpublishedFileTest.java`

**Interfaces:**
- Produces: `public final class RoqFrontMatterUnpublishedFileBuildItem extends MultiBuildItem` with `String resourcePath()`; the value matches `RoqFrontMatterStaticFileBuildItem.link()` (resource path without leading slash, e.g. `images/cover.png`, `posts/ext-post/chart.svg`).
- `bindStaticFiles(..., List<RoqFrontMatterUnpublishedFileBuildItem> unpublished, ...)` skips matching static files entirely (no `RoqPathBuildItem`, no `GeneratedStaticResourceBuildItem`), with a debug log.

- [ ] **Step 1: Write the failing test**

Build chain step produces `new RoqFrontMatterUnpublishedFileBuildItem("images/cover.png")` on `files-site`.
```java
@Test void unpublishedFileIsNotServed() { RestAssured.get("/images/cover.png").then().statusCode(404); }
@Test void otherFilesStillServed() { RestAssured.get("/images/logo.png").then().statusCode(200); }
@Test void pageStillResolvesTheImage() { /* GET a page of files-site that prints site.image('cover.png'); expect "/images/cover.png" in body (see RoqFrontMatterFilesTest for the page and selector) */ }
```

- [ ] **Step 2: Run test to verify it fails** (first test returns 200).

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** `mvn -q install -pl roq-frontmatter/deployment`. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A roq-frontmatter && git commit -m "Let extensions unpublish scanned static files"
```

### Task 12: Fallback `img:url` returning a `RoqUrl`, and `seoImage.html` through the image pipeline

**Files:**
- Modify: `roq-frontmatter/runtime/src/main/java/io/quarkiverse/roq/frontmatter/runtime/RoqImageFallbackNamespaceResolver.java`
- Modify: `roq-frontmatter/runtime/src/main/resources/templates/tags/seoImage.html`
- Modify: `roq-theme/blog-common/src/main/resources/templates/tags/roq/postCard.html` (`img:url` to `img:path`)
- Create: `.../apptest/RoqFrontMatterSeoImageTest.java`

**Interfaces:**
- `RoqImageFallbackNamespaceResolver` keeps namespace `img` and answers `url` (returns the evaluated src unchanged when it is a `RoqUrl`, else `site.url().resolve(src)` using the `Site` bean, else `Results.NotFound`), `path` (the src as a string) and `tag`; everything else is `Results.NotFound`. Its priority must be lower than both qute-web's resolver and the plugin's (Task 13): check `WithPriority.DEFAULT_PRIORITY` and the ordering in Qute's `EvaluatorImpl` and pick the value accordingly, documenting it in a constant.
- `seoImage.html` renders `og:image` and `twitter:image:src` as `{img:url(page.image, 'roq-og').absolute}`.

- [ ] **Step 1: Write the failing test**

`files-site` uses local layouts, so add `{#seoImage /}` to `templates/layouts/post.html` of that site if it is not already included, and give `content/posts/2026-08-13-ext-post/index.*` the front matter `image: chart.svg` (it already has `chart.svg` attached). Test with `.overrideConfigKey("quarkus.http.root-path", "/blog")` and `.overrideConfigKey("site.url", "https://example.com")`:
```java
@Test
void testOgImageAbsoluteWithRootPath() {
    String body = RestAssured.get("/posts/ext-post/").then().statusCode(200).extract().asString();
    assertThat(body, containsString("property=\"og:image\" content=\"https://example.com/blog/posts/ext-post/chart.svg\""));
}
```
(Check the post's actual URL in the existing `RoqFrontMatterFilesTest` and adjust.)

- [ ] **Step 2: Run test to verify it fails**: temporarily rename the fallback's `url` case so the tag has no resolver, or assert on a `roq-og` marker; the simplest reliable failing state is the tag change made before the fallback change (the fallback currently returns a `String`, so `.absolute` fails).

- [ ] **Step 3: Implement** the fallback resolver and the tag, then the `postCard.html` rename.

- [ ] **Step 4: Run** `mvn -q install -pl roq-frontmatter/runtime,roq-frontmatter/deployment,roq-theme/blog-common`. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A roq-frontmatter roq-theme && git commit -m "Use img:path and route the SEO image through the image pipeline"
```

### Task 13: Image plugin cleanup, config and literal-only integration site

**Files:**
- Modify: `roq-plugin/image/runtime/pom.xml`, `roq-plugin/image/deployment/pom.xml`, `roq-plugin/image/integration-tests/pom.xml` (remove every `quarkus-qute-web-image-dynamic*` dependency)
- Delete: `roq-plugin/image/runtime/src/main/java/io/quarkiverse/roq/plugin/image/runtime/RoqImageCustomizer.java`, `RoqRuntimeImageListener.java`
- Create: `roq-plugin/image/runtime/src/main/java/io/quarkiverse/roq/plugin/image/runtime/RoqImageConfig.java`
- Create: `roq-plugin/image/runtime/src/main/java/io/quarkiverse/roq/plugin/image/runtime/RoqImageNamespaceResolver.java`
- Modify: `roq-plugin/image/runtime/src/main/resources/application.properties`
- Modify: `roq-plugin/image/deployment/src/main/java/io/quarkiverse/roq/plugin/image/deployment/RoqPluginImageProcessor.java` (`registerBeans` removed, `registerImageDirs` rewritten)
- Modify: integration site under `roq-plugin/image/integration-tests/src/main/resources/content/` and tests `RoqImageTest.java`, `RoqImageAndRollTest.java`, `RoqImageVipsTest.java`

**Interfaces:**
- Produces: `@ConfigMapping(prefix = "quarkus.roq.plugin.image") @ConfigRoot(phase = BUILD_AND_RUN_TIME_FIXED) interface RoqImageConfig { @WithDefault("false") boolean publishOriginals(); }` (same pattern as `OgCardConfig`).
- `application.properties` gains `quarkus.qute.image.strict=true` and preset `roq-og` (`formats=jpg`, `widths=1200`, `quality=80`, `crop.ratio=1.91:1`, `crop.keep=center`).
- Produces: `@EngineConfiguration RoqImageNamespaceResolver implements NamespaceResolver` for namespace `img`, priority between the frontmatter fallback and qute-web's resolver, answering only `url`: evaluate the first param (src) and the optional second (preset, default `"default"`); remote src returns `RoqUrl.fromRoot(null, src)`; otherwise `images.get(templateId, src, preset)`; a hit returns `site.url().root().resolve(ImageTemplateExtension.imgSrc(tag))` (check whether `imgSrc` already includes the HTTP root path and use `RoqUrl.fromRoot(root, path)` on the right form so the root path appears once); a miss throws the same `TemplateException` as qute-web's strict mode when `ImageConfig.strict()` is true, else returns the src as `RoqUrl` (`src` itself if it already is one). Registered with `AdditionalBeanBuildItem` in `RoqPluginImageProcessor`.
- `registerImageDirs` registers `public/` and `static/` only: local dirs with `ImagesDirBuildItem.of("", localDir)` for public and `of(config.staticDir(), localDir)` for static, plus the classpath equivalents, so a literal `/images/x.jpg` resolves to `public/images/x.jpg` and `/static/x.png` to `static/x.png`. Verify `ImagesDirBuildItem.normalizePrefix` accepts an empty prefix; fix it in `$QW` if it does not (then rebuild `$QW` image tree).

- [ ] **Step 1: Migrate the integration site to literal usages only**

Move `content/_images/hero-landscape.jpg` and `avatar-portrait.jpg` to `public/images/`; move `content/image-test/_images/gallery-food.jpg` to `content/image-test/gallery-food.jpg`; delete `_prefixed-photo.jpg` and the `prefixed-image` section. Remove the `fm-hero`, `fm-avatar`, `fm-direct` and `img-url` sections from `content/image-test/index.html` and the FM `image`/`avatar-image` keys (they return in Task 14 on a new page). Remove the matching tests (`testDirectUrlOutput`, `testImgUrlNamespace`, `testHeroPresetWidths`, `testAvatarPixelRatio`, `testPrefixedImage`, the `FM hero`/`Author` assertions, `testSiblingAndPrefixedImages` prefixed assertion, `testOriginalsNotPublished`), keeping the literal ones. Keep a copy of the removed sections and tests in the task notes for Task 14.

- [ ] **Step 2: Apply the cleanup and config** listed in Files and Interfaces.

- [ ] **Step 3: Build and run the integration tests**

Run: `mvn -q install -DskipTests -pl roq-plugin/image/runtime,roq-plugin/image/deployment && mvn -q verify -pl roq-plugin/image/integration-tests`
Expected: PASS (`RoqImageTest`, `RoqImageAndRollTest`, `RoqImageVipsTest` if libvips is present).

- [ ] **Step 4: Commit**

```bash
git add -A roq-plugin/image && git commit -m "Drop the dynamic image resolver from the Roq image plugin"
```

### Task 14: Evaluate image expressions at build time

**Files:**
- Create: `roq-plugin/image/deployment/src/main/java/io/quarkiverse/roq/plugin/image/deployment/ImageExpressionEvaluator.java`
- Create: `roq-plugin/image/deployment/src/main/java/io/quarkiverse/roq/plugin/image/deployment/LayoutChains.java`
- Modify: `RoqPluginImageProcessor.java` (new step `evaluateImageExpressions`)
- Create: integration site page `content/expressions/index.html` with attachments, `content/posts/...`, `templates/layouts/post.html`, `templates/layouts/unused.html`, `templates/tags/card.html`, `content/remote/index.html`
- Create: `roq-plugin/image/integration-tests/src/test/java/io/quarkiverse/roq/plugin/image/RoqImageExpressionsTest.java`
- Create: `roq-plugin/image/deployment/src/test/java/io/quarkiverse/roq/plugin/image/deployment/RoqImageUnresolvableTest.java` plus resources `unresolvable-site/`

**Interfaces:**
- Consumes: `QuteImageExpressionBuildItem` (Task 2), `QuteImageToProcessBuildItem` (Task 3), `RoqFrontMatterSiteModelBuildItem` (Task 10), `RoqFrontMatterPageTemplateBuildItem` (`raw().layout()`, `raw().id()`), `RoqFrontMatterLayoutTemplateBuildItem` (`raw().id()`, `raw().layout()`, `raw().templateSource().generatedQuteTemplateId()`), `RoqFrontMatterStaticFileBuildItem`, `RoqFrontMatterRootUrlBuildItem`.
- Produces: `LayoutChains.of(List<RoqFrontMatterLayoutTemplateBuildItem>)` with `Set<String> quteIdsFor(String pageLayout)` returning the generated Qute ids of the layout and all its parents (follow `layout()` by `id()`; stop on null or a repeat). `raw().layout()` on both page and layout items is the resolved layout id produced by `availableLayouts.resolveLayoutId` in step 2, the same string as the matching layout item's `id()`, so the lookup is a plain map get.
- Produces: `ImageExpressionEvaluator` holding one `Engine` (`Engine.builder().addDefaults().addValueResolver(new ReflectionValueResolver()).addValueResolver(new JsonObjectValueResolver()).strictRendering(true).build()`) with `Result evaluate(String expression, String rootName, Page page, Site site)` where `Result` is one of `Value(String)`, `Empty`, `Failed(TemplateException)`; `rootName` is the first part of the expression (`page`, `site`, `post`, ...) and is bound to `page` when it is not `site`. A `TemplateException` whose cause chain contains `RoqStaticFileException` is rethrown as that cause (hard failure, the file is missing).
- Produces: `@BuildStep void evaluateImageExpressions(...)`: context selection per the spec table: build `Map<String, List<Page>> byQuteTemplateId` from `model.allPages()` keyed by `p.source().template().generatedQuteTemplateId()` (paginated pages share one template, hence the list); a usage whose template id is a key gets those pages; a layout id matches every page whose chain contains it; anything else gets all pages for root `page`/`site` and all documents otherwise). Per context: evaluate `src` then `preset` (a preset `Failed` or `Empty` counts as `default`); `Empty` src is a "no image" context; a remote value (`ImageUtils.isRemote`) is a "remote" context and produces nothing; a `Value` is looked up in `Map<String, RoqFrontMatterStaticFileBuildItem>` keyed by `RoqUrl.fromRoot(rootUrl.rootUrl(), link()).toString()`; no entry fails the build. Dedup on `(declaredPath, presetName)` and produce `QuteImageToProcessBuildItem.of(declaredPath, presetName, filePath)` or `of(..., fileName, content)`. Fail with `RoqException.builder("Unresolvable image expression").detail("'%s' (preset %s) in template '%s', %s: %s".formatted(expression, preset, templateId, origin, firstFailureMessage)).hint("Use a literal path, or a page/site image expression the build can evaluate.")` only when the usage had at least one context and every context is `Failed`. Log one INFO line: usages, contexts, images queued, no-image contexts; DEBUG per evaluation.

- [ ] **Step 1: Write the integration site and the failing tests**

Site additions (all images are small JPEGs copied from the existing ones):
- `content/expressions/index.html`: FM `layout: page`, `title: Expressions`, `image: hero-landscape.jpg`, `cover: gallery-food.jpg`; attachments `hero-landscape.jpg`, `avatar-portrait.jpg`, `gallery-food.jpg`, `Foé photo.jpg`, `notes.txt`. Sections (ids as classes): `fm-hero` `{#img src=page.image preset='hero' alt='FM hero'/}`; `fm-avatar` `{#img src=page.image('avatar-portrait.jpg') preset='avatar' alt='Author'/}`; `fm-direct` `{#img src=page.image preset='direct'/}`; `img-path` `<a style='background-image: url("{img:path(page.image, "hero")}")'>`; `img-url` `<span>{img:url(page.image, "hero").absolute}</span>`; `elvis` `{#img src=page.image(page.data.cover) ?: page.image preset='gallery-image' alt='Elvis'/}` (`page.data.cover` alone would be a bare file name, not an image URL, and must fail the build; `page.image(null)` returns null so the elvis falls back); `encoded` `{#img src=page.image('Foé photo.jpg') preset='single-format' alt='Encoded'/}`; `listing` `{#for post in site.collections.posts}{#card post=post /}{/for}`.
- `templates/tags/card.html`: `{#img src=post.image preset='post-card' alt=post.title/}`.
- `templates/layouts/post.html`: `{#img src=page.image preset='hero' alt=page.title/}{#insert /}` (with whatever layout boilerplate `page.html` uses in this site); `templates/layouts/unused.html`: same tag, used by no page.
- `content/posts/2024-01-01-first/index.md` (`layout: post`, `image: first.jpg`, attachment `first.jpg`), `content/posts/2024-01-02-second/index.md` (`image: first.jpg` too, attachment copy named `first.jpg`), `content/posts/2024-01-03-draft/index.md` (`draft: true`, `image: draft.jpg`, attachment `draft.jpg`).
- `content/remote/index.html`: `image: https://example.com/remote.jpg`, `{#img src=page.image preset='hero' alt='Remote'/}`.
- `content/vector/index.html`: `image: logo.svg` with an SVG attachment `logo.svg`, `{#img src=page.image preset='hero' alt='Vector'/}`.
- `content/no-image/index.html`: no `image:` key, body `{#img src=page.image preset='hero' alt='None'/}`.

`RoqImageExpressionsTest` (QuarkusTest) assertions on `GET /expressions/`: body contains `alt="FM hero"`, `alt="Author"`, `350w`, `1x` and `2x`, `fm-direct` section contains `/static/images/generated/` and no `<img`; `img-path` section contains `background-image: url("/static/images/generated/`; `testImgUrlAbsoluteWithRootPath` (a second profile with `quarkus.http.root-path=/blog` and `site.url=https://example.com`): `img-url` span text starts with `https://example.com/blog/static/images/generated/` and contains `/blog/` once; `elvis` img src contains `gallery-food`; `testEncodedAttachmentName`: `encoded` section contains `/static/images/generated/` and no `Fo%C3%A9` fallback; `testSharedImageAcrossPresets`: `/posts/first/` and `/posts/second/` both render `alt="First"`-style hero with `350w`, and `/expressions/` listing has two `post-card` imgs with `160w`; every `/static/images/generated/...` token in these bodies returns 200; `GET /remote/` contains `<img src="https://example.com/remote.jpg"` and no `srcset`; `GET /vector/` contains `<img src="/vector/logo.svg"` and no `srcset`; `GET /no-image/` is 200 and contains no `<img`; `GET /posts/draft/` is 404 and no file under `target/qute-image` has a name starting with `draft-`.

`RoqImageUnresolvableTest` (`QuarkusUnitTest` in the deployment module, resources `unresolvable-site/content/index.html` with `{#img src=cdi:nothing.image preset='default'/}`, `quarkus.roq.resource-dir=unresolvable-site`): `.assertException(t -> assertThat(t).hasMessageContaining("Unresolvable image expression").hasMessageContaining("cdi:nothing.image"))`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q verify -pl roq-plugin/image/integration-tests -Dtest=RoqImageExpressionsTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL with 500 responses (strict mode, nothing registered).

- [ ] **Step 3: Implement** `LayoutChains`, `ImageExpressionEvaluator`, the build step.

- [ ] **Step 4: Run all plugin tests**

Run: `mvn -q install -pl roq-plugin/image/deployment && mvn -q verify -pl roq-plugin/image/integration-tests`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A roq-plugin/image && git commit -m "Evaluate image expressions at build time against the site model"
```

### Task 15: Select generated images for the generator and unpublish originals

**Files:**
- Modify: `RoqPluginImageProcessor.java` (new step `selectGeneratedImages`)
- Modify: `RoqImageAndRollTest.java`; Create: `RoqImagePublishOriginalsTest.java`
- Add: `content/expressions/notes.txt` (already added in Task 14) is the unconverted control file

**Interfaces:**
- Consumes: `ImagesBuildItem` (`builder().imageIdsByPath()` from Task 3 and the generated variants reachable through `computeImages()` values), `RoqImageConfig`, `List<RoqFrontMatterStaticFileBuildItem>`.
- Produces: one `SelectedPathBuildItem(generatedImage.outputPath(), null)` per generated variant; when `!config.publishOriginals()`, one `RoqFrontMatterUnpublishedFileBuildItem(staticFile.link())` for every static file whose absolute path (`filePath().toAbsolutePath().toString()`) is a key of `imageIdsByPath()`, or whose `ImageUtils.digest(content())` equals a value's `digest()`, restricted to images whose `generated()` list is non-empty (a passed-through source such as an SVG must stay published because its original is what gets rendered).

- [ ] **Step 1: Write the failing tests**

In `RoqImageAndRollTest`:
```java
@Test
public void testGeneratedImagesWrittenToOutput() {
    String body = RestAssured.get("/expressions/").then().statusCode(200).extract().asString();
    int count = 0;
    for (String token : body.split("[\"\\s,]+")) {
        if (token.startsWith("/static/images/generated/")) {
            assertThat(RoqLinks.outputDir().resolve(token.substring(1))).exists();
            count++;
        }
    }
    assertThat(count).isGreaterThan(0);
}
@Test
public void testOriginalsNotPublished() {
    RestAssured.get("/expressions/hero-landscape.jpg").then().statusCode(404);
    RestAssured.get("/images/hero-landscape.jpg").then().statusCode(404);   // converted by the literal usage
    RestAssured.get("/expressions/notes.txt").then().statusCode(200);
    assertThat(RoqLinks.outputDir().resolve("expressions/hero-landscape.jpg")).doesNotExist();
}
@Test
public void testUnconvertedOriginalStaysPublished() {
    RestAssured.get("/vector/logo.svg").then().statusCode(200);
    assertThat(RoqLinks.outputDir().resolve("vector/logo.svg")).exists();
}
```
`RoqImagePublishOriginalsTest`: `@QuarkusTest @TestProfile` with `getConfigOverrides()` returning `quarkus.roq.plugin.image.publish-originals=true`; asserts `/expressions/hero-landscape.jpg` is 200.

- [ ] **Step 2: Run tests to verify they fail** (files missing from the output dir; originals served).

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** `mvn -q install -pl roq-plugin/image/deployment && mvn -q verify -pl roq-plugin/image/integration-tests`. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A roq-plugin/image && git commit -m "Select generated images for the generator and unpublish converted originals"
```

### Task 16: Blog migration and generation check

**Files:**
- Rename: every `blog/content/**/_image.jpg` to `image.jpg` (33 files) and the front matter `image: _image.jpg` to `image: image.jpg` (29 files; the remaining four files have other references, inspect them)
- Modify: `blog/content/marketplace/plugins/image.md` (remove the `_` and `content/_images/` sections; document `publish-originals`, `img:path`, build-time evaluation of `page.image`/`site.image`/collection expressions, that `{page.image}` in plain HTML still points at the original, which is served only when `publish-originals=true`, and that the OG card needs a raster page image since an SVG is sent as is)

- [ ] **Step 1: Rename**

```bash
cd $ROQ/blog/content && for f in $(find . -name "_image.jpg"); do git mv "$f" "$(dirname "$f")/image.jpg"; done
grep -rl "^image: _image.jpg" . | xargs sed -i '' 's/^image: _image.jpg/image: image.jpg/'
grep -rn "_image" . | head    # expected: nothing left, or only prose
```

- [ ] **Step 2: Build everything and generate the blog**

```bash
cd $ROQ && mvn -q install -DskipTests
cd $ROQ/blog && QUARKUS_ROQ_GENERATOR_BATCH=true mvn -q package quarkus:run -DskipTests
```
Expected: the run logs the build-time INFO summary with zero unresolved usages, no `TemplateException` in the output, generated site under `blog/target/roq` (check the generator's configured output dir) contains `static/images/generated/**` files, and `grep -rl 'src="image.jpg"' blog/target/roq` finds nothing.

- [ ] **Step 3: Commit**

```bash
git add -A blog && git commit -m "Blog: drop the _image naming and document build-time image resolution"
```

### Task 17: Whole-branch verification

- [ ] **Step 1:** `cd $ROQ && mvn -q install` (full build with tests). Expected: PASS. Report any skipped vips tests.
- [ ] **Step 2:** `git diff upstream/main --stat` in `$ROQ` and read it once: no leftover `enqueue`, `PageImageCustomizer`, `dynamic`, `_images` references (`git grep -n "image-dynamic\|PageImageCustomizer\|content/_images\|img:url"` must be empty).
- [ ] **Step 3:** Dev-mode smoke: start the blog on a free port, edit a post's `image:` to another attachment, confirm the restart re-registers it and the page renders a generated path, then stop the server.
