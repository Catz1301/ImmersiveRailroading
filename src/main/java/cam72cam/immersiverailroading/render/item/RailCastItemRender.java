package cam72cam.immersiverailroading.render.item;

import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.mod.model.obj.OBJModel;
import cam72cam.mod.render.ItemRender;
import cam72cam.mod.render.obj.OBJRender;
import cam72cam.mod.render.GLBoolTracker;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.render.StandardModel;
import cam72cam.mod.resource.Identifier;
import cam72cam.mod.world.World;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

public class RailCastItemRender implements ItemRender.IItemModel {
	private static OBJRender model;
	private static List<String> groups;

	@Override
	public StandardModel getModel(World world, ItemStack stack) {
		if (model == null) {
			try {
				model = new OBJRender(new OBJModel(new Identifier(ImmersiveRailroading.MODID, "models/multiblocks/rail_machine.obj"), 0.05f));
				groups = new ArrayList<>();

				for (String groupName : model.model.groups())  {
					if (groupName.contains("INPUT_CAST")) {
						groups.add(groupName);
					}
				}
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}


		return new StandardModel().addCustom(() -> {
			GL11.glPushMatrix();
			{
				GLBoolTracker tex = new GLBoolTracker(GL11.GL_TEXTURE_2D, true);
				model.bindTexture();
				GL11.glRotated(90, 1, 0, 0);
				GL11.glTranslated(0, -1, 1);
				GL11.glTranslated(-0.5, 0.6, 0.6);
				model.drawGroups(groups);
				model.restoreTexture();
				tex.restore();
			}
			GL11.glPopMatrix();
		});
	}

	@Override
	public void applyTransform(ItemRender.ItemRenderType type) {
		ItemRender.IItemModel.defaultTransform(type);

		if (type == ItemRender.ItemRenderType.GUI) {
			GL11.glScaled(1, 0.1, 1);
		}
	}
}
