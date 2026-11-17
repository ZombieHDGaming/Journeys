package dev.voidscape.journeys.core.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidscape.journeys.api.holder.HolderScope;
import dev.voidscape.journeys.api.quest.BranchMode;
import dev.voidscape.journeys.api.quest.ObjectiveMode;
import dev.voidscape.journeys.api.quest.QuestDefinition;
import dev.voidscape.journeys.api.quest.QuestStage;
import dev.voidscape.journeys.api.quest.TransitionTarget;
import dev.voidscape.journeys.core.QuestFixtures;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class QuestYamlCodecTest {

    private static QuestDefinition roundTrip(QuestDefinition quest) throws Exception {
        YamlConfiguration out = new YamlConfiguration();
        QuestYamlCodec.encode(quest, out);
        YamlConfiguration in = new YamlConfiguration();
        in.loadFromString(out.saveToString());
        return QuestYamlCodec.decode(quest.id(), in);
    }

    @Test
    void encodeThenDecodeIsLossless() throws Exception {
        QuestDefinition quest = QuestFixtures.branching();
        assertEquals(quest, roundTrip(quest));
    }

    @Test
    void bundledExampleQuestParses() throws Exception {
        YamlConfiguration yaml;
        try (var in = new InputStreamReader(Objects.requireNonNull(
                getClass().getResourceAsStream("/quests/example_woodcutter.yml")), StandardCharsets.UTF_8)) {
            yaml = YamlConfiguration.loadConfiguration(in);
        }
        QuestDefinition quest = QuestYamlCodec.decode("example_woodcutter", yaml);

        assertEquals("gather", quest.startStage());
        assertEquals(Duration.ofDays(1), quest.settings().cooldown());
        assertEquals(HolderScope.SOLO, quest.settings().scope());

        QuestStage gather = quest.stage("gather").orElseThrow();
        assertEquals(BranchMode.CHOICE, gather.branchMode());
        assertEquals(2, gather.transitions().size());
        assertEquals("oak_log, birch_log", gather.objectives().getFirst().params().raw("block").orElseThrow(),
                "YAML lists become comma separated parameter values");
        assertEquals(1, gather.transitions().get(1).conditions().size());

        assertEquals(ObjectiveMode.SEQUENTIAL, quest.stage("deliver").orElseThrow().objectiveMode());
        assertEquals(TransitionTarget.COMPLETE, quest.stage("deliver").orElseThrow().transitions().getFirst().target());
        assertTrue(quest.stage("sell").orElseThrow().isFinal());

        // And it survives a write/read cycle.
        assertEquals(quest, roundTrip(quest));
    }

    @Test
    void bundledGatedExampleParses() throws Exception {
        YamlConfiguration yaml;
        try (var in = new InputStreamReader(Objects.requireNonNull(
                getClass().getResourceAsStream("/quests/example_lumber_baron.yml")), StandardCharsets.UTF_8)) {
            yaml = YamlConfiguration.loadConfiguration(in);
        }
        QuestDefinition quest = QuestYamlCodec.decode("example_lumber_baron", yaml);
        assertEquals("story", quest.settings().board().category());
        assertEquals(false, quest.settings().board().showWhenLocked());
        assertEquals(2, quest.requirements().size());
        assertEquals("<gray>Sell your own wood for the woodcutter", quest.requirements().getFirst().description());
        assertEquals(quest, roundTrip(quest));
    }

    @Test
    void missingBoardSettingsUseDefaults() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                settings:
                  repeatable: true
                stages:
                  only: {}
                """);
        QuestDefinition quest = QuestYamlCodec.decode("q", yaml);
        assertEquals(dev.voidscape.journeys.api.quest.BoardSettings.DEFAULT, quest.settings().board());
        assertEquals(false, quest.settings().requirePermission());
    }

    @Test
    void typeKeysDefaultToTheJourneysNamespace() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                stages:
                  only:
                    objectives:
                      - type: kill_entity
                """);
        QuestDefinition quest = QuestYamlCodec.decode("q", yaml);
        assertEquals(Key.key("journeys", "kill_entity"), quest.stage("only").orElseThrow().objectives().getFirst().type());
        assertEquals("only", quest.startStage(), "start stage defaults to the first stage");
    }

    @Test
    void structuralErrorsNameThePath() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                stages:
                  a:
                    objective-mode: sometimes
                """);
        QuestFormatException error = assertThrows(QuestFormatException.class, () -> QuestYamlCodec.decode("q", yaml));
        assertTrue(error.getMessage().startsWith("stages.a.objective-mode"), error.getMessage());
    }
}
