package dev.evvie.waylandcraft.utils;

public class TwoBitElementArray {
	
	/* Utility for bit arrays with an element size of two bits
	 * 
	 * The array is of a fixed-size and by default zero-initialized like a normal java array.
	 * 
	 * [a a b b c c d d] [e e f f g g h h]
	 * <-   byte 1    -> <-   byte 2    ->
	 */
	
	private final int count; // Amount of elements
	private byte[] data;
	
	public TwoBitElementArray(int count) {
		this.count = count;
		this.data = new byte[bytesForElements(count)];
	}
	
	public TwoBitElementArray(int count, byte[] backing) {
		if(backing.length < bytesForElements(count)) throw new IllegalArgumentException("Backing array is too small!");
		
		this.count = count;
		this.data = backing;
	}
	
	public static int bytesForElements(int count) {
		return Math.ceilDiv(count * 2, 8);
	}
	
	public void put(int idx, byte elem) {
		if(idx < 0 || idx >= count) throw new ArrayIndexOutOfBoundsException();
		
		// Truncate to two bits
		elem = (byte) (elem & 0x3);
		
		// Shift element into byte position
		int shift = (0x3 - (idx & 0x3)) * 2;
		byte s = (byte) (elem << shift);
		
		// OR it into the correct byte
		data[idx >> 2] |= s;
	}
	
	public byte get(int idx) {
		if(idx < 0 || idx >= count) throw new ArrayIndexOutOfBoundsException();
		
		// Shift amount for given index
		int shift = (0x3 - (idx & 0x3)) * 2;
		
		// Retrieve byte
		byte b = data[idx >> 2];
		
		// Shift the byte by the given amount and mask out only the two relevant bits
		return (byte) ((b >> shift) & 0x3);
	}
	
	public byte[] getData() {
		return data;
	}
	
}
