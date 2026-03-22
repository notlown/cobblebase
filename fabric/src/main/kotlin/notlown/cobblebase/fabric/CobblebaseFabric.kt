package notlown.cobblebase.fabric

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import notlown.cobblebase.core.BaseManager
import notlown.cobblebase.core.Cobblebase
import notlown.cobblebase.core.net.SkillAssignmentC2SPacket

object CobblebaseFabric : ModInitializer {
    override fun onInitialize() {
        Cobblebase.init()

        // Register C2S packet for skill assignments
        PayloadTypeRegistry.playC2S().register(SkillAssignmentC2SPacket.ID, SkillAssignmentC2SPacket.CODEC)
        ServerPlayNetworking.registerGlobalReceiver(SkillAssignmentC2SPacket.ID) { packet, context ->
            context.server().execute {
                packet.handle(context.player())
            }
        }

        // Load assignments when world starts
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            val world = server.overworld
            BaseManager.load(world)
        }

        // Save assignments when world stops
        ServerLifecycleEvents.SERVER_STOPPING.register { server ->
            val world = server.overworld
            BaseManager.save(world)
        }
    }
}
