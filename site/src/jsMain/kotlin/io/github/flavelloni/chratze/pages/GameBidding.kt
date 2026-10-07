package io.github.flavelloni.chratze.pages

import kotlinx.coroutines.delay

suspend fun userBid(
    state: GameState,
    saysChratze: Boolean,
    setGame: (GameState) -> Unit,
): GameState = applyBid(state, Seat.User, saysChratze, setGame)

suspend fun applyBid(
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

suspend fun handleAllPassed(
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

suspend fun userJoin(
    state: GameState,
    joins: Boolean,
    setGame: (GameState) -> Unit,
): GameState = applyJoin(state, Seat.User, joins, setGame)

suspend fun applyJoin(
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

suspend fun animateFoldToDeck(
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

suspend fun GameState.startExchange(setGame: (GameState) -> Unit): GameState {
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
