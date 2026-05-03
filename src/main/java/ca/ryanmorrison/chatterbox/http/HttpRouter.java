package ca.ryanmorrison.chatterbox.http;

import io.javalin.http.Handler;

/**
 * Route registration surface exposed to modules. Backed by Javalin under the
 * hood; modules see only the registration verbs they need so the server's
 * lifecycle remains owned by {@link HttpServer}.
 */
public interface HttpRouter {
    HttpRouter get(String path, Handler handler);
    HttpRouter post(String path, Handler handler);
    HttpRouter put(String path, Handler handler);
    HttpRouter patch(String path, Handler handler);
    HttpRouter delete(String path, Handler handler);
}
