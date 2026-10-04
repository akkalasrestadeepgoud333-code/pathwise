package app.pathwise.web.rooms;

import app.pathwise.core.ids.Ids;
import app.pathwise.core.security.Actor;
import app.pathwise.core.security.PlatformAction;
import app.pathwise.web.platform.http.PageServlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

/**
 * {@code /materials/<roomId>}: POST adds a note or a PDF (multipart) or removes an item;
 * {@code /materials/<roomId>/<id>}: GET streams a PDF to members of the room. Permissions are checked in
 * {@link MaterialService}.
 */
public class MaterialController extends PageServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Actor actor = require(req, PlatformAction.USE_STUDENT_AREA);
        String[] p = parts(req);
        UUID roomId = p.length == 2 ? Ids.parseOrNull(p[0]) : null;
        UUID id = p.length == 2 ? Ids.parseOrNull(p[1]) : null;
        if (roomId == null || id == null) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        MaterialService.Download d;
        try {
            d = app().materials().download(actor, roomId, id);
        } catch (RoomService.RoomNotFoundException e) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        res.setContentType("application/pdf");
        res.setContentLengthLong(Files.size(d.path()));
        // Shown in the browser's PDF viewer; sandboxed so it can't run as part of this site.
        res.setHeader("Content-Security-Policy", "sandbox");
        res.setHeader("Content-Disposition", "inline; filename=\"" + d.fileName().replaceAll("[^\\x20-\\x7E]", "_")
                + "\"; filename*=UTF-8''" + URLEncoder.encode(d.fileName(), StandardCharsets.UTF_8).replace("+", "%20"));
        res.setHeader("Cache-Control", "private, no-store");
        Files.copy(d.path(), res.getOutputStream());
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Actor actor = require(req, PlatformAction.USE_STUDENT_AREA);
        String[] p = parts(req);
        UUID roomId = p.length == 1 ? Ids.parseOrNull(p[0]) : null;
        if (roomId == null) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        MaterialService svc = app().materials();
        try {
            switch (param(req, "action")) {
                case "add-note" -> {
                    svc.addNote(actor, roomId, req.getParameter("title"), req.getParameter("body"));
                    flash(req, "success", "Note shared with the room.");
                }
                case "add-pdf" -> {
                    Part file = req.getPart("file");
                    if (file == null) {
                        throw new RoomService.RoomRuleException("Choose a PDF file to upload.");
                    }
                    try (InputStream in = file.getInputStream()) {
                        svc.addPdf(actor, roomId, req.getParameter("title"), file.getSubmittedFileName(), file.getSize(), in);
                    }
                    flash(req, "success", "PDF shared with the room.");
                }
                case "delete" -> {
                    UUID id = Ids.parseOrNull(req.getParameter("id"));
                    if (id == null) {
                        throw new RoomService.RoomNotFoundException();
                    }
                    svc.delete(actor, roomId, id);
                    flash(req, "success", "Removed.");
                }
                default -> {
                    res.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
            }
        } catch (RoomService.RoomRuleException e) {
            flash(req, "error", e.getMessage());
        } catch (RoomService.RoomNotFoundException e) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        } catch (IllegalStateException e) {
            // Tomcat refuses a request bigger than the multipart limits.
            flash(req, "error", "PDFs can be up to 10 MB.");
        }
        redirect(req, res, "/teach/rooms/" + roomId + "/materials");
    }

    private static String[] parts(HttpServletRequest req) {
        String info = req.getPathInfo();
        return info == null || info.length() < 2 ? new String[0] : info.substring(1).split("/");
    }
}
