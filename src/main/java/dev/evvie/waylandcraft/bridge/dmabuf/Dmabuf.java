package dev.evvie.waylandcraft.bridge.dmabuf;

import java.util.function.Consumer;

public record Dmabuf(long handle, int width, int height, int format, long modifier, DmabufPlane[] planes) {
	
	public void debugPrint() {
		debugPrint(System.out::println);
	}
	
	public void debugPrint(Consumer<String> printFunc) {
		printFunc.accept("DMABUF");
		printFunc.accept(String.format(" handle: 0x%016X", handle()));
		printFunc.accept(String.format(" width: %d", width()));
		printFunc.accept(String.format(" height: %d", height()));
		printFunc.accept(String.format(" format: 0x%X" , format()));
		printFunc.accept(String.format(" modifier: 0x%016X" , modifier()));
		
		for(int i = 0; i < planes().length; i++) {
			DmabufPlane plane = planes()[i];
			printFunc.accept(String.format(" PLANE %d", i));
			printFunc.accept(String.format("  fd: %d", plane.fd()));
			printFunc.accept(String.format("  offset: %d", plane.offset()));
			printFunc.accept(String.format("  stride: %d", plane.stride()));
		}
	}
	
}
