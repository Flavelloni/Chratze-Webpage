package io.github.flavelloni.chratze.pages

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.dom.Div

@Composable
fun GameTable(
    game: GameState,
    inspectedSeat: Seat?,
    onNewRoundClick: () -> Unit,
    onUserBid: (Boolean) -> Unit,
    onUserJoin: (Boolean) -> Unit,
    onUserCardClick: (SwissCard) -> Unit,
    onUserExchangeConfirm: () -> Unit,
    onPlayerClick: (Seat) -> Unit,
    onClosePlayerInfo: () -> Unit,
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
                width:100%;
                height:100%;
                min-height:0;
                overflow:hidden;
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
                    width:min(104vw, 1220px);
                    height:96%;
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
            active = game.active,
            dealer = game.dealer,
            out = Seat.Player1 in game.outPlayers,
            speech = game.speechBubbles[Seat.Player1],
            position = "left:23%; top:19%;",
            onPlayerClick = onPlayerClick,
        )
        PlayerArea(
            seat = Seat.Player2,
            cardCount = game.hand(Seat.Player2).size,
            score = game.score(Seat.Player2),
            bankroll = game.bankrolls.getValueOrZero(Seat.Player2),
            active = game.active,
            dealer = game.dealer,
            out = Seat.Player2 in game.outPlayers,
            speech = game.speechBubbles[Seat.Player2],
            position = "left:50%; top:10%;",
            onPlayerClick = onPlayerClick,
        )
        PlayerArea(
            seat = Seat.Player3,
            cardCount = game.hand(Seat.Player3).size,
            score = game.score(Seat.Player3),
            bankroll = game.bankrolls.getValueOrZero(Seat.Player3),
            active = game.active,
            dealer = game.dealer,
            out = Seat.Player3 in game.outPlayers,
            speech = game.speechBubbles[Seat.Player3],
            position = "left:77%; top:19%;",
            onPlayerClick = onPlayerClick,
        )
        TrumpCard(game.trump)
        UserStatus(game)
        TrickCards(game.trick, game.phase == GamePhase.Collecting, game.leader)
        UserHand(game, onUserCardClick, onUserExchangeConfirm)
        RoundControls(game, onNewRoundClick, onUserBid, onUserJoin, onUserExchangeConfirm)
        SettlementModal(game, onNewRoundClick)
        PlayerInfoPopup(game, inspectedSeat, onClosePlayerInfo)
    }
}

