package cat.xoriguer.lectura

import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.ui.Modifier

@OptIn(ExperimentalFoundationApi::class)
actual fun Modifier.opcioMenuText(etiqueta: String, accio: () -> Unit): Modifier =
    appendTextContextMenuComponents {
        separator()
        item(key = "opcio-$etiqueta", label = etiqueta) { close(); accio() }
    }

@OptIn(ExperimentalFoundationApi::class)
actual fun activaMenuText() { ComposeFoundationFlags.isNewContextMenuEnabled = true }
