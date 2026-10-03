package app.pathwise.web.platform.events;

import app.pathwise.data.Tx;
import app.pathwise.data.platform.OutboxDao;
import app.pathwise.data.platform.OutboxDao.OutboxEvent;
import app.pathwise.web.platform.Json;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

/**
 * Delivers outbox events to handlers. Each event is handled in its own transaction with the row locked,
 * so a crash mid-delivery simply retries the event later.
 */
public final class OutboxWorker {
    private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);
    static final int MAX_ATTEMPTS = 8;
    private static final int BATCH = 50;

    private final Tx tx;
    private final EventBus bus;
    private final OutboxDao outbox = new OutboxDao();

    public OutboxWorker(Tx tx, EventBus bus) {
        this.tx = tx;
        this.bus = bus;
    }

    /** Processes every due event once. Returns the number of events attempted. Safe to call concurrently. */
    public int drain() {
        int attempted = 0;
        while (true) {
            int handled = tx.call(c -> {
                List<OutboxEvent> due = outbox.claimDue(c, 1);
                if (due.isEmpty()) {
                    return 0;
                }
                OutboxEvent event = due.get(0);
                try {
                    JsonNode payload = Json.mapper().readTree(event.payloadJson());
                    for (EventBus.Handler h : bus.handlersFor(event.type())) {
                        h.handle(payload);
                    }
                    outbox.markProcessed(c, event.id());
                } catch (Exception e) {
                    Duration backoff = Duration.ofSeconds((long) Math.min(3600, Math.pow(2, event.attempts() + 1)));
                    log.warn("Event {} ({}) failed on attempt {}: {}", event.id(), event.type(), event.attempts() + 1, e.toString());
                    outbox.markAttemptFailed(c, event.id(), e.toString(), backoff, MAX_ATTEMPTS);
                }
                return 1;
            });
            if (handled == 0 || ++attempted >= BATCH) {
                return attempted;
            }
        }
    }
}
