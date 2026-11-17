package dev.voidscape.journeys.core.editor;

import dev.voidscape.journeys.api.quest.QuestDefinition;
import dev.voidscape.journeys.core.quest.QuestValidator;
import dev.voidscape.journeys.core.quest.YamlQuestRepository;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks who is editing which quest. Only one player may edit a quest at a time so two editors never overwrite each
 * other's work; drafts survive closing the menu until saved or discarded.
 */
public final class EditorSessions {

    private final YamlQuestRepository repository;
    private final QuestValidator validator;
    private final Map<UUID, Session> byPlayer = new ConcurrentHashMap<>();

    public EditorSessions(YamlQuestRepository repository, QuestValidator validator) {
        this.repository = repository;
        this.validator = validator;
    }

    /** An open edit. {@code originalId} is null for quests that have not been saved yet. */
    public static final class Session {
        public final UUID editor;
        public final QuestDraft draft;
        public final String originalId;
        public final boolean isNew;

        Session(UUID editor, QuestDraft draft, boolean isNew) {
            this.editor = editor;
            this.draft = draft;
            this.originalId = draft.id;
            this.isNew = isNew;
        }
    }

    public sealed interface OpenResult {
        record Opened(Session session) implements OpenResult {
        }

        record Locked(UUID by) implements OpenResult {
        }
    }

    public Optional<Session> session(UUID player) {
        return Optional.ofNullable(byPlayer.get(player));
    }

    /** Opens (or resumes) an edit of an existing quest. */
    public OpenResult edit(UUID player, QuestDefinition quest) {
        Session existing = byPlayer.get(player);
        if (existing != null && existing.originalId.equals(quest.id())) {
            return new OpenResult.Opened(existing);
        }
        Optional<UUID> holder = lockHolder(quest.id());
        if (holder.isPresent() && !holder.get().equals(player)) {
            return new OpenResult.Locked(holder.get());
        }
        Session session = new Session(player, QuestDraft.of(quest), false);
        byPlayer.put(player, session);
        return new OpenResult.Opened(session);
    }

    /** Starts a brand-new quest. The caller has checked that the id is valid and unused. */
    public Session create(UUID player, String id) {
        Session session = new Session(player, new QuestDraft(id), true);
        byPlayer.put(player, session);
        return session;
    }

    public boolean isLocked(String questId) {
        return lockHolder(questId).isPresent();
    }

    private Optional<UUID> lockHolder(String questId) {
        return byPlayer.values().stream().filter(s -> s.originalId.equals(questId)).map(s -> s.editor).findFirst();
    }

    public void discard(UUID player) {
        byPlayer.remove(player);
    }

    public List<QuestValidator.Issue> validate(Session session) {
        return validator.validate(session.draft.build());
    }

    /**
     * Validates and saves the draft. Returns the issues; when any is an error nothing is saved.
     */
    public List<QuestValidator.Issue> save(Session session) throws IOException {
        QuestDefinition quest = session.draft.build();
        List<QuestValidator.Issue> issues = validator.validate(quest);
        if (QuestValidator.hasErrors(issues)) {
            return issues;
        }
        repository.save(quest);
        byPlayer.remove(session.editor);
        return issues;
    }
}
