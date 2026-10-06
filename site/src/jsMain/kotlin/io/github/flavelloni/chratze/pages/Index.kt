package io.github.flavelloni.chratze.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.core.data.add
import com.varabyte.kobweb.core.init.InitRoute
import com.varabyte.kobweb.core.init.InitRouteContext
import com.varabyte.kobweb.core.layout.Layout
import com.varabyte.kobweb.navigation.BasePath
import io.github.flavelloni.chratze.components.layouts.PageLayoutData
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Text

private enum class SwissSuit(
    val assetPath: String,
    val ink: String,
) {
    Eichle("/suits/eichle.png", "#1f2512"),
    Rose("/suits/rose.png", "#2a2314"),
    Schilte("/suits/schilte.png", "#171717"),
    Schelle("/suits/schelle.png", "#171717"),
}

private enum class Seat(val label: String, val avatar: String) {
    User("You", "🙂"),
    Player1("Player 1", "😎"),
    Player2("Player 2", "🤠"),
    Player3("Player 3", "🧐"),
}

private enum class GamePhase {
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

private data class SwissCard(val rank: String, val suit: SwissSuit) {
    val id = "${suit.name}-$rank"
}

private data class PlayedCard(val card: SwissCard, val seat: Seat)

private data class DealAnimation(
    val card: SwissCard,
    val target: Seat,
    val arrived: Boolean,
)

private data class CardFlight(
    val card: SwissCard,
    val from: Pair<String, String>,
    val to: Pair<String, String>,
    val arrived: Boolean,
    val faceUp: Boolean,
    val offset: Int,
)

private data class GameState(
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

private val seats = listOf(Seat.User, Seat.Player1, Seat.Player2, Seat.Player3)
private val counterClockwiseSeats = listOf(Seat.User, Seat.Player1, Seat.Player2, Seat.Player3)
private val ranks = listOf("6", "7", "8", "9", "10", "Under", "Ober", "Koenig", "Ass")
private val deck = SwissSuit.entries.flatMap { suit -> ranks.map { rank -> SwissCard(rank, suit) } }
private const val CHF = 100

@InitRoute
fun initHomePage(ctx: InitRouteContext) {
    ctx.data.add(PageLayoutData("Game Setup"))
}

@Page
@Layout(".components.layouts.PageLayout")
@Composable
fun HomePage() {
    var game by remember { mutableStateOf(GameState()) }
    val scope = rememberCoroutineScope()

    Div({
        attr(
            "style",
            """
                display:flex;
                flex-direction:column;
                gap:24px;
                width:100%;
                padding:14px 0 42px;
            """.trimIndent()
        )
    }) {
        Header()
        GameTable(
            game = game,
            onNewRoundClick = {
                scope.launch {
                    game = dealNewRound(game) { next -> game = next }
                    game = advanceComputers(game) { next -> game = next }
                }
            },
            onUserBid = { saysChratze ->
                if (game.phase == GamePhase.CallingChratze && game.active == Seat.User) {
                    scope.launch {
                        game = userBid(game, saysChratze) { next -> game = next }
                        game = advanceComputers(game) { next -> game = next }
                    }
                }
            },
            onUserJoin = { joins ->
                if (game.phase == GamePhase.CallingAlong && game.active == Seat.User) {
                    scope.launch {
                        game = userJoin(game, joins) { next -> game = next }
                        game = advanceComputers(game) { next -> game = next }
                    }
                }
            },
            onUserCardClick = { card ->
                when {
                    canUserSelectExchange(game, card) -> game = toggleExchangeSelection(game, card)
                    canUserPlay(game, card) -> {
                        scope.launch {
                            game = playTurn(game, Seat.User, card) { next -> game = next }
                            game = advanceComputers(game) { next -> game = next }
                        }
                    }
                }
            },
            onUserExchangeConfirm = {
                if (game.phase == GamePhase.Exchanging && game.active == Seat.User) {
                    scope.launch {
                        game = exchangeCards(game, Seat.User, game.selectedExchangeIds) { next -> game = next }
                        game = advanceComputers(game) { next -> game = next }
                    }
                }
            },
            onTableClick = {
                if (game.phase == GamePhase.TrickReview) {
                    scope.launch {
                        game = collectReviewedTrick(game) { next -> game = next }
                        game = advanceComputers(game) { next -> game = next }
                    }
                }
            },
        )
    }
}

private fun emptyHands(): Map<Seat, List<SwissCard>> = seats.associateWith { emptyList() }

private fun emptyScores(): Map<Seat, Int> = seats.associateWith { 0 }

private fun emptyMoney(): Map<Seat, Int> = seats.associateWith { 0 }

private suspend fun dealNewRound(previous: GameState, setGame: (GameState) -> Unit): GameState {
    val shuffled = deck.shuffled(Random.Default)
    val firstToAct = nextCounterClockwise(previous.dealer)
    val dealOrder = counterClockwiseOrderFrom(firstToAct, seats.toSet())
    val needsAnte = previous.pot == 0
    val startingBankrolls = if (needsAnte) {
        previous.bankrolls.mapValues { it.value - CHF }
    } else {
        previous.bankrolls
    }
    val startingPot = if (needsAnte) seats.size * CHF else previous.pot
    val anteEvents = if (needsAnte) seats.associateWith { -CHF } else emptyMap()
    var current = GameState(
        phase = GamePhase.Dealing,
        hands = emptyHands(),
        drawPile = shuffled,
        trump = null,
        trick = emptyList(),
        dealer = previous.dealer,
        chratzer = null,
        leader = firstToAct,
        active = firstToAct,
        activePlayers = seats.toSet(),
        outPlayers = emptySet(),
        scores = emptyScores(),
        bankrolls = startingBankrolls,
        pot = startingPot,
        moneyEvents = anteEvents,
        speechBubbles = emptyMap(),
        selectedExchangeIds = emptySet(),
        dealAnimation = null,
        cardFlights = emptyList(),
        trumpAttempts = 1,
        pendingTrickWinner = null,
        roundMessage = if (needsAnte) {
            "Everyone pays CHF 1 into the pot. ${previous.dealer.label} deals."
        } else {
            "The carried pot is ${startingPot.chf()}. ${previous.dealer.label} deals."
        },
    )
    setGame(current)
    delay(180)

    repeat(4) {
        dealOrder.forEach { seat ->
            val card = current.drawPile.first()
            current = current.copy(dealAnimation = DealAnimation(card, seat, arrived = false))
            setGame(current)
            delay(30)

            current = current.copy(dealAnimation = current.dealAnimation?.copy(arrived = true))
            setGame(current)
            delay(190)

            current = current.copy(
                hands = current.hands.plus(seat to current.hand(seat) + card),
                drawPile = current.drawPile.drop(1),
                dealAnimation = null,
            )
            setGame(current)
            delay(35)
        }
    }

    val trump = current.drawPile.first()
    current = current.copy(
        trump = trump,
        drawPile = current.drawPile.drop(1),
        phase = GamePhase.CallingChratze,
        active = firstToAct,
        leader = firstToAct,
        dealAnimation = null,
        roundMessage = "${firstToAct.label} starts: chratze or lose.",
    )
    setGame(current)
    delay(260)
    return current
}

private suspend fun advanceComputers(state: GameState, setGame: (GameState) -> Unit): GameState {
    var current = state
    var keepGoing = true

    while (keepGoing) {
        keepGoing = false

        when {
            current.phase == GamePhase.CallingChratze && current.active != Seat.User -> {
                delay(520)
                val saysChratze = Random.Default.nextDouble() < .34
                current = applyBid(current, current.active, saysChratze, setGame)
                keepGoing = current.phase != GamePhase.Finished && current.awaitingComputer()
            }

            current.phase == GamePhase.CallingAlong && current.active != Seat.User -> {
                delay(520)
                val joins = Random.Default.nextDouble() < .64
                current = applyJoin(current, current.active, joins, setGame)
                keepGoing = current.awaitingComputer()
            }

            current.phase == GamePhase.Exchanging && current.active != Seat.User -> {
                delay(520)
                val hand = current.hand(current.active)
                val count = Random.Default.nextInt(minOf(3, hand.size) + 1)
                val selected = hand.shuffled(Random.Default).take(count).map { it.id }.toSet()
                current = exchangeCards(current, current.active, selected, setGame)
                keepGoing = current.awaitingComputer()
            }

            current.phase == GamePhase.Playing && current.active != Seat.User && current.hand(current.active).isNotEmpty() -> {
                delay(520)
                val hand = current.hand(current.active)
                val card = legalCards(hand, current.trick, current.trump!!.suit).random(Random.Default)
                current = playTurn(current, current.active, card, setGame)
                keepGoing = current.awaitingComputer()
            }
        }
    }

    return current
}

private fun GameState.awaitingComputer(): Boolean =
    when (phase) {
        GamePhase.CallingChratze,
        GamePhase.CallingAlong,
        GamePhase.Exchanging,
        GamePhase.Playing,
        -> active != Seat.User

        else -> false
    }

private suspend fun userBid(
    state: GameState,
    saysChratze: Boolean,
    setGame: (GameState) -> Unit,
): GameState = applyBid(state, Seat.User, saysChratze, setGame)

private suspend fun applyBid(
    state: GameState,
    seat: Seat,
    saysChratze: Boolean,
    setGame: (GameState) -> Unit,
): GameState {
    if (saysChratze) {
        val next = nextCounterClockwise(seat)
        val current = state.copy(
            phase = if (next == seat) GamePhase.Exchanging else GamePhase.CallingAlong,
            chratzer = seat,
            active = next,
            leader = seat,
            activePlayers = setOf(seat),
            speechBubbles = state.speechBubbles.plus(seat to "chratze"),
            roundMessage = "${seat.label} says chratze. Others can call or fold.",
        )
        setGame(current)
        return if (next == seat) current.startExchange(setGame) else current
    }

    val firstBidder = nextCounterClockwise(state.dealer)
    val next = nextCounterClockwise(seat)
    val current = state.copy(
        active = next,
        speechBubbles = state.speechBubbles.plus(seat to "lose"),
        roundMessage = "${seat.label} says lose.",
    )
    setGame(current)

    if (next == firstBidder) {
        delay(520)
        return handleAllPassed(current, setGame)
    }

    return current
}

private suspend fun handleAllPassed(
    state: GameState,
    setGame: (GameState) -> Unit,
): GameState {
    if (state.trumpAttempts < 3 && state.drawPile.isNotEmpty()) {
        val nextTrump = state.drawPile.first()
        val firstBidder = nextCounterClockwise(state.dealer)
        val current = state.copy(
            trump = nextTrump,
            drawPile = state.drawPile.drop(1),
            active = firstBidder,
            leader = firstBidder,
            speechBubbles = emptyMap(),
            trumpAttempts = state.trumpAttempts + 1,
            roundMessage = "Everyone said lose. New trump ${nextTrump.rankLabel()} ${nextTrump.suit.name}; call again.",
        )
        setGame(current)
        delay(300)
        return current
    }

    var current = state.copy(
        hands = emptyHands(),
        activePlayers = emptySet(),
        outPlayers = seats.toSet(),
        speechBubbles = emptyMap(),
        selectedExchangeIds = emptySet(),
        cardFlights = seats.flatMap { seat ->
            state.hand(seat).toDeckFlights(fromSeat = seat, faceUp = seat == Seat.User)
        },
        roundMessage = "Everyone passed three trumps. Cards return to the deck and everyone adds CHF 1.",
    )
    setGame(current)
    delay(30)

    current = current.copy(cardFlights = current.cardFlights.map { it.copy(arrived = true) })
    setGame(current)
    delay(280)

    val anteEvents = seats.associateWith { -CHF }
    current = current.copy(
        phase = GamePhase.Finished,
        dealer = nextCounterClockwise(current.dealer),
        active = nextCounterClockwise(current.dealer),
        bankrolls = current.bankrolls.applyMoneyEvents(anteEvents),
        pot = current.pot + seats.size * CHF,
        moneyEvents = anteEvents,
        trump = null,
        cardFlights = emptyList(),
        trumpAttempts = 0,
        roundMessage = "Three trumps passed. Dealer button moves; next round plays for ${ (current.pot + seats.size * CHF).chf() }.",
    )
    setGame(current)
    return current
}

private suspend fun userJoin(
    state: GameState,
    joins: Boolean,
    setGame: (GameState) -> Unit,
): GameState = applyJoin(state, Seat.User, joins, setGame)

private suspend fun applyJoin(
    state: GameState,
    seat: Seat,
    joins: Boolean,
    setGame: (GameState) -> Unit,
): GameState {
    val withDecision = if (joins) {
        state.copy(
            activePlayers = state.activePlayers + seat,
            speechBubbles = state.speechBubbles.plus(seat to "chume mit"),
            roundMessage = "${seat.label} calls.",
        )
    } else {
        animateFoldToDeck(
            state.copy(
                speechBubbles = state.speechBubbles.plus(seat to "ich bin weg"),
                roundMessage = "${seat.label} folds.",
            ),
            seat,
            setGame,
        )
    }

    val next = nextCounterClockwise(seat)
    val current = if (next == withDecision.chratzer) {
        withDecision.copy(active = next)
    } else {
        withDecision.copy(active = next)
    }
    setGame(current)

    return if (next == current.chratzer) {
        delay(420)
        current.startExchange(setGame)
    } else {
        current
    }
}

private suspend fun animateFoldToDeck(
    state: GameState,
    seat: Seat,
    setGame: (GameState) -> Unit,
): GameState {
    val foldingCards = state.hand(seat)
    if (foldingCards.isEmpty()) {
        return state.copy(
            activePlayers = state.activePlayers - seat,
            outPlayers = state.outPlayers + seat,
            selectedExchangeIds = if (seat == Seat.User) emptySet() else state.selectedExchangeIds,
        )
    }

    var current = state.copy(
        hands = state.hands.plus(seat to emptyList()),
        activePlayers = state.activePlayers - seat,
        outPlayers = state.outPlayers + seat,
        selectedExchangeIds = if (seat == Seat.User) emptySet() else state.selectedExchangeIds,
        cardFlights = foldingCards.toDeckFlights(fromSeat = seat, faceUp = seat == Seat.User),
    )
    setGame(current)
    delay(30)

    current = current.copy(cardFlights = current.cardFlights.map { it.copy(arrived = true) })
    setGame(current)
    delay(260)

    current = current.copy(
        cardFlights = emptyList(),
    )
    setGame(current)
    return current
}

private suspend fun GameState.startExchange(setGame: (GameState) -> Unit): GameState {
    val starter = chratzer ?: active
    val current = copy(
        phase = GamePhase.Exchanging,
        active = starter,
        leader = starter,
        selectedExchangeIds = emptySet(),
        roundMessage = "${starter.label} exchanges first.",
    )
    setGame(current)
    return current
}

private suspend fun exchangeCards(
    state: GameState,
    seat: Seat,
    selectedIds: Set<String>,
    setGame: (GameState) -> Unit,
): GameState {
    val selected = state.hand(seat).filter { it.id in selectedIds }.take(3)
    val kept = state.hand(seat).filterNot { card -> selected.any { it.id == card.id } }
    val drawn = state.drawPile.take(selected.size)
    val nextActive = nextActiveCounterClockwise(seat, state.activePlayers)
    val starter = state.chratzer ?: seat
    val spoken = if (selected.isEmpty()) "keini" else "${selected.size} weg"

    var exchanged = state.copy(
        active = nextActive,
        speechBubbles = state.speechBubbles.plus(seat to spoken),
        selectedExchangeIds = emptySet(),
        roundMessage = "${seat.label} exchanges ${selected.size}.",
    )

    if (selected.isNotEmpty()) {
        exchanged = exchanged.copy(
            hands = exchanged.hands.plus(seat to kept),
            cardFlights = selected.toDeckFlights(fromSeat = seat, faceUp = seat == Seat.User),
        )
        setGame(exchanged)
        delay(30)

        exchanged = exchanged.copy(cardFlights = exchanged.cardFlights.map { it.copy(arrived = true) })
        setGame(exchanged)
        delay(260)

        exchanged = exchanged.copy(
            cardFlights = emptyList(),
        )
        setGame(exchanged)
        delay(90)

        drawn.forEach { card ->
            exchanged = exchanged.copy(
                cardFlights = listOf(card.fromDeckFlight(toSeat = seat, faceUp = seat == Seat.User)),
            )
            setGame(exchanged)
            delay(30)

            exchanged = exchanged.copy(cardFlights = exchanged.cardFlights.map { it.copy(arrived = true) })
            setGame(exchanged)
            delay(210)

            exchanged = exchanged.copy(
                hands = exchanged.hands.plus(seat to exchanged.hand(seat) + card),
                drawPile = exchanged.drawPile.drop(1),
                cardFlights = emptyList(),
            )
            setGame(exchanged)
            delay(55)
        }
    }

    exchanged = exchanged.copy(
        hands = exchanged.hands.plus(seat to exchanged.hand(seat).ifEmpty { kept }),
        cardFlights = emptyList(),
    )
    setGame(exchanged)
    delay(160)

    if (nextActive == starter) {
        val playing = exchanged.copy(
            phase = GamePhase.Playing,
            active = starter,
            leader = starter,
            trick = emptyList(),
            speechBubbles = exchanged.speechBubbles.plus(starter to "spiel"),
            roundMessage = "${starter.label} starts the game.",
        )
        setGame(playing)
        return playing
    }

    return exchanged.copy(roundMessage = "${nextActive.label} may exchange cards.").also(setGame)
}

private suspend fun playTurn(
    state: GameState,
    seat: Seat,
    card: SwissCard,
    setGame: (GameState) -> Unit,
): GameState {
    val updatedHand = state.hand(seat).filterNot { it.id == card.id }
    var current = state.copy(
        hands = state.hands.plus(seat to updatedHand),
        trick = state.trick + PlayedCard(card, seat),
        active = nextActiveCounterClockwise(seat, state.activePlayers),
        selectedExchangeIds = emptySet(),
        speechBubbles = state.speechBubbles - seat,
        roundMessage = "${seat.label} plays ${card.rankLabel()} ${card.suit.name}.",
    )
    setGame(current)
    delay(380)

    if (current.trick.size == current.activePlayers.size) {
        val winner = trickWinner(current.trick, current.trump!!.suit)
        current = current.copy(
            phase = GamePhase.TrickReview,
            active = winner,
            leader = winner,
            pendingTrickWinner = winner,
            roundMessage = "${winner.label} wins the trick. Click the table to collect it.",
        )
        setGame(current)
    }

    return current
}

private suspend fun collectReviewedTrick(
    state: GameState,
    setGame: (GameState) -> Unit,
): GameState {
    val winner = state.pendingTrickWinner ?: return state
    var current = state.copy(
        phase = GamePhase.Collecting,
        active = winner,
        leader = winner,
        scores = state.scores.plus(winner to state.score(winner) + 1),
        pendingTrickWinner = null,
        roundMessage = "${winner.label} collects the trick.",
    )
    setGame(current)
    delay(620)

    val finished = current.activePlayers.all { current.hand(it).isEmpty() }
    current = current.copy(
        trick = emptyList(),
        active = winner,
        leader = winner,
    )

    if (finished) {
        return settleRound(current, setGame)
    }

    current = current.copy(
        phase = GamePhase.Playing,
        roundMessage = "${winner.label} leads the next trick.",
    )
    setGame(current)
    return current
}

private fun settleRound(
    state: GameState,
    setGame: (GameState) -> Unit,
): GameState {
    val chratzer = state.chratzer ?: return state.copy(phase = GamePhase.Finished).also(setGame)
    val currentPot = state.pot
    val chratzerTricks = state.score(chratzer)
    val callers = state.activePlayers - chratzer
    val payoutEvents = mutableMapOf<Seat, Int>()

    val chratzerShare = when {
        chratzerTricks == 4 -> currentPot
        chratzerTricks >= 2 -> ceilToNextTenRappen(ceilDiv(currentPot * 2, 3)).coerceAtMost(currentPot)
        else -> 0
    }
    if (chratzerShare > 0) {
        payoutEvents[chratzer] = chratzerShare
    }

    val callerShareTotal = currentPot - chratzerShare
    val paidCallers = callers.filter { state.score(it) > 0 }
    if (callerShareTotal > 0 && paidCallers.isNotEmpty()) {
        distribute(callerShareTotal, paidCallers).forEach { (seat, amount) ->
            payoutEvents[seat] = payoutEvents.getValueOrZero(seat) + amount
        }
    }

    val loserPayments = mutableMapOf<Seat, Int>()
    if (chratzerTricks < 2) {
        loserPayments[chratzer] = -(currentPot * 2)
    }
    callers.filter { state.score(it) == 0 }.forEach { caller ->
        loserPayments[caller] = loserPayments.getValueOrZero(caller) - currentPot
    }

    val anteEvents = if (loserPayments.isEmpty()) seats.associateWith { -CHF } else emptyMap()
    val moneyEvents = mergeMoneyEvents(payoutEvents, loserPayments, anteEvents)
    val nextPot = if (loserPayments.isEmpty()) {
        seats.size * CHF
    } else {
        -loserPayments.values.sum()
    }
    val summary = settlementSummary(chratzer, chratzerTricks, payoutEvents, loserPayments, anteEvents, nextPot)
    val current = state.copy(
        phase = GamePhase.Settlement,
        dealer = nextCounterClockwise(state.dealer),
        active = nextCounterClockwise(state.dealer),
        bankrolls = state.bankrolls.applyMoneyEvents(moneyEvents),
        pot = nextPot,
        moneyEvents = moneyEvents,
        trump = null,
        activePlayers = emptySet(),
        outPlayers = emptySet(),
        speechBubbles = emptyMap(),
        selectedExchangeIds = emptySet(),
        trumpAttempts = 0,
        roundMessage = summary,
    )
    setGame(current)
    return current
}

private fun canUserSelectExchange(game: GameState, card: SwissCard): Boolean =
    game.phase == GamePhase.Exchanging &&
        game.active == Seat.User &&
        card in game.hand(Seat.User)

private fun toggleExchangeSelection(game: GameState, card: SwissCard): GameState {
    val selected = game.selectedExchangeIds
    val updated = if (card.id in selected) {
        selected - card.id
    } else if (selected.size < 3) {
        selected + card.id
    } else {
        selected
    }
    return game.copy(selectedExchangeIds = updated)
}

private fun canUserPlay(game: GameState, card: SwissCard): Boolean {
    if (game.phase != GamePhase.Playing || game.active != Seat.User || game.trump == null) return false
    if (Seat.User !in game.activePlayers) return false
    return legalCards(game.hand(Seat.User), game.trick, game.trump.suit).any { it.id == card.id }
}

private fun legalCards(hand: List<SwissCard>, trick: List<PlayedCard>, trumpSuit: SwissSuit): List<SwissCard> {
    val leadSuit = trick.firstOrNull()?.card?.suit ?: return hand
    val sameSuit = hand.filter { it.suit == leadSuit }
    if (sameSuit.isNotEmpty()) return sameSuit
    val trumps = hand.filter { it.suit == trumpSuit }
    if (trumps.isNotEmpty()) return trumps
    return hand
}

private fun trickWinner(trick: List<PlayedCard>, trumpSuit: SwissSuit): Seat {
    val trumps = trick.filter { it.card.suit == trumpSuit }
    val candidates = if (trumps.isNotEmpty()) {
        trumps
    } else {
        val leadSuit = trick.first().card.suit
        trick.filter { it.card.suit == leadSuit }
    }
    return candidates.maxBy { rankValue(it.card.rank) }.seat
}

private fun rankValue(rank: String): Int = ranks.indexOf(rank)

private fun ceilDiv(value: Int, divisor: Int): Int = (value + divisor - 1) / divisor

private fun ceilToNextTenRappen(value: Int): Int = ceilDiv(value, 10) * 10

private fun distribute(total: Int, players: List<Seat>): Map<Seat, Int> {
    if (players.isEmpty()) return emptyMap()
    val base = total / players.size
    var remainder = total % players.size
    return players.associateWith {
        val extra = if (remainder > 0) {
            remainder -= 1
            1
        } else {
            0
        }
        base + extra
    }
}

private fun mergeMoneyEvents(vararg maps: Map<Seat, Int>): Map<Seat, Int> =
    buildMap {
        maps.forEach { events ->
            events.forEach { (seat, amount) ->
                put(seat, getValueOrZero(seat) + amount)
            }
        }
    }.filterValues { it != 0 }

private fun Map<Seat, Int>.applyMoneyEvents(events: Map<Seat, Int>): Map<Seat, Int> =
    seats.associateWith { seat -> getValueOrZero(seat) + events.getValueOrZero(seat) }

private fun Map<Seat, Int>.getValueOrZero(seat: Seat): Int = this[seat] ?: 0

private fun Int.chf(): String {
    val sign = if (this < 0) "-" else ""
    val absValue = kotlin.math.abs(this)
    val francs = absValue / 100
    val rappen = absValue % 100
    return "${sign}CHF $francs.${rappen.toString().padStart(2, '0')}"
}

private fun settlementSummary(
    chratzer: Seat,
    chratzerTricks: Int,
    payouts: Map<Seat, Int>,
    loserPayments: Map<Seat, Int>,
    anteEvents: Map<Seat, Int>,
    nextPot: Int,
): String {
    val result = if (chratzerTricks >= 2) {
        "${chratzer.label} wins with $chratzerTricks tricks."
    } else {
        "${chratzer.label} loses with $chratzerTricks tricks."
    }
    val payoutText = payouts.entries.joinToString { "${it.key.label} +${it.value.chf()}" }
    val lossText = loserPayments.entries.joinToString { "${it.key.label} ${it.value.chf()}" }
    val anteText = if (anteEvents.isNotEmpty()) "No one loses; everyone antes CHF 1." else ""
    return listOf(result, payoutText, lossText, anteText, "Next pot: ${nextPot.chf()}.")
        .filter { it.isNotBlank() }
        .joinToString(" ")
}

private fun nextCounterClockwise(seat: Seat): Seat =
    counterClockwiseSeats[(counterClockwiseSeats.indexOf(seat) + 1) % counterClockwiseSeats.size]

private fun nextActiveCounterClockwise(seat: Seat, activePlayers: Set<Seat>): Seat {
    var next = nextCounterClockwise(seat)
    while (next !in activePlayers) {
        next = nextCounterClockwise(next)
    }
    return next
}

private fun counterClockwiseOrderFrom(first: Seat, included: Set<Seat>): List<Seat> {
    val result = mutableListOf<Seat>()
    var current = first
    repeat(seats.size) {
        if (current in included) result += current
        current = nextCounterClockwise(current)
    }
    return result
}

private fun GameState.hand(seat: Seat): List<SwissCard> = hands[seat].orEmpty()

private fun GameState.score(seat: Seat): Int = scores[seat] ?: 0

@Composable
private fun Header() {
    Div({
        attr(
            "style",
            """
                display:flex;
                flex-direction:column;
                gap:10px;
                max-width:760px;
            """.trimIndent()
        )
    }) {
        H1({
            attr(
                "style",
                """
                    margin:0;
                    font-size:42px;
                    line-height:1.05;
                    font-weight:800;
                    color:#17130f;
                """.trimIndent()
            )
        }) {
            Text("Chratze table")
        }
        Div({
            attr(
                "style",
                """
                    color:#64594f;
                    font-size:17px;
                    max-width:650px;
                """.trimIndent()
            )
        }) {
            Text("Deal, call chratze or lose, decide who comes along, exchange up to three cards, then play the tricks.")
        }
    }
}

@Composable
private fun GameTable(
    game: GameState,
    onNewRoundClick: () -> Unit,
    onUserBid: (Boolean) -> Unit,
    onUserJoin: (Boolean) -> Unit,
    onUserCardClick: (SwissCard) -> Unit,
    onUserExchangeConfirm: () -> Unit,
    onTableClick: () -> Unit,
) {
    Div({
        onClick {
            if (game.phase == GamePhase.TrickReview) {
                onTableClick()
            }
        }
        attr(
            "style",
            """
                position:relative;
                width:min(100%, 1160px);
                height:clamp(560px, 82vh, 780px);
                margin:0 auto;
                cursor:${if (game.phase == GamePhase.TrickReview) "pointer" else "default"};
            """.trimIndent()
        )
    }) {
        Div({
            attr(
                "style",
                """
                    position:absolute;
                    left:50%;
                    top:49%;
                    width:96%;
                    height:82%;
                    transform:translate(-50%, -50%);
                    border-radius:50%;
                    background:
                        radial-gradient(circle at 50% 43%, rgba(255,255,255,.14), transparent 38%),
                        linear-gradient(145deg, #3e8f63 0%, #236440 62%, #17462f 100%);
                    border:5px solid #7a4d2c;
                    box-shadow:0 22px 48px rgba(28,20,13,.24), inset 0 0 0 3px rgba(255,255,255,.14);
                    z-index:0;
                """.trimIndent()
            )
        })

        TableSpot("left:9%; top:43%;") { DeckIcon(game.drawPile.size, game.phase != GamePhase.Idle) }
        PotArea(game.pot)
        FlyingDealCard(game.dealAnimation)
        FlyingCards(game.cardFlights)
        PlayerArea(
            seat = Seat.Player1,
            cardCount = game.hand(Seat.Player1).size,
            score = game.score(Seat.Player1),
            bankroll = game.bankrolls.getValueOrZero(Seat.Player1),
            moneyEvent = game.moneyEvents[Seat.Player1],
            active = game.active,
            dealer = game.dealer,
            out = Seat.Player1 in game.outPlayers,
            speech = game.speechBubbles[Seat.Player1],
            position = "left:23%; top:19%;",
        )
        PlayerArea(
            seat = Seat.Player2,
            cardCount = game.hand(Seat.Player2).size,
            score = game.score(Seat.Player2),
            bankroll = game.bankrolls.getValueOrZero(Seat.Player2),
            moneyEvent = game.moneyEvents[Seat.Player2],
            active = game.active,
            dealer = game.dealer,
            out = Seat.Player2 in game.outPlayers,
            speech = game.speechBubbles[Seat.Player2],
            position = "left:50%; top:10%;",
        )
        PlayerArea(
            seat = Seat.Player3,
            cardCount = game.hand(Seat.Player3).size,
            score = game.score(Seat.Player3),
            bankroll = game.bankrolls.getValueOrZero(Seat.Player3),
            moneyEvent = game.moneyEvents[Seat.Player3],
            active = game.active,
            dealer = game.dealer,
            out = Seat.Player3 in game.outPlayers,
            speech = game.speechBubbles[Seat.Player3],
            position = "left:77%; top:19%;",
        )
        TrumpCard(game.trump)
        UserStatus(game)
        TrickCards(game.trick, game.phase == GamePhase.Collecting, game.leader)
        UserHand(game, onUserCardClick)
        RoundControls(game, onNewRoundClick, onUserBid, onUserJoin, onUserExchangeConfirm)
        SettlementModal(game, onNewRoundClick)
    }
}

@Composable
private fun PotArea(pot: Int) {
    Div({
        attr(
            "style",
            """
                position:absolute;
                left:50%;
                top:34%;
                transform:translate(-50%, -50%);
                z-index:2;
                min-width:72px;
                padding:4px 8px;
                border-radius:999px;
                color:#fff8eb;
                background:rgba(27,38,56,.74);
                border:1px solid rgba(255,248,235,.42);
                box-shadow:0 8px 16px rgba(0,0,0,.18);
                text-align:center;
                font-size:17px;
                font-weight:900;
            """.trimIndent()
        )
    }) {
        Div { Text("▰") }
        Div({ attr("style", "font-size:11px;font-weight:850;line-height:1;") }) { Text(pot.chf()) }
    }
}

@Composable
private fun FlyingCards(cardFlights: List<CardFlight>) {
    cardFlights.forEach { flight ->
        val position = if (flight.arrived) flight.to else flight.from
        Div({
            attr(
                "style",
                """
                    position:absolute;
                    left:${position.first};
                    top:${position.second};
                    transform:translate(-50%, -50%) translate(${flight.offset}px, ${flight.offset / 2}px) rotate(${if (flight.arrived) "-8deg" else "6deg"});
                    transition:left .24s ease-in, top .24s ease-in, transform .24s ease-in, opacity .24s ease-in;
                    opacity:${if (flight.arrived && flight.to == deckPosition()) ".25" else "1"};
                    z-index:8;
                    pointer-events:none;
                """.trimIndent()
            )
        }) {
            if (flight.faceUp) {
                CardView(flight.card, width = 72, height = 101, clickable = false)
            } else {
                FaceDownCard(width = 52, height = 72)
            }
        }
    }
}

@Composable
private fun FlyingDealCard(dealAnimation: DealAnimation?) {
    if (dealAnimation == null) return

    val position = if (dealAnimation.arrived) dealTargetPosition(dealAnimation.target) else deckPosition()
    Div({
        attr(
            "style",
            """
                position:absolute;
                left:${position.first};
                top:${position.second};
                transform:translate(-50%, -50%) rotate(${if (dealAnimation.arrived) "-4deg" else "8deg"});
                transition:left .18s ease-out, top .18s ease-out, transform .18s ease-out;
                z-index:7;
                pointer-events:none;
            """.trimIndent()
        )
    }) {
        if (dealAnimation.target == Seat.User) {
            CardView(dealAnimation.card, width = 72, height = 101, clickable = false)
        } else {
            FaceDownCard(width = 52, height = 72)
        }
    }
}

private fun deckPosition(): Pair<String, String> = "9%" to "43%"

private fun dealTargetPosition(seat: Seat): Pair<String, String> = when (seat) {
    Seat.User -> "50%" to "82%"
    Seat.Player1 -> "25%" to "27%"
    Seat.Player2 -> "50%" to "17%"
    Seat.Player3 -> "75%" to "27%"
}

private fun List<SwissCard>.toDeckFlights(fromSeat: Seat, faceUp: Boolean): List<CardFlight> =
    mapIndexed { index, card ->
        CardFlight(
            card = card,
            from = dealTargetPosition(fromSeat),
            to = deckPosition(),
            arrived = false,
            faceUp = faceUp,
            offset = flightOffset(index),
        )
    }

private fun SwissCard.fromDeckFlight(toSeat: Seat, faceUp: Boolean): CardFlight =
    CardFlight(
        card = this,
        from = deckPosition(),
        to = dealTargetPosition(toSeat),
        arrived = false,
        faceUp = faceUp,
        offset = 0,
    )

private fun flightOffset(index: Int): Int = (index - 1) * 10

@Composable
private fun TableSpot(position: String, content: @Composable () -> Unit) {
    Div({
        attr(
            "style",
            """
                position:absolute;
                $position
                transform:translate(-50%, -50%);
                display:flex;
                align-items:center;
                justify-content:center;
                z-index:2;
            """.trimIndent()
        )
    }) {
        content()
    }
}

@Composable
private fun PlayerArea(
    seat: Seat,
    cardCount: Int,
    score: Int,
    bankroll: Int,
    moneyEvent: Int?,
    active: Seat,
    dealer: Seat,
    out: Boolean,
    speech: String?,
    position: String,
) {
    TableSpot(position) {
        Div({
            attr(
                "style",
                """
                    position:relative;
                    display:flex;
                    flex-direction:column;
                    align-items:center;
                    gap:6px;
                    width:112px;
                    opacity:${if (out) ".48" else "1"};
                """.trimIndent()
            )
        }) {
            if (speech != null) {
                SpeechBubble(speech)
            }
            ComputerPlayerIcon(seat, score, bankroll, moneyEvent, active == seat, dealer == seat, out)
            Div({
                attr(
                    "style",
                    """
                        display:flex;
                        justify-content:center;
                        gap:0;
                        width:100%;
                        min-height:42px;
                    """.trimIndent()
                )
            }) {
                repeat(cardCount) { index ->
                    Div({
                        attr(
                            "style",
                            """
                                margin-left:${if (index == 0) "0" else "-18px"};
                                transform:rotate(${(index - cardCount / 2) * 5}deg);
                                transform-origin:50% 100%;
                                z-index:${index};
                            """.trimIndent()
                        )
                    }) {
                        FaceDownCard(width = 32, height = 45)
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeechBubble(text: String) {
    Div({
        attr(
            "style",
            """
                position:absolute;
                top:-38px;
                left:50%;
                transform:translateX(-50%);
                z-index:5;
                max-width:132px;
                padding:7px 10px;
                border-radius:8px;
                color:#1f1a14;
                background:#fff8eb;
                border:1px solid rgba(40,31,22,.16);
                box-shadow:0 10px 20px rgba(0,0,0,.18);
                font-size:13px;
                line-height:1.1;
                font-weight:800;
                text-align:center;
                white-space:nowrap;
            """.trimIndent()
        )
    }) {
        Text(text)
    }
}

@Composable
private fun ComputerPlayerIcon(
    seat: Seat,
    score: Int,
    bankroll: Int,
    moneyEvent: Int?,
    active: Boolean,
    dealer: Boolean,
    out: Boolean,
) {
    Div({
        attr(
            "style",
            """
                position:relative;
                width:58px;
                height:58px;
                border-radius:50%;
                display:flex;
                flex-direction:column;
                align-items:center;
                justify-content:center;
                padding:5px;
                box-sizing:border-box;
                text-align:center;
                font-size:26px;
                line-height:1.12;
                font-weight:780;
                color:#1b2638;
                background:${if (out) "#d8dde2" else "#fff8eb"};
                border:2px solid ${if (active) "#f6d55c" else "rgba(255,255,255,.62)"};
                box-shadow:0 9px 18px rgba(0,0,0,.2);
            """.trimIndent()
        )
    }) {
        if (dealer) DealerBadge()
        Text(seat.avatar)
        Div({
            attr(
                "style",
                """
                    position:absolute;
                    left:50%;
                    top:60px;
                    transform:translateX(-50%);
                    min-width:88px;
                    color:#fff8eb;
                    font-size:11px;
                    line-height:1.15;
                    font-weight:850;
                    text-shadow:0 1px 2px rgba(0,0,0,.45);
                    white-space:nowrap;
                """.trimIndent()
            )
        }) {
            Text("${seat.label} · ${if (out) "out" else "$score"} · ${bankroll.chf()}")
        }
        Div({ attr("style", "display:none;") }) {
            Text(bankroll.chf())
        }
        if (moneyEvent != null) {
            MoneyChange(moneyEvent)
        }
    }
}

@Composable
private fun MoneyChange(amount: Int) {
    Div({
        attr(
            "style",
            """
                position:absolute;
                left:50%;
                bottom:-23px;
                transform:translateX(-50%);
                padding:3px 7px;
                border-radius:999px;
                color:${if (amount >= 0) "#103b24" else "#611b1b"};
                background:${if (amount >= 0) "#dff8e9" else "#ffe0dc"};
                border:1px solid rgba(28,22,15,.12);
                box-shadow:0 6px 12px rgba(0,0,0,.14);
                font-size:12px;
                font-weight:900;
                white-space:nowrap;
            """.trimIndent()
        )
    }) {
        Text("${if (amount >= 0) "+" else ""}${amount.chf()}")
    }
}

@Composable
private fun DealerBadge() {
    Div({
        attr(
            "style",
            """
                position:absolute;
                right:-6px;
                top:-6px;
                width:24px;
                height:24px;
                border-radius:50%;
                display:flex;
                align-items:center;
                justify-content:center;
                color:#1b2638;
                background:#f6d55c;
                border:2px solid #fff8eb;
                font-size:13px;
                font-weight:900;
                box-shadow:0 5px 10px rgba(0,0,0,.2);
            """.trimIndent()
        )
    }) {
        Text("D")
    }
}

@Composable
private fun DeckIcon(remainingCards: Int, visible: Boolean) {
    Div({
        attr(
            "style",
            """
                position:relative;
                width:66px;
                height:92px;
                opacity:${if (visible && remainingCards > 0) "1" else ".2"};
            """.trimIndent()
        )
        attr("title", "$remainingCards cards left")
    }) {
        repeat(3) { index ->
            Div({
                attr(
                    "style",
                    """
                        position:absolute;
                        left:${index * 5}px;
                        top:${index * 4}px;
                    """.trimIndent()
                )
            }) {
                FaceDownCard(width = 52, height = 72)
            }
        }
    }
}

@Composable
private fun FaceDownCard(width: Int, height: Int) {
    Div({
        attr(
            "style",
            """
                width:${width}px;
                height:${height}px;
                flex:0 0 auto;
                border-radius:6px;
                border:1px solid rgba(20,17,14,.35);
                background:
                    linear-gradient(135deg, transparent 0 44%, rgba(255,255,255,.2) 45% 55%, transparent 56% 100%),
                    repeating-linear-gradient(45deg, #842a30 0 7px, #a93b43 7px 14px);
                box-shadow:0 7px 13px rgba(0,0,0,.2), inset 0 0 0 4px #f8ecd6, inset 0 0 0 6px #842a30;
            """.trimIndent()
        )
    })
}

@Composable
private fun TrumpCard(trump: SwissCard?) {
    if (trump == null) return

    Div({
        attr(
            "style",
            """
                position:absolute;
                left:18%;
                top:57%;
                transform:translate(-50%, -50%);
                z-index:2;
                display:flex;
                flex-direction:column;
                align-items:center;
                gap:6px;
                color:#fff8eb;
                font-size:13px;
                font-weight:800;
                text-shadow:0 1px 2px rgba(0,0,0,.3);
            """.trimIndent()
        )
    }) {
        Text("Trump")
        CardView(trump, width = 62, height = 87, clickable = false)
    }
}

@Composable
private fun UserStatus(game: GameState) {
    Div({
        attr(
            "style",
            """
                position:absolute;
                left:50%;
                bottom:168px;
                transform:translateX(-50%);
                z-index:5;
                display:flex;
                align-items:center;
                gap:10px;
            """.trimIndent()
        )
    }) {
        Div({
            attr(
                "style",
                """
                    position:relative;
                    padding:7px 13px;
                    border-radius:999px;
                    color:#fff8eb;
                    background:${if (Seat.User in game.outPlayers) "#687077" else "#1b2638"};
                    font-size:13px;
                    font-weight:800;
                    box-shadow:0 10px 20px rgba(0,0,0,.18);
                """.trimIndent()
            )
        }) {
            if (game.dealer == Seat.User) DealerBadge()
            Text("${Seat.User.label}: ${if (Seat.User in game.outPlayers) "out" else "${game.score(Seat.User)} tricks"} | ${game.bankrolls.getValueOrZero(Seat.User).chf()}")
            game.moneyEvents[Seat.User]?.let { MoneyChange(it) }
        }
        game.speechBubbles[Seat.User]?.let { SpeechBubbleInline(it) }
    }
}

@Composable
private fun SpeechBubbleInline(text: String) {
    Div({
        attr(
            "style",
            """
                padding:7px 10px;
                border-radius:8px;
                color:#1f1a14;
                background:#fff8eb;
                border:1px solid rgba(40,31,22,.16);
                box-shadow:0 10px 20px rgba(0,0,0,.16);
                font-size:13px;
                line-height:1.1;
                font-weight:800;
                white-space:nowrap;
            """.trimIndent()
        )
    }) {
        Text(text)
    }
}

@Composable
private fun RoundControls(
    game: GameState,
    onNewRoundClick: () -> Unit,
    onUserBid: (Boolean) -> Unit,
    onUserJoin: (Boolean) -> Unit,
    onUserExchangeConfirm: () -> Unit,
) {
    Div({
        attr(
            "style",
            """
                position:absolute;
                left:50%;
                top:53%;
                transform:translate(-50%, -50%);
                z-index:6;
                display:flex;
                flex-direction:column;
                align-items:center;
                gap:10px;
                min-width:250px;
            """.trimIndent()
        )
    }) {
        when {
            game.phase == GamePhase.Idle || game.phase == GamePhase.Finished -> {
                PrimaryButton(if (game.phase == GamePhase.Idle) "Start new round" else "Next round", onNewRoundClick)
            }

            game.phase == GamePhase.CallingChratze && game.active == Seat.User -> {
                Div({ attr("style", "display:flex;gap:10px;") }) {
                    PrimaryButton("chratze") { onUserBid(true) }
                    SecondaryButton("lose") { onUserBid(false) }
                }
            }

            game.phase == GamePhase.CallingAlong && game.active == Seat.User -> {
                Div({ attr("style", "display:flex;gap:10px;") }) {
                    PrimaryButton("chume mit") { onUserJoin(true) }
                    SecondaryButton("ich bin weg") { onUserJoin(false) }
                }
            }

            game.phase == GamePhase.Exchanging && game.active == Seat.User -> {
                PrimaryButton("Exchange ${game.selectedExchangeIds.size}") { onUserExchangeConfirm() }
            }

            game.phase == GamePhase.TrickReview -> {
                Div({
                    attr(
                        "style",
                        """
                            padding:7px 10px;
                            border-radius:999px;
                            color:#fff8eb;
                            background:rgba(27,38,56,.76);
                            font-size:12px;
                            font-weight:850;
                            box-shadow:0 8px 16px rgba(0,0,0,.16);
                        """.trimIndent()
                    )
                }) {
                    Text("Tap table")
                }
            }
        }
    }
}

@Composable
private fun SettlementModal(game: GameState, onNewRoundClick: () -> Unit) {
    if (game.phase != GamePhase.Settlement) return

    Div({
        attr(
            "style",
            """
                position:absolute;
                left:50%;
                top:8%;
                transform:translateX(-50%);
                width:min(92vw, 520px);
                z-index:10;
                padding:16px;
                border-radius:8px;
                color:#1f1a14;
                background:#fffaf0;
                border:1px solid rgba(36,28,21,.16);
                box-shadow:0 22px 50px rgba(0,0,0,.32);
            """.trimIndent()
        )
    }) {
        Div({
            attr(
                "style",
                """
                    display:flex;
                    align-items:center;
                    justify-content:space-between;
                    gap:12px;
                    margin-bottom:12px;
                """.trimIndent()
            )
        }) {
            Div {
                Div({ attr("style", "font-size:18px;font-weight:900;line-height:1.1;") }) {
                    Text("Round settled")
                }
                Div({ attr("style", "font-size:12px;color:#6d6259;font-weight:750;margin-top:3px;") }) {
                    Text("Next pot ${game.pot.chf()}")
                }
            }
            PrimaryButton("Next round", onNewRoundClick)
        }

        Div({
            attr(
                "style",
                """
                    display:grid;
                    grid-template-columns:repeat(2, minmax(0, 1fr));
                    gap:8px;
                """.trimIndent()
            )
        }) {
            seats.forEach { seat ->
                SettlementPlayerCard(game, seat)
            }
        }
    }
}

@Composable
private fun SettlementPlayerCard(game: GameState, seat: Seat) {
    val amount = game.moneyEvents[seat] ?: 0
    Div({
        attr(
            "style",
            """
                display:flex;
                align-items:center;
                gap:8px;
                min-width:0;
                padding:8px;
                border-radius:8px;
                background:${if (amount >= 0) "#f1fbf4" else "#fff1ee"};
                border:1px solid ${if (amount >= 0) "#cdebd6" else "#ffd0c8"};
            """.trimIndent()
        )
    }) {
        Div({ attr("style", "font-size:26px;line-height:1;") }) { Text(seat.avatar) }
        Div({ attr("style", "min-width:0;flex:1;") }) {
            Div({ attr("style", "font-size:12px;font-weight:900;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;") }) {
                Text(seat.label)
            }
            Div({ attr("style", "font-size:11px;color:#655c53;line-height:1.2;") }) {
                Text("${game.score(seat)} tricks · ${game.bankrolls.getValueOrZero(seat).chf()}")
            }
        }
        Div({
            attr(
                "style",
                """
                    font-size:12px;
                    font-weight:950;
                    color:${if (amount >= 0) "#126334" else "#8a2520"};
                    white-space:nowrap;
                """.trimIndent()
            )
        }) {
            Text("${if (amount >= 0) "+" else ""}${amount.chf()}")
        }
    }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    Button(attrs = {
        onClick { onClick() }
        attr(
            "style",
            """
                border:0;
                border-radius:8px;
                padding:12px 18px;
                font-size:15px;
                font-weight:850;
                color:#fff8eb;
                background:#1b2638;
                box-shadow:0 12px 24px rgba(0,0,0,.24);
                cursor:pointer;
            """.trimIndent()
        )
    }) {
        Text(label)
    }
}

@Composable
private fun SecondaryButton(label: String, onClick: () -> Unit) {
    Button(attrs = {
        onClick { onClick() }
        attr(
            "style",
            """
                border:0;
                border-radius:8px;
                padding:12px 18px;
                font-size:15px;
                font-weight:850;
                color:#1b2638;
                background:#fff8eb;
                box-shadow:0 12px 24px rgba(0,0,0,.18);
                cursor:pointer;
            """.trimIndent()
        )
    }) {
        Text(label)
    }
}

@Composable
private fun TrickCards(trick: List<PlayedCard>, collecting: Boolean, winner: Seat) {
    trick.forEachIndexed { index, playedCard ->
        val target = trickPosition(playedCard.seat, index)
        val collectTarget = collectPosition(winner)
        Div({
            attr(
                "style",
                """
                    position:absolute;
                    left:${if (collecting) collectTarget.first else target.first};
                    top:${if (collecting) collectTarget.second else target.second};
                    transform:${if (collecting) "translate(-50%, -50%) scale(.35)" else "translate(-50%, -50%)"};
                    opacity:${if (collecting) "0" else "1"};
                    transition:left .65s ease, top .65s ease, transform .65s ease, opacity .65s ease;
                    z-index:4;
                """.trimIndent()
            )
        }) {
            CardView(playedCard.card, width = 92, height = 129, clickable = false)
        }
    }
}

private fun trickPosition(seat: Seat, index: Int): Pair<String, String> = when (seat) {
    Seat.User -> "50%" to "60%"
    Seat.Player1 -> "43%" to "45%"
    Seat.Player2 -> "50%" to "39%"
    Seat.Player3 -> "57%" to "45%"
}

private fun collectPosition(seat: Seat): Pair<String, String> = when (seat) {
    Seat.User -> "50%" to "91%"
    Seat.Player1 -> "25%" to "22%"
    Seat.Player2 -> "50%" to "12%"
    Seat.Player3 -> "75%" to "22%"
}

@Composable
private fun UserHand(game: GameState, onUserCardClick: (SwissCard) -> Unit) {
    val legalIds = if (game.phase == GamePhase.Playing && game.active == Seat.User && game.trump != null) {
        legalCards(game.hand(Seat.User), game.trick, game.trump.suit).map { it.id }.toSet()
    } else {
        emptySet()
    }
    val exchangeSelectable = game.phase == GamePhase.Exchanging && game.active == Seat.User

    Div({
        attr(
            "style",
            """
                position:absolute;
                left:50%;
                bottom:4px;
                transform:translateX(-50%);
                display:flex;
                align-items:flex-end;
                justify-content:center;
                gap:0;
                width:min(98vw, 680px);
                min-height:170px;
                z-index:3;
            """.trimIndent()
        )
    }) {
        game.hand(Seat.User).forEachIndexed { index, card ->
            val legal = legalIds.contains(card.id)
            val selected = card.id in game.selectedExchangeIds
            val clickable = legal || exchangeSelectable
            Div({
                if (clickable) {
                    onClick { onUserCardClick(card) }
                }
                attr(
                    "style",
                    """
                        cursor:${if (clickable) "pointer" else "default"};
                        margin-left:${if (index == 0) "0" else "clamp(-56px, calc((430px - 100vw) * -1), 14px)"};
                        opacity:${if (game.phase == GamePhase.Playing && game.active == Seat.User && !legal) ".45" else "1"};
                        transform:${if (selected) "translateY(-18px)" else "translateY(0)"};
                        transition:transform .16s ease, opacity .16s ease;
                        outline:${if (selected) "4px solid #f6d55c" else "0"};
                        border-radius:9px;
                        z-index:$index;
                    """.trimIndent()
                )
            }) {
                CardView(card, width = 118, height = 166, clickable = clickable)
            }
        }
    }
}

@Composable
private fun CardView(card: SwissCard, width: Int, height: Int, clickable: Boolean) {
    Div({
        attr(
            "style",
            """
                position:relative;
                width:${width}px;
                height:${height}px;
                flex:0 0 auto;
                overflow:hidden;
                border-radius:8px;
                border:1px solid rgba(36,28,21,.16);
                background:#fffaf0;
                box-shadow:0 16px 30px rgba(28,22,15,.18), inset 0 0 0 7px #fffdf7, inset 0 0 0 9px rgba(38,31,22,.10);
                color:${card.suit.ink};
                transform:${if (clickable) "translateY(0)" else "none"};
            """.trimIndent()
        )
    }) {
        Div({
            attr(
                "style",
                """
                    position:absolute;
                    top:${if (height > 140) "14px" else "9px"};
                    left:0;
                    right:0;
                    text-align:center;
                    font-size:${if (height > 140) "28px" else "21px"};
                    line-height:1;
                    font-weight:820;
                    letter-spacing:0;
                """.trimIndent()
            )
        }) {
            Text(card.rankLabel())
        }
        Div({
            attr(
                "style",
                """
                    position:absolute;
                    inset:${if (height > 140) "44px 10px 14px" else "33px 8px 10px"};
                    display:flex;
                    align-items:center;
                    justify-content:center;
                """.trimIndent()
            )
        }) {
            Img(src = BasePath.prependTo(card.suit.assetPath), attrs = {
                attr("alt", card.suit.name)
                attr(
                    "style",
                    """
                        display:block;
                        width:100%;
                        height:100%;
                        object-fit:contain;
                    """.trimIndent()
                )
            })
        }
    }
}

private fun SwissCard.rankLabel() = when (rank) {
    "Under" -> "U"
    "Ober" -> "O"
    "Koenig" -> "K"
    "Ass" -> "A"
    else -> rank
}
