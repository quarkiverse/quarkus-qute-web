package io.quarkiverse.qute.web.asciidoc.deployment.devservice;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.jboss.logging.Logger;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

import io.quarkiverse.qute.web.asciidoc.runtime.QuteWebAsciidocBuildTimeConfig;
import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CuratedApplicationShutdownBuildItem;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem;
import io.quarkus.deployment.builditem.DockerStatusBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.console.ConsoleInstalledBuildItem;
import io.quarkus.deployment.console.StartupLogCompressor;
import io.quarkus.deployment.dev.devservices.GlobalDevServicesConfig;
import io.quarkus.deployment.logging.LoggingSetupBuildItem;
import io.quarkus.devservices.common.ConfigureUtil;
import io.quarkus.devservices.common.ContainerAddress;
import io.quarkus.devservices.common.ContainerLocator;
import io.quarkus.runtime.configuration.ConfigUtils;

public class DevServiceProcessor {

    /**
     * Label to add to shared Dev Service for kroki server running in containers.
     * This allows other applications to discover the running service and use it instead of starting a new instance.
     */
    static final String DEV_SERVICE_LABEL = "kroki-dev-service";
    static final int KROKI_PORT = 8000;
    private static final Logger LOGGER = Logger.getLogger(DevServiceProcessor.class);
    private static final String KROKI_URL_KEY = "quarkus.rest-client.kroki.url";
    private static final ContainerLocator krokiContainerLocator = new ContainerLocator(DEV_SERVICE_LABEL, KROKI_PORT);
    static volatile DevServicesResultBuildItem.RunningDevService devService;

    static volatile boolean first = true;

    @BuildStep(onlyIfNot = IsNormal.class, onlyIf = GlobalDevServicesConfig.Enabled.class)
    public DevServicesResultBuildItem startEnvProviderDevService(
            DockerStatusBuildItem dockerStatusBuildItem,
            LaunchModeBuildItem launchMode,
            Optional<ConsoleInstalledBuildItem> consoleInstalledBuildItem,
            CuratedApplicationShutdownBuildItem closeBuildItem,
            LoggingSetupBuildItem loggingSetupBuildItem,
            QuteWebAsciidocBuildTimeConfig buildTimeConfig,
            GlobalDevServicesConfig devServicesConfig,
            PrerenderingDevservicesConfig prerenderingDevservicesConfig) {

        StartupLogCompressor compressor = new StartupLogCompressor(
                (launchMode.isTest() ? "(test) " : "") + "Qute web asciidoc prerendering Dev Services Starting:",
                consoleInstalledBuildItem, loggingSetupBuildItem);

        try {
            if (buildTimeConfig.prerenderDiagram()) {
                devService = startKrokiServer(dockerStatusBuildItem, prerenderingDevservicesConfig, launchMode,
                        devServicesConfig.timeout);
            }
            if (devService == null) {
                compressor.closeAndDumpCaptured();
            } else {
                compressor.close();
            }
        } catch (Throwable t) {
            compressor.closeAndDumpCaptured();
            throw t instanceof RuntimeException ? (RuntimeException) t : new RuntimeException(t);
        }

        if (devService == null) {
            return null;
        }

        if (first) {
            first = false;
            Runnable closeTask = () -> {
                if (devService != null) {
                    shutdownServer();
                }
                first = true;
                devService = null;
            };
            closeBuildItem.addCloseTask(closeTask, true);
        }

        if (devService.isOwner()) {
            LOGGER.infof("Dev Services for Kroki started on %s", getKrokiUrl());
            LOGGER.infof("Other Quarkus applications in dev mode will find the "
                    + "instance automatically. For Quarkus applications in production mode, you can connect to"
                    + " this by starting your application with -D%s=%s",
                    KROKI_URL_KEY, getKrokiUrl());
        }

        return devService.toBuildItem();
    }

    private Object getKrokiUrl() {
        return devService.getConfig().get(KROKI_URL_KEY);
    }

    private void shutdownServer() {
        if (devService != null) {
            try {
                devService.close();
            } catch (Throwable e) {
                LOGGER.error("Failed to stop the Kroki server", e);
            } finally {
                devService = null;
            }
        }
    }

    private DevServicesResultBuildItem.RunningDevService startKrokiServer(
            DockerStatusBuildItem dockerStatusBuildItem,
            PrerenderingDevservicesConfig devServicesConfig,
            LaunchModeBuildItem launchMode,
            Optional<Duration> timeout) {

        // Check if kroki server url is set
        if (ConfigUtils.isPropertyPresent(KROKI_URL_KEY)) {
            LOGGER.debugf("Not starting dev services for diagram prerendering, the % is configured.", KROKI_URL_KEY);
            return null;
        }

        // Check if kroki devservice is enabled
        if (!devServicesConfig.enabled()) {
            LOGGER.debugf("Not starting dev services for diagram prerendering, as it was explicitely disabled");
            return null;
        }

        if (!dockerStatusBuildItem.isContainerRuntimeAvailable()) {
            LOGGER.warnf("Docker isn't working, please configure the Kroki Url property (%s).", KROKI_URL_KEY);
            return null;
        }

        final Optional<ContainerAddress> maybeContainerAddress = krokiContainerLocator.locateContainer(DEV_SERVICE_LABEL,
                true,
                launchMode.getLaunchMode());

        // Starting the server
        final Supplier<DevServicesResultBuildItem.RunningDevService> defaultKrokiServerSupplier = () -> {
            KrokiContainer container = new KrokiContainer(
                    DockerImageName.parse(devServicesConfig.imageName()));

            ConfigureUtil.configureSharedNetwork(container, "kroki");

            timeout.ifPresent(container::withStartupTimeout);

            container.start();
            return new DevServicesResultBuildItem.RunningDevService(DEV_SERVICE_LABEL,
                    container.getContainerId(),
                    container::close,
                    Map.of(KROKI_URL_KEY, "http://%s:%d".formatted(container.getHost(), container.getPort())));
        };

        return maybeContainerAddress
                .map(containerAddress -> new DevServicesResultBuildItem.RunningDevService(DEV_SERVICE_LABEL,
                        containerAddress.getId(),
                        null,
                        Map.of(KROKI_URL_KEY,
                                "http://%s:%d".formatted(containerAddress.getHost(), containerAddress.getPort()))))
                .orElseGet(defaultKrokiServerSupplier);
    }

    /**
     * Container configuring and starting the Kroki server.
     */
    private static final class KrokiContainer extends GenericContainer<KrokiContainer> {

        private KrokiContainer(DockerImageName dockerImageName) {
            super(dockerImageName);
            withNetwork(Network.SHARED);
            withExposedPorts(KROKI_PORT);
        }

        public int getPort() {
            return getMappedPort(KROKI_PORT);
        }
    }
}
