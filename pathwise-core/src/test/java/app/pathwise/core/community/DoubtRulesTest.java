package app.pathwise.core.community;

import app.pathwise.core.community.DoubtRules.Scope;
import app.pathwise.core.community.DoubtRules.Viewer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DoubtRulesTest {
    private static final Viewer STRANGER = new Viewer(false, false, false, false);
    private static final Viewer AUTHOR = new Viewer(true, false, false, false);
    private static final Viewer TEACHER = new Viewer(false, true, false, false);
    private static final Viewer MOD = new Viewer(false, false, true, false);
    private static final Viewer EDUCATOR = new Viewer(false, false, false, true);

    @Test
    void privateDoubtsAreForTheAuthorAndTheirTeachers() {
        assertThat(DoubtRules.canSee(Scope.PRIVATE_TEACHER, "open", AUTHOR)).isTrue();
        assertThat(DoubtRules.canSee(Scope.PRIVATE_TEACHER, "open", TEACHER)).isTrue();
        assertThat(DoubtRules.canSee(Scope.PRIVATE_TEACHER, "open", STRANGER)).isFalse();
        assertThat(DoubtRules.canSee(Scope.PRIVATE_TEACHER, "open", EDUCATOR)).isFalse();
        assertThat(DoubtRules.canSee(Scope.PRIVATE_TEACHER, "open", MOD)).isTrue();
    }

    @Test
    void publicDoubtsAreForEveryoneUntilModerated() {
        assertThat(DoubtRules.canSee(Scope.PUBLIC, "resolved", STRANGER)).isTrue();
        assertThat(DoubtRules.canSee(Scope.PUBLIC, "hidden", STRANGER)).isFalse();
        assertThat(DoubtRules.canSee(Scope.PUBLIC, "hidden", AUTHOR)).isTrue();
        assertThat(DoubtRules.canSee(Scope.PUBLIC, "removed", AUTHOR)).isFalse();
        assertThat(DoubtRules.canSee(Scope.PUBLIC, "removed", MOD)).isTrue();
    }

    @Test
    void verificationAndAnonymity() {
        assertThat(DoubtRules.canVerify(Scope.PRIVATE_TEACHER, TEACHER)).isTrue();
        assertThat(DoubtRules.canVerify(Scope.PRIVATE_TEACHER, EDUCATOR)).isFalse();
        assertThat(DoubtRules.canVerify(Scope.PUBLIC, EDUCATOR)).isTrue();
        assertThat(DoubtRules.canVerify(Scope.PUBLIC, TEACHER)).isFalse();
        assertThat(DoubtRules.anonymous(Scope.PUBLIC, false)).isTrue();
        assertThat(DoubtRules.anonymous(Scope.PUBLIC, true)).isFalse();
        assertThat(DoubtRules.anonymous(Scope.PRIVATE_TEACHER, false)).isFalse();
        for (int i = 0; i < 100; i++) {
            assertThat(DoubtRules.newAlias()).isBetween(1000, 9999);
        }
    }
}
