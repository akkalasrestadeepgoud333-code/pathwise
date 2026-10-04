package app.pathwise.core.community;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class LiveSessionRulesTest {

    @Test
    void lifecycleOnlyMovesForward() {
        assertThat(LiveSessionRules.canMove("scheduled", "live")).isTrue();
        assertThat(LiveSessionRules.canMove("scheduled", "ended")).isTrue();
        assertThat(LiveSessionRules.canMove("live", "ended")).isTrue();
        assertThat(LiveSessionRules.canMove("live", "scheduled")).isFalse();
        assertThat(LiveSessionRules.canMove("ended", "live")).isFalse();
        assertThat(LiveSessionRules.canMove("nonsense", "live")).isFalse();
        assertThat(LiveSessionRules.canMoveQuestion("answered", "queued")).isFalse();
        assertThat(LiveSessionRules.canMoveQuestion("dismissed", "queued")).isTrue();
    }

    @Test
    void joiningRespectsLockRemovalAndEnd() {
        assertThat(LiveSessionRules.canJoin("scheduled", false, null)).isTrue();
        assertThat(LiveSessionRules.canJoin("live", true, null)).isFalse();
        assertThat(LiveSessionRules.canJoin("live", true, "left")).isTrue();
        assertThat(LiveSessionRules.canJoin("live", false, "removed")).isFalse();
        assertThat(LiveSessionRules.canJoin("ended", false, "active")).isFalse();
    }

    @Test
    void askingNeedsALiveSessionAnActiveSeatAndRoomInTheQueue() {
        assertThat(LiveSessionRules.canAsk("live", "active", 0)).isTrue();
        assertThat(LiveSessionRules.canAsk("live", "active", LiveSessionRules.MAX_QUEUED_PER_PERSON)).isFalse();
        assertThat(LiveSessionRules.canAsk("scheduled", "active", 0)).isFalse();
        assertThat(LiveSessionRules.canAsk("live", "left", 0)).isFalse();
        assertThat(LiveSessionRules.canAsk("live", null, 0)).isFalse();
    }

    @Test
    void queueShowsTheCurrentQuestionThenMostVotedThenOldest() {
        Instant t = Instant.parse("2026-10-04T10:00:00Z");
        var a = new LiveSessionRules.Entry("queued", 1, t);
        var b = new LiveSessionRules.Entry("queued", 3, t.plusSeconds(60));
        var c = new LiveSessionRules.Entry("queued", 1, t.minusSeconds(60));
        var d = new LiveSessionRules.Entry("answering", 0, t.plusSeconds(120));
        var e = new LiveSessionRules.Entry("answered", 9, t);
        var f = new LiveSessionRules.Entry("dismissed", 9, t);
        assertThat(LiveSessionRules.ordered(List.of(a, b, c, d, e, f), Function.identity())).containsExactly(d, b, c, a, e, f);
        assertThat(LiveSessionRules.aliasLabel(4)).isEqualTo("Participant 4");
    }
}
