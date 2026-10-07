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
import io.github.flavelloni.chratze.components.layouts.PageLayoutData
import kotlinx.coroutines.launch

@InitRoute
fun initHomePage(ctx: InitRouteContext) {
    ctx.data.add(PageLayoutData("Game Setup"))
}

@Page
@Layout(".components.layouts.PageLayout")
@Composable
fun HomePage() {
    var game by remember { mutableStateOf(GameState()) }
    var inspectedSeat by remember { mutableStateOf<Seat?>(null) }
    val scope = rememberCoroutineScope()

    GamePageFrame {
        GameTable(
            game = game,
            inspectedSeat = inspectedSeat,
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
            onPlayerClick = { seat -> inspectedSeat = seat },
            onClosePlayerInfo = { inspectedSeat = null },
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
