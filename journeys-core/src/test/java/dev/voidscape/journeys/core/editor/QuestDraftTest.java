package dev.voidscape.journeys.core.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidscape.journeys.api.quest.QuestDefinition;
import dev.voidscape.journeys.api.quest.TransitionTarget;
import dev.voidscape.journeys.core.QuestFixtures;
import org.junit.jupiter.api.Test;

class QuestDraftTest {

    @Test
    void draftRoundTripsToAnEqualDefinition() {
        QuestDefinition quest = QuestFixtures.branching();
        assertEquals(quest, QuestDraft.of(quest).build());
    }

    @Test
    void renamingAStageRewritesReferences() {
        QuestDraft draft = QuestDraft.of(QuestFixtures.branching());
        draft.renameStage("start", "begin");
        draft.renameStage("left", "west");

        QuestDefinition quest = draft.build();
        assertEquals("begin", quest.startStage());
        assertEquals(TransitionTarget.stage("west"), quest.stage("begin").orElseThrow().transitions().getFirst().target());
        assertEquals("begin", quest.stages().keySet().iterator().next(), "stage order is preserved");
    }

    @Test
    void removingAStageDropsTransitionsToIt() {
        QuestDraft draft = QuestDraft.of(QuestFixtures.branching());
        draft.removeStage("right");
        QuestDefinition quest = draft.build();
        assertFalse(quest.stages().containsKey("right"));
        assertTrue(quest.stage("start").orElseThrow().transitions().stream()
                .noneMatch(t -> t.target().equals(TransitionTarget.stage("right"))));
    }
}
