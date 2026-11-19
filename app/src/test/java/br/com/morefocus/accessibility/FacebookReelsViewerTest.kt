package br.com.morefocus.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FacebookReelsViewerTest {
    @Test
    fun recognizesTheThreeOpenPageLabelsObservedOnFacebook588() {
        assertTrue(isFacebookReelPage("android.widget.Button", "Reel de Criador", false))
        assertTrue(isFacebookReelPage("android.widget.Button", "Reel", false))
        assertTrue(isFacebookReelPage("android.view.ViewGroup", "Reel. Deslize para cima para ver mais.", false))
    }

    @Test
    fun inlineVideoCardsAndNavigationLabelsDoNotIdentifyTheViewer() {
        assertFalse(isFacebookReelPage("android.widget.Button", "Reel", true))
        assertFalse(isFacebookReelPage("android.widget.ImageView", "Reproduzir reel atual", false))
        assertFalse(isFacebookReelPage("android.view.ViewGroup", "Reels", false))
        assertFalse(isFacebookReelPage("android.widget.Button", "Voltar", true))
    }
}
