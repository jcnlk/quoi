package quoi.api.input

import org.lwjgl.sdl.SDLMouse

object CursorShape {
    val ARROW by lazy { SDLMouse.SDL_CreateSystemCursor(SDLMouse.SDL_SYSTEM_CURSOR_DEFAULT) }
    val HAND by lazy { SDLMouse.SDL_CreateSystemCursor(SDLMouse.SDL_SYSTEM_CURSOR_POINTER) }
    val IBEAM by lazy { SDLMouse.SDL_CreateSystemCursor(SDLMouse.SDL_SYSTEM_CURSOR_TEXT) }
    val CROSSHAIR by lazy { SDLMouse.SDL_CreateSystemCursor(SDLMouse.SDL_SYSTEM_CURSOR_CROSSHAIR) }
    val HRESIZE by lazy { SDLMouse.SDL_CreateSystemCursor(SDLMouse.SDL_SYSTEM_CURSOR_EW_RESIZE) }
    val VRESIZE by lazy { SDLMouse.SDL_CreateSystemCursor(SDLMouse.SDL_SYSTEM_CURSOR_NS_RESIZE) }
    const val NORMAL = 0L
}