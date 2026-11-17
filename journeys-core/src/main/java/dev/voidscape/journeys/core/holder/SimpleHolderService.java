package dev.voidscape.journeys.core.holder;

import dev.voidscape.journeys.api.holder.HolderScope;
import dev.voidscape.journeys.api.holder.HolderService;
import dev.voidscape.journeys.api.holder.PartyProvider;
import dev.voidscape.journeys.api.holder.QuestHolder;
import dev.voidscape.journeys.api.holder.SoloHolder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.jspecify.annotations.Nullable;

/** Default holder service: solo holders always, party holders when a provider is set. */
public final class SimpleHolderService implements HolderService {

    private final Logger logger;
    private volatile @Nullable PartyProvider partyProvider;
    private Consumer<UUID> membershipListener = id -> {
    };

    public SimpleHolderService(Logger logger) {
        this.logger = logger;
    }

    /** Set by the engine so it can load or release party progress. */
    public void onMembershipChange(Consumer<UUID> listener) {
        this.membershipListener = listener;
    }

    @Override
    public QuestHolder solo(UUID playerId) {
        return new SoloHolder(playerId);
    }

    @Override
    public Optional<QuestHolder> party(UUID playerId) {
        PartyProvider provider = partyProvider;
        if (provider == null) {
            return Optional.empty();
        }
        try {
            return provider.partyOf(playerId).filter(holder -> holder.scope() == HolderScope.PARTY);
        } catch (RuntimeException e) {
            logger.warning("Party provider " + provider.name() + " failed: " + e);
            return Optional.empty();
        }
    }

    @Override
    public QuestHolder holderFor(UUID playerId, HolderScope scope) {
        if (scope == HolderScope.PARTY) {
            return party(playerId).orElseGet(() -> solo(playerId));
        }
        return solo(playerId);
    }

    @Override
    public List<QuestHolder> holdersOf(UUID playerId) {
        List<QuestHolder> holders = new ArrayList<>(2);
        holders.add(solo(playerId));
        party(playerId).ifPresent(holders::add);
        return holders;
    }

    @Override
    public void setPartyProvider(@Nullable PartyProvider provider) {
        this.partyProvider = provider;
        if (provider != null) {
            logger.info("Using party provider " + provider.name());
        }
    }

    @Override
    public void partyChanged(UUID playerId) {
        membershipListener.accept(playerId);
    }
}
