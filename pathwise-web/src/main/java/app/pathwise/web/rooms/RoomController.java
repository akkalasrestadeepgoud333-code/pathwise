package app.pathwise.web.rooms;

import app.pathwise.core.ids.Ids;
import app.pathwise.core.security.Actor;
import app.pathwise.core.security.PlatformAction;
import app.pathwise.web.platform.http.PageServlet;
import app.pathwise.web.platform.view.ViewModels;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.UUID;

/**
 * The student side of rooms.
 * <ul>
 *   <li>{@code GET /app/rooms}: my rooms and the join-by-code form; {@code POST /app/rooms/join}</li>
 *   <li>{@code GET /app/rooms/<id>}: the room (announcements, curriculum with my progress, next step)</li>
 *   <li>{@code GET /join/<token>}: invite preview; {@code POST /join/<token>}: accept</li>
 * </ul>
 */
public class RoomController extends PageServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Actor actor = require(req, PlatformAction.USE_STUDENT_AREA);
        RoomService svc = app().rooms();
        if ("/join".equals(req.getServletPath())) {
            String token = token(req);
            req.setAttribute("invite", ViewModels.of(svc.previewInvite(token)));
            req.setAttribute("token", token);
            render(req, res, "rooms/join");
            return;
        }
        req.setAttribute("nav", "rooms");
        String[] p = parts(req);
        if (p.length == 0) {
            req.setAttribute("rooms", ViewModels.of(svc.myRooms(actor)));
            render(req, res, "rooms/index");
            return;
        }
        UUID id = p.length >= 1 ? Ids.parseOrNull(p[0]) : null;
        UUID assignment = p.length == 3 && "assignments".equals(p[1]) ? Ids.parseOrNull(p[2]) : null;
        if (id == null || (p.length != 1 && assignment == null)) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        try {
            if (assignment != null) {
                req.setAttribute("a", ViewModels.of(app().assignments().studentView(actor, id, assignment)));
                render(req, res, "rooms/assignment");
                return;
            }
            req.setAttribute("room", ViewModels.of(svc.studentView(actor, id)));
            req.setAttribute("materials", ViewModels.of(app().materials().page(actor, id)));
            render(req, res, "rooms/room");
        } catch (RoomService.RoomNotFoundException e) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Actor actor = require(req, PlatformAction.USE_STUDENT_AREA);
        RoomService svc = app().rooms();
        try {
            if ("/join".equals(req.getServletPath())) {
                RoomService.JoinOutcome out = svc.acceptInvite(actor, token(req));
                flash(req, "success", "You've joined " + out.roomName() + ".");
                redirect(req, res, "/app/rooms/" + out.roomId());
                return;
            }
            String[] p = parts(req);
            UUID room = p.length == 3 && "assignments".equals(p[1]) ? Ids.parseOrNull(p[0]) : null;
            UUID assignment = room == null ? null : Ids.parseOrNull(p[2]);
            if (assignment != null) {
                boolean handIn = "hand-in".equals(req.getParameter("action"));
                try {
                    app().assignments().save(actor, room, assignment, req.getParameter("body"), handIn);
                    flash(req, "success", handIn ? "Handed in. Your teacher will grade it." : "Draft saved.");
                } catch (RoomService.RoomNotFoundException e) {
                    res.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                } catch (RoomService.RoomRuleException e) {
                    flash(req, "error", e.getMessage());
                }
                redirect(req, res, "/app/rooms/" + room + "/assignments/" + assignment);
                return;
            }
            if (p.length == 1 && "join".equals(p[0])) {
                RoomService.JoinOutcome out = svc.joinByCode(actor, req.getParameter("code"));
                if ("pending".equals(out.status())) {
                    flash(req, "info", "Request sent to " + out.roomName() + ". You'll get in as soon as the teacher approves it.");
                    redirect(req, res, "/app/rooms");
                } else {
                    flash(req, "success", "You're in " + out.roomName() + ".");
                    redirect(req, res, "/app/rooms/" + out.roomId());
                }
                return;
            }
        } catch (RoomService.RoomRuleException e) {
            flash(req, "error", e.getMessage());
            redirect(req, res, "/join".equals(req.getServletPath()) ? "/join/" + token(req) : "/app/rooms");
            return;
        }
        res.sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    private static String token(HttpServletRequest req) {
        String info = req.getPathInfo();
        String t = info == null || info.length() < 2 ? "" : info.substring(1);
        return t.matches("[A-Za-z0-9_-]{16,200}") ? t : "invalid";
    }

    private static String[] parts(HttpServletRequest req) {
        String info = req.getPathInfo();
        return info == null || info.length() < 2 ? new String[0] : info.substring(1).split("/");
    }
}
