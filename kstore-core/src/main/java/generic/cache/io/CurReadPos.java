package generic.cache.io;

/**
 * 
 *  Not intended for end-used, this is a package-private class.</br>
 *  Just some useful functions.
 * 
 *  @author Sylvain Joube
 *	@version 2020-05 v1
 */
class CurReadPos {
	public int pos = 0;
	
	
	/** Increments pos and returns the unincremented pos.
	 * @param amount  increment by
	 * @return  pos, not incremented
	 */
	public int postInc(int amount) {
		int lastPos = pos;
		pos += amount;
		return lastPos;
	}

	/** Increments pos and returns it.
	 * @param amount  increment by
	 * @return  pos, incremented
	 */
	public int preInc(int amount) {
		pos += amount;
		return pos;
	}

	/** Gets the amount of bytes that could be read. 
	 * @param readLen  amount we would like to read
	 * @param maxPos   maximum position
	 * @return  the real amount we can read. If nothing, zero.
	 */
	public int getPossibleReadLen(int readLen, int maxPos) {
		return getPossibleReadLen(pos, readLen, maxPos);
	}

	/** Gets the amount of bytes that could be read. 
	 * @param curPos   current position
	 * @param readLen  amount we would like to read
	 * @param maxPos   maximum position
	 * @return  the real amount we can read. If nothing, zero.
	 */
	public static int getPossibleReadLen(int curPos, int readLen, int maxPos) {
		int realReadLen;
		if (curPos + readLen >= maxPos) {
			realReadLen = maxPos - curPos;
		} else {
			realReadLen = readLen;
		}
		if (realReadLen < 0) realReadLen = 0;
		return realReadLen;
	}
	
}
