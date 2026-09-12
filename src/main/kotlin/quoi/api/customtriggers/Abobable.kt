package quoi.api.customtriggers

import quoi.api.abobaui.elements.ElementScope

interface Abobable {
    fun displayString(): String
    /**
     * @return the first configuration error, or `null` if the settings are valid
     */
    fun validationError(): String? = null // TODO: replace with toast notifications or smth more user-friendly
    fun ElementScope<*>.draw(): ElementScope<*>
}