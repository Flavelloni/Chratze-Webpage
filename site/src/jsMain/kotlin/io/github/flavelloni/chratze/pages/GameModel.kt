package io.github.flavelloni.chratze.pages

enum class SwissSuit(
    val assetPath: String,
    val ink: String,
) {
    Eichle("/suits/eichle.png", "#1f2512"),
    Rose("/suits/rose.png", "#2a2314"),
    Schilte("/suits/schilte.png", "#171717"),
    Schelle("/suits/schelle.png", "#171717"),
}

enum class Seat(val label: String, val avatar: String) {
    User("You", "\uD83D\uDE42"),
    Player1("Player 1", "\uD83D\uDE0E"),
    Player2("Player 2", "\uD83E\uDD20"),
    Player3("Player 3", "\uD83E\uDDD0"),
}

enum class GamePhase {
    Idle,
    Dealing,
    CallingChratze,
    CallingAlong,
    Exchanging,
    Playing,
    TrickReview,
    Collecting,
    Settlement,
    Finished,
}

data class SwissCard(val rank: String, val suit: SwissSuit) {
    val id = "${suit.name}-$rank"
}

data class PlayedCard(val card: SwissCard, val seat: Seat)

data class DealAnimation(
    val card: SwissCard,
    val target: Seat,
    val arrived: Boolean,
)

data class CardFlight(
    val card: SwissCard,
    val from: Pair<String, String>,
    val to: Pair<String, String>,
    val arrived: Boolean,
    val faceUp: Boolean,
    val offset: Int,
)

data class GameState(
    val phase: GamePhase = GamePhase.Idle,
    val hands: Map<Seat, List<SwissCard>> = emptyHands(),
    val drawPile: List<SwissCard> = deck,
    val trump: SwissCard? = null,
    val trick: List<PlayedCard> = emptyList(),
    val dealer: Seat = Seat.User,
    val chratzer: Seat? = null,
    val leader: Seat = Seat.User,
    val active: Seat = Seat.User,
    val activePlayers: Set<Seat> = seats.toSet(),
    val outPlayers: Set<Seat> = emptySet(),
    val scores: Map<Seat, Int> = emptyScores(),
    val bankrolls: Map<Seat, Int> = emptyMoney(),
    val pot: Int = 0,
    val moneyEvents: Map<Seat, Int> = emptyMap(),
    val speechBubbles: Map<Seat, String> = emptyMap(),
    val selectedExchangeIds: Set<String> = emptySet(),
    val dealAnimation: DealAnimation? = null,
    val cardFlights: List<CardFlight> = emptyList(),
    val trumpAttempts: Int = 0,
    val pendingTrickWinner: Seat? = null,
    val roundMessage: String = "Start a new round.",
)

val seats = listOf(Seat.User, Seat.Player1, Seat.Player2, Seat.Player3)
val counterClockwiseSeats = listOf(Seat.User, Seat.Player1, Seat.Player2, Seat.Player3)
val ranks = listOf("6", "7", "8", "9", "10", "Under", "Ober", "Koenig", "Ass")
val deck = SwissSuit.entries.flatMap { suit -> ranks.map { rank -> SwissCard(rank, suit) } }
const val CHF = 100

fun emptyHands(): Map<Seat, List<SwissCard>> = seats.associateWith { emptyList() }

fun emptyScores(): Map<Seat, Int> = seats.associateWith { 0 }

fun emptyMoney(): Map<Seat, Int> = seats.associateWith { 0 }

fun Map<Seat, Int>.getValueOrZero(seat: Seat): Int = this[seat] ?: 0

fun Int.chf(): String {
    val sign = if (this < 0) "-" else ""
    val absValue = kotlin.math.abs(this)
    val francs = absValue / 100
    val rappen = absValue % 100
    return "${sign}CHF $francs.${rappen.toString().padStart(2, '0')}"
}

fun trickLabel(count: Int): String = "$count ${if (count == 1) "trick" else "tricks"}"

fun moneyBag(): String = "\uD83D\uDCB0"

fun SwissCard.rankLabel() = when (rank) {
    "Under" -> "U"
    "Ober" -> "O"
    "Koenig" -> "K"
    "Ass" -> "A"
    else -> rank
}
