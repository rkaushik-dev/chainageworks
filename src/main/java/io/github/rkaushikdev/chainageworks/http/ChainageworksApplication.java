package io.github.rkaushikdev.chainageworks.http;

import io.github.rkaushikdev.chainageworks.app.Service;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.logging.LoggingApplicationListener;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * The HTTP API over one job: the routes, the problem bodies, the request ids and the route listing.
 *
 * <p>There is one factory, {@link #run}, and the service is handed in rather than built here, because
 * the command line has already loaded the job and checked the config before anything is served. The
 * service becomes the one bean every route reads, and Boot builds the web server and the request
 * handling around the routes but nothing that holds any part of the job.
 *
 * <p>Boot's own logging set up is left out, so starting the server does not reconfigure the logging
 * the process already has, and the web server's own log is passed to the same loggers. Boot's error
 * page is left out too: every failure is answered by {@link ApiErrors} in the API's own shape, and a
 * route of Boot's would appear in the route listing.
 */
@SpringBootApplication(exclude = ErrorMvcAutoConfiguration.class)
public class ChainageworksApplication implements WebMvcConfigurer {

    /**
     * Start serving {@code service} where its config says to listen.
     *
     * @param args Spring arguments, which override the config: {@code --server.port=0} listens on any
     *     free port
     * @return the running application; closing it stops the server
     */
    public static ConfigurableApplicationContext run(Service service, String... args) {
        Objects.requireNonNull(service, "service");
        if (!SLF4JBridgeHandler.isInstalled()) {
            SLF4JBridgeHandler.removeHandlersForRootLogger();
            SLF4JBridgeHandler.install();
        }
        SpringApplication application = new SpringApplicationBuilder(ChainageworksApplication.class)
                .initializers(context -> context.getBeanFactory().registerSingleton("service", service))
                .build();
        List<ApplicationListener<?>> listeners = application.getListeners().stream()
                .filter(listener -> !(listener instanceof LoggingApplicationListener))
                .toList();
        application.setListeners(listeners);
        application.setDefaultProperties(Map.ofEntries(
                Map.entry("server.address", service.config().http().host()),
                Map.entry("server.port", service.config().http().port()),
                Map.entry("spring.main.banner-mode", "off"),
                Map.entry("spring.main.log-startup-info", false),
                Map.entry("spring.web.resources.add-mappings", false)));
        return application.run(args);
    }

    /** The port {@code context} is listening on, which is the real one when any free port was asked for. */
    public static int port(ApplicationContext context) {
        if (context instanceof WebServerApplicationContext web) {
            return web.getWebServer().getPort();
        }
        throw new IllegalArgumentException("that application is not serving anything");
    }

    /**
     * Block until {@code context} is closed, by {@link ConfigurableApplicationContext#close} from another
     * thread or by the process being stopped: Boot closes the context on {@code SIGINT} and {@code
     * SIGTERM}, letting the requests in flight finish first.
     *
     * @throws InterruptedException when the waiting thread is interrupted, after closing the context
     */
    public static void awaitClose(ConfigurableApplicationContext context) throws InterruptedException {
        CountDownLatch closed = new CountDownLatch(1);
        context.addApplicationListener(event -> {
            if (event instanceof ContextClosedEvent) {
                closed.countDown();
            }
        });
        try {
            while (context.isActive()) {
                if (closed.await(1, TimeUnit.SECONDS)) {
                    return;
                }
            }
        } catch (InterruptedException interrupted) {
            context.close();
            throw interrupted;
        }
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RouteGuard());
    }
}
