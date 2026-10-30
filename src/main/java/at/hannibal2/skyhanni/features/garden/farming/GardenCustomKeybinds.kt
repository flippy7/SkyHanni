package at.hannibal2.skyhanni.features.garden.farming

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.ConfigUpdaterMigrator
import at.hannibal2.skyhanni.features.fishing.FishingApi
import at.hannibal2.skyhanni.features.garden.GardenApi
import at.hannibal2.skyhanni.features.garden.pests.PestApi
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ConditionalUtils
import at.hannibal2.skyhanni.utils.KeyboardManager
import at.hannibal2.skyhanni.utils.KeyboardManager.isKeyClicked
import at.hannibal2.skyhanni.utils.KeyboardManager.isKeyHeld
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.compat.MinecraftCompat
import io.github.notenoughupdates.moulconfig.observer.Property
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.ToggleKeyMapping
import net.minecraft.client.gui.screens.inventory.SignEditScreen
import org.lwjgl.glfw.GLFW
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import kotlin.time.Duration.Companion.milliseconds

@SkyHanniModule
object GardenCustomKeybinds {

    private val config get() = GardenApi.config.keyBind
    private val mcSettings get() = Minecraft.getInstance().options

    private var map: Map<KeyMapping, List<Int>> = emptyMap()
    private val pressedToggleKeys = mutableMapOf<KeyMapping, Set<Int>>()
    private var lastWindowOpenTime = SimpleTimeMark.farPast()
    private var wasActive = false

    @JvmStatic
    fun isKeyDown(keyBinding: KeyMapping, isDown: Boolean, cir: CallbackInfoReturnable<Boolean>) {
        if (!updateActiveState()) return
        val override = map[keyBinding] ?: run {
            if (map.values.any { keyBinding.key.value in it }) {
                cir.returnValue = false
            }
            return
        }

        cir.returnValue = when {
            keyBinding.isToggle() -> keyBinding.updateToggleState(override, isDown)
            else -> override.any { it.isKeyHeld() }
        }
    }

    @JvmStatic
    fun isKeyPressed(keyBinding: KeyMapping, cir: CallbackInfoReturnable<Boolean>) {
        if (!updateActiveState()) return
        val override = map[keyBinding] ?: run {
            if (map.values.any { keyBinding.key.value in it }) {
                cir.returnValue = false
            }
            return
        }
        cir.returnValue = if (keyBinding.isToggle()) {
            keyBinding.consumeToggleClick(override)
        } else {
            // Poll both keys even after a click, so released keys are unlocked.
            override.count { it.isKeyClicked() } > 0
        }
    }

    @HandleEvent
    fun onTick() {
        if (!isEnabled()) return
        val screen = MinecraftCompat.screen ?: return
        if (screen !is SignEditScreen) return
        lastWindowOpenTime = SimpleTimeMark.now()
    }

    @HandleEvent
    fun onConfigLoad() {
        with(config) {
            ConditionalUtils.onToggle(
                attack, attackSecondary,
                useItem, useItemSecondary,
                left, leftSecondary,
                right, rightSecondary,
                forward, forwardSecondary,
                back, backSecondary,
                jump, jumpSecondary,
                sneak, sneakSecondary,
            ) {
                update()
            }
            update()
        }
    }

    private fun update() {
        pressedToggleKeys.clear()
        wasActive = false
        with(config) {
            with(mcSettings) {
                map = buildMap {
                    fun add(keyBinding: KeyMapping, primary: Property<Int>, secondary: Property<Int>) {
                        val keys = listOf(primary.get(), secondary.get()).filter { it != GLFW.GLFW_KEY_UNKNOWN }.distinct()
                        put(keyBinding, keys)
                    }
                    add(keyAttack, attack, attackSecondary)
                    add(keyUse, useItem, useItemSecondary)
                    add(keyLeft, left, leftSecondary)
                    add(keyRight, right, rightSecondary)
                    add(keyUp, forward, forwardSecondary)
                    add(keyDown, back, backSecondary)
                    add(keyJump, jump, jumpSecondary)
                    add(keyShift, sneak, sneakSecondary)
                }
            }
        }
        KeyMapping.releaseAll()
    }

    private fun updateActiveState(): Boolean {
        val active = isActive()
        if (wasActive == active) return active

        wasActive = active
        pressedToggleKeys.clear()
        if (active) primePressedToggleKeys()
        return active
    }

    private fun primePressedToggleKeys() {
        for ((keyBinding, override) in map) {
            if (keyBinding.isToggle()) {
                pressedToggleKeys[keyBinding] = override.filter { it != keyBinding.key.value && it.isKeyHeld() }.toSet()
            }
        }
    }

    private fun KeyMapping.isToggle(): Boolean =
        this is ToggleKeyMapping && needsToggle.asBoolean

    private fun KeyMapping.consumeRemappedTogglePress(override: List<Int>): Boolean {
        // Minecraft already toggles the original key; handle only the alternative keys here.
        val held = override.filter { it != key.value && it.isKeyHeld() }.toSet()
        val previous = pressedToggleKeys.put(this, held).orEmpty()
        return held.any { it !in previous }
    }

    private fun KeyMapping.updateToggleState(override: List<Int>, isDown: Boolean): Boolean {
        if (!consumeRemappedTogglePress(override)) return isDown
        setDown(true)
        return !isDown
    }

    private fun KeyMapping.consumeToggleClick(override: List<Int>): Boolean {
        if (consumeRemappedTogglePress(override)) {
            isDown = true
            return true
        }
        return key.value in override && key.value.isKeyClicked()
    }

    private fun isEnabled(): Boolean =
        GardenApi.inGarden() &&
            config.enabled &&
            !(GardenApi.onUnfarmablePlot && config.excludeBarn)

    private fun isHoldingTool(): Boolean =
        GardenApi.hasFarmingToolInHand() ||
            (config.mousemat && GardenApi.hasMousematInHand()) ||
            (config.vacuum && PestApi.hasVacuumInHand()) ||
            (config.fishingRod && FishingApi.holdingRod) ||
            (config.sunsGrasp && GardenApi.hasActiveSunsGrasp())

    private fun isActive(): Boolean =
        isEnabled() &&
            isHoldingTool() &&
            !hasGuiOpen() &&
            lastWindowOpenTime.passedSince() > 300.milliseconds

    private fun hasGuiOpen() = MinecraftCompat.screen != null

    @JvmStatic
    fun disableAll() {
        with(config) {
            attack.set(GLFW.GLFW_KEY_UNKNOWN)
            useItem.set(GLFW.GLFW_KEY_UNKNOWN)
            left.set(GLFW.GLFW_KEY_UNKNOWN)
            right.set(GLFW.GLFW_KEY_UNKNOWN)
            forward.set(GLFW.GLFW_KEY_UNKNOWN)
            back.set(GLFW.GLFW_KEY_UNKNOWN)
            jump.set(GLFW.GLFW_KEY_UNKNOWN)
            sneak.set(GLFW.GLFW_KEY_UNKNOWN)
            clearSecondaryKeys()
        }
    }

    @JvmStatic
    fun defaultAll() {
        with(config) {
            attack.set(KeyboardManager.LEFT_MOUSE)
            useItem.set(KeyboardManager.RIGHT_MOUSE)
            left.set(GLFW.GLFW_KEY_A)
            right.set(GLFW.GLFW_KEY_D)
            forward.set(GLFW.GLFW_KEY_W)
            back.set(GLFW.GLFW_KEY_S)
            jump.set(GLFW.GLFW_KEY_SPACE)
            sneak.set(GLFW.GLFW_KEY_LEFT_SHIFT)
            clearSecondaryKeys()
        }
    }

    private fun clearSecondaryKeys() {
        with(config) {
            attackSecondary.set(GLFW.GLFW_KEY_UNKNOWN)
            useItemSecondary.set(GLFW.GLFW_KEY_UNKNOWN)
            leftSecondary.set(GLFW.GLFW_KEY_UNKNOWN)
            rightSecondary.set(GLFW.GLFW_KEY_UNKNOWN)
            forwardSecondary.set(GLFW.GLFW_KEY_UNKNOWN)
            backSecondary.set(GLFW.GLFW_KEY_UNKNOWN)
            jumpSecondary.set(GLFW.GLFW_KEY_UNKNOWN)
            sneakSecondary.set(GLFW.GLFW_KEY_UNKNOWN)
        }
    }

    @HandleEvent
    fun onConfigFix(event: ConfigUpdaterMigrator.ConfigFixEvent) {
        event.move(3, "garden.keyBindEnabled", "garden.keyBind.enabled")
        event.move(3, "garden.keyBindAttack", "garden.keyBind.attack")
        event.move(3, "garden.keyBindUseItem", "garden.keyBind.useItem")
        event.move(3, "garden.keyBindLeft", "garden.keyBind.left")
        event.move(3, "garden.keyBindRight", "garden.keyBind.right")
        event.move(3, "garden.keyBindForward", "garden.keyBind.forward")
        event.move(3, "garden.keyBindBack", "garden.keyBind.back")
        event.move(3, "garden.keyBindJump", "garden.keyBind.jump")
        event.move(3, "garden.keyBindSneak", "garden.keyBind.sneak")
    }
}
