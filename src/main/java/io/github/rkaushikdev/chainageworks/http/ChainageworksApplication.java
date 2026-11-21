package io.github.rkaushikdev.chainageworks.http;

import io.github.rkaushikdev.chainageworks.app.Service;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
        application.setDefaultProperties(Map.of(
                "server.address",
                service.config().http().host(),
                "server.port",
                service.config().http().port(),
                "spring.main.banner-mode",
                "off",
                "spring.main.log-startup-info",
                false,
                "spring.web.resources.add-mappings",
                false));
        return application.run(args);
    }

    /** The port {@code context} is listening on, which is the real one when any free port was asked for. */
    public static int port(ApplicationContext context) {
        if (context instanceof WebServerApplicationContext web) {
            return web.getWebServer().getPort();
        }
        throw new IllegalArgumentException("that application is not serving anything");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RouteGuard());
    }
}
