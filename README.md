# Quarkus Qute Web

[![Version](https://img.shields.io/maven-central/v/io.quarkiverse.qute.web/quarkus-qute-web.svg?label=Maven%20Central)](https://search.maven.org/artifact/io.quarkiverse.qute.web/quarkus-qute-web)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

The goal of this extension is to expose the [Qute](https://quarkus.io/guides/qute-reference) templates located in the `src/main/resource/templates/pub` directory via HTTP. Automatically, no controllers needed. For example, the template `src/main/resource/templates/pub/foo.html` will be served from the paths `/foo` and `/foo.html` by default.

In a template you can access:

- [`@Named` CDI beans](https://quarkus.io/guides/qute-reference#injecting-beans-directly-in-templates); similar to EL; e.g. `{cdi:myBean.findItems()}`
- [static members of a class](https://quarkus.io/guides/qute-reference#accessing-static-fields-and-methods) annotated with `@TemplateData`
- [enums](https://quarkus.io/guides/qute-reference#convenient-annotation-for-enums) annotated with `@TemplateEnum`
- [Namespace Extension Methods](https://quarkus.io/guides/qute-reference#namespace_extension_methods) in general
- [global variables](https://quarkus.io/guides/qute-reference#global_variables)
- the current `io.vertx.core.http.HttpServerRequest` via the `http:` namespace, e.g. `{http:request.path}`
- the query parameters via the `http:` namespace, e.g. `{http:param('name')}` and `{http:param('name','DefaultName'}`

Read the full [documentation](https://quarkiverse.github.io/quarkiverse-docs/quarkus-qute-web/dev/index.html).
