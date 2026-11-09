# Attic: dynamic (runtime) image resolution

This directory is **not** part of the Maven build. It keeps the source of the
removed `quarkus-qute-web-image-dynamic` extension, together with the two
runtime hooks that were deleted from `quarkus-qute-web-image` when it was
removed, so the approach can be restored or consulted later.

## What it did

When a `{#img}` tag or `{img:url()}` expression (since renamed `{img:path()}`) was rendered with a `src` that
was not pre-processed at build time (typically a non-literal expression such
as `page.image`), the dynamic extension resolved it at render time:

1. `DynamicImageResolverImpl` looked the source file up in the registered
   image directories (`ImagesDirBuildItem`, local dirs first, then classpath),
   converted the preset variants with `ImageIOConverter` or `VipsConverter`
   (loaded by reflection), and cached the result in a temp directory created
   at startup.
2. `DynamicImageRouteHandler` served `/static/images/generated/*` from that
   temp directory.
3. `RuntimeImageListener` (CDI, optional) was notified for each converted
   variant so a host such as a static site generator could copy the file into
   its output and enqueue the path.

`DynamicImageResolver` was the runtime SPI looked up by
`ImageSectionHelperFactory` and `ImageNamespaceResolver` when the build-time
`Images` registry had no entry for the (template, src, preset) key.

## Why it was removed

- The main consumer, Quarkus Roq, resolves these expressions at **build
  time** instead: it evaluates `page.image`-style expressions against its
  page model and feeds the resulting images to the build-time processor
  through `QuteImageExpressionBuildItem` and `QuteImageToProcessBuildItem`.
  See the design next to this directory
  (`../build-time-resolution/design.md`).
- Runtime conversion required the converter (and libvips bindings for the
  vips extension) on the **runtime** classpath. Without it, conversion is a
  build-time-only concern, which allowed merging the `converter` modules into
  the deployment modules and making `quarkus-qute-web-image-vips` a thin
  runtime artifact.
- It produced non-deterministic output for static site generation (files
  appeared as pages were rendered), used a temp dir that was recreated on
  every start, and added a route and a render-path conversion that are hard
  to reason about in native images.

## What was removed from the live modules

- Module `image/dynamic` (parent, `runtime`, `deployment`) and its entry in
  `image/pom.xml`.
- `io.quarkiverse.qute.web.image.runtime.DynamicImageResolver` and
  `io.quarkiverse.qute.web.image.runtime.RuntimeImageListener` (kept here
  under `removed-runtime-hooks/`).
- In `ImageSectionHelperFactory` and `ImageNamespaceResolver`: the
  `Instance<DynamicImageResolver>` injection and the
  `if (imageTag == null && dynamicResolver.isResolvable()) { ... }` branch
  between the `Images` lookup and the fallback rendering.
- Modules `image/converter` and `image/vips/converter` were merged into
  `image/deployment` and `image/vips/deployment` in the same change (the
  dynamic runtime was their only runtime consumer).

## How to restore it

1. Move `runtime/` and `deployment/` back to `image/dynamic/` and re-add
   `<module>dynamic</module>` to `image/pom.xml`.
2. Put the two interfaces from `removed-runtime-hooks/` back into
   `image/runtime/src/main/java/io/quarkiverse/qute/web/image/runtime/`.
3. Re-add the injection and the fallback branch in `ImageSectionHelperFactory`
   and `ImageNamespaceResolver` (see above).
4. The dynamic runtime needs the converter classes at runtime again, so either
   split `io.quarkiverse.qute.web.image.converter` back out of
   `image/deployment` into a runtime-safe module, or depend on the deployment
   artifact (not recommended for a runtime module).
5. `DynamicImageProcessor` hardcodes the `/static/images/generated/*` route;
   align it with `quarkus.qute.image.output-path`, which now exists.
