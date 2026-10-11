package pub.pigeon.yggdyy.hexmob.mixin;

import pub.pigeon.yggdyy.hexmob.HexMob;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

// disable MixinDatagenMain if we're not running the datagen task, since it's not necessary at any other time
public class HexMobMixinConfigPlugin implements IMixinConfigPlugin {
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.equals("pub.pigeon.yggdyy.hexmob.mixin.MixinDatagenMain")) {
            var shouldApply = System.getProperty("hexmob.apply-datagen-mixin", "false").equals("true");
            if (shouldApply) {
                HexMob.LOGGER.warn("Applying datagen mixin to {}. This should not happen if not running datagen!", targetClassName);
            }
            return shouldApply;
        }
        return true;
    }

    @Override
    public void onLoad(String mixinPackage) {}

    /**
     * Common mixins.json points at {@code hexmob-common.refmap.json}, which Loom
     * generates as {@code named:intermediary} ({@code class_310}, {@code method_*}).
     * That is correct for Fabric, but Forge 1.20.1 runs Minecraft under official/SRG
     * names. Applying the intermediary refmap rewrites inject owners to Yarn classes
     * (e.g. {@code net/minecraft/class_310}) and Mixin then rejects them.
     *
     * <p>On Forge, load the platform refmap instead. In {@code runClient} that file
     * is usually absent, so Mixin keeps the mojmap names already in the mixin
     * bytecode — which match the named Forge dev environment.
     */
    @Override
    public String getRefMapperConfig() {
        // Architectury common depends on fabric-loader, so Knot.class being visible
        // does NOT mean we are on Fabric. sun.java.command is the launch line:
        // Forge userdev contains "forgeclient" / "fml.forgeVersion"; Fabric Knot does not.
        String cmd = System.getProperty("sun.java.command", "");
        boolean fabricLaunch = cmd.contains("Knot") || cmd.contains("fabric.loader");
        if (fabricLaunch) {
            return "hexmob-common.refmap.json";
        }
        return "hexmob-forge.refmap.json";
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() { return null; }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
