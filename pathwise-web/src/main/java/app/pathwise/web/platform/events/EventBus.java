package app.pathwise.web.platform.events;

import app.pathwise.data.platform.OutboxDao;
import app.pathwise.web.platform.Json;
import com.fasterxml.jackson.databind.JsonNode;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-process domain events over a transactional outbox. {@link #publish} writes the event in the
 * caller's transaction, so the event exists only if the change commits. {@link OutboxWorker} delivers
 * events to handlers after commit, with retries. Handlers must be idempotent.
 */
public final class EventBus {

    @FunctionalInterface
    public interface Handler {
        void handle(JsonNode payload) throws Exception;
    }

    private final OutboxDao outbox = new OutboxDao();
    private final Map<String, List<Handler>> handlers = new ConcurrentHashMap<>();

    public void publish(Connection c, String type, Object payload) throws SQLException {
        outbox.insert(c, type, Json.write(payload == null ? Map.of() : payload));
    }

    public void subscribe(String type, Handler handler) {
        handlers.computeIfAbsent(type, t -> new CopyOnWriteArrayList<>()).add(handler);
    }

    List<Handler> handlersFor(String type) {
        return handlers.getOrDefault(type, List.of());
    }
}
