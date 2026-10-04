package app.pathwise.web.rooms;

import app.pathwise.core.ids.Ids;
import app.pathwise.core.rooms.RoomPolicy;
import app.pathwise.core.security.Actor;
import app.pathwise.core.security.PlatformAction;
import app.pathwise.web.platform.http.PageServlet;
import app.pathwise.web.platform.view.ViewModels;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A room's teacher pages: {@code /teach/rooms/<id>[/curriculum|people|assess|assess/new|assess/<aid>|analytics|
 * analytics.csv|settings]}. Every POST names its {@code action}; each action is permission-checked in the services.
 */
public class TeachRoomController extends PageServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Actor actor = require(req, PlatformAction.USE_STUDENT_AREA);
        req.setAttribute("nav", "teach");
        String[] p = parts(req);
        UUID roomId = p.length >= 1 ? Ids.parseOrNull(p[0]) : null;
        if (roomId == null) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String section = p.length >= 2 ? p[1] : "";
        req.setAttribute("section", section.isEmpty() ? "today" : section);
        try {
            switch (section) {
                case "" -> {
                    req.setAttribute("cockpit", ViewModels.of(app().roomInsights().cockpit(actor, roomId)));
                    req.setAttribute("unansweredDoubts", app().doubts().unansweredInRoom(roomId));
                    render(req, res, "teacher/room-cockpit");
                }
                case "curriculum" -> {
                    req.setAttribute("curriculum", ViewModels.of(app().rooms().curriculum(actor, roomId)));
                    render(req, res, "teacher/room-curriculum");
                }
                case "people" -> {
                    req.setAttribute("people", ViewModels.of(app().rooms().people(actor, roomId)));
                    var session = req.getSession(false);
                    if (session != null && session.getAttribute("pw.inviteLink") != null) {
                        req.setAttribute("inviteLink", session.getAttribute("pw.inviteLink")); // shown once
                        session.removeAttribute("pw.inviteLink");
                    }
                    render(req, res, "teacher/room-people");
                }
                case "assess" -> assess(req, res, actor, roomId, p);
                case "assignments" -> {
                    UUID aid = p.length == 3 ? Ids.parseOrNull(p[2]) : null;
                    if (p.length == 2) {
                        req.setAttribute("page", ViewModels.of(app().assignments().page(actor, roomId)));
                        render(req, res, "teacher/room-assignments");
                    } else if (aid != null) {
                        req.setAttribute("grading", ViewModels.of(app().assignments().grading(actor, roomId, aid)));
                        render(req, res, "teacher/room-grading");
                    } else {
                        res.sendError(HttpServletResponse.SC_NOT_FOUND);
                    }
                }
                case "materials" -> {
                    req.setAttribute("materials", ViewModels.of(app().materials().page(actor, roomId)));
                    req.setAttribute("roomHeader", ViewModels.of(app().rooms().header(actor, roomId)));
                    req.setAttribute("maxMb", MaterialService.MAX_PDF_BYTES / (1024 * 1024));
                    render(req, res, "teacher/room-materials");
                }
                case "doubts" -> {
                    req.setAttribute("doubts", ViewModels.list(app().doubts().roomDoubts(actor, roomId)));
                    req.setAttribute("roomHeader", ViewModels.of(app().rooms().header(actor, roomId)));
                    render(req, res, "teacher/room-doubts");
                }
                case "analytics" -> {
                    req.setAttribute("analytics", ViewModels.of(app().roomInsights().analytics(actor, roomId)));
                    render(req, res, "teacher/room-analytics");
                }
                case "analytics.csv" -> {
                    String csv = app().roomInsights().csv(actor, roomId);
                    res.setContentType("text/csv;charset=UTF-8");
                    res.setHeader("Content-Disposition", "attachment; filename=\"room-students.csv\"");
                    res.getOutputStream().write(("﻿" + csv).getBytes(StandardCharsets.UTF_8));
                }
                case "settings" -> {
                    req.setAttribute("settings", ViewModels.of(app().rooms().settings(actor, roomId)));
                    req.setAttribute("roomHeader", ViewModels.of(app().rooms().header(actor, roomId)));
                    render(req, res, "teacher/room-settings");
                }
                default -> res.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        } catch (RoomService.RoomNotFoundException e) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private void assess(HttpServletRequest req, HttpServletResponse res, Actor actor, UUID roomId, String[] p)
            throws ServletException, IOException {
        RoomAssessmentService svc = app().roomAssessments();
        if (p.length == 2) {
            req.setAttribute("page", ViewModels.of(svc.page(actor, roomId)));
            render(req, res, "teacher/room-assess");
            return;
        }
        if (p.length == 3 && "new".equals(p[2])) {
            var builder = svc.builder(actor, roomId, param(req, "topic"));
            if (builder.isEmpty()) {
                flash(req, "info", "Choose a topic first.");
                redirect(req, res, "/teach/rooms/" + roomId + "/assess");
                return;
            }
            req.setAttribute("builder", ViewModels.of(builder.get()));
            render(req, res, "teacher/room-assess-new");
            return;
        }
        UUID aid = p.length == 3 ? Ids.parseOrNull(p[2]) : null;
        var analysis = aid == null ? java.util.Optional.<app.pathwise.web.assessment.AssessmentService.Analysis>empty()
                : svc.analysis(actor, roomId, aid);
        if (analysis.isEmpty()) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        req.setAttribute("analysis", ViewModels.of(analysis.get()));
        req.setAttribute("backHref", "/teach/rooms/" + roomId + "/assess");
        req.setAttribute("backLabel", "Assessments");
        render(req, res, "admin/assessment-analysis");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Actor actor = require(req, PlatformAction.USE_STUDENT_AREA);
        String[] p = parts(req);
        UUID roomId = p.length >= 1 ? Ids.parseOrNull(p[0]) : null;
        if (roomId == null) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String section = p.length >= 2 ? p[1] : "";
        String back = "/teach/rooms/" + roomId + (section.isEmpty() ? "" : "/" + section)
                + (p.length >= 3 && Ids.parseOrNull(p[2]) != null ? "/" + p[2] : "");
        String action = param(req, "action");
        RoomService rooms = app().rooms();
        try {
            switch (action) {
                case "announce" -> {
                    rooms.announce(actor, roomId, req.getParameter("body"), "1".equals(req.getParameter("pinned")));
                    flash(req, "success", "Announcement posted.");
                }
                case "delete-announcement" -> rooms.deleteAnnouncement(actor, roomId, id(req, "id"));
                case "approve" -> {
                    rooms.approve(actor, roomId, id(req, "userId"), true);
                    flash(req, "success", "Approved.");
                }
                case "reject" -> rooms.approve(actor, roomId, id(req, "userId"), false);
                case "remove" -> {
                    rooms.remove(actor, roomId, id(req, "userId"));
                    flash(req, "success", "Removed from the room.");
                }
                case "perms" -> {
                    rooms.setPerms(actor, roomId, id(req, "userId"), new RoomPolicy.Perms(on(req, "author"), on(req, "publish"),
                            on(req, "grade"), on(req, "moderate"), on(req, "analytics")));
                    flash(req, "success", "Permissions saved.");
                }
                case "invite" -> {
                    String token = rooms.createInvite(actor, roomId, param(req, "role"), intParam(req, "maxUses", 40), intParam(req, "days", 14));
                    String link = req.getScheme() + "://" + req.getServerName()
                            + (req.getServerPort() == 80 || req.getServerPort() == 443 ? "" : ":" + req.getServerPort())
                            + req.getContextPath() + "/join/" + token;
                    req.getSession(true).setAttribute("pw.inviteLink", link);
                    flash(req, "success", "Invite link created. Copy it now: for safety it is shown only once.");
                }
                case "revoke" -> rooms.revokeInvite(actor, roomId, id(req, "inviteId"));
                case "add-unit" -> rooms.addUnit(actor, roomId, req.getParameter("title"), date(req, "publishOn"));
                case "update-unit" -> {
                    rooms.updateUnit(actor, roomId, id(req, "unitId"), req.getParameter("title"), date(req, "publishOn"));
                    flash(req, "success", "Unit saved.");
                }
                case "delete-unit" -> rooms.deleteUnit(actor, roomId, id(req, "unitId"));
                case "move-unit" -> rooms.moveUnit(actor, roomId, id(req, "unitId"), "up".equals(req.getParameter("dir")) ? -1 : 1);
                case "add-item" -> rooms.addItem(actor, roomId, id(req, "unitId"), req.getParameter("choice"), date(req, "publishOn"),
                        !"0".equals(req.getParameter("required")));
                case "delete-item" -> rooms.deleteItem(actor, roomId, id(req, "itemId"));
                case "move-item" -> rooms.moveItem(actor, roomId, id(req, "itemId"), "up".equals(req.getParameter("dir")) ? -1 : 1);
                case "create-assessment" -> {
                    List<UUID> qids = new ArrayList<>();
                    String[] picked = req.getParameterValues("q");
                    if (picked != null) {
                        for (String v : picked) {
                            UUID q = Ids.parseOrNull(v);
                            if (q != null) {
                                qids.add(q);
                            }
                        }
                    }
                    String limit = param(req, "timeLimit");
                    app().roomAssessments().create(actor, roomId, new RoomAssessmentService.Draft(param(req, "topic"),
                            req.getParameter("title"), param(req, "kind"), req.getParameter("instructions"),
                            limit.isBlank() ? null : intParam(req, "timeLimit", 0), intParam(req, "maxAttempts", 1),
                            "1".equals(req.getParameter("shuffle")), param(req, "feedback"), date(req, "dueOn"), qids));
                    flash(req, "success", "Draft saved. Publish it when you're ready, then add it to the curriculum.");
                    back = "/teach/rooms/" + roomId + "/assess";
                }
                case "publish-assessment" -> {
                    app().roomAssessments().publish(actor, roomId, id(req, "assessmentId"));
                    flash(req, "success", "Published. Students in this room can now see it.");
                }
                case "release-results" -> {
                    app().roomAssessments().release(actor, roomId, id(req, "assessmentId"));
                    flash(req, "success", "Results released. Students can now see which answers were right.");
                }
                case "archive-assessment" -> app().roomAssessments().archive(actor, roomId, id(req, "assessmentId"));
                case "create-assignment" -> {
                    List<UUID> skills = new ArrayList<>();
                    String[] picked = req.getParameterValues("skill");
                    if (picked != null) {
                        for (String v : picked) {
                            UUID k = Ids.parseOrNull(v);
                            if (k != null) {
                                skills.add(k);
                            }
                        }
                    }
                    app().assignments().create(actor, roomId, new AssignmentService.Draft(req.getParameter("title"),
                            req.getParameter("instructions"), intParam(req, "maxPoints", 10), date(req, "dueOn"),
                            !"0".equals(req.getParameter("allowLate")), skills));
                    flash(req, "success", "Draft saved. Publish it, then add it to the curriculum so students see it.");
                }
                case "publish-assignment" -> {
                    app().assignments().publish(actor, roomId, id(req, "assignmentId"));
                    flash(req, "success", "Published.");
                }
                case "archive-assignment" -> app().assignments().archive(actor, roomId, id(req, "assignmentId"));
                case "grade" -> {
                    boolean sendBack = "1".equals(req.getParameter("sendBack"));
                    app().assignments().grade(actor, roomId, id(req, "assignmentId"), id(req, "userId"), req.getParameter("grade"),
                            req.getParameter("feedback"), sendBack);
                    flash(req, "success", sendBack ? "Sent back with your comments." : "Graded.");
                }
                case "save-settings" -> {
                    rooms.updateSettings(actor, roomId, req.getParameter("name"), req.getParameter("description"),
                            req.getParameter("subject"), req.getParameter("joinPolicy"), on(req, "classStats"));
                    flash(req, "success", "Settings saved.");
                }
                case "new-code" -> {
                    rooms.newCode(actor, roomId);
                    flash(req, "success", "New code created. The old one no longer works.");
                }
                case "archive" -> {
                    rooms.setArchived(actor, roomId, true);
                    flash(req, "info", "Room archived. It's read-only now; you can restore it from Settings.");
                }
                case "restore" -> {
                    rooms.setArchived(actor, roomId, false);
                    flash(req, "success", "Room restored.");
                }
                default -> {
                    res.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
            }
        } catch (RoomService.RoomRuleException e) {
            flash(req, "error", e.getMessage());
            if ("create-assessment".equals(action)) {
                back = "/teach/rooms/" + roomId + "/assess/new?topic=" + java.net.URLEncoder.encode(param(req, "topic"), StandardCharsets.UTF_8);
            }
        } catch (RoomService.RoomNotFoundException e) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        redirect(req, res, back);
    }

    private static UUID id(HttpServletRequest req, String name) {
        UUID id = Ids.parseOrNull(req.getParameter(name));
        if (id == null) {
            throw new RoomService.RoomNotFoundException();
        }
        return id;
    }

    private static boolean on(HttpServletRequest req, String name) {
        return "1".equals(req.getParameter(name)) || "on".equals(req.getParameter(name));
    }

    private static int intParam(HttpServletRequest req, String name, int fallback) {
        try {
            return Integer.parseInt(param(req, name).strip());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static LocalDate date(HttpServletRequest req, String name) {
        String v = param(req, name).strip();
        if (v.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(v);
        } catch (DateTimeParseException e) {
            throw new RoomService.RoomRuleException("Dates look like 2026-11-30.");
        }
    }

    private static String[] parts(HttpServletRequest req) {
        String info = req.getPathInfo();
        return info == null || info.length() < 2 ? new String[0] : info.substring(1).split("/");
    }
}
