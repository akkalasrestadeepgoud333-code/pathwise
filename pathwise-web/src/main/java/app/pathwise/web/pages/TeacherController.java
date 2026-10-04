package app.pathwise.web.pages;

import app.pathwise.core.security.Actor;
import app.pathwise.core.security.PlatformAction;
import app.pathwise.web.platform.http.PageServlet;
import app.pathwise.web.platform.view.ViewModels;
import app.pathwise.web.rooms.RoomService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** {@code /teach}: the teacher home: rooms you teach, and creating a room. */
public class TeacherController extends PageServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Actor actor = require(req, PlatformAction.USE_TEACHER_AREA);
        req.setAttribute("nav", "teach");
        req.setAttribute("rooms", ViewModels.of(app().rooms().myRooms(actor)));
        List<Map<String, String>> subjects = app().catalog().subjects().stream()
                .map(s -> Map.of("key", s.key(), "title", s.title())).toList();
        req.setAttribute("subjects", subjects);
        render(req, res, "teacher/home");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Actor actor = require(req, PlatformAction.CREATE_ROOM);
        try {
            UUID id = app().rooms().create(actor, req.getParameter("name"), req.getParameter("description"),
                    req.getParameter("subject"), req.getParameter("joinPolicy"));
            flash(req, "success", "Room created. Share its code with your students, then build the curriculum.");
            redirect(req, res, "/teach/rooms/" + id);
        } catch (RoomService.RoomRuleException e) {
            flash(req, "error", e.getMessage());
            redirect(req, res, "/teach");
        }
    }
}
