package generic.cache.io;

import java.io.FilterInputStream;
import java.io.IOException;

/**
 * Please use CachedOutputStream if you use CachedInputStream, for synchronization reasons.
 * 
 * Requests on this stream should behave exactly the same as with any other InputStream.</br></br>
 * 
 * A CachedInputStream is basically an InputStream decorator, except (and this is very important)
 * it doesn't need any already created InputStream. The associated InputStream is only created
 * if no cache is found to have the "unique URI" passed on construction.</br></br>
 * 
 * UniqueURI should be the unique identifier of the ressource we'd like to access.</br>
 * 
 * CachedInputStream extends FilterInputStream, only for the functions names and does not use
 * the "in" variable of FilterInputStream.</br>
 * 
 * An instance of this class is not designed to be used by multiples threads at the same time.
 * If you need to access the same ressource, but from another thread,
 * consider creating another CachedInputStream. The underlying implementation, howerever, is thread-safe.
 * 
 * @author Sylvain Joube
 * @version 2020-05 v1
 * 
 */
public class CachedInputStream extends FilterInputStream {
	
	/** Many CachedInputStream objects may refer to the same CachedInputStreamSys.
	 *  CachedInputStream is just a decoy, and forwards all requests to the underlying CachedInputStreamSys,
	 *  registered on InputStreamCacheController */
	protected CachedInputStreamSys cache;
	
	
	/** The current position in the stream, for this instance of CachedInputStream. */
	protected CurReadPos readPos = new CurReadPos();
	
	/** ONLY FOR JUNIT TESTS, this variable should NEVER be true on production.
	    Simulates a long wait before any data is available on the underlying InputStream.
	    In the tests, checks whether CachedInputStream really blocks while waiting for more data. */
	protected static boolean testOnly_sleepOnLoadAsync = false; // <- /!\ ON PRODUCTION, MUST BE FALSE /!\ <-
	
	/** Creates a new CacheInputStream. Searches for the cache associated with the uniqueURI.
	 *  If no cache is found, the optionalConstructor is called and the InputStream is created.</br>
	 *  
	 *  Blocks until the URI can be taken. (supports many readers at diffrent offsets, but only one writer at a time).</br>
	 *  
	 *  The cache loads the whole InputStream, asychronously.
	 *  All the queries on the loaded part are immediate,  the other queries waits until enough of the file is loaded.
	 *  
	 * @param uniqueURI  The URI (Uniform ressource Ientifier) associated with this file.
	 *                   It should be unique, and only refer to this file.
	 * @param optionalConstructor  If no cache is found, the optionalConstructor is called and the InputStream is created.
	 * @throws IOException
	 */
	public CachedInputStream(String uniqueURI, OptionalInputStreamConstructor optionalConstructor) throws IOException {
		// If data is in cache, don't use the constructor
		super(null);
		cache = CachedStreamController.createOrFindInput(uniqueURI, optionalConstructor);
	}
	
	@Override
	public int read() throws IOException {
		return cache.read(readPos);
	}
	
	@Override
	public int read(byte b[]) throws IOException {
		return cache.read(b, readPos);
	}
	@Override
	public int read(byte b[], int off, int len) throws IOException {
		return cache.read(b, off, len, readPos);
	}
	
	@Override
	public long skip(long n) throws IOException {
		return cache.skip((int)n, readPos);
	}
	
	@Override
	public int available() throws IOException {
		return cache.available(readPos);
	}
	
	@Override
	public boolean equals(Object o) {
		return (o == this);
	}
	
    /** Closes this input stream and releases any system resources associated with the stream.
     *  Wakes any thread waiting on CachedOutputStream assoiated with the same URI as this cache. 
     *
     * @exception  IOException  if an I/O error occurs.
     * @see        java.io.FilterInputStream#in
     */
	@Override
	public void close() {
		cache.closeInstance();
	}

	/** CURRENTLY UNSUPPORTED OPERATION (will be in the future) */
	@Override
	public synchronized void mark(int readlimit) {
		cache.mark(readlimit);
	}
	
	/** CURRENTLY UNSUPPORTED OPERATION (will be in the future) */
	@Override
	public synchronized void reset() {
		cache.reset();
	}

	/** CURRENTLY UNSUPPORTED OPERATION (will be in the future) */
	@Override
	public boolean markSupported() {
		return cache.markSupported();
	}
	
	/** Mostly for debug, to check than many CachedInputStream with the same URI points to the same
	 *  underlying cache.
	 *  @return
	 */
	public String getUnderlyingCacheAsString() {
		return cache.toString();
	}
	
	
	/** Enters test mode, checks whether the CachedInputStream realy blocs on reads,
	    when the underlying InputStream is not at EOF and waits for mode data.
	 */
	public static void enterTestAsyncMode() {
		testOnly_sleepOnLoadAsync = true;
	}
	
	/** Leaves test mode for asynchronous load and reads. */
	public static void leaveTestAsyncMode() {
		testOnly_sleepOnLoadAsync = false;
	}
	
	/** Gets the number of caches pointing to the same opened URI */
	public int getUnderlyingRefCount() {
		return cache.getRefCount();
	}
	
}
