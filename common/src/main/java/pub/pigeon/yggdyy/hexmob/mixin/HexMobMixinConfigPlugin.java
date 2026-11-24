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
     * Refmap 按「运行期命名环境」三分支选择，而不是按平台二分：
     *
     * <ul>
     *   <li><b>Fabric（Knot）</b> → {@code hexmob-common.refmap.json}：jar 里这份是 Loom 生成的
     *       {@code named:intermediary}（{@code class_4766} / {@code method_*}）。</li>
     *   <li><b>Forge 生产</b> → {@code hexmob-forge-prod.refmap.json}：手写的 {@code named:searge} 表
     *       （Loom 生成的 SRG 表 + Loom 生成不出来的两个 {@code @Shadow} 项合成）。Forge 生产运行期
     *       用 SRG 成员名，缺这张表 {@code @Inject} / {@code @Shadow} 找不到目标，
     *       {@code defaultRequire = 1} 会直接崩启动。</li>
     *   <li><b>Forge dev（userdev）与未知环境</b> → 空的 {@code hexmob-forge.refmap.json}：
     *       dev 运行期是 mojmap 名，空表让 Mixin 直接用字节码里的字面名，正好对得上。
     *       dev classpath 上的 {@code hexmob-common.refmap.json} 是 Fabric intermediary，
     *       用它会把 owner 改写成 Yarn 类名（{@code class_310}）而失败。</li>
     * </ul>
     *
     * <p>Architectury common 依赖 fabric-loader，所以 {@code Knot.class} 可见 ≠ 在 Fabric 上；
     * 这里只看 {@code sun.java.command}（启动命令行）：dev 的 launchTarget 是
     * {@code forgeclientuserdev} / {@code forgeserveruserdev}，生产是 {@code forgeclient} / {@code forgeserver}。
     */
    @Override
    public String getRefMapperConfig() {
        String cmd = System.getProperty("sun.java.command", "");
        if (cmd.contains("Knot") || cmd.contains("fabric.loader")) {
            return "hexmob-common.refmap.json";
        }
        if ((cmd.contains("forgeclient") || cmd.contains("forgeserver")) && !cmd.contains("userdev")) {
            return "hexmob-forge-prod.refmap.json";
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
