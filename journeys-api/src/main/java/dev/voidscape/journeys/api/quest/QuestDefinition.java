package dev.voidscape.journeys.api.quest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.kyori.adventure.key.Key;

/**
 * An immutable quest definition.
 *
 * <p>A quest is a directed graph of {@link QuestStage stages}. Play begins at {@link #startStage()}; when a stage's
 * objectives are done, one of its {@link StageTransition transitions} is followed to another stage, to completion,
 * or to failure. A stage without transitions completes the quest.</p>
 *
 * <p>All player-facing text is stored as MiniMessage strings.</p>
 *
 * @param id           unique id, also the file name ({@code quests/<id>.yml}); see {@link QuestIds}
 * @param displayName  MiniMessage name
 * @param description  MiniMessage lines
 * @param icon         item key for menus
 * @param settings     repeatability, cooldowns, holder scope and other flags
 * @param startStage   id of the first stage
 * @param stages       stages by id, in editor order
 * @param requirements conditions the starting player must pass
 * @param rewards      granted on completion
 * @param properties   free-form key/value data for integrations, e.g. {@code fancynpcs.givers}
 */
public record QuestDefinition(
        String id,
        String displayName,
        List<String> description,
        Key icon,
        QuestSettings settings,
        String startStage,
        Map<String, QuestStage> stages,
        List<ConfiguredCondition> requirements,
        List<ConfiguredReward> rewards,
        Map<String, String> properties
) {

    public QuestDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(startStage, "startStage");
        description = List.copyOf(description);
        stages = Collections.unmodifiableMap(new LinkedHashMap<>(stages));
        requirements = List.copyOf(requirements);
        rewards = List.copyOf(rewards);
        properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }

    public Optional<QuestStage> stage(String stageId) {
        return Optional.ofNullable(stages.get(stageId));
    }

    public Optional<String> property(String key) {
        return Optional.ofNullable(properties.get(key));
    }
}
