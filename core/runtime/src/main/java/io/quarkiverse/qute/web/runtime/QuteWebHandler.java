package io.quarkiverse.qute.web.runtime;

import static io.quarkiverse.qute.web.runtime.PathUtils.removeLeadingSlash;
import static io.quarkiverse.qute.web.runtime.PathUtils.removeTrailingSlash;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import jakarta.enterprise.event.Event;

import org.jboss.logging.Logger;

import io.quarkiverse.qute.web.DataInitializer;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InjectableContext.ContextState;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.arc.ManagedContext;
import io.quarkus.arc.impl.LazyValue;
import io.quarkus.qute.Template;
import io.quarkus.qute.Template.Fragment;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.qute.Variant;
import io.quarkus.qute.runtime.QuteRecorder.QuteContext;
import io.quarkus.qute.runtime.TemplateProducer;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import io.quarkus.vertx.http.runtime.VertxHttpBuildTimeConfig;
import io.quarkus.vertx.http.runtime.security.QuarkusHttpUser;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.MIMEHeader;
import io.vertx.ext.web.RoutingContext;

public class QuteWebHandler implements Handler<RoutingContext> {

    private static final Logger LOG = Logger.getLogger(QuteWebHandler.class);

    private static final String FRAGMENT_PARAM = "frag";

    private final String rootPath;
    private final Set<String> templatePaths;
    private final String webTemplatesPath;
    private final List<String> compressMediaTypes;
    // request path to template path
    private final Map<String, String> extractedPaths;

    private final Event<SecurityIdentity> securityIdentityEvent;
    private final CurrentIdentityAssociation currentIdentity;
    private final CurrentVertxRequest currentVertxRequest;
    private final ManagedContext requestContext;
    private final LazyValue<TemplateProducer> templateProducer;
    private final QuteContext quteContext;
    private final Map<String, String> templateLinks;
    private final List<DataInitializer> dataInitializers;

    public QuteWebHandler(String rootPath, String publicDir, Set<String> templatePaths, Map<String, String> templateLinks,
            VertxHttpBuildTimeConfig httpBuildTimeConfig) {
        this.rootPath = rootPath;
        this.templatePaths = templatePaths;
        if (publicDir.equals("/") || publicDir.isBlank()) {
            this.webTemplatesPath = "";
        } else {
            this.webTemplatesPath = publicDir.startsWith("/") ? publicDir.substring(1) : publicDir;
        }
        this.templateLinks = templateLinks;
        this.compressMediaTypes = httpBuildTimeConfig.enableCompression()
                ? httpBuildTimeConfig.compressMediaTypes().orElse(List.of())
                : null;
        this.extractedPaths = new ConcurrentHashMap<>();
        ArcContainer container = Arc.container();
        this.securityIdentityEvent = container.beanManager().getEvent().select(SecurityIdentity.class);
        this.currentVertxRequest = container.instance(CurrentVertxRequest.class).get();
        this.requestContext = container.requestContext();
        this.currentIdentity = container.instance(CurrentIdentityAssociation.class).get();
        // TemplateProducer is singleton and we want to initialize lazily
        this.templateProducer = new LazyValue<>(
                () -> Arc.container().instance(TemplateProducer.class).get());
        this.quteContext = Arc.container().instance(QuteContext.class).get();
        this.dataInitializers = container.listAll(DataInitializer.class).stream().map(InstanceHandle::get).toList();
    }

    @Override
    public void handle(RoutingContext rc) {
        QuarkusHttpUser user = (QuarkusHttpUser) rc.user();

        if (requestContext.isActive()) {
            processCurrentIdentity(rc, user);
            handlePage(rc);
        } else {
            try {
                // Activate the context
                requestContext.activate();
                currentVertxRequest.setCurrent(rc);
                processCurrentIdentity(rc, user);
                // Terminate the context correctly when the response is disposed or an exception is thrown
                final ContextState endState = requestContext.getState();
                rc.addEndHandler(new Handler<AsyncResult<Void>>() {
                    @Override
                    public void handle(AsyncResult<Void> result) {
                        requestContext.destroy(endState);
                    }
                });
                handlePage(rc);
            } finally {
                // Deactivate the context
                requestContext.deactivate();
            }
        }
    }

    private void processCurrentIdentity(RoutingContext rc, QuarkusHttpUser user) {
        if (currentIdentity != null) {
            if (user != null) {
                SecurityIdentity identity = user.getSecurityIdentity();
                currentIdentity.setIdentity(identity);
            } else {
                currentIdentity.setIdentity(QuarkusHttpUser.getSecurityIdentity(rc, null));
            }
        }
        if (user != null) {
            securityIdentityEvent.fire(user.getSecurityIdentity());
        }
    }

    private void handlePage(RoutingContext rc) {
        String requestPath = rc.request().path();
        LOG.debugf("Handle page: %s", requestPath);

        // Extract the real template path, e.g. /item.html -> web/item
        String path = extractedPaths.computeIfAbsent(requestPath, this::extractTemplatePath);
        if (path != null) {
            Template template = templateProducer.get().getInjectableTemplate(path);
            TemplateInstance originalInstance = template.instance();
            TemplateInstance instance = originalInstance;

            // It is possible to specify the fragment via query param, e.g. /item?frag=detail
            String fragmentId = rc.request().getParam(FRAGMENT_PARAM);
            if (fragmentId != null) {
                // Note that we have to use the original instance to obtain the fragment
                // because getFragment() invoked upon the injectable template returns an injectable fragment and never null
                Fragment fragment = originalInstance.getTemplate().getFragment(fragmentId);
                if (fragment == null) {
                    LOG.errorf("Fragment [%s] not found: %s", fragmentId, rc.request().path());
                    rc.response().setStatusCode(404).end();
                    return;
                } else {
                    instance = fragment.instance();
                }
            }

            List<MIMEHeader> acceptableTypes = rc.parsedHeaders().accept();
            // Note that we need to obtain the variants from the original template, even if fragment is used
            Variant selected = trySelectVariant(rc, originalInstance, acceptableTypes);

            if (selected != null) {
                instance.setAttribute(TemplateInstance.SELECTED_VARIANT, selected);
                rc.response().putHeader(HttpHeaders.CONTENT_TYPE, selected.getContentType());

                // Compression support - only compress the response if the content type matches the config value
                if (compressMediaTypes != null
                        && compressMediaTypes.contains(selected.getContentType())) {
                    String contentEncoding = rc.response().headers().get(HttpHeaders.CONTENT_ENCODING);
                    if (contentEncoding != null && HttpHeaders.IDENTITY.toString().equals(contentEncoding)) {
                        rc.response().headers().remove(HttpHeaders.CONTENT_ENCODING);
                    }
                }
            }

            if (selected == null && !acceptableTypes.isEmpty()) {
                // The Accept header is set but we are not able to select the appropriate variant
                LOG.errorf("Appropriate template variant not found %s: %s",
                        acceptableTypes.stream().map(MIMEHeader::rawValue).collect(Collectors.toList()),
                        rc.request().path());
                rc.response().setStatusCode(406).end();
            } else {
                if (!dataInitializers.isEmpty()) {
                    DataInitializer.InitContext ctx = new DataInitializer.InitContext(path, instance, rc.request());
                    for (DataInitializer initializer : dataInitializers) {
                        try {
                            initializer.initialize(ctx);
                        } catch (Exception e) {
                            LOG.errorf(e, "Error initializing page [%s] with %s", path, initializer.getClass().getName());
                        }
                    }
                }
                instance.renderAsync().whenComplete((r, t) -> {
                    if (t != null) {
                        Throwable rootCause = rootCause(t);
                        LOG.errorf("Error occured while rendering the template [%s]: %s", path, rootCause.toString());
                        rc.fail(rootCause);
                    } else {
                        rc.response().setStatusCode(200).end(r);
                    }
                });
            }
        } else {
            LOG.debugf("Template page not found: %s", rc.request().path());
            rc.next();
        }
    }

    private Throwable rootCause(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root;
    }

    private Variant trySelectVariant(RoutingContext rc, TemplateInstance instance, List<MIMEHeader> acceptableTypes) {
        Object variantsAttr = instance.getAttribute(TemplateInstance.VARIANTS);
        if (variantsAttr != null) {
            @SuppressWarnings("unchecked")
            List<Variant> variants = (List<Variant>) variantsAttr;
            if (!acceptableTypes.isEmpty()) {
                for (MIMEHeader accept : acceptableTypes) {
                    // https://github.com/vert-x3/vertx-web/issues/2388
                    accept.value();
                    for (Variant variant : variants) {
                        if (new ContentType(variant.getContentType()).matches(accept.component(),
                                accept.subComponent())) {
                            return variant;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Extract the real template path, for example:
     * <ul>
     * <li>{@code /item.html} => {@code pub/item}</li>
     * <li>{@code /item?id=1} => {@code pub/item}</li>
     * <li>{@code /nested/item.html?foo=bar} => {@code pub/nested/item}</li>
     * </ul>
     * <p>
     * Note that a path that ends with {@code /} is handled specifically. The {@code index} is appended to the path.
     *
     * @param path
     * @return the template path without suffix
     */
    private String extractTemplatePath(String path) {
        if (path.length() >= rootPath.length()) {
            // /foo.html -> foo.html
            path = path.substring(rootPath.length());
            path = removeLeadingSlash(path);

            // Check if we have a matching linked template
            final String link = templateLinks.get(removeTrailingSlash(path));
            if (link != null) {
                path = link;
            } else {
                // Check if we have a matching template path
                if (path.isEmpty()) {
                    // Root translates to an index page
                    path = "index";
                } else if (path.endsWith("/")) {
                    // "index" is appended to a path that ends with "/"
                    path = path + "index";
                }
                if (!webTemplatesPath.isEmpty()) {
                    // Append the public dir if needed
                    path = webTemplatesPath + "/" + path;
                }
                if (!templatePaths.contains(path)) {
                    // No matching template path exists
                    return null;
                }
            }
            return pathFromVariant(path);
        }
        return null;
    }

    private String pathFromVariant(String path) {
        if (path.contains(".")) {
            for (Map.Entry<String, List<String>> e : quteContext.getVariants().entrySet()) {
                if (e.getValue().contains(path)) {
                    return e.getKey();
                }
            }
        }
        return path;
    }

}
