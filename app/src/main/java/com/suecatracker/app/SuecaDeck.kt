package com.suecatracker.app

enum class Suit(val symbol: String) {
    SPADES("♠"),
    HEARTS("♥"),
    DIAMONDS("♦"),
    CLUBS("♣")
}

enum class Rank(val label: String, val points: Int) {
    ACE("A", 11),
    SEVEN("7", 10),
    KING("K", 4),
    JACK("J", 3),
    QUEEN("Q", 2),
    SIX("6", 0),
    FIVE("5", 0),
    FOUR("4", 0),
    THREE("3", 0),
    TWO("2", 0)
}

data class SuecaCard(
    val suit: Suit,
    val rank: Rank,
    val isPlayed: Boolean = false
)

object SuecaDeck {
    val ranksInOrder: List<Rank> = listOf(
        Rank.ACE,
        Rank.SEVEN,
        Rank.KING,
        Rank.JACK,
        Rank.QUEEN,
        Rank.SIX,
        Rank.FIVE,
        Rank.FOUR,
        Rank.THREE,
        Rank.TWO
    )

    val suitsInOrder: List<Suit> = listOf(
        Suit.SPADES,
        Suit.HEARTS,
        Suit.DIAMONDS,
        Suit.CLUBS
    )

    fun createFreshDeck(): MutableList<SuecaCard> {
        val deck = mutableListOf<SuecaCard>()
        for (suit in suitsInOrder) {
            for (rank in ranksInOrder) {
                deck.add(SuecaCard(suit = suit, rank = rank, isPlayed = false))
            }
        }
        return deck
    }
}
