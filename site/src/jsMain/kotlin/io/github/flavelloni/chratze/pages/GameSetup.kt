package io.github.flavelloni.chratze.pages

import kotlin.random.Random
import kotlinx.coroutines.delay

suspend fun dealNewRound(previous: GameState, setGame: (GameState) -> Unit): GameState {
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

suspend fun advanceComputers(state: GameState, setGame: (GameState) -> Unit): GameState {
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

fun GameState.awaitingComputer(): Boolean =
    when (phase) {
        GamePhase.CallingChratze,
        GamePhase.CallingAlong,
        GamePhase.Exchanging,
        GamePhase.Playing,
        -> active != Seat.User

        else -> false
    }
