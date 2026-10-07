package io.github.flavelloni.chratze.pages

import androidx.compose.runtime.Composable
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.foundation.layout.Row
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.gap
import com.varabyte.kobweb.compose.ui.modifiers.minHeight
import com.varabyte.kobweb.compose.ui.modifiers.width
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

@Composable
fun TableSpot(position: String, content: @Composable () -> Unit) {
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
fun PlayerArea(
    seat: Seat,
    cardCount: Int,
    score: Int,
    bankroll: Int,
    active: Seat,
    dealer: Seat,
    out: Boolean,
    speech: String?,
    position: String,
    onPlayerClick: (Seat) -> Unit,
) {
    TableSpot(position) {
        Column(
            Modifier
                .width(126.px)
                .gap(8.px),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Div({
                attr("style", "position:relative;opacity:${if (out) ".48" else "1"};")
            }) {
                if (speech != null) {
                    SpeechBubble(speech)
                }
                PlayerAvatar(seat, score, bankroll, active == seat, dealer == seat, onPlayerClick)
            }
            Row(
                Modifier
                    .width(126.px)
                    .minHeight(48.px),
                verticalAlignment = Alignment.Bottom,
            ) {
                repeat(cardCount) { index ->
                    Div({
                        attr(
                            "style",
                            """
                                margin-left:${if (index == 0) "0" else "-15px"};
                                transform:rotate(${(index - cardCount / 2) * 5}deg);
                                transform-origin:50% 100%;
                                z-index:${index};
                            """.trimIndent()
                        )
                    }) {
                        FaceDownCard(width = 36, height = 50)
                    }
                }
            }
        }
    }
}

@Composable
fun SpeechBubble(text: String) {
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
fun PlayerAvatar(
    seat: Seat,
    score: Int,
    bankroll: Int,
    active: Boolean,
    dealer: Boolean,
    onPlayerClick: (Seat) -> Unit,
) {
    Div({
        onClick { onPlayerClick(seat) }
        attr(
            "style",
            """
                position:relative;
                width:clamp(62px, 7vw, 84px);
                height:clamp(62px, 7vw, 84px);
                border-radius:50%;
                display:flex;
                flex-direction:column;
                align-items:center;
                justify-content:center;
                padding:0;
                box-sizing:border-box;
                text-align:center;
                font-size:clamp(48px, 6.2vw, 68px);
                line-height:1.12;
                font-weight:780;
                color:#1b2638;
                background:transparent;
                border:0;
                filter:${if (active) "drop-shadow(0 0 8px #f6d55c)" else "drop-shadow(0 3px 5px rgba(0,0,0,.25))"};
                cursor:pointer;
                margin-bottom:16px;
            """.trimIndent()
        )
        attr("title", "${seat.label}: ${trickLabel(score)}, ${bankroll.chf()}")
    }) {
        if (dealer) DealerBadge()
        Text(seat.avatar)
        PlayerMoney(bankroll)
    }
}

@Composable
fun PlayerMoney(bankroll: Int) {
    Div({
        attr(
            "style",
            """
                position:absolute;
                left:50%;
                top:calc(100% + 1px);
                transform:translateX(-50%);
                display:flex;
                align-items:center;
                gap:2px;
                color:#fff8eb;
                font-size:10px;
                line-height:1;
                font-weight:900;
                text-shadow:0 1px 2px rgba(0,0,0,.45);
                white-space:nowrap;
            """.trimIndent()
        )
    }) {
        Text("${moneyBag()} ${bankroll.chf().removePrefix("CHF ")}")
    }
}

@Composable
fun DealerBadge() {
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
fun UserStatus(game: GameState) {
    Div({
        attr(
            "style",
            """
                position:absolute;
                right:clamp(10px, 4vw, 44px);
                bottom:clamp(142px, 20vh, 230px);
                z-index:5;
                max-width:calc(100% - 20px);
            """.trimIndent()
        )
    }) {
        Row(Modifier.gap(7.px), verticalAlignment = Alignment.CenterVertically) {
            Div({
                attr(
                    "style",
                    """
                        position:relative;
                        padding:7px 13px;
                        border-radius:999px;
                        color:#fff8eb;
                        background:${if (Seat.User in game.outPlayers) "#687077" else "#1b2638"};
                        font-size:clamp(12px, 3.2vw, 14px);
                        line-height:1;
                        font-weight:800;
                        box-shadow:0 10px 20px rgba(0,0,0,.18);
                        white-space:nowrap;
                    """.trimIndent()
                )
            }) {
                if (game.dealer == Seat.User) DealerBadge()
                val trickText = if (Seat.User in game.outPlayers) "out" else trickLabel(game.score(Seat.User))
                Text(trickText)
            }
            Div({
                attr(
                    "style",
                    """
                        color:#fff8eb;
                        font-size:clamp(12px, 3.2vw, 14px);
                        line-height:1;
                        font-weight:900;
                        text-shadow:0 1px 2px rgba(0,0,0,.45);
                        white-space:nowrap;
                    """.trimIndent()
                )
            }) {
                Text("${moneyBag()} ${game.bankrolls.getValueOrZero(Seat.User).chf().removePrefix("CHF ")}")
            }
            game.speechBubbles[Seat.User]?.let { SpeechBubbleInline(it) }
        }
    }
}

@Composable
fun SpeechBubbleInline(text: String) {
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
fun PlayerInfoPopup(game: GameState, seat: Seat?, onClose: () -> Unit) {
    if (seat == null) return

    Div({
        onClick { onClose() }
        attr(
            "style",
            """
                position:absolute;
                inset:0;
                z-index:11;
            """.trimIndent()
        )
    }) {
        Div({
            onClick { it.stopPropagation() }
            attr(
                "style",
                """
                    position:absolute;
                    left:50%;
                    top:18%;
                    transform:translateX(-50%);
                    width:min(84vw, 260px);
                    padding:12px;
                    border-radius:8px;
                    background:#fffaf0;
                    border:1px solid rgba(36,28,21,.16);
                    box-shadow:0 18px 40px rgba(0,0,0,.28);
                    color:#1f1a14;
                """.trimIndent()
            )
        }) {
            Row(Modifier.gap(10.px), verticalAlignment = Alignment.CenterVertically) {
                Div({ attr("style", "font-size:34px;line-height:1;") }) { Text(seat.avatar) }
                Div({ attr("style", "flex:1;min-width:0;") }) {
                    Div({ attr("style", "font-size:16px;font-weight:950;") }) { Text(seat.label) }
                    Div({ attr("style", "font-size:12px;color:#6d6259;font-weight:750;") }) {
                        Text("${trickLabel(game.score(seat))} · ${game.bankrolls.getValueOrZero(seat).chf()}")
                    }
                }
                Button(attrs = {
                    onClick { onClose() }
                    attr(
                        "style",
                        """
                            border:0;
                            background:transparent;
                            color:#6d6259;
                            font-size:20px;
                            font-weight:900;
                            cursor:pointer;
                        """.trimIndent()
                    )
                }) {
                    Text("x")
                }
            }
        }
    }
}
