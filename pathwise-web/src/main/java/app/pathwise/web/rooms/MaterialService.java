package app.pathwise.web.rooms;

import app.pathwise.core.ids.Ids;
import app.pathwise.core.rooms.RoomPolicy.Action;
import app.pathwise.core.security.Actor;
import app.pathwise.data.Tx;
import app.pathwise.data.platform.AuditDao;
import app.pathwise.data.rooms.MaterialDao;
import app.pathwise.data.rooms.MaterialDao.MaterialRow;
import app.pathwise.web.content.Markdown;
import app.pathwise.web.content.SafeHtml;
import app.pathwise.web.platform.Json;
import app.pathwise.web.rooms.RoomService.RoomNotFoundException;
import app.pathwise.web.rooms.RoomService.RoomRuleException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Teachers' own notes and PDFs in a room. Anyone in the room can read them; adding and removing needs the room's
 * AUTHOR permission (the owner, or a co-teacher allowed to author). PDFs are checked by content, not by name,
 * stored under a server-generated name outside the web root, and served only to room members.
 */
public final class MaterialService {
    public static final int MAX_PDF_BYTES = 10 * 1024 * 1024;
    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};

    private final Tx tx;
    private final RoomService rooms;
    private final Path storage;
    private final Clock clock;
    private final MaterialDao dao = new MaterialDao();
    private final AuditDao audit = new AuditDao();

    public MaterialService(Tx tx, RoomService rooms, Path storageDir, Clock clock) {
        this.tx = tx;
        this.rooms = rooms;
        this.storage = storageDir.resolve("materials");
        this.clock = clock;
    }

    public record Item(UUID id, String kind, String title, SafeHtml html, String fileName, String sizeLabel, String authorName,
                       Instant createdAt) {}

    public record Page(boolean canEdit, List<Item> items) {}

    /** A file ready to stream to a room member. */
    public record Download(Path path, String fileName, long size) {}

    public Page page(Actor actor, UUID roomId) {
        RoomService.Ctx ctx = rooms.check(actor, roomId, Action.VIEW_ROOM);
        List<Item> items = tx.read(c -> dao.ofRoom(c, roomId)).stream().map(MaterialService::item).toList();
        return new Page(ctx.can(Action.AUTHOR), items);
    }

    private static Item item(MaterialRow m) {
        return new Item(m.id(), m.kind(), m.title(), "note".equals(m.kind()) ? Markdown.render(m.body()) : null, m.fileName(),
                m.fileSize() == null ? null : sizeLabel(m.fileSize()), m.authorName(), m.createdAt());
    }

    static String sizeLabel(long bytes) {
        return bytes < 1024 * 1024 ? Math.max(1, Math.round(bytes / 1024.0)) + " KB"
                : String.format(java.util.Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private static String title(String title) {
        String t = title == null ? "" : title.strip().replaceAll("\\s+", " ");
        if (t.length() < 2 || t.length() > 160) {
            throw new RoomRuleException("Give it a title (2 to 160 characters).");
        }
        return t;
    }

    public UUID addNote(Actor actor, UUID roomId, String title, String body) {
        rooms.check(actor, roomId, Action.AUTHOR);
        String t = title(title);
        String b = body == null ? "" : body.strip();
        if (b.isEmpty() || b.length() > 20_000) {
            throw new RoomRuleException("Write the note (up to 20,000 characters).");
        }
        UUID id = Ids.newId();
        tx.run(c -> {
            dao.insertNote(c, id, roomId, actor.userId(), t, b);
            audit.record(c, actor.userId(), "room.material_added", "room", roomId.toString(), Json.write(Map.of("kind", "note")), null);
        });
        return id;
    }

    /**
     * Stores an uploaded PDF. {@code declaredSize} comes from the request; the bytes actually read are counted too,
     * so a lying client can't exceed the limit.
     */
    public UUID addPdf(Actor actor, UUID roomId, String title, String originalName, long declaredSize, InputStream in) throws IOException {
        rooms.check(actor, roomId, Action.AUTHOR);
        String t = title(title == null || title.isBlank() ? baseName(originalName) : title);
        if (declaredSize <= 0) {
            throw new RoomRuleException("Choose a PDF file to upload.");
        }
        if (declaredSize > MAX_PDF_BYTES) {
            throw new RoomRuleException("PDFs can be up to 10 MB.");
        }
        Files.createDirectories(storage);
        UUID id = Ids.newId();
        String key = id + ".pdf";
        Path temp = Files.createTempFile(storage, "upload-", ".part");
        long written;
        try {
            written = copyLimited(in, temp);
            if (!startsWithPdfMagic(temp)) {
                throw new RoomRuleException("That file isn't a PDF. Only PDF files can be uploaded.");
            }
            Files.move(temp, storage.resolve(key), StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
        String name = displayName(originalName);
        int size = (int) written;
        try {
            tx.run(c -> {
                dao.insertPdf(c, id, roomId, actor.userId(), t, name, size, key);
                audit.record(c, actor.userId(), "room.material_added", "room", roomId.toString(),
                        Json.write(Map.of("kind", "pdf", "bytes", size)), null);
            });
        } catch (RuntimeException e) {
            Files.deleteIfExists(storage.resolve(key));
            throw e;
        }
        return id;
    }

    private static long copyLimited(InputStream in, Path target) throws IOException {
        byte[] buf = new byte[64 * 1024];
        long total = 0;
        try (var out = Files.newOutputStream(target)) {
            int n;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > MAX_PDF_BYTES) {
                    throw new RoomRuleException("PDFs can be up to 10 MB.");
                }
                out.write(buf, 0, n);
            }
        }
        if (total == 0) {
            throw new RoomRuleException("Choose a PDF file to upload.");
        }
        return total;
    }

    private static boolean startsWithPdfMagic(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return Arrays.equals(in.readNBytes(PDF_MAGIC.length), PDF_MAGIC);
        }
    }

    /** Only for showing and for the download's suggested name: no path parts, no control or quote characters. */
    static String displayName(String original) {
        String n = original == null ? "" : original.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"<>|:*?]", "").strip();
        if (n.isEmpty()) {
            n = "document.pdf";
        }
        if (!n.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) {
            n = n + ".pdf";
        }
        return n.length() > 200 ? n.substring(n.length() - 200) : n;
    }

    private static String baseName(String original) {
        String n = displayName(original);
        return n.substring(0, n.length() - 4);
    }

    public void delete(Actor actor, UUID roomId, UUID id) {
        rooms.check(actor, roomId, Action.AUTHOR);
        MaterialRow m = tx.call(c -> {
            MaterialRow row = dao.find(c, roomId, id).orElseThrow(RoomNotFoundException::new);
            dao.delete(c, roomId, id, clock.instant());
            audit.record(c, actor.userId(), "room.material_removed", "room", roomId.toString(), Json.write(Map.of("kind", row.kind())), null);
            return row;
        });
        if (m.storageKey() != null) {
            try {
                Files.deleteIfExists(storage.resolve(m.storageKey()));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /** For streaming a PDF to a member of the room (404 for anyone else, and for notes). */
    public Download download(Actor actor, UUID roomId, UUID id) {
        rooms.check(actor, roomId, Action.VIEW_ROOM);
        MaterialRow m = tx.read(c -> dao.find(c, roomId, id)).filter(r -> r.storageKey() != null).orElseThrow(RoomNotFoundException::new);
        Path file = storage.resolve(m.storageKey()).normalize();
        if (!file.startsWith(storage) || !Files.isRegularFile(file)) {
            throw new RoomNotFoundException();
        }
        return new Download(file, m.fileName(), m.fileSize());
    }
}
