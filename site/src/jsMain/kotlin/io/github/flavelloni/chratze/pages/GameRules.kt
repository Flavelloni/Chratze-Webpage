package io.github.flavelloni.chratze.pages

fun canUserSelectExchange(game: GameState, card: SwissCard): Boolean =
    game.phase == GamePhase.Exchanging &&
        game.active == Seat.User &&
        card in game.hand(Seat.User)

fun toggleExchangeSelection(game: GameState, card: SwissCard): GameState {
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

fun canUserPlay(game: GameState, card: SwissCard): Boolean {
    if (game.phase != GamePhase.Playing || game.active != Seat.User || game.trump == null) return false
    if (Seat.User !in game.activePlayers) return false
    return legalCards(game.hand(Seat.User), game.trick, game.trump.suit).any { it.id == card.id }
}

fun legalCards(hand: List<SwissCard>, trick: List<PlayedCard>, trumpSuit: SwissSuit): List<SwissCard> {
    val leadSuit = trick.firstOrNull()?.card?.suit ?: return hand
    val sameSuit = hand.filter { it.suit == leadSuit }
    if (sameSuit.isNotEmpty()) return sameSuit
    val trumps = hand.filter { it.suit == trumpSuit }
    if (trumps.isNotEmpty()) return trumps
    return hand
}

fun trickWinner(trick: List<PlayedCard>, trumpSuit: SwissSuit): Seat {
    val trumps = trick.filter { it.card.suit == trumpSuit }
    val candidates = if (trumps.isNotEmpty()) {
        trumps
    } else {
        val leadSuit = trick.first().card.suit
        trick.filter { it.card.suit == leadSuit }
    }
    return candidates.maxBy { rankValue(it.card.rank) }.seat
}

fun rankValue(rank: String): Int = ranks.indexOf(rank)

fun ceilDiv(value: Int, divisor: Int): Int = (value + divisor - 1) / divisor

fun ceilToNextTenRappen(value: Int): Int = ceilDiv(value, 10) * 10

fun distribute(total: Int, players: List<Seat>): Map<Seat, Int> {
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

fun mergeMoneyEvents(vararg maps: Map<Seat, Int>): Map<Seat, Int> =
    buildMap {
        maps.forEach { events ->
            events.forEach { (seat, amount) ->
                put(seat, getValueOrZero(seat) + amount)
            }
        }
    }.filterValues { it != 0 }

fun Map<Seat, Int>.applyMoneyEvents(events: Map<Seat, Int>): Map<Seat, Int> =
    seats.associateWith { seat -> getValueOrZero(seat) + events.getValueOrZero(seat) }

fun settlementSummary(
    chratzer: Seat,
    chratzerTricks: Int,
    payouts: Map<Seat, Int>,
    loserPayments: Map<Seat, Int>,
    anteEvents: Map<Seat, Int>,
    nextPot: Int,
): String {
    val result = if (chratzerTricks >= 2) {
        "${chratzer.label} wins with ${trickLabel(chratzerTricks)}."
    } else {
        "${chratzer.label} loses with ${trickLabel(chratzerTricks)}."
    }
    val payoutText = payouts.entries.joinToString { "${it.key.label} +${it.value.chf()}" }
    val lossText = loserPayments.entries.joinToString { "${it.key.label} ${it.value.chf()}" }
    val anteText = if (anteEvents.isNotEmpty()) "No one loses; everyone antes CHF 1." else ""
    return listOf(result, payoutText, lossText, anteText, "Next pot: ${nextPot.chf()}.")
        .filter { it.isNotBlank() }
        .joinToString(" ")
}

fun nextCounterClockwise(seat: Seat): Seat =
    counterClockwiseSeats[(counterClockwiseSeats.indexOf(seat) + 1) % counterClockwiseSeats.size]

fun nextActiveCounterClockwise(seat: Seat, activePlayers: Set<Seat>): Seat {
    var next = nextCounterClockwise(seat)
    while (next !in activePlayers) {
        next = nextCounterClockwise(next)
    }
    return next
}

fun counterClockwiseOrderFrom(first: Seat, included: Set<Seat>): List<Seat> {
    val result = mutableListOf<Seat>()
    var current = first
    repeat(seats.size) {
        if (current in included) result += current
        current = nextCounterClockwise(current)
    }
    return result
}

fun GameState.hand(seat: Seat): List<SwissCard> = hands[seat].orEmpty()

fun GameState.score(seat: Seat): Int = scores[seat] ?: 0

fun deckPosition(): Pair<String, String> = "9%" to "43%"

fun dealTargetPosition(seat: Seat): Pair<String, String> = when (seat) {
    Seat.User -> "50%" to "82%"
    Seat.Player1 -> "25%" to "27%"
    Seat.Player2 -> "50%" to "17%"
    Seat.Player3 -> "75%" to "27%"
}

fun List<SwissCard>.toDeckFlights(fromSeat: Seat, faceUp: Boolean): List<CardFlight> =
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

fun SwissCard.fromDeckFlight(toSeat: Seat, faceUp: Boolean): CardFlight =
    CardFlight(
        card = this,
        from = deckPosition(),
        to = dealTargetPosition(toSeat),
        arrived = false,
        faceUp = faceUp,
        offset = 0,
    )

fun flightOffset(index: Int): Int = (index - 1) * 10
