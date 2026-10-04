package app.pathwise.web.questions;

import app.pathwise.data.questions.QuestionDao.OptionRow;
import app.pathwise.data.questions.QuestionDao.QuestionRow;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionServiceTest {

    private static QuestionRow question() {
        List<OptionRow> options = IntStream.range(0, 4)
                .mapToObj(i -> new OptionRow(UUID.randomUUID(), i, "Option " + i, i == 0, "why " + i)).toList();
        return new QuestionRow(UUID.randomUUID(), "q", "single_choice", "Stem?", "{}", 2, "[\"h1\",\"h2\"]", "Because.",
                options, List.of());
    }

    @Test
    void optionOrderIsStablePerStudentAndDoesNotAlwaysPutTheAnswerFirst() {
        QuestionRow q = question();
        UUID student = UUID.randomUUID();
        assertThat(QuestionService.shuffled(q, student)).isEqualTo(QuestionService.shuffled(q, student));
        assertThat(QuestionService.shuffled(q, student)).containsExactlyInAnyOrderElementsOf(q.options());

        // Across many students the correct option lands in every position, not always first.
        Set<Integer> positions = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            List<OptionRow> order = QuestionService.shuffled(q, UUID.randomUUID());
            positions.add(order.indexOf(q.options().get(0)));
        }
        assertThat(positions).containsExactlyInAnyOrder(0, 1, 2, 3);
    }

    @Test
    void studentViewsNeverContainAnswersOrRationales() {
        QuestionRow q = question();
        var view = QuestionService.view(q, UUID.randomUUID());
        assertThat(view.options()).hasSize(4);
        assertThat(view.toString()).doesNotContain("why 0").doesNotContain("Because.");
        assertThat(view.hintsAvailable()).isEqualTo(2);
    }

    @Test
    void responsesRoundTripThroughJson() {
        UUID a = UUID.randomUUID();
        var r = new QuestionService.Response(Set.of(a), "8, 4");
        var back = QuestionService.Response.fromJson(r.toJson());
        assertThat(back.options()).containsExactly(a);
        assertThat(back.text()).isEqualTo("8, 4");
        assertThat(QuestionService.Response.fromJson("not json").options()).isEmpty();
    }
}
