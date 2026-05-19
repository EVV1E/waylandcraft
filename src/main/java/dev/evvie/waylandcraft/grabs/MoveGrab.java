package dev.evvie.waylandcraft.grabs;

import dev.evvie.waylandcraft.CursorShape;
import dev.evvie.waylandcraft.WindowDisplay;
import dev.evvie.waylandcraft.grabs.PointerGrabMap.ImplicitGrab;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class MoveGrab extends PointerGrab {

	private final WindowDisplay window;
	private final Vec3 initialSurfaceLocal;
	private Vec3 lPos = Vec3.ZERO;
	private Vec3 lView = Vec3.ZERO;
	private Vec3 lUp = Vec3.ZERO;
	private float rotationAngle = 0.0f;
	private boolean[] lastRotateState = {false, false}; 
	
	public MoveGrab(ImplicitGrab implicit) {
		super(implicit.button());
		this.window = implicit.window();
		this.initialSurfaceLocal = implicit.startSurfaceLocal();
	}

	@Override
	public void init() throws GrabDroppedException {
		rotationAngle = 0.0f;
		lastRotateState[0] = false;
		lastRotateState[1] = false;
	}

	@Override
	public void release(boolean force) throws GrabDroppedException {
		window.anchorToPosView(lPos, lView, lUp);
	}

	@Override
	public void moveWorld(Vec3 pos, Vec3 view, Vec3 up) throws GrabDroppedException {
		if(!window.isValid()) this.drop();
		wlc.cursorShape = CursorShape.ALL_RESIZE;
		if (Minecraft.getInstance().options.keyShift.isDown()) {
			if (wlc.keyRotateWindowLeft.isDown() && !lastRotateState[0]) {
				rotationAngle -= 45.0f;
			}
			if (wlc.keyRotateWindowRight.isDown() && !lastRotateState[1]) {
				rotationAngle += 45.0f;
			}
		} 
		lastRotateState[0] = wlc.keyRotateWindowLeft.isDown();
		lastRotateState[1] = wlc.keyRotateWindowRight.isDown();
		Vec3 to = pos.add(view.scale(Minecraft.getInstance().player.blockInteractionRange()*2));
		BlockHitResult blockHit = Minecraft.getInstance().level.clip(
			new ClipContext(
				pos,
				to, 
				ClipContext.Block.COLLIDER, 
				ClipContext.Fluid.NONE, 
				Minecraft.getInstance().player
			)
		);
		if (Minecraft.getInstance().options.keyShift.isDown()) {
			if(blockHit.getType() == BlockHitResult.Type.BLOCK) {
				Vec3 faceDir = new Vec3(blockHit.getDirection().getStepX(), blockHit.getDirection().getStepY(), blockHit.getDirection().getStepZ());
				lPos = blockHit.getBlockPos().getCenter().add(faceDir.scale(2.55));
				lView = faceDir.reverse();

				if (Math.abs(faceDir.y) > 0.5) { // is the window facing up or down
					lUp = new Vec3(Math.sin(Math.toRadians(rotationAngle)), 0, Math.cos(Math.toRadians(rotationAngle)));
				} else {
					lUp = new Vec3(0, 1, 0);
					rotationAngle = 0.0f;
				}

				window.anchorToPosView(lPos, lView, lUp);
				return;
			} else {
				this.init();
				lPos = pos.add(view.scale(1));
				lView = view; 
				lUp = up;

				window.anchorToPosView(lPos, lView, lUp);
			}
		} else {
			this.init();
			lPos = pos.add(view.scale(1));
			lView = view;
			lUp = up;

			window.anchorToPosView(lPos, lView, lUp);
		}
	}

}
