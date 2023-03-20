# Quarkus QuteServerPages

[![Version](https://img.shields.io/maven-central/v/io.quarkiverse.qutepages/quarkus-qutepages?logo=apache-maven&style=flat-square)](https://search.maven.org/artifact/io.quarkiverse.quteserverpages/quarkus-qute-server-pages)

The goal of this simple extension is to expose all Qute templates located in the `src/main/resource/templates` directory via HTTP. No controllers needed. For example, a template located in `src/main/resource/templates/foo.html` will be served from the paths `/qsp/foo` and `/qsp/foo.html` by default.

In a template you can access:

- [`@Named` CDI beans](https://quarkus.io/guides/qute-reference#injecting-beans-directly-in-templates); similar to EL; e.g. `{cdi:myBean.findItems()}`
- [static members of a class](https://quarkus.io/guides/qute-reference#accessing-static-fields-and-methods) annotated with `@TemplateData`
- [enums](https://quarkus.io/guides/qute-reference#convenient-annotation-for-enums) annotated with `@TemplateEnum`
- [Namespace Extension Methods](https://quarkus.io/guides/qute-reference#namespace_extension_methods) in general
- [global variables](https://quarkus.io/guides/qute-reference#global_variables)
- the current `io.vertx.core.http.HttpServerRequest` via CDI, e.g. `{cdi:vertxRequest.getParam('foo')}`

Read the full [documentation](https://quarkiverse.github.io/quarkiverse-docs/quarkus-quteserverpages/dev/index.html).
