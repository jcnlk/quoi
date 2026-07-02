package quoi.api.abobaui.elements.impl

import quoi.api.abobaui.constraints.Constraints
import quoi.api.abobaui.elements.Element
import quoi.api.colour.Colour
import quoi.utils.ui.data.Radii
import quoi.utils.ui.rendering.Image
import quoi.utils.ui.rendering.UIRenderer

class ImageElement(
    private val image: Image,
    constraints: Constraints,
    colour: Colour?,
    radius: Radii?,
) : Element(constraints, colour) {

    private val radius: Radii = radius ?: Block.EMPTY_RADIUS

    init {
//        registerEvent(Lifetime.Initialised) {
//            UIRenderer.createImage(image)
//            false
//        }
//        registerEvent(Lifetime.Uninitialised) {
//            UIRenderer.deleteImage(image)
//            false
//        }
    }

    override fun drawNvg() {
        UIRenderer.image(image, x, y, width, height, radius, colour?.rgb)
    }
}