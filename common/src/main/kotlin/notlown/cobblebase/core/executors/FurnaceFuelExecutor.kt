package notlown.cobblebase.core.executors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.block.AbstractFurnaceBlock
import net.minecraft.block.entity.AbstractFurnaceBlockEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import notlown.cobblebase.core.SkillDef
import notlown.cobblebase.core.CobblebaseConfig
import notlown.cobblebase.core.effects.SkillEffects
import notlown.cobblebase.core.SkillEntry
import notlown.cobblebase.core.SkillExecutor
import notlown.cobblebase.core.NavigationHelper
import java.util.UUID

/**
 * Furnace fuel executor: finds nearby furnaces and adds burn time to them.
 * Works with regular furnaces, blast furnaces, and smokers.
 */
object FurnaceFuelExecutor : SkillExecutor {

    private val lastFuelTime = mutableMapOf<UUID, Long>()
    private val furnaceTarget = mutableMapOf<UUID, BlockPos>()
    private const val BURN_TIME_ADDED = 1600 // Same as a piece of coal (80 seconds)

    override fun tick(
        world: World,
        origin: BlockPos,
        pokemonEntity: PokemonEntity,
        skill: SkillDef,
        skillEntry: SkillEntry
    ) {
        val pokemonId = pokemonEntity.pokemon.uuid
        val now = world.time
        val cooldownTicks = CobblebaseConfig.getEffectiveCooldownTicks(skill.cooldownSeconds, skillEntry.proficiency)

        // Cooldown check
        val lastTime = lastFuelTime[pokemonId] ?: 0L
        if (now - lastTime < cooldownTicks) return

        // Find or navigate to a furnace that needs fuel
        val target = furnaceTarget[pokemonId] ?: findFurnaceNeedingFuel(world, origin, skill.searchRadius)
        if (target == null) return

        furnaceTarget[pokemonId] = target

        // Verify the furnace still needs fuel
        if (!furnaceNeedsFuel(world, target)) {
            furnaceTarget.remove(pokemonId)
            return
        }

        NavigationHelper.navigateTo(pokemonEntity, target)
        if (NavigationHelper.isPokemonAtPosition(pokemonEntity, target)) {
            addFuel(world, target)
            lastFuelTime[pokemonId] = now
            furnaceTarget.remove(pokemonId)
            SkillEffects.playSuccess(world, pokemonEntity, skill.effectType)
        }
    }

    private fun findFurnaceNeedingFuel(world: World, origin: BlockPos, radius: Int): BlockPos? {
        var best: BlockPos? = null
        var bestDist = Double.MAX_VALUE

        for (x in -radius..radius) {
            for (y in -radius..radius) {
                for (z in -radius..radius) {
                    val pos = origin.add(x, y, z)
                    if (furnaceNeedsFuel(world, pos)) {
                        val dist = pos.getSquaredDistance(origin)
                        if (dist < bestDist) {
                            bestDist = dist
                            best = pos.toImmutable()
                        }
                    }
                }
            }
        }
        return best
    }

    private fun furnaceNeedsFuel(world: World, pos: BlockPos): Boolean {
        val block = world.getBlockState(pos).block
        if (block !is AbstractFurnaceBlock) return false
        val blockEntity = world.getBlockEntity(pos)
        if (blockEntity !is AbstractFurnaceBlockEntity) return false
        // Furnace needs fuel if it has items to smelt AND is NOT already burning
        val hasInput = !blockEntity.getStack(0).isEmpty
        val nbt = blockEntity.createNbt(world.registryManager)
        val burnTime = nbt.getShort("BurnTime").toInt()
        return hasInput && burnTime <= 0
    }

    private fun addFuel(world: World, pos: BlockPos) {
        val blockEntity = world.getBlockEntity(pos) as? AbstractFurnaceBlockEntity ?: return
        // Add burn time by setting the fuel properties via NBT manipulation
        // AbstractFurnaceBlockEntity stores burnTime and fuelTime fields
        // We use reflection-free approach: put a coal equivalent burn time
        val nbt = blockEntity.createNbt(world.registryManager)
        val currentBurnTime = nbt.getShort("BurnTime").toInt()
        nbt.putShort("BurnTime", (currentBurnTime + BURN_TIME_ADDED).toShort())
        if (nbt.getShort("CookTimeTotal").toInt() == 0) {
            nbt.putShort("CookTimeTotal", 200.toShort())
        }
        blockEntity.read(nbt, world.registryManager)
        blockEntity.markDirty()
        // Update the lit state of the furnace block
        val state = world.getBlockState(pos)
        if (state.contains(AbstractFurnaceBlock.LIT)) {
            world.setBlockState(pos, state.with(AbstractFurnaceBlock.LIT, true), 3)
        }
    }



}
