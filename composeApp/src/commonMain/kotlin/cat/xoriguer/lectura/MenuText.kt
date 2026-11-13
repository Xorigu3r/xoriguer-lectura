package cat.xoriguer.lectura

import androidx.compose.ui.Modifier

/**
 * Afegeix una opció pròpia (p. ex. «Diccionari») al menú del sistema que surt en seleccionar text,
 * al costat de Copia / Tradueix. Cada plataforma ho fa amb la seva API.
 */
expect fun Modifier.opcioMenuText(etiqueta: String, accio: () -> Unit): Modifier

/** Activa el menú de text nou de Compose (el que permet afegir-hi opcions). Es crida abans de mostrar res. */
expect fun activaMenuText()
