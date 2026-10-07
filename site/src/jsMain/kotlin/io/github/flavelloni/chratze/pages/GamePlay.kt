package io.github.flavelloni.chratze.pages

import kotlinx.coroutines.delay

suspend fun exchangeCards(
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

suspend fun playTurn(
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

suspend fun collectReviewedTrick(
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

fun settleRound(
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
