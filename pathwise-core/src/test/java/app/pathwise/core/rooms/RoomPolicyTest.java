package app.pathwise.core.rooms;

import app.pathwise.core.rooms.RoomPolicy.Action;
import app.pathwise.core.rooms.RoomPolicy.Membership;
import app.pathwise.core.rooms.RoomPolicy.Perms;
import app.pathwise.core.rooms.RoomPolicy.Role;
import app.pathwise.core.rooms.RoomPolicy.RoomState;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** The full room permission matrix (blueprint §18), checked cell by cell. */
class RoomPolicyTest {
    private static final RoomState OPEN = new RoomState(false, false);
    private static final RoomState OPEN_WITH_STATS = new RoomState(false, true);
    private static final RoomState ARCHIVED = new RoomState(true, false);

    private static Set<Action> allowed(Membership m, RoomState room) {
        Set<Action> out = EnumSet.noneOf(Action.class);
        for (Action a : Action.values()) {
            if (RoomPolicy.can(m, room, a)) {
                out.add(a);
            }
        }
        return out;
    }

    @Test
    void ownerCanDoEverythingExceptStudentWork() {
        Membership owner = new Membership(Role.OWNER, true, Perms.NONE);
        Set<Action> expected = EnumSet.allOf(Action.class);
        expected.remove(Action.DO_ROOM_WORK);
        assertThat(allowed(owner, OPEN)).isEqualTo(expected);
    }

    @Test
    void coTeacherRightsFollowTheirToggles() {
        Membership bare = new Membership(Role.CO_TEACHER, true, Perms.NONE);
        assertThat(allowed(bare, OPEN)).containsExactlyInAnyOrder(Action.VIEW_ROOM, Action.USE_TEACHER_VIEW,
                Action.MANAGE_STUDENTS, Action.POST_ANNOUNCEMENT, Action.VIEW_CLASS_AGGREGATES);
        Map<Action, Perms> toggles = Map.of(
                Action.AUTHOR, new Perms(true, false, false, false, false),
                Action.PUBLISH, new Perms(false, true, false, false, false),
                Action.GRADE, new Perms(false, false, true, false, false),
                Action.MODERATE, new Perms(false, false, false, true, false),
                Action.VIEW_INDIVIDUAL_ANALYTICS, new Perms(false, false, false, false, true));
        toggles.forEach((action, perms) -> {
            Set<Action> got = allowed(new Membership(Role.CO_TEACHER, true, perms), OPEN);
            Set<Action> extra = new HashSet<>(got);
            extra.removeAll(allowed(bare, OPEN));
            assertThat(extra).as(action.name()).containsExactly(action);
        });
        Membership full = new Membership(Role.CO_TEACHER, true, new Perms(true, true, true, true, true));
        assertThat(allowed(full, OPEN)).doesNotContain(Action.EDIT_SETTINGS, Action.ARCHIVE, Action.MANAGE_CO_TEACHERS,
                Action.DO_ROOM_WORK);
    }

    @Test
    void studentsDoWorkAndSeeClassStatsOnlyWhenEnabled() {
        Membership student = new Membership(Role.STUDENT, true, null);
        assertThat(allowed(student, OPEN)).containsExactlyInAnyOrder(Action.VIEW_ROOM, Action.DO_ROOM_WORK);
        assertThat(allowed(student, OPEN_WITH_STATS)).containsExactlyInAnyOrder(Action.VIEW_ROOM, Action.DO_ROOM_WORK,
                Action.VIEW_CLASS_AGGREGATES);
    }

    @Test
    void pendingRemovedAndStrangersGetNothing() {
        assertThat(allowed(null, OPEN)).isEmpty();
        assertThat(allowed(new Membership(Role.STUDENT, false, null), OPEN)).isEmpty();
        assertThat(allowed(new Membership(Role.OWNER, false, Perms.NONE), OPEN)).isEmpty();
    }

    @Test
    void archivedRoomsAreReadOnly() {
        assertThat(allowed(new Membership(Role.OWNER, true, Perms.NONE), ARCHIVED)).containsExactlyInAnyOrder(
                Action.VIEW_ROOM, Action.USE_TEACHER_VIEW, Action.VIEW_CLASS_AGGREGATES, Action.VIEW_INDIVIDUAL_ANALYTICS,
                Action.EDIT_SETTINGS);
        assertThat(allowed(new Membership(Role.STUDENT, true, null), ARCHIVED)).containsExactly(Action.VIEW_ROOM);
        assertThat(allowed(new Membership(Role.CO_TEACHER, true, new Perms(true, true, true, true, true)), ARCHIVED))
                .doesNotContain(Action.AUTHOR, Action.PUBLISH, Action.GRADE, Action.POST_ANNOUNCEMENT, Action.EDIT_SETTINGS);
    }

    @Test
    void codesAreReadableAndForgiving() {
        for (int i = 0; i < 200; i++) {
            String code = RoomCodes.generate();
            assertThat(code).hasSize(8).matches("[" + RoomCodes.ALPHABET + "]{8}").doesNotContain("0", "O", "1", "I", "L");
            assertThat(RoomCodes.normalise(RoomCodes.format(code).toLowerCase())).isEqualTo(code);
        }
        assertThat(RoomCodes.normalise(" k7m3 - qx9p ")).isEqualTo("K7M3QX9P");
        assertThat(RoomCodes.normalise("K7M3QX9")).isNull();
        assertThat(RoomCodes.normalise("K7M3QX0P")).isNull();
        assertThat(RoomCodes.format("K7M3QX9P")).isEqualTo("K7M3-QX9P");
    }
}
