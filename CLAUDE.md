# Cobblebase — Development Notes

## NeoForge: NEVER use @JvmStatic on @SubscribeEvent methods

Kotlin `object` classes are singletons. Kotlin for Forge (`thedarkcolour.kfflang`) registers the object **instance** on the event bus via `AutoKotlinEventBusSubscriber`. If a `@SubscribeEvent` method has `@JvmStatic`, the event bus throws `IllegalArgumentException` because it expects non-static methods when registered with an instance.

**Wrong:**
```kotlin
@EventBusSubscriber(modid = "cobblebase", bus = EventBusSubscriber.Bus.MOD)
object MyEvents {
    @SubscribeEvent
    @JvmStatic  // ❌ NEVER DO THIS
    fun onSomeEvent(event: SomeEvent) { }
}
```

**Correct:**
```kotlin
@EventBusSubscriber(modid = "cobblebase", bus = EventBusSubscriber.Bus.MOD)
object MyEvents {
    @SubscribeEvent  // ✅ No @JvmStatic
    fun onSomeEvent(event: SomeEvent) { }
}
```

## GUI: NEVER call super.render() or renderBackground()

Minecraft 1.21+ applies a Gaussian blur shader in `Screen.render()` → `renderBackground()`. This blurs everything rendered before `super.render()` is called, making custom GUI text unreadable.

Instead of `super.render()`, manually render widgets:
```kotlin
for (child in this.children()) {
    if (child is Drawable) {
        child.render(context, mouseX, mouseY, delta)
    }
}
```

## Dependencies

Cobblebase requires **Cloth Config** (`me.shedaniel.autoconfig`). Without it, the mod crashes with `ClassNotFoundException: me.shedaniel.autoconfig.ConfigData`.

## Known Mod Incompatibilities

- **Chiselmon** — Causes GUI crashes when opening the Cobblebase screen.
