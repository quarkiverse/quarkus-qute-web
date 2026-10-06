package io.quarkiverse.qute.web.image.dynamic.runtime;

import java.nio.file.Files;
import java.nio.file.Path;

import io.quarkiverse.qute.web.image.runtime.ImageUtils;
import io.quarkus.arc.Arc;
import io.vertx.core.Handler;
import io.vertx.core.http.impl.MimeMapping;
import io.vertx.ext.web.RoutingContext;

public class DynamicImageRouteHandler implements Handler<RoutingContext> {

    private volatile Path cacheDir;

    @Override
    public void handle(RoutingContext ctx) {
        Path dir = getCacheDir();
        if (dir == null) {
            ctx.next();
            return;
        }
        String path = ctx.normalizedPath();
        Path file = dir.resolve(path.substring(1));
        if (Files.isRegularFile(file)) {
            String contentType = MimeMapping.getMimeTypeForFilename(path);
            if (contentType == null) {
                int dot = path.lastIndexOf('.');
                if (dot > 0) {
                    contentType = ImageUtils.normalizeFormat(path.substring(dot + 1));
                }
            }
            if (contentType != null) {
                ctx.response().putHeader("content-type", contentType);
            }
            ctx.response().sendFile(file.toString());
        } else {
            ctx.next();
        }
    }

    private Path getCacheDir() {
        if (cacheDir == null) {
            DynamicImageRuntimeConfig config = Arc.container()
                    .instance(DynamicImageRuntimeConfig.class).get();
            if (config != null) {
                cacheDir = config.cacheDir();
            }
        }
        return cacheDir;
    }
}
