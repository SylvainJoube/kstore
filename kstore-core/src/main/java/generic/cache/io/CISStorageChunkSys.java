package generic.cache.io;


/** 
 * Not intended for end-used, this is a package-private class.</br>
 * 
 * A simple data chunk owned by an instance of CISStorageSys.
 * 
 * @author Sylvian Joube
 * @version 2020-05 v1
 *
 */
class CISStorageChunkSys {
	
	// The position in the file of the first byte of this block
	protected final int blockPosition;
	protected final byte[] blockData;
	
	public CISStorageChunkSys(int blockPosition, byte[] data) {
		this.blockPosition = blockPosition;
		blockData = data;
	}

	public int getBlockPosition() {
		return blockPosition;
	}

	public byte[] getBlockData() {
		return blockData;
	}

	public int getLength() {
		return blockData.length;
	}
	
	public int length() {
		return blockData.length;
	}
	
}
