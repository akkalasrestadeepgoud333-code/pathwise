package app.pathwise.core.ids;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IdsTest {

    @Test
    void generatesVersion7WithIetfVariant() {
        UUID id = Ids.newId();
        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void idsAreUniqueAndStrictlyIncreasingWithinTheJvm() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 20_000; i++) {
            ids.add(Ids.newId());
        }
        assertThat(new HashSet<>(ids)).hasSize(ids.size());
        for (int i = 1; i < ids.size(); i++) {
            // Compare as unsigned big-endian strings: that is how Postgres orders uuid values.
            assertThat(ids.get(i).toString()).isGreaterThan(ids.get(i - 1).toString());
        }
    }

    @Test
    void embedsTheCurrentTimestamp() {
        long before = System.currentTimeMillis();
        UUID id = Ids.newId();
        long millis = id.getMostSignificantBits() >>> 16;
        assertThat(millis).isBetween(before, System.currentTimeMillis() + 5);
    }

    @Test
    void parseOrNullRejectsGarbage() {
        assertThat(Ids.parseOrNull(null)).isNull();
        assertThat(Ids.parseOrNull("not-a-uuid")).isNull();
        assertThat(Ids.parseOrNull("zzzzzzzz-zzzz-zzzz-zzzz-zzzzzzzzzzzz")).isNull();
        UUID id = Ids.newId();
        assertThat(Ids.parseOrNull(id.toString())).isEqualTo(id);
    }
}
