package io.github.flavelloni.chratze.pages

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

@Composable
fun RoundControls(
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
                Div({ attr("style", "display:flex;flex-direction:column;gap:9px;min-width:145px;") }) {
                    ActionButton("chratze", "#1f7a46", "#f3fff7") { onUserBid(true) }
                    ActionButton("lose", "#a7372f", "#fff7f5") { onUserBid(false) }
                }
            }

            game.phase == GamePhase.CallingAlong && game.active == Seat.User -> {
                Div({ attr("style", "display:flex;flex-direction:column;gap:9px;min-width:145px;") }) {
                    ActionButton("chume mit", "#1f7a46", "#f3fff7") { onUserJoin(true) }
                    ActionButton("ich bin weg", "#a7372f", "#fff7f5") { onUserJoin(false) }
                }
            }

            game.phase == GamePhase.Exchanging && game.active == Seat.User -> {
                SecondaryButton(if (game.selectedExchangeIds.isEmpty()) "Keep all" else "Exchange ${game.selectedExchangeIds.size}") {
                    onUserExchangeConfirm()
                }
            }
        }
    }
}

@Composable
fun SettlementModal(game: GameState, onNewRoundClick: () -> Unit) {
    if (game.phase != GamePhase.Settlement) return

    Div({
        onClick { onNewRoundClick() }
        attr(
            "style",
            """
                position:absolute;
                left:50%;
                top:5%;
                transform:translateX(-50%);
                width:min(92vw, 420px);
                max-height:82vh;
                overflow:auto;
                z-index:10;
                padding:12px;
                border-radius:8px;
                color:#1f1a14;
                background:#fffaf0;
                border:1px solid rgba(36,28,21,.16);
                box-shadow:0 22px 50px rgba(0,0,0,.32);
                cursor:pointer;
            """.trimIndent()
        )
    }) {
        Div({
            attr(
                "style",
                """
                    display:flex;
                    align-items:center;
                    justify-content:flex-start;
                    gap:12px;
                    margin-bottom:10px;
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
        }

        Div({
            attr(
                "style",
                """
                    display:flex;
                    flex-direction:column;
                    gap:6px;
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
fun SettlementPlayerCard(game: GameState, seat: Seat) {
    val amount = game.moneyEvents[seat] ?: 0
    Div({
        attr(
            "style",
            """
                display:flex;
                align-items:center;
                gap:9px;
                min-width:0;
                padding:7px 9px;
                border-radius:8px;
                background:${if (amount >= 0) "#f1fbf4" else "#fff1ee"};
                border:1px solid ${if (amount >= 0) "#cdebd6" else "#ffd0c8"};
            """.trimIndent()
        )
    }) {
        Div({ attr("style", "font-size:25px;line-height:1;width:32px;text-align:center;") }) { Text(seat.avatar) }
        Div({ attr("style", "min-width:0;flex:1;") }) {
            Div({ attr("style", "font-size:13px;font-weight:900;white-space:nowrap;") }) {
                Text(seat.label)
            }
            Div({ attr("style", "font-size:11px;color:#655c53;line-height:1.15;white-space:nowrap;") }) {
                Text("${trickLabel(game.score(seat))} · total ${game.bankrolls.getValueOrZero(seat).chf()}")
            }
        }
        Div({
            attr(
                "style",
                """
                    font-size:13px;
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
fun PrimaryButton(label: String, onClick: () -> Unit) {
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
fun ActionButton(label: String, background: String, color: String, onClick: () -> Unit) {
    Button(attrs = {
        onClick { onClick() }
        attr(
            "style",
            """
                border:0;
                border-radius:8px;
                padding:12px 15px;
                font-size:15px;
                font-weight:900;
                color:$color;
                background:$background;
                box-shadow:0 10px 20px rgba(0,0,0,.2);
                cursor:pointer;
            """.trimIndent()
        )
    }) {
        Text(label)
    }
}

@Composable
fun SecondaryButton(label: String, onClick: () -> Unit) {
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

