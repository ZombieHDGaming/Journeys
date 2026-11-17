package dev.voidscape.journeys.core;

import dev.voidscape.journeys.api.holder.HolderScope;
import dev.voidscape.journeys.api.quest.BoardSettings;
import dev.voidscape.journeys.api.quest.BossBarSettings;
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
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;

/** Builds small quests for tests. */
public final class QuestFixtures {

    private QuestFixtures() {
    }

    public static ConfiguredObjective objective(String id, int amount) {
        return new ConfiguredObjective(id, Key.key("journeys:test_objective"), amount, ParameterValues.of(Map.of("target", id)), null);
    }

    public static QuestStage stage(String id, ObjectiveMode mode, List<ConfiguredObjective> objectives, BranchMode branch,
                                   List<StageTransition> transitions) {
        return new QuestStage(id, id, List.of(), mode, objectives, branch, transitions,
                List.of(new ConfiguredReward(Key.key("journeys:test_reward"), ParameterValues.empty())));
    }

    public static StageTransition to(String target) {
        return new StageTransition(TransitionTarget.parse(target), null, List.of());
    }

    public static StageTransition to(String target, String label, int level) {
        return new StageTransition(TransitionTarget.parse(target), label,
                List.of(new ConfiguredCondition(Key.key("journeys:test_condition"), ParameterValues.of(Map.of("level", Integer.toString(level))), false)));
    }

    /** start --(choice)--> left | right; left completes, right fails. */
    public static QuestDefinition branching() {
        Map<String, QuestStage> stages = new LinkedHashMap<>();
        stages.put("start", stage("start", ObjectiveMode.ALL, List.of(objective("a", 3), objective("b", 1)), BranchMode.CHOICE,
                List.of(to("left", "<green>Left", 1), to("right", "<red>Right", 5))));
        stages.put("left", stage("left", ObjectiveMode.SEQUENTIAL, List.of(objective("c", 1), objective("d", 2)), BranchMode.AUTOMATIC,
                List.of(to("@complete"))));
        stages.put("right", stage("right", ObjectiveMode.ANY, List.of(), BranchMode.AUTOMATIC, List.of(to("@fail"))));
        return new QuestDefinition("branching", "<gold>Branching", List.of("<gray>A test quest"), Key.key("minecraft:book"),
                new QuestSettings(true, Duration.ofHours(2), 3, HolderScope.PARTY, false, true, true,
                        new BoardSettings("story", BoardSettings.BoardMode.VIEW, false),
                        new BossBarSettings(true, BossBar.Color.PURPLE, BossBar.Overlay.NOTCHED_10, BossBarSettings.Mode.ON_PROGRESS)),
                "start", stages,
                List.of(new ConfiguredCondition(Key.key("journeys:test_condition"), ParameterValues.of(Map.of("level", "2")), true,
                        "<gray>Be at least level 2")),
                List.of(new ConfiguredReward(Key.key("journeys:test_reward"), ParameterValues.empty())),
                Map.of("fancynpcs.givers", "bob"));
    }
}
