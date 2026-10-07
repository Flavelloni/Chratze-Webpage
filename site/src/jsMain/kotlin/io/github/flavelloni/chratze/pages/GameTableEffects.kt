package io.github.flavelloni.chratze.pages

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

@Composable
fun PotArea(pot: Int) {
    Div({
        attr(
            "style",
            """
                position:absolute;
                left:50%;
                top:33%;
                transform:translate(-50%, -50%);
                z-index:2;
                min-width:54px;
                padding:0;
                color:#fff8eb;
                background:transparent;
                border:0;
                text-shadow:0 2px 4px rgba(0,0,0,.42);
                text-align:center;
                font-size:27px;
                font-weight:900;
            """.trimIndent()
        )
    }) {
        Div { Text(moneyBag()) }
        Div({ attr("style", "font-size:11px;font-weight:900;line-height:1;margin-top:-2px;") }) { Text(pot.chf()) }
    }
}

@Composable
fun FlyingCards(cardFlights: List<CardFlight>) {
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
fun FlyingDealCard(dealAnimation: DealAnimation?) {
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

