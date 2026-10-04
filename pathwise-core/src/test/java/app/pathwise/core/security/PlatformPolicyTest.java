package app.pathwise.core.security;

import app.pathwise.core.identity.PlatformRole;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static app.pathwise.core.identity.PlatformRole.*;
import static app.pathwise.core.security.PlatformAction.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The full permission matrix: every action × every role combination that exists in practice. */
class PlatformPolicyTest {

    private static Actor actor(PlatformRole... roles) {
        return new Actor(UUID.randomUUID(), "Test", Set.of(roles), UUID.randomUUID());
    }

    static Stream<Arguments> matrix() {
        return Stream.of(
                // action,            student, teacher, mentor, admin
                Arguments.of(USE_STUDENT_AREA, true, true, true, true),
                Arguments.of(MANAGE_OWN_ACCOUNT, true, true, true, true),
                Arguments.of(USE_TEACHER_AREA, false, true, false, true),
                Arguments.of(CREATE_ROOM, false, true, false, true),
                Arguments.of(USE_ADMIN_AREA, false, false, false, true),
                Arguments.of(MANAGE_USERS, false, false, false, true),
                Arguments.of(GRANT_ROLES, false, false, false, true),
                Arguments.of(VIEW_AUDIT_LOG, false, false, false, true),
                Arguments.of(MODERATE_COMMUNITY, false, false, false, true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("matrix")
    void permissionMatrix(PlatformAction action, boolean student, boolean teacher, boolean mentor, boolean admin) {
        assertThat(PlatformPolicy.can(actor(STUDENT), action)).isEqualTo(student);
        assertThat(PlatformPolicy.can(actor(STUDENT, TEACHER), action)).isEqualTo(teacher);
        assertThat(PlatformPolicy.can(actor(STUDENT, MENTOR), action)).isEqualTo(mentor);
        assertThat(PlatformPolicy.can(actor(STUDENT, ADMIN), action)).isEqualTo(admin);
    }

    @Test
    void anonymousVisitorsCanDoNothing() {
        for (PlatformAction action : PlatformAction.values()) {
            assertThat(PlatformPolicy.can(Actor.anonymous(), action)).as(action.name()).isFalse();
            assertThat(PlatformPolicy.can(null, action)).isFalse();
        }
    }

    @Test
    void requireDistinguishesUnauthenticatedFromForbidden() {
        assertThatThrownBy(() -> PlatformPolicy.require(Actor.anonymous(), USE_STUDENT_AREA))
                .isInstanceOf(NotAuthenticatedException.class);
        assertThatThrownBy(() -> PlatformPolicy.require(actor(STUDENT), USE_ADMIN_AREA))
                .isInstanceOf(AccessDeniedException.class);
        PlatformPolicy.require(actor(STUDENT, ADMIN), USE_ADMIN_AREA);
    }

    @Test
    void homePathFollowsTheHighestRole() {
        assertThat(actor(STUDENT).homePath()).isEqualTo("/app/today");
        assertThat(actor(STUDENT, TEACHER).homePath()).isEqualTo("/teach");
        assertThat(actor(STUDENT, TEACHER, ADMIN).homePath()).isEqualTo("/admin");
    }
}
