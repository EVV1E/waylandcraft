package dev.evvie.waylandcraft.egl;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;

import dev.evvie.waylandcraft.bridge.dmabuf.Dmabuf;
import dev.evvie.waylandcraft.bridge.dmabuf.DmabufFormat;
import dev.evvie.waylandcraft.bridge.dmabuf.DmabufPlane;

public class EGLHelper {
	
	// Get the active DRM render node of the EGLDisplay.
	// May return null if the EXT_device_drm_render_node egl extension is not supported or the device is not backed by a render node
	public static @Nullable String queryRenderNodePath(long display) {
		String renderNodePath;
		try(MemoryStack stack = MemoryStack.stackPush()) {
			PointerBuffer deviceRet = stack.callocPointer(1);
			if(!EGL.eglQueryDisplayAttribEXT(display, EGL.EGL_DEVICE_EXT, deviceRet)) return null;
			
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
	
	private static final long[] PLANE_FD_ATTR = {
			EGL.EGL_DMA_BUF_PLANE0_FD_EXT,
			EGL.EGL_DMA_BUF_PLANE1_FD_EXT,
			EGL.EGL_DMA_BUF_PLANE2_FD_EXT,
			EGL.EGL_DMA_BUF_PLANE3_FD_EXT
	};
	private static final long[] PLANE_OFFSET_ATTR = {
			EGL.EGL_DMA_BUF_PLANE0_OFFSET_EXT,
			EGL.EGL_DMA_BUF_PLANE1_OFFSET_EXT,
			EGL.EGL_DMA_BUF_PLANE2_OFFSET_EXT,
			EGL.EGL_DMA_BUF_PLANE3_OFFSET_EXT
	};
	private static final long[] PLANE_PITCH_ATTR = {
			EGL.EGL_DMA_BUF_PLANE0_PITCH_EXT,
			EGL.EGL_DMA_BUF_PLANE1_PITCH_EXT,
			EGL.EGL_DMA_BUF_PLANE2_PITCH_EXT,
			EGL.EGL_DMA_BUF_PLANE3_PITCH_EXT
	};
	private static final long[] PLANE_MOD_LO_ATTR = {
			EGL.EGL_DMA_BUF_PLANE0_MODIFIER_LO_EXT,
			EGL.EGL_DMA_BUF_PLANE1_MODIFIER_LO_EXT,
			EGL.EGL_DMA_BUF_PLANE2_MODIFIER_LO_EXT,
			EGL.EGL_DMA_BUF_PLANE3_MODIFIER_LO_EXT
	};
	private static final long[] PLANE_MOD_HI_ATTR = {
			EGL.EGL_DMA_BUF_PLANE0_MODIFIER_HI_EXT,
			EGL.EGL_DMA_BUF_PLANE1_MODIFIER_HI_EXT,
			EGL.EGL_DMA_BUF_PLANE2_MODIFIER_HI_EXT,
			EGL.EGL_DMA_BUF_PLANE3_MODIFIER_HI_EXT
	};
	
	private static final long U32_MAX = 0xffffffffL;
	
	public static long importDmabufToImage(long display, Dmabuf dmabuf) {
		EGLAttribList attribList = new EGLAttribList();
		attribList.add(EGL.EGL_WIDTH, dmabuf.width());
		attribList.add(EGL.EGL_HEIGHT, dmabuf.height());
		attribList.add(EGL.EGL_LINUX_DRM_FOURCC_EXT, dmabuf.format());
		
		long modifier = dmabuf.modifier();
		long modLo = (modifier & U32_MAX);
		long modHi = (modifier >>> 32);
		boolean hasModifier = modifier != DmabufFormat.MODIFIER_LINEAR && modifier != DmabufFormat.MODIFIER_INVALID;
		
		DmabufPlane[] planes = dmabuf.planes();
		for(int i = 0; i < planes.length; i++) {
			DmabufPlane plane = planes[i];
			attribList.add(PLANE_FD_ATTR[i], plane.fd());
			attribList.add(PLANE_OFFSET_ATTR[i], plane.offset());
			attribList.add(PLANE_PITCH_ATTR[i], plane.stride());
			
			if(hasModifier) {
				attribList.add(PLANE_MOD_LO_ATTR[i], modLo);
				attribList.add(PLANE_MOD_HI_ATTR[i], modHi);
			}
		}
		
		PointerBuffer attribListBuf = attribList.build();
		return EGL.eglCreateImage(display, EGL.EGL_NO_CONTEXT, (int) EGL.EGL_LINUX_DMA_BUF_EXT, EGL.NULL, attribListBuf);
	}
	
}
