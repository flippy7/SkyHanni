package at.hannibal2.skyhanni.test.garden

import at.hannibal2.skyhanni.features.garden.farming.GardenCustomKeybinds
import at.hannibal2.skyhanni.utils.KeyboardManager
import at.hannibal2.skyhanni.utils.KeyboardManager.isKeyHeld
import at.hannibal2.skyhanni.utils.ReflectionUtils.getPrivateFieldValue
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import net.minecraft.client.KeyMapping
import net.minecraft.client.ToggleKeyMapping
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

class GardenCustomKeybindsTest {

    private val heldKeys = mutableSetOf<Int>()

    @BeforeEach
    fun setUp() {
        mockkObject(KeyboardManager)
        every { any<Int>().isKeyHeld() } answers { firstArg<Int>() in heldKeys }
        (KeyboardManager.getPrivateFieldValue("lockedKeys") as MutableSet<*>).clear()
        mockkObject(GardenCustomKeybinds, recordPrivateCalls = true)
        every { GardenCustomKeybinds["isActive"]() } returns true
        (GardenCustomKeybinds.getPrivateFieldValue("pressedToggleKeys") as MutableMap<*, *>).clear()
        setState("wasActive", true)
    }

    @AfterEach
    fun tearDown() {
        setState("map", emptyMap<KeyMapping, List<Int>>())
        setState("wasActive", false)
        (GardenCustomKeybinds.getPrivateFieldValue("pressedToggleKeys") as MutableMap<*, *>).clear()
        (KeyboardManager.getPrivateFieldValue("lockedKeys") as MutableSet<*>).clear()
        unmockkObject(GardenCustomKeybinds, KeyboardManager)
    }

    @Test
    fun `either key holds the action and releasing one keeps the other active`() {
        val binding = keyBinding()
        bind(binding, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_D)

        assertFalse(isDown(binding))
        heldKeys.add(GLFW.GLFW_KEY_A)
        assertTrue(isDown(binding))
        heldKeys.add(GLFW.GLFW_KEY_D)
        heldKeys.remove(GLFW.GLFW_KEY_A)
        assertTrue(isDown(binding))
        heldKeys.clear()
        assertFalse(isDown(binding))
    }

    @Test
    fun `both keys can click again after overlapping presses`() {
        val binding = keyBinding()
        bind(binding, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_D)

        heldKeys.add(GLFW.GLFW_KEY_D)
        assertTrue(isClicked(binding))
        assertFalse(isClicked(binding))
        heldKeys.clear()
        heldKeys.add(GLFW.GLFW_KEY_A)
        assertTrue(isClicked(binding))
        heldKeys.add(GLFW.GLFW_KEY_D)
        assertTrue(isClicked(binding))
        assertFalse(isClicked(binding))
    }

    @Test
    fun `secondary key suppresses its original action`() {
        val binding = keyBinding()
        bind(binding, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_D)
        val original = KeyMapping("skyhanni.test.original", GLFW.GLFW_KEY_D, KeyMapping.Category.MOVEMENT)
        heldKeys.add(GLFW.GLFW_KEY_D)

        assertFalse(isDown(original))
        assertFalse(isClicked(original))
    }

    @Test
    fun `each alternative toggles once even while the other is held`() {
        val binding = toggleBinding()
        bind(binding, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_D)

        heldKeys.add(GLFW.GLFW_KEY_A)
        assertTrue(isDown(binding))
        assertTrue(isDown(binding))
        assertFalse(isClicked(binding))
        heldKeys.add(GLFW.GLFW_KEY_D)
        assertFalse(isDown(binding))
        assertFalse(isDown(binding))
        heldKeys.remove(GLFW.GLFW_KEY_D)
        assertFalse(isDown(binding))
        heldKeys.add(GLFW.GLFW_KEY_D)
        assertTrue(isDown(binding))
    }

    @Test
    fun `secondary toggle works alongside the vanilla key without toggling vanilla twice`() {
        val binding = toggleBinding()
        bind(binding, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_D)

        heldKeys.add(GLFW.GLFW_KEY_LEFT_SHIFT)
        binding.setDown(true)
        assertTrue(isDown(binding))
        heldKeys.add(GLFW.GLFW_KEY_D)
        assertFalse(isDown(binding))
        assertFalse(isDown(binding))
    }

    @Test
    fun `click and held queries share the same toggle press`() {
        val binding = toggleBinding()
        bind(binding, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_D)

        heldKeys.add(GLFW.GLFW_KEY_D)
        assertTrue(isClicked(binding))
        assertTrue(isDown(binding))
        assertFalse(isClicked(binding))
    }

    @Test
    fun `held toggle keys are primed when custom bindings activate`() {
        val binding = toggleBinding()
        bind(binding, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_D)
        setState("wasActive", false)
        heldKeys.addAll(listOf(GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_D))

        assertFalse(isDown(binding))
        heldKeys.clear()
        assertFalse(isDown(binding))
        heldKeys.add(GLFW.GLFW_KEY_D)
        assertTrue(isDown(binding))
    }

    private fun keyBinding() = KeyMapping("skyhanni.test.movement", GLFW.GLFW_KEY_W, KeyMapping.Category.MOVEMENT)

    private fun toggleBinding() = ToggleKeyMapping(
        "skyhanni.test.sneak", GLFW.GLFW_KEY_LEFT_SHIFT, KeyMapping.Category.MOVEMENT, { true }, false,
    )

    private fun bind(binding: KeyMapping, vararg keys: Int) {
        setState("map", mapOf(binding to keys.toList()))
    }

    private fun isDown(binding: KeyMapping): Boolean {
        val result = CallbackInfoReturnable<Boolean>("isDown", true)
        GardenCustomKeybinds.isKeyDown(binding, binding.isDown, result)
        return result.returnValue
    }

    private fun isClicked(binding: KeyMapping): Boolean {
        val result = CallbackInfoReturnable<Boolean>("consumeClick", true)
        GardenCustomKeybinds.isKeyPressed(binding, result)
        return result.returnValue
    }

    private fun setState(name: String, value: Any) {
        GardenCustomKeybinds::class.java.getDeclaredField(name).apply { isAccessible = true }.set(GardenCustomKeybinds, value)
    }
}
