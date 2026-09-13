package dev.evvie.waylandcraft.egl;

import java.util.ArrayList;

import org.lwjgl.PointerBuffer;

public class EGLAttribList {
	
	private final ArrayList<AttribPair> pairs;
	
	public EGLAttribList() {
		pairs = new ArrayList<AttribPair>();
	}
	
	public void add(long key, long value) {
		pairs.add(new AttribPair(key, value));
	}
	
	public PointerBuffer build() {
		AttribPair[] array = pairs.toArray(AttribPair[]::new);
		long[] values = new long[array.length * 2 + 1];
		for(int i = 0; i < array.length; i++) {
			values[i * 2 + 0] = array[i].key;
			values[i * 2 + 1] = array[i].value;
		}
		values[array.length * 2] = EGL.EGL_NONE;
		
		PointerBuffer buffer = PointerBuffer.allocateDirect(values.length);
		buffer.put(values);
		buffer.rewind();
		
		return buffer;
	}
	
	private static record AttribPair(long key, long value) {}
	
}
