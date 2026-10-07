package io.github.flavelloni.chratze.pages

import androidx.compose.runtime.Composable
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.fillMaxSize
import com.varabyte.kobweb.compose.ui.modifiers.flexGrow
import com.varabyte.kobweb.compose.ui.modifiers.minHeight
import com.varabyte.kobweb.compose.ui.modifiers.padding
import org.jetbrains.compose.web.css.px

@Composable
fun GamePageFrame(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .flexGrow(1)
            .minHeight(0.px)
            .padding(bottom = 8.px)
    ) {
        content()
    }
}
