package app.pathwise.web.platform.http;

import app.pathwise.core.security.AccessDeniedException;
import app.pathwise.core.security.Actor;
import app.pathwise.core.security.NotAuthenticatedException;
import app.pathwise.web.platform.AppContext;
import app.pathwise.web.platform.Json;
import app.pathwise.web.platform.RequestMeta;
import app.pathwise.web.platform.Validation;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/** Base class for {@code /api/v1} JSON endpoints: body parsing, validation and the error envelope. */
public abstract class ApiServlet extends HttpServlet {
    private static final Logger log = LoggerFactory.getLogger(ApiServlet.class);
    private static final int MAX_BODY_BYTES = 64 * 1024;

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        try {
            super.service(req, res);
        } catch (ApiException e) {
            Errors.send(req, res, e.status(), e.code(), e.getMessage(), e.details());
        } catch (NotAuthenticatedException e) {
            Errors.send(req, res, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHENTICATED", "Sign in to continue.");
        } catch (AccessDeniedException e) {
            Errors.send(req, res, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "You are not allowed to do that.");
        } catch (RuntimeException e) {
            log.error("Unhandled API error on {} {}", req.getMethod(), req.getRequestURI(), e);
            Errors.send(req, res, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "INTERNAL",
                    "Something went wrong on our side. Please try again.");
        }
    }

    protected AppContext app() {
        return AppContext.from(getServletContext());
    }

    protected Actor actor(HttpServletRequest req) {
        return AuthFilter.actor(req);
    }

    protected Actor requireActor(HttpServletRequest req) {
        Actor actor = actor(req);
        if (!actor.isAuthenticated()) {
            throw new NotAuthenticatedException();
        }
        return actor;
    }

    protected RequestMeta meta(HttpServletRequest req) {
        return RequestMeta.of(req, app().config());
    }

    /** Path below the servlet mapping, e.g. "/login" for /api/v1/auth/login. Never null. */
    protected static String subPath(HttpServletRequest req) {
        String p = req.getPathInfo();
        return p == null ? "/" : p;
    }

    /** Parses and validates a JSON body into {@code type}. */
    protected <T> T readBody(HttpServletRequest req, Class<T> type) throws IOException {
        String contentType = req.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("application/json")) {
            throw new ApiException(415, "UNSUPPORTED_MEDIA_TYPE", "Send the body as application/json.");
        }
        if (req.getContentLengthLong() > MAX_BODY_BYTES) {
            throw new ApiException(413, "BODY_TOO_LARGE", "Request body is too large.");
        }
        T body;
        try (InputStream in = req.getInputStream()) {
            byte[] bytes = in.readNBytes(MAX_BODY_BYTES + 1);
            if (bytes.length > MAX_BODY_BYTES) {
                throw new ApiException(413, "BODY_TOO_LARGE", "Request body is too large.");
            }
            body = Json.mapper().readValue(bytes, type);
        } catch (JsonProcessingException e) {
            throw new ApiException(400, "INVALID_JSON", "The request body is not valid JSON for this endpoint.");
        }
        if (body == null) {
            throw new ApiException(400, "INVALID_JSON", "A JSON body is required.");
        }
        Map<String, String> errors = Validation.validate(body);
        if (!errors.isEmpty()) {
            throw new ApiException(400, "VALIDATION_FAILED", "Some fields need attention.", errors);
        }
        return body;
    }

    protected void writeJson(HttpServletResponse res, int status, Object body) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write(Json.write(body));
    }

    protected void notFound() {
        throw new ApiException(404, "NOT_FOUND", "No such endpoint.");
    }
}
