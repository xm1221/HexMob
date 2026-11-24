package pub.pigeon.yggdyy.hexmob.content.amethyst_silverfish

import net.minecraft.client.model.HierarchicalModel
import net.minecraft.client.model.geom.ModelLayerLocation
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.model.geom.PartPose
import net.minecraft.client.model.geom.builders.CubeDeformation
import net.minecraft.client.model.geom.builders.CubeListBuilder
import net.minecraft.client.model.geom.builders.LayerDefinition
import net.minecraft.client.model.geom.builders.MeshDefinition
import net.minecraft.util.Mth
import pub.pigeon.yggdyy.hexmob.HexMob

class AmethystSilverfishModel(private val root: ModelPart) : HierarchicalModel<AmethystSilverfishEntity>() {
    private val body: ModelPart = root.getChild("body")
    private val head: ModelPart = body.getChild("head")
    private val tail: ModelPart = body.getChild("tail")

    override fun root(): ModelPart = root

    override fun setupAnim(
        entity: AmethystSilverfishEntity,
        limbSwing: Float,
        limbSwingAmount: Float,
        ageInTicks: Float,
        netHeadYaw: Float,
        headPitch: Float,
    ) {
        val ageSec = ageInTicks / 20f
        if (entity.isFighting()) {
            val phase = ageSec * (2f * Math.PI.toFloat() / 1.5f)
            head.yRot = Mth.sin(phase) * (15f * Mth.DEG_TO_RAD)
            tail.yRot = -Mth.sin(phase) * (25f * Mth.DEG_TO_RAD)
        } else {
            val phase = ageSec * (2f * Math.PI.toFloat() / 2.0f)
            head.yRot = 0f
            tail.yRot = -Mth.sin(phase) * (15f * Mth.DEG_TO_RAD)
        }
    }

    companion object {
        val LAYER_LOCATION = ModelLayerLocation(HexMob.id("amethyst_silverfish"), "main")

        fun createBodyLayer(): LayerDefinition {
            val mesh = MeshDefinition()
            val partDefinition = mesh.root
            val body = partDefinition.addOrReplaceChild(
                "body",
                CubeListBuilder.create()
                    .texOffs(0, 0).addBox(-1f, -1f, -1f, 2f, 2f, 3f, CubeDeformation.NONE)
                    .texOffs(8, 5).addBox(-2f, -2f, -1f, 4f, 3f, 0f, CubeDeformation.NONE)
                    .texOffs(8, 8).addBox(-2f, -2f, 0f, 4f, 3f, 0f, CubeDeformation.NONE)
                    .texOffs(0, 9).addBox(-2f, -2f, 1f, 4f, 3f, 0f, CubeDeformation.NONE)
                    .texOffs(10, 3).addBox(-1f, 0f, 2f, 2f, 1f, 1f, CubeDeformation.NONE),
                PartPose.offset(0f, 23f, 0f),
            )
            body.addOrReplaceChild(
                "head",
                CubeListBuilder.create()
                    .texOffs(10, 0).addBox(-2f, -2f, -1.5f, 4f, 3f, 0f, CubeDeformation.NONE)
                    .texOffs(0, 5).addBox(-1f, -1f, -2.5f, 2f, 2f, 2f, CubeDeformation.NONE),
                PartPose.offset(0f, 0f, -0.5f),
            )
            body.addOrReplaceChild(
                "tail",
                CubeListBuilder.create()
                    .texOffs(8, 11).addBox(0f, -1f, 1f, 0f, 2f, 3f, CubeDeformation.NONE),
                PartPose.offset(0f, 0f, 1f),
            )
            return LayerDefinition.create(mesh, 32, 32)
        }
    }
}
