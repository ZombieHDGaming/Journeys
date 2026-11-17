package dev.voidscape.journeys.core.quest;

import dev.voidscape.journeys.api.holder.HolderScope;
import dev.voidscape.journeys.api.quest.BranchMode;
import dev.voidscape.journeys.api.quest.ConfiguredCondition;
import dev.voidscape.journeys.api.quest.ConfiguredObjective;
import dev.voidscape.journeys.api.quest.ConfiguredReward;
import dev.voidscape.journeys.api.quest.ObjectiveMode;
import dev.voidscape.journeys.api.quest.QuestDefinition;
import dev.voidscape.journeys.api.quest.QuestSettings;
import dev.voidscape.journeys.api.quest.QuestStage;
import dev.voidscape.journeys.api.quest.StageTransition;
import dev.voidscape.journeys.api.quest.TransitionTarget;
import dev.voidscape.journeys.api.type.ParameterValues;
import dev.voidscape.journeys.core.util.Durations;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.jspecify.annotations.Nullable;

/**
 * Converts between quest definitions and their YAML form. See {@code docs/quest-format.md} for the format.
 *
 * <p>This only checks structure (required keys, number formats, enum values). Semantic checks such as unknown types
 * or dangling transitions live in {@link QuestValidator} so the editor can show them all at once.</p>
 */
public final class QuestYamlCodec {

    /** Namespace assumed for type keys written without one, e.g. {@code kill_entity}. */
    public static final String DEFAULT_NAMESPACE = "journeys";

    private QuestYamlCodec() {
    }

    // ---------------------------------------------------------------- decode

    public static QuestDefinition decode(String id, ConfigurationSection root) throws QuestFormatException {
        Map<String, QuestStage> stages = new LinkedHashMap<>();
        ConfigurationSection stagesSection = root.getConfigurationSection("stages");
        if (stagesSection != null) {
            for (String stageId : stagesSection.getKeys(false)) {
                ConfigurationSection section = stagesSection.getConfigurationSection(stageId);
                if (section == null) {
                    throw new QuestFormatException("stages." + stageId, "must be a section");
                }
                stages.put(stageId, decodeStage(stageId, section, "stages." + stageId));
            }
        }

        String startStage = root.getString("start-stage");
        if (startStage == null) {
            startStage = stages.isEmpty() ? "" : stages.keySet().iterator().next();
        }

        return new QuestDefinition(
                id,
                root.getString("display-name", id),
                root.getStringList("description"),
                key(root.getString("icon", "minecraft:book"), "icon", "minecraft"),
                decodeSettings(root.getConfigurationSection("settings")),
                startStage,
                stages,
                decodeConditions(root, "requirements"),
                decodeRewards(root, "rewards"),
                stringMap(root.getConfigurationSection("properties"))
        );
    }

    private static QuestSettings decodeSettings(@Nullable ConfigurationSection section) throws QuestFormatException {
        if (section == null) {
            return QuestSettings.DEFAULT;
        }
        QuestSettings d = QuestSettings.DEFAULT;
        try {
            return new QuestSettings(
                    section.getBoolean("repeatable", d.repeatable()),
                    Durations.parse(section.getString("cooldown", "0s")),
                    section.getInt("max-completions", d.maxCompletions()),
                    enumValue(HolderScope.class, section.getString("scope", d.scope().name()), "settings.scope"),
                    section.getBoolean("abandonable", d.abandonable()),
                    section.getBoolean("hidden", d.hidden())
            );
        } catch (IllegalArgumentException e) {
            throw new QuestFormatException("settings", e.getMessage());
        }
    }

    private static QuestStage decodeStage(String id, ConfigurationSection section, String path) throws QuestFormatException {
        List<ConfiguredObjective> objectives = new ArrayList<>();
        List<Map<?, ?>> rawObjectives = section.getMapList("objectives");
        for (int i = 0; i < rawObjectives.size(); i++) {
            String itemPath = path + ".objectives[" + i + "]";
            ConfigurationSection objective = toSection(rawObjectives.get(i));
            String objectiveId = objective.getString("id", "objective_" + (i + 1));
            int amount = objective.getInt("amount", 1);
            if (amount < 1) {
                throw new QuestFormatException(itemPath + ".amount", "must be at least 1");
            }
            objectives.add(new ConfiguredObjective(
                    objectiveId,
                    requiredKey(objective, itemPath),
                    amount,
                    params(objective.getConfigurationSection("params")),
                    objective.getString("description")
            ));
        }

        List<StageTransition> transitions = new ArrayList<>();
        List<Map<?, ?>> rawTransitions = section.getMapList("transitions");
        for (int i = 0; i < rawTransitions.size(); i++) {
            String itemPath = path + ".transitions[" + i + "]";
            ConfigurationSection transition = toSection(rawTransitions.get(i));
            String to = transition.getString("to");
            if (to == null || to.isBlank()) {
                throw new QuestFormatException(itemPath + ".to", "is required");
            }
            transitions.add(new StageTransition(
                    TransitionTarget.parse(to.trim()),
                    transition.getString("label"),
                    decodeConditions(transition, "conditions")
            ));
        }

        return new QuestStage(
                id,
                section.getString("display-name", id),
                section.getStringList("description"),
                enumValue(ObjectiveMode.class, section.getString("objective-mode", "all"), path + ".objective-mode"),
                objectives,
                enumValue(BranchMode.class, section.getString("branch-mode", "automatic"), path + ".branch-mode"),
                transitions,
                decodeRewards(section, "rewards")
        );
    }

    private static List<ConfiguredCondition> decodeConditions(ConfigurationSection parent, String key) throws QuestFormatException {
        List<ConfiguredCondition> conditions = new ArrayList<>();
        List<Map<?, ?>> raw = parent.getMapList(key);
        for (int i = 0; i < raw.size(); i++) {
            ConfigurationSection section = toSection(raw.get(i));
            conditions.add(new ConfiguredCondition(
                    requiredKey(section, key + "[" + i + "]"),
                    params(section.getConfigurationSection("params")),
                    section.getBoolean("negate", false)
            ));
        }
        return conditions;
    }

    private static List<ConfiguredReward> decodeRewards(ConfigurationSection parent, String key) throws QuestFormatException {
        List<ConfiguredReward> rewards = new ArrayList<>();
        List<Map<?, ?>> raw = parent.getMapList(key);
        for (int i = 0; i < raw.size(); i++) {
            ConfigurationSection section = toSection(raw.get(i));
            rewards.add(new ConfiguredReward(
                    requiredKey(section, key + "[" + i + "]"),
                    params(section.getConfigurationSection("params"))
            ));
        }
        return rewards;
    }

    /** Parameter values are stored as strings. YAML lists are joined with commas, matching list parameter types. */
    private static ParameterValues params(@Nullable ConfigurationSection section) {
        if (section == null) {
            return ParameterValues.empty();
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            if (value instanceof List<?> list) {
                values.put(key, String.join(", ", list.stream().map(String::valueOf).toList()));
            } else if (value != null) {
                values.put(key, String.valueOf(value));
            }
        }
        return ParameterValues.of(values);
    }

    /**
     * Reads leaf values with their full dotted path. Bukkit treats dots in keys as nesting, so a property such as
     * {@code fancynpcs.givers} is written as a nested section and read back here as one key.
     */
    private static Map<String, String> stringMap(@Nullable ConfigurationSection section) {
        Map<String, String> values = new LinkedHashMap<>();
        if (section != null) {
            for (String key : section.getKeys(true)) {
                Object value = section.get(key);
                if (value != null && !(value instanceof ConfigurationSection)) {
                    values.put(key, String.valueOf(value));
                }
            }
        }
        return values;
    }

    private static Key requiredKey(ConfigurationSection section, String path) throws QuestFormatException {
        String raw = section.getString("type");
        if (raw == null || raw.isBlank()) {
            throw new QuestFormatException(path + ".type", "is required");
        }
        return key(raw, path + ".type", DEFAULT_NAMESPACE);
    }

    /** Parses a key, applying {@code defaultNamespace} when none is given. */
    public static Key key(String raw, String path, String defaultNamespace) throws QuestFormatException {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        try {
            return value.indexOf(':') >= 0 ? Key.key(value) : Key.key(defaultNamespace, value);
        } catch (InvalidKeyException e) {
            throw new QuestFormatException(path, "'" + raw + "' is not a valid key");
        }
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String raw, String path) throws QuestFormatException {
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            List<String> options = new ArrayList<>();
            for (E constant : type.getEnumConstants()) {
                options.add(constant.name().toLowerCase(Locale.ROOT));
            }
            throw new QuestFormatException(path, "'" + raw + "' is not one of " + options);
        }
    }

    private static ConfigurationSection toSection(Map<?, ?> map) {
        MemoryConfiguration section = new MemoryConfiguration();
        map.forEach((k, v) -> {
            if (v instanceof Map<?, ?> nested) {
                section.createSection(String.valueOf(k), nested);
            } else {
                section.set(String.valueOf(k), v);
            }
        });
        return section;
    }

    // ---------------------------------------------------------------- encode

    public static void encode(QuestDefinition quest, ConfigurationSection root) {
        root.set("display-name", quest.displayName());
        root.set("description", quest.description());
        root.set("icon", quest.icon().asString());

        QuestSettings s = quest.settings();
        ConfigurationSection settings = root.createSection("settings");
        settings.set("repeatable", s.repeatable());
        settings.set("cooldown", Durations.format(s.cooldown()));
        settings.set("max-completions", s.maxCompletions());
        settings.set("scope", lower(s.scope()));
        settings.set("abandonable", s.abandonable());
        settings.set("hidden", s.hidden());

        root.set("start-stage", quest.startStage());
        root.set("requirements", quest.requirements().stream().map(QuestYamlCodec::encodeCondition).toList());
        root.set("rewards", quest.rewards().stream().map(QuestYamlCodec::encodeReward).toList());
        if (!quest.properties().isEmpty()) {
            ConfigurationSection properties = root.createSection("properties");
            quest.properties().forEach(properties::set);
        }

        ConfigurationSection stages = root.createSection("stages");
        for (QuestStage stage : quest.stages().values()) {
            ConfigurationSection section = stages.createSection(stage.id());
            section.set("display-name", stage.displayName());
            section.set("description", stage.description());
            section.set("objective-mode", lower(stage.objectiveMode()));
            section.set("objectives", stage.objectives().stream().map(QuestYamlCodec::encodeObjective).toList());
            section.set("branch-mode", lower(stage.branchMode()));
            section.set("transitions", stage.transitions().stream().map(QuestYamlCodec::encodeTransition).toList());
            section.set("rewards", stage.rewards().stream().map(QuestYamlCodec::encodeReward).toList());
        }
    }

    private static Map<String, Object> encodeObjective(ConfiguredObjective objective) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", objective.id());
        map.put("type", objective.type().asString());
        map.put("amount", objective.amount());
        if (objective.description() != null) {
            map.put("description", objective.description());
        }
        putParams(map, objective.params());
        return map;
    }

    private static Map<String, Object> encodeTransition(StageTransition transition) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("to", transition.target().serialize());
        if (transition.label() != null) {
            map.put("label", transition.label());
        }
        if (!transition.conditions().isEmpty()) {
            map.put("conditions", transition.conditions().stream().map(QuestYamlCodec::encodeCondition).toList());
        }
        return map;
    }

    private static Map<String, Object> encodeCondition(ConfiguredCondition condition) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", condition.type().asString());
        if (condition.negate()) {
            map.put("negate", true);
        }
        putParams(map, condition.params());
        return map;
    }

    private static Map<String, Object> encodeReward(ConfiguredReward reward) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", reward.type().asString());
        putParams(map, reward.params());
        return map;
    }

    private static void putParams(Map<String, Object> map, ParameterValues params) {
        if (!params.asMap().isEmpty()) {
            map.put("params", new LinkedHashMap<>(params.asMap()));
        }
    }

    private static String lower(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
