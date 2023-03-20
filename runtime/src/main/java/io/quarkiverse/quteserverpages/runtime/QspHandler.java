package io.quarkiverse.quteserverpages.runtime;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.enterprise.event.Event;

import org.jboss.logging.Logger;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InjectableContext.ContextState;
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
import io.quarkus.vertx.http.runtime.HttpBuildTimeConfig;
import io.quarkus.vertx.http.runtime.security.QuarkusHttpUser;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.MIMEHeader;
import io.vertx.ext.web.RoutingContext;

public class QspHandler implements Handler<RoutingContext> {

    private static final Logger LOG = Logger.getLogger(QspHandler.class);

    private static final String FRAGMENT_PARAM = "frag";

    private final String rootPath;
    private final Set<String> templatePaths;
    private final List<String> compressMediaTypes;
    private final Map<String, String> extractedPaths;

    private final Event<SecurityIdentity> securityIdentityEvent;
    private final CurrentIdentityAssociation currentIdentity;
    private final CurrentVertxRequest currentVertxRequest;
    private final ManagedContext requestContext;
    private final LazyValue<TemplateProducer> templateProducer;
    private final LazyValue<QuteContext> quteContext;

    public QspHandler(String rootPath, Set<String> templatePaths, HttpBuildTimeConfig httpBuildTimeConfig) {
        this.rootPath = rootPath;
        this.templatePaths = templatePaths;
        this.compressMediaTypes = httpBuildTimeConfig.enableCompression
                ? httpBuildTimeConfig.compressMediaTypes.orElse(List.of())
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
        this.quteContext = new LazyValue<>(() -> Arc.container().instance(QuteContext.class).get());
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
        // Extract the template path, e.g. /qp/item.html -> item
        String path = extractedPaths.computeIfAbsent(rc.request().path(), this::extractTemplatePath);

        if (path != null && templatePaths.contains(path)) {
            Template template = templateProducer.get().getInjectableTemplate(path);
            TemplateInstance originalInstance = template.instance();
            TemplateInstance instance = originalInstance;

            // It is possible to specify the fragment via query param, e.g. /qp/item?frag=detail
            String fragmentId = rc.request().getParam(FRAGMENT_PARAM);
            if (fragmentId != null) {
                Fragment fragment = template.getFragment(fragmentId);
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
                instance.renderAsync().whenComplete((r, t) -> {
                    if (t != null) {
                        LOG.errorf(t, "Error occured during rendering template: %s", path);
                        rc.response().setStatusCode(500).end();
                    } else {
                        rc.response().setStatusCode(200).end(r);
                    }
                });
            }
        } else {
            LOG.debugf("Template page not found: %s", rc.request().path());
            rc.next();
            ;
        }
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
     * Extract the template path, e.g.:
     * <p>
     * {@code /qsp/item.html} -> {@code item}
     * <p>
     * {@code /qsp/item?id=1} -> {@code item}
     * <p>
     * {@code /qsp/nested/item.html?foo=bar} -> {@code nested/item}
     *
     * @param rc
     * @return the template path without suffix
     */
    private String extractTemplatePath(String path) {
        if (path.length() > rootPath.length()) {
            path = path.substring(rootPath.length());
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            if (path.contains(".")) {
                Map<String, List<String>> allVariants = quteContext.get().getVariants();
                for (Entry<String, List<String>> e : allVariants.entrySet()) {
                    if (e.getValue().contains(path)) {
                        path = e.getKey();
                        break;
                    }
                }
            }
            return path;
        }
        return null;
    }

}
