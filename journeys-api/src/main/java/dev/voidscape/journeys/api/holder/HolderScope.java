package dev.voidscape.journeys.api.holder;

/** Who owns the progress of a quest. */
public enum HolderScope {
    /** Each player progresses on their own. */
    SOLO,
    /**
     * Progress is shared by the player's party, as reported by a registered {@link PartyProvider}. Players who are not
     * in a party, or servers without a provider, fall back to {@link #SOLO}.
     */
    PARTY
}
