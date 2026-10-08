package dev.evvie.waylandcraft.bridge;

public class WindowRequestHandlers {
	
	public MaximizeRequestHandler maximizeHandler = null;
	public UnmaximizeRequestHandler unmaximizeHandler = null;
	public FullscreenRequestHandler fullscreenHandler = null;
	public UnfullscreenRequestHandler unfullscreenHandler = null;
	public MinimizeRequestHandler minimizeHandler = null;
	public MoveRequestHandler moveHandler = null;
	public ResizeRequestHandler resizeHandler = null;
	public DNDRequestHandler dndHandler = null;
	
	@FunctionalInterface
	public static interface MaximizeRequestHandler {
		
		void onMaximizeRequest(WLCToplevel toplevel);
		
	}
	
	@FunctionalInterface
	public static interface UnmaximizeRequestHandler {
		
		void onUnmaximizeRequest(WLCToplevel toplevel);
		
	}
	
	@FunctionalInterface
	public static interface FullscreenRequestHandler {
		
		void onFullscreenRequest(WLCToplevel toplevel);
		
	}
	
	@FunctionalInterface
	public static interface UnfullscreenRequestHandler {
		
		void onUnfullscreenRequest(WLCToplevel toplevel);
		
	}
	
	@FunctionalInterface
	public static interface MinimizeRequestHandler {
		
		void onMinimizeRequest(WLCToplevel toplevel);
		
	}
	
	@FunctionalInterface
	public static interface MoveRequestHandler {
		
		void onMoveRequest(WLCToplevel toplevel, int serial);
		
	}
	
	@FunctionalInterface
	public static interface ResizeRequestHandler {
		
		void onResizeRequest(WLCToplevel toplevel, int serial, int edges);
		
	}
	
	@FunctionalInterface
	public static interface DNDRequestHandler {
		
		void onDNDRequest(WLCToplevel toplevel, int serial);
		
	}
	
}
