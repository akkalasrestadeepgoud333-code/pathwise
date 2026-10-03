package app.pathwise.web.admin;

import app.pathwise.core.security.Actor;
import app.pathwise.core.security.PlatformAction;
import app.pathwise.core.security.PlatformPolicy;
import app.pathwise.data.Tx;
import app.pathwise.data.platform.PlatformStatsDao;

import java.util.List;

/** Read-only platform overview for administrators. Every method checks the admin policy itself. */
public final class AdminService {
    private final Tx tx;
    private final PlatformStatsDao stats = new PlatformStatsDao();

    public AdminService(Tx tx) {
        this.tx = tx;
    }

    public PlatformStatsDao.Overview overview(Actor actor) {
        PlatformPolicy.require(actor, PlatformAction.USE_ADMIN_AREA);
        return tx.read(stats::overview);
    }

    public List<PlatformStatsDao.AuditEntry> recentAudit(Actor actor, int limit) {
        PlatformPolicy.require(actor, PlatformAction.VIEW_AUDIT_LOG);
        return tx.read(c -> stats.recentAudit(c, Math.max(1, Math.min(limit, 100))));
    }
}
