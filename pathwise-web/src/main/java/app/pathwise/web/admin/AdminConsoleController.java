package app.pathwise.web.admin;

import app.pathwise.core.ids.Ids;
import app.pathwise.core.security.Actor;
import app.pathwise.core.security.PlatformAction;
import app.pathwise.data.platform.AdminDao;
import app.pathwise.web.platform.http.PageServlet;
import app.pathwise.web.platform.view.TimeLabels;
import app.pathwise.web.platform.view.ViewModels;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The rest of the admin console: {@code /admin/users[/<id>]}, {@code /admin/rooms}, {@code /admin/games},
 * {@code /admin/audit}, {@code /admin/settings} and {@code /admin/reauth} (password confirmation for sensitive actions).
 */
public class AdminConsoleController extends PageServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Actor actor = require(req, PlatformAction.USE_ADMIN_AREA);
        AdminService admin = app().admin();
        String path = req.getServletPath();
        String info = req.getPathInfo();
        switch (path) {
            case "/admin/users" -> {
                req.setAttribute("nav", "admin-users");
                if (info != null && info.length() > 1) {
                    UUID id = Ids.parseOrNull(info.substring(1));
                    var user = id == null ? java.util.Optional.<AdminDao.UserRow>empty() : admin.user(actor, id);
                    if (user.isEmpty()) {
                        res.sendError(HttpServletResponse.SC_NOT_FOUND);
                        return;
                    }
                    req.setAttribute("person", userView(user.get()));
                    req.setAttribute("self", user.get().id().equals(actor.userId()));
                    req.setAttribute("reauthenticated", admin.reauthenticated(actor));
                    req.setAttribute("history", auditViews(admin.audit(actor, "", null).stream()
                            .filter(a -> user.get().id().toString().equals(a.entityId())).limit(20).toList()));
                    render(req, res, "admin/user");
                    return;
                }
                req.setAttribute("q", param(req, "q"));
                req.setAttribute("people", admin.users(actor, param(req, "q")).stream().map(AdminConsoleController::userView).toList());
                render(req, res, "admin/users");
            }
            case "/admin/rooms" -> {
                req.setAttribute("nav", "admin-rooms");
                req.setAttribute("q", param(req, "q"));
                req.setAttribute("rooms", admin.rooms(actor, param(req, "q")).stream().map(r -> {
                    Map<String, Object> v = new LinkedHashMap<>(ViewModels.of(r));
                    v.put("createdLabel", TimeLabels.date(r.createdAt(), TimeLabels.DEFAULT_ZONE));
                    return v;
                }).toList());
                render(req, res, "admin/rooms");
            }
            case "/admin/games" -> {
                req.setAttribute("nav", "admin-games");
                req.setAttribute("templates", ViewModels.list(admin.templates(actor)));
                render(req, res, "admin/games");
            }
            case "/admin/audit" -> {
                req.setAttribute("nav", "admin-audit");
                Long before = null;
                try {
                    before = req.getParameter("before") == null ? null : Long.parseLong(req.getParameter("before"));
                } catch (NumberFormatException ignored) {
                    // first page
                }
                List<AdminDao.AuditRow> rows = admin.audit(actor, param(req, "action"), before);
                req.setAttribute("action", param(req, "action"));
                req.setAttribute("entries", auditViews(rows));
                req.setAttribute("nextBefore", rows.size() == 50 ? rows.get(rows.size() - 1).id() : null);
                render(req, res, "admin/audit");
            }
            case "/admin/settings" -> {
                require(req, PlatformAction.MANAGE_PLATFORM);
                req.setAttribute("nav", "admin-settings");
                req.setAttribute("registrationOpen", admin.registrationOpen());
                req.setAttribute("ai", app().config().ai().enabled() ? "Local model through Ollama (" + app().config().ai().model() + ")" : "Off (Guided Help only)");
                render(req, res, "admin/settings");
            }
            case "/admin/reauth" -> {
                req.setAttribute("nav", "admin");
                req.setAttribute("next", safeNext(req.getParameter("next"), "/admin"));
                render(req, res, "admin/reauth");
            }
            default -> res.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Actor actor = require(req, PlatformAction.USE_ADMIN_AREA);
        AdminService admin = app().admin();
        String path = req.getServletPath();
        String info = req.getPathInfo();
        String back = path + (info == null ? "" : info);
        try {
            switch (path) {
                case "/admin/reauth" -> {
                    String next = safeNext(req.getParameter("next"), "/admin");
                    if (!app().loginLimiter().tryAcquire("reauth:" + actor.userId())) {
                        flash(req, "error", "Too many attempts. Wait a minute and try again.");
                        back = "/admin/reauth?next=" + enc(next);
                    } else if (app().identity().reauthenticate(actor, req.getParameter("password"), meta(req))) {
                        flash(req, "success", "Confirmed for 15 minutes. You can repeat the action now.");
                        back = next;
                    } else {
                        flash(req, "error", "That password isn't right.");
                        back = "/admin/reauth?next=" + enc(next);
                    }
                }
                case "/admin/users" -> {
                    UUID id = info == null ? null : Ids.parseOrNull(info.substring(1));
                    if (id == null) {
                        res.sendError(HttpServletResponse.SC_NOT_FOUND);
                        return;
                    }
                    switch (param(req, "action")) {
                        case "suspend" -> admin.setSuspended(actor, id, true, req.getParameter("reason"));
                        case "reactivate" -> admin.setSuspended(actor, id, false, req.getParameter("reason"));
                        case "grant" -> admin.setRole(actor, id, req.getParameter("role"), true);
                        case "revoke" -> admin.setRole(actor, id, req.getParameter("role"), false);
                        case "verify" -> admin.setVerifiedEducator(actor, id, true);
                        case "unverify" -> admin.setVerifiedEducator(actor, id, false);
                        case "trust" -> admin.setTrustLevel(actor, id, Integer.parseInt(param(req, "level").strip()));
                        default -> {
                            res.sendError(HttpServletResponse.SC_NOT_FOUND);
                            return;
                        }
                    }
                    flash(req, "success", "Saved. The change is in the audit log.");
                }
                case "/admin/games" -> {
                    admin.setTemplateEnabled(actor, param(req, "key"), "on".equals(param(req, "enabled")));
                    flash(req, "success", "Saved.");
                }
                case "/admin/rooms" -> {
                    UUID id = Ids.parseOrNull(req.getParameter("roomId"));
                    if (id == null) {
                        res.sendError(HttpServletResponse.SC_NOT_FOUND);
                        return;
                    }
                    admin.setRoomArchived(actor, id, "archive".equals(param(req, "action")), req.getParameter("reason"));
                    flash(req, "success", "Saved. The room's teachers can still see it in their archive.");
                }
                case "/admin/settings" -> {
                    admin.setRegistrationOpen(actor, "on".equals(param(req, "registrationOpen")));
                    flash(req, "success", "Settings saved.");
                }
                default -> {
                    res.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
            }
        } catch (AdminService.ReauthRequiredException e) {
            flash(req, "info", "Please confirm your password, then repeat the action.");
            back = "/admin/reauth?next=" + enc(back);
        } catch (AdminService.AdminRuleException e) {
            flash(req, "error", e.getMessage());
        } catch (NumberFormatException e) {
            flash(req, "error", "Trust levels go from 0 to 4.");
        }
        redirect(req, res, back);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static Map<String, Object> userView(AdminDao.UserRow u) {
        Map<String, Object> v = new LinkedHashMap<>(ViewModels.of(u));
        v.put("rolesLabel", String.join(", ", u.roles()));
        v.put("teacher", u.roles().contains("teacher"));
        v.put("mentor", u.roles().contains("mentor"));
        v.put("admin", u.roles().contains("admin"));
        v.put("createdLabel", TimeLabels.date(u.createdAt(), TimeLabels.DEFAULT_ZONE));
        v.put("lastSeenLabel", u.lastSeenAt() == null ? "never" : TimeLabels.dateTime(u.lastSeenAt(), TimeLabels.DEFAULT_ZONE));
        return v;
    }

    private static List<Map<String, Object>> auditViews(List<AdminDao.AuditRow> rows) {
        return rows.stream().map(a -> {
            Map<String, Object> v = new LinkedHashMap<>(ViewModels.of(a));
            v.put("atLabel", TimeLabels.dateTime(a.at(), TimeLabels.DEFAULT_ZONE));
            v.put("details", "{}".equals(a.metadata()) ? "" : a.metadata());
            return v;
        }).toList();
    }
}
