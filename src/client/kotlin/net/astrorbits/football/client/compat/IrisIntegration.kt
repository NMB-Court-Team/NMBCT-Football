package net.astrorbits.football.client.compat

import net.fabricmc.loader.api.FabricLoader

object IrisIntegration {
    private val shaderPackInUseChecker: (() -> Boolean)? by lazy { resolveChecker() }

    fun isShaderPackInUse(): Boolean = shaderPackInUseChecker?.invoke() ?: false

    private fun resolveChecker(): (() -> Boolean)? {
        if (!FabricLoader.getInstance().isModLoaded("iris")) {
            return null
        }
        try {
            val irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi")
            val getInstance = irisApiClass.getMethod("getInstance")
            val isShaderPackInUse = irisApiClass.getMethod("isShaderPackInUse")
            return {
                try {
                    isShaderPackInUse.invoke(getInstance.invoke(null)) as Boolean
                } catch (_: ReflectiveOperationException) {
                    false
                }
            }
        } catch (_: ReflectiveOperationException) {
            return null
        }
    }
}
