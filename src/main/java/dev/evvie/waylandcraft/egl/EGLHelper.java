package dev.evvie.waylandcraft.egl;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;

import dev.evvie.waylandcraft.bridge.dmabuf.DmabufFormat;

public class EGLHelper {
	
	public static String queryRenderNodePath(long display) {
		String renderNodePath;
		try(MemoryStack stack = MemoryStack.stackPush()) {
			PointerBuffer deviceRet = stack.callocPointer(1);
			EGL.eglQueryDisplayAttribEXT(display, EGL.EGL_DEVICE_EXT, deviceRet);
			
			long device = deviceRet.get(0);
			renderNodePath = EGL.eglQueryDeviceStringEXT(device, EGL.EGL_DRM_RENDER_NODE_FILE_EXT);
		}
		return renderNodePath;
	}
	
	public static ArrayList<DmabufFormat> queryDmabufFormats(long display) {
		ArrayList<Integer> codes = queryDmabufFormatCodes(display);
		ArrayList<DmabufFormat> formats = new ArrayList<DmabufFormat>();
		
		for(int code : codes) {
			formats.add(new DmabufFormat(code, DmabufFormat.MODIFIER_INVALID));
		}
		
		for(int code : codes) {
			ArrayList<Long> modifiers = queryDmabufFormatModifiers(display, code);
			formats.addAll(modifiers.stream().map((m) -> new DmabufFormat(code, m)).toList());
		}
		
		return formats;
	}
	
	public static ArrayList<Integer> queryDmabufFormatCodes(long display) {
		ArrayList<Integer> codes = new ArrayList<Integer>();
		int count;
		
		try(MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer countRet = stack.callocInt(1);
			EGL.eglQueryDmaBufFormatsEXT(display, 0, null, countRet);
			count = countRet.get(0);
			
			IntBuffer codesBuf = stack.callocInt(count);
			EGL.eglQueryDmaBufFormatsEXT(display, count, codesBuf, countRet);
			count = countRet.get(0);
			
			for(int i = 0; i < count; i++) {
				codes.add(codesBuf.get());
			}
		}
		
		return codes;
	}
	
	public static ArrayList<Long> queryDmabufFormatModifiers(long display, int format) {
		ArrayList<Long> modifiers = new ArrayList<Long>();
		int count;
		
		try(MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer countRet = stack.callocInt(1);
			EGL.eglQueryDmaBufModifiersEXT(display, format, 0, null, null, countRet);
			count = countRet.get(0);
			
			LongBuffer modifiersBuf = stack.callocLong(count);
			IntBuffer externalOnlyBuf = stack.callocInt(count);
			EGL.eglQueryDmaBufModifiersEXT(display, format, count, modifiersBuf, externalOnlyBuf, countRet);
			count = countRet.get(0);
			
			for(int i = 0; i < count; i++) {
				long m = modifiersBuf.get();
				int e = externalOnlyBuf.get();
				
				if(e != EGL.EGL_TRUE) {
					modifiers.add(m);
				}
			}
		}
		
		return modifiers;
	}
	
}
