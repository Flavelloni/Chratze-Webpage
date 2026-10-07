package io.github.flavelloni.chratze.pages

import androidx.compose.runtime.Composable
import com.varabyte.kobweb.navigation.BasePath
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Text

@Composable
fun DeckIcon(remainingCards: Int, visible: Boolean) {
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
fun FaceDownCard(width: Int, height: Int) {
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
fun TrumpCard(trump: SwissCard?) {
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
fun TrickCards(trick: List<PlayedCard>, collecting: Boolean, winner: Seat) {
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
            CardView(playedCard.card, width = 82, height = 115, clickable = false)
        }
    }
}

fun trickPosition(seat: Seat, index: Int): Pair<String, String> = when (seat) {
    Seat.User -> "50%" to "61%"
    Seat.Player1 -> "35%" to "49%"
    Seat.Player2 -> "50%" to "37%"
    Seat.Player3 -> "65%" to "49%"
}

fun collectPosition(seat: Seat): Pair<String, String> = when (seat) {
    Seat.User -> "50%" to "91%"
    Seat.Player1 -> "25%" to "22%"
    Seat.Player2 -> "50%" to "12%"
    Seat.Player3 -> "75%" to "22%"
}

@Composable
fun UserHand(
    game: GameState,
    onUserCardClick: (SwissCard) -> Unit,
    onUserExchangeConfirm: () -> Unit,
) {
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
                min-height:156px;
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
                        margin-left:${if (index == 0) "0" else "clamp(-42px, calc((415px - 100vw) * -1), 12px)"};
                        opacity:${if (game.phase == GamePhase.Playing && game.active == Seat.User && !legal) ".45" else "1"};
                        transform:${if (selected) "translateY(-32px)" else "translateY(0)"};
                        transition:transform .16s ease, opacity .16s ease;
                        outline:${if (selected) "4px solid #f6d55c" else "0"};
                        border-radius:9px;
                        z-index:$index;
                    """.trimIndent()
                )
            }) {
                val rankOffset = if (index < game.hand(Seat.User).lastIndex) -14 else 0
                CardView(card, width = 108, height = 152, clickable = clickable, rankOffsetX = rankOffset)
            }
        }
    }
}

@Composable
fun CardView(card: SwissCard, width: Int, height: Int, clickable: Boolean, rankOffsetX: Int = 0) {
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
                    transform:translateX(${rankOffsetX}px);
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

