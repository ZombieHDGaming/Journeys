package dev.voidscape.journeys.core.quest;

import dev.voidscape.journeys.api.quest.QuestDefinition;
import dev.voidscape.journeys.api.quest.QuestIds;
import dev.voidscape.journeys.api.quest.QuestRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Stores one quest per file in {@code plugins/Journeys/quests/<id>.yml}.
 *
 * <p>Invalid files are skipped on load and their problems are kept so admins can see them with
 * {@code /journeys list}. Saves are written to a temporary file and moved into place, so a crash mid-write never
 * leaves a truncated quest behind.</p>
 */
public final class YamlQuestRepository implements QuestRepository {

    private final Path directory;
    private final QuestValidator validator;
    private final Logger logger;
    private final Map<String, QuestDefinition> quests = new ConcurrentHashMap<>();
    private final Map<String, List<QuestValidator.Issue>> problems = new ConcurrentHashMap<>();
    private Runnable changeListener = () -> {
    };

    public YamlQuestRepository(Path directory, QuestValidator validator, Logger logger) {
        this.directory = directory;
        this.validator = validator;
        this.logger = logger;
    }

    /** Called after any change to the loaded set, on the thread that made the change. */
    public void onChange(Runnable listener) {
        this.changeListener = listener;
    }

    /** (Re)loads every quest file. Returns the number of quests loaded. */
    public int load() throws IOException {
        Files.createDirectories(directory);
        Map<String, QuestDefinition> loaded = new LinkedHashMap<>();
        Map<String, List<QuestValidator.Issue>> found = new LinkedHashMap<>();

        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.yml")) {
            for (Path file : files) {
                String fileName = file.getFileName().toString();
                String id = fileName.substring(0, fileName.length() - ".yml".length());
                try {
                    YamlConfiguration yaml = new YamlConfiguration();
                    yaml.loadFromString(Files.readString(file, StandardCharsets.UTF_8));
                    QuestDefinition quest = QuestYamlCodec.decode(id, yaml);
                    List<QuestValidator.Issue> issues = validator.validate(quest);
                    if (!issues.isEmpty()) {
                        found.put(id, issues);
                        issues.forEach(issue -> logger.log(issue.severity() == QuestValidator.Severity.ERROR ? Level.SEVERE : Level.WARNING,
                                "Quest " + id + ": " + issue));
                    }
                    if (QuestValidator.hasErrors(issues)) {
                        logger.severe("Quest " + id + " was not loaded because it has errors.");
                        continue;
                    }
                    loaded.put(id, quest);
                } catch (InvalidConfigurationException | QuestFormatException e) {
                    found.put(id, List.of(new QuestValidator.Issue(QuestValidator.Severity.ERROR, fileName, e.getMessage())));
                    logger.severe("Quest file " + fileName + " could not be read: " + e.getMessage());
                }
            }
        }

        quests.clear();
        quests.putAll(loaded);
        problems.clear();
        problems.putAll(found);
        changeListener.run();
        return loaded.size();
    }

    @Override
    public Optional<QuestDefinition> get(String id) {
        return Optional.ofNullable(quests.get(id));
    }

    @Override
    public Collection<QuestDefinition> all() {
        return List.copyOf(quests.values());
    }

    /** Issues found during the last load, by quest id. */
    public Map<String, List<QuestValidator.Issue>> problems() {
        return Map.copyOf(problems);
    }

    /** Whether a file exists for the id, even if it failed to load. */
    public boolean exists(String id) {
        return quests.containsKey(id) || Files.exists(file(id));
    }

    @Override
    public void save(QuestDefinition quest) throws IOException {
        QuestIds.requireValid(quest.id(), "Quest");
        List<QuestValidator.Issue> issues = validator.validate(quest);
        if (QuestValidator.hasErrors(issues)) {
            throw new IllegalArgumentException("Quest " + quest.id() + " has errors: " + issues);
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of(
                "Journeys quest file. Edit in game with /journeys editor " + quest.id(),
                "or by hand, then run /journeys reload."));
        QuestYamlCodec.encode(quest, yaml);

        Files.createDirectories(directory);
        Path target = file(quest.id());
        Path temp = directory.resolve("." + quest.id() + ".yml.tmp");
        Files.writeString(temp, yaml.saveToString(), StandardCharsets.UTF_8);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }

        quests.put(quest.id(), quest);
        problems.remove(quest.id());
        changeListener.run();
    }

    @Override
    public boolean delete(String id) throws IOException {
        boolean existed = Files.deleteIfExists(file(id));
        existed |= quests.remove(id) != null;
        problems.remove(id);
        if (existed) {
            changeListener.run();
        }
        return existed;
    }

    private Path file(String id) {
        return directory.resolve(QuestIds.requireValid(id, "Quest") + ".yml");
    }
}
