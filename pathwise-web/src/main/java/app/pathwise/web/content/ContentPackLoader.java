package app.pathwise.web.content;

import app.pathwise.core.content.ContentPack;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Reads content packs bundled on the classpath under {@code content-packs/}. Each pack has a
 * {@code pack.yaml} that lists its module files, so loading works the same from a directory, a JAR or a WAR.
 */
public final class ContentPackLoader {
    private static final String ROOT = "content-packs/";
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    /** {@code content-packs/index.yaml}. */
    public record Index(List<String> packs) {}

    /** {@code content-packs/<key>/pack.yaml}: the pack minus its modules, plus the module file list. */
    public record PackFile(String key, int version, String title, ContentPack.Catalog catalog,
                           List<ContentPack.TopicDef> topics, List<String> modules, List<String> questions,
                           List<ContentPack.AssessmentDef> assessments, List<String> games) {}

    /** {@code content-packs/<key>/questions/<file>.yaml}. */
    public record QuestionFile(List<ContentPack.QuestionDef> questions) {}

    public record Loaded(ContentPack pack, String title, String checksum) {}

    private final ClassLoader classLoader;

    public ContentPackLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public List<String> available() {
        byte[] raw = read(ROOT + "index.yaml");
        if (raw == null) {
            return List.of();
        }
        try {
            Index index = YAML.readValue(raw, Index.class);
            return index.packs() == null ? List.of() : List.copyOf(index.packs());
        } catch (IOException e) {
            throw new UncheckedIOException("content-packs/index.yaml is not valid", e);
        }
    }

    /** Parses a pack. Syntax and unknown-field errors are reported as {@link PackFormatException}. */
    public Loaded load(String key) {
        if (!key.matches("[a-z0-9][a-z0-9-]{1,80}")) {
            throw new PackFormatException("Invalid pack key: " + key);
        }
        MessageDigest digest = sha256();
        String base = ROOT + key + "/";
        byte[] packBytes = read(base + "pack.yaml");
        if (packBytes == null) {
            throw new PackFormatException("Pack '" + key + "' not found (" + base + "pack.yaml)");
        }
        digest.update(packBytes);
        PackFile file = parse(packBytes, PackFile.class, base + "pack.yaml");
        List<ContentPack.ModuleDef> modules = new ArrayList<>();
        for (String moduleFile : file.modules() == null ? List.<String>of() : file.modules()) {
            if (!moduleFile.matches("[a-z0-9][a-z0-9-]{0,80}\\.yaml")) {
                throw new PackFormatException("Invalid module file name: " + moduleFile);
            }
            byte[] bytes = read(base + "modules/" + moduleFile);
            if (bytes == null) {
                throw new PackFormatException("Module file missing: " + base + "modules/" + moduleFile);
            }
            digest.update(bytes);
            modules.add(parse(bytes, ContentPack.ModuleDef.class, base + "modules/" + moduleFile));
        }
        List<ContentPack.QuestionDef> questions = new ArrayList<>();
        for (String questionFile : file.questions() == null ? List.<String>of() : file.questions()) {
            if (!questionFile.matches("[a-z0-9][a-z0-9-]{0,80}\\.yaml")) {
                throw new PackFormatException("Invalid question file name: " + questionFile);
            }
            byte[] bytes = read(base + "questions/" + questionFile);
            if (bytes == null) {
                throw new PackFormatException("Question file missing: " + base + "questions/" + questionFile);
            }
            digest.update(bytes);
            QuestionFile qf = parse(bytes, QuestionFile.class, base + "questions/" + questionFile);
            if (qf.questions() != null) {
                questions.addAll(qf.questions());
            }
        }
        List<ContentPack.GameDef> games = new ArrayList<>();
        for (String gameFile : file.games() == null ? List.<String>of() : file.games()) {
            if (!gameFile.matches("[a-z0-9][a-z0-9-]{0,80}\\.yaml")) {
                throw new PackFormatException("Invalid game file name: " + gameFile);
            }
            byte[] bytes = read(base + "games/" + gameFile);
            if (bytes == null) {
                throw new PackFormatException("Game file missing: " + base + "games/" + gameFile);
            }
            digest.update(bytes);
            games.add(parse(bytes, ContentPack.GameDef.class, base + "games/" + gameFile));
        }
        ContentPack pack = new ContentPack(file.key(), file.version(), file.catalog(), file.topics(), modules, questions,
                file.assessments() == null ? List.of() : file.assessments(), games);
        return new Loaded(pack, file.title(), HexFormat.of().formatHex(digest.digest()));
    }

    private <T> T parse(byte[] bytes, Class<T> type, String where) {
        try {
            return YAML.readValue(bytes, type);
        } catch (IOException e) {
            String message = e instanceof com.fasterxml.jackson.core.JsonProcessingException j
                    ? j.getOriginalMessage() + (j.getLocation() == null ? "" : " (line " + j.getLocation().getLineNr() + ")")
                    : e.getMessage();
            throw new PackFormatException(where + ": " + message);
        }
    }

    private byte[] read(String path) {
        try (InputStream in = classLoader.getResourceAsStream(path)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A pack file could not be read or parsed. */
    public static final class PackFormatException extends RuntimeException {
        public PackFormatException(String message) {
            super(message);
        }
    }
}
