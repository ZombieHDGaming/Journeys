package dev.voidscape.journeys.api.quest;

import java.util.Objects;

/**
 * Destination of a {@link StageTransition}: another stage, quest completion, or quest failure.
 *
 * <p>Serialized as the stage id, or the reserved values {@value #COMPLETE_TOKEN} and {@value #FAIL_TOKEN}. Stage ids
 * cannot start with {@code @}, so the forms never collide.</p>
 */
public sealed interface TransitionTarget {

    String COMPLETE_TOKEN = "@complete";
    String FAIL_TOKEN = "@fail";

    /** Moves to another stage of the same quest. */
    record ToStage(String stageId) implements TransitionTarget {
        public ToStage {
            Objects.requireNonNull(stageId, "stageId");
        }

        @Override
        public String serialize() {
            return stageId;
        }
    }

    /** Completes the quest and grants its rewards. */
    record Complete() implements TransitionTarget {
        @Override
        public String serialize() {
            return COMPLETE_TOKEN;
        }
    }

    /** Fails the quest. */
    record Fail() implements TransitionTarget {
        @Override
        public String serialize() {
            return FAIL_TOKEN;
        }
    }

    TransitionTarget COMPLETE = new Complete();
    TransitionTarget FAIL = new Fail();

    String serialize();

    static TransitionTarget parse(String raw) {
        return switch (raw) {
            case COMPLETE_TOKEN -> COMPLETE;
            case FAIL_TOKEN -> FAIL;
            default -> new ToStage(raw);
        };
    }

    static TransitionTarget stage(String stageId) {
        return new ToStage(stageId);
    }
}
