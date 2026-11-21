package io.github.rkaushikdev.chainageworks.http;

import io.github.rkaushikdev.chainageworks.util.Patterns;
import io.github.rkaushikdev.chainageworks.util.Text;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.support.RequestContextUtils;

/**
 * Every route the server answers, read off the router itself.
 *
 * <p>Read off the request mappings rather than kept in a list, because a list beside the routes is a
 * list that stops matching them the first week. A path parameter is written {@code :name}, as in
 * {@code /packages/:id}, and a route that answers {@code GET} answers {@code HEAD} as well.
 *
 * @param entries the routes, in path order
 */
record RouteTable(List<RouteEntry> entries) {

    /** The bean every application context of this server registers its request mappings under. */
    private static final String MAPPINGS = "requestMappingHandlerMapping";

    private static final Pattern VARIABLE = Patterns.compile("\\{([^}:]+)(?::[^}]*)?\\}");

    RouteTable {
        entries = List.copyOf(entries);
    }

    /**
     * One route and the methods it answers.
     *
     * @param path the path, parameters written {@code :name}
     * @param methods the methods, in alphabetical order
     */
    record RouteEntry(String path, List<String> methods) {

        RouteEntry {
            Objects.requireNonNull(path, "path");
            methods = List.copyOf(methods);
        }
    }

    /** The routes of the server that is answering {@code request}. */
    static RouteTable of(HttpServletRequest request) {
        WebApplicationContext context = Objects.requireNonNull(
                RequestContextUtils.findWebApplicationContext(request), "a request is answered inside a context");
        return of(context.getBean(MAPPINGS, RequestMappingHandlerMapping.class)
                .getHandlerMethods()
                .keySet());
    }

    /** The routes the request mappings {@code mappings} declare. */
    static RouteTable of(Collection<RequestMappingInfo> mappings) {
        Map<String, Set<String>> byPath = new TreeMap<>();
        for (RequestMappingInfo mapping : mappings) {
            Set<RequestMethod> declared = mapping.getMethodsCondition().getMethods();
            for (String pattern : mapping.getPatternValues()) {
                Set<String> methods = byPath.computeIfAbsent(path(pattern), path -> new TreeSet<>());
                for (RequestMethod method : declared) {
                    methods.add(method.name());
                    if (method == RequestMethod.GET) {
                        methods.add(RequestMethod.HEAD.name());
                    }
                }
            }
        }
        List<RouteEntry> entries = new ArrayList<>();
        byPath.forEach((path, methods) -> entries.add(new RouteEntry(path, List.copyOf(methods))));
        return new RouteTable(entries);
    }

    /**
     * The methods a path would answer, so a request with the wrong one can be told what to use.
     *
     * <p>A route fits when it has as many segments as the path and every segment is a parameter or the
     * same text. The query is not part of the path, and empty segments are not counted.
     *
     * @param url the path as it was requested, with or without its query
     * @return the methods, in alphabetical order; empty when no route has that shape
     */
    List<String> methodsFor(String url) {
        int query = url.indexOf('?');
        List<String> segments = segments(query < 0 ? url : url.substring(0, query));
        Set<String> methods = new TreeSet<>();
        for (RouteEntry entry : entries) {
            List<String> route = segments(entry.path());
            if (route.size() == segments.size() && fits(route, segments)) {
                methods.addAll(entry.methods());
            }
        }
        return List.copyOf(methods);
    }

    private static boolean fits(List<String> route, List<String> segments) {
        for (int i = 0; i < route.size(); i++) {
            if (!route.get(i).startsWith(":") && !route.get(i).equals(segments.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static List<String> segments(String path) {
        return Text.split(path, "/").stream().filter(part -> !part.isEmpty()).toList();
    }

    private static String path(String pattern) {
        return VARIABLE.matcher(pattern).replaceAll(":$1");
    }
}
