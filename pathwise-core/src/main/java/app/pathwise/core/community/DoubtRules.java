package app.pathwise.core.community;

import java.security.SecureRandom;
import java.util.Set;

/**
 * Who may see a doubt, how authors appear, and who may verify answers (blueprint §19). Pure rules; the
 * service supplies the facts (is the viewer the author, a teacher of the room, a moderator).
 */
public final class DoubtRules {
    public static final Set<String> CATEGORIES = Set.of("concept", "problem_solving", "coding", "debugging", "resources",
            "where_wrong", "teacher_help");
    private static final SecureRandom RANDOM = new SecureRandom();

    private DoubtRules() {}

    public enum Scope { PRIVATE_TEACHER, PUBLIC }

    /** The viewer's relationship to a thread. */
    public record Viewer(boolean author, boolean roomTeacher, boolean moderator, boolean verifiedEducator) {}

    /**
     * Private doubts: the author and the room's teachers. Public doubts: everyone signed in, while visible;
     * hidden or removed threads only for moderators (and hidden ones for their author, so they know why).
     */
    public static boolean canSee(Scope scope, String status, Viewer v) {
        if (v.moderator()) {
            return true;
        }
        if ("removed".equals(status)) {
            return false;
        }
        if ("hidden".equals(status)) {
            return v.author();
        }
        return scope == Scope.PUBLIC || v.author() || v.roomTeacher();
    }

    /** Teacher Verified: the room's teachers on private doubts; Verified Educators and moderators on public ones. */
    public static boolean canVerify(Scope scope, Viewer v) {
        return scope == Scope.PRIVATE_TEACHER ? v.roomTeacher() || v.moderator() : v.verifiedEducator() || v.moderator();
    }

    /**
     * How a person appears. Private doubts always use real names (the teacher must know who asks). In the
     * public network people are anonymous unless they chose to show their name.
     */
    public static boolean anonymous(Scope scope, boolean showName) {
        return scope == Scope.PUBLIC && !showName;
    }

    public static String aliasLabel(int alias, boolean isAsker) {
        return (isAsker ? "Anonymous asker #" : "Anonymous helper #") + alias;
    }

    public static int newAlias() {
        return 1000 + RANDOM.nextInt(9000);
    }
}
