package generic.cache.io;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Not intended for end-used, this is a package-private class.</br>
 * 
 * The system implementation of CachedInputStream. This class is only accessible from
 * this package, and is not intended for the final user to use. </br></br>
 * 
 * The end-user should use a CachedInputStream as a decorator of any InputStream.
 * Each CachedInputStream sh
 * 
 * Does not support file sizes superior to 2Gbyte.
 * 
 * @author Syvain Joube
 * @version 2020-05 v1
 * 
 */
class CachedInputStreamSys implements Comparable<CachedInputStreamSys> {
	
	protected final OptionalInputStreamConstructor optionalConstructor;
	protected final String uniqueURI;

	protected static final boolean asyncLoad = false;
	
	/** This CachedInputStream supports non-blocking reads (returns 0 when no data is available)
	    but a normal InputStream is blocking. */
	protected static final boolean normalBlockingInputStream = true;
	
	/** TODO : Once the file is fully loaded, it can be flushed on disk.
	    A possible improvment would be to save the file locally while it's being loaded. */
	protected static final AtomicBoolean currentlySavingOnDisk = new AtomicBoolean(false);

	/** TODO : This cache can be loaded onmemory from the local storage save. */
	protected static final AtomicBoolean currentlyLoadingFromLocalStorage = new AtomicBoolean(false);
	
	/** TODO : By default, a cache is only on memory. But it can be only on disk. */
	protected static final AtomicBoolean onlyOnDisk = new AtomicBoolean(false);
	
	/** Associated storage */
	protected CISStorageSys storage = null;
	
	/** Real data size, stored by this cache*/
	//protected int realDataSize = 0;
	
	/** Last time this cache was accessed (milliseconds since 1970) */
	protected AtomicLong lastAccess = new AtomicLong(0);
	
	/** There is only one "real" cache per loaded URI. refCount registers the number of CachedInputStream
	 * actually refering to this object. This is important for the LRU algorithm of InputStreamCacheController,
	 * a cache should never be disposed of if the refCount isn't null. */
	protected AtomicInteger refCount = new AtomicInteger(0);
	
	// I may add the (int medium) parameter to avoid ID collisions.
	//InputStream in
	public CachedInputStreamSys(String uniqueURI, OptionalInputStreamConstructor optionalConstructor) throws IOException {
		// If data is in cache, don't use the constructor
		this.uniqueURI = uniqueURI;
		this.optionalConstructor = optionalConstructor;
		
		loadFromInputStream(asyncLoad);
		// If we are still here, that means we have to load the whole file.
		//storeWholeRessourceSynchronous();
	}
	
	/** 
	 *  @return the unique uniform ressource identifier associated with ths cache.
	 */
	public String getUniqueURI() {
		return uniqueURI;
	}
	
	public void loadFromInputStream(boolean blocking) throws IOException {
		if (storage != null) return; // already loaded
		storage = new CISStorageSys();
		if (blocking) {
			storage.loadRessourceBlocking(optionalConstructor);
		} else {
			storage.loadRessourceAsync(optionalConstructor);
		}
	}
	
	
	/** Only use this function when you only have one byte to read, do NOT use for multiple calls,
	 *  as performance will be very disapointing. Use read(byte[], int, int) instead, or read(byte[]).
	 * @param readPos
	 * @return
	 * @throws IOException
	 */
	protected int read(CurReadPos readPos) throws IOException {
		
		byte[] b = new byte[1];
		
		int realReadLen = storage.read(b, 0, 1, readPos.pos, true);
		if (realReadLen == -1) return -1;
		return b[0];
		
		/*
		if (readPos.pos >= realDataSize) return -1; // EOF
		if (realReadLen > 0) readPos.preInc(realReadLen);
		
		return data[readPos.postInc(1)];*/
	}
	
	
	public int read(byte b[], CurReadPos readPos) throws IOException {
		return read(b, 0, b.length, readPos);
	}
	
	protected int read(byte b[], int off, int len, CurReadPos readPos) throws IOException {
		int realReadLen = storage.read(b, off, len, readPos.pos, normalBlockingInputStream);
		if (realReadLen > 0) readPos.preInc(realReadLen);
		
		return realReadLen;
	}
	
	
	public int skip(int len, CurReadPos readPos) throws IOException {
		int realSkipLen = readPos.getPossibleReadLen(len, storage.length());
		readPos.preInc(realSkipLen);
		return realSkipLen;
	}
	
	public int available(CurReadPos readPos) throws IOException {
		return storage.length() - readPos.pos;
		//return in.available();
	}


	protected void updateLastUsed() {
		lastAccess.set(System.currentTimeMillis());
		CachedStreamController.updateLastUsedInput(this);
	}

	protected long getLastAccess() {
		return lastAccess.get();
	}
	

	@Override
	public int compareTo(CachedInputStreamSys o) {
		if (o == null) return -1;
		
		// Supposing the time diffrence won't be more than (about) 24 days.
		return (int) (getLastAccess() - o.getLastAccess());
	}
	
	@Override
	public boolean equals(Object o) {
		return (o == this);
	}
	
	
	public int getRefCount() {
		return refCount.get();
	}
	
	public void closeInstance() {
		if (refCount.decrementAndGet() == 0) {
			CachedStreamController.wakeLocked();
		}
		//DebugLog.info("closeInstance " + refCount);
	}
	
	/** Closes this cache, frees the storage.
	 *  Blocks until the underlying InputStream is closed.
	 */
	protected void closeCache() {
		storage.free();
	}

	/** UNSUPPORTED OPERATION (will be in the future) */
	public synchronized void mark(int readlimit) {
		//in.mark(readlimit);
	}
	
	/** UNSUPPORTED OPERATION (will be in the future) */
	public synchronized void reset() {
		//in.reset();
	}

	/** UNSUPPORTED OPERATION (will be in the future) */
	public boolean markSupported() {
		return false; //in.markSupported();
	}
	
	// TODO
	/*public void saveOnDisk() {
		synchronized(currentlySavingOnDisk) {
			
		}
	}*/
	
}
