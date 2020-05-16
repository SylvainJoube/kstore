package generic.cache.io;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.util.Deque;
import java.util.LinkedList;


/**
 * Not intended for end-used, this is a package-private class.</br>
 * 
 * Global cache handler.
 * Associates a unique URI with a unique CachedInputStreamSys object.
 * Many CachedInputStream (for end-user) may refer to one single ressource CachedInputStreamSys (system, local).
 * 
 * Currently, this handler is not persistent.
 * Improvements to this class may consist in making it save and load the list of caches
 * from the local storage.
 * 
 * TODO : disk storage (not only memory), 
 * 
 * @author Sylvain Joube
 * @version 2020-05 v1
 */

class CachedStreamController {

	static protected Object lock = new Object();
	
	// For future LRU
	static protected volatile Deque<CachedInputStreamSys> readers = new LinkedList<>();
	static protected volatile Deque<CachedOutputStream> writers = new LinkedList<>();

	// readers waiting to be closed
	static protected volatile LinkedList<String> closingURI = new LinkedList<>();
	
	/** Returne whether a given URI is being closed (meaning stinn not available for reuse)
	 *  @param uri  unique Uniform Ressource Identifier for the file
	 *  @return  true if the URI is not yet closed (meaning it's still in the closingURI list)
	 */
	static protected boolean isClosingURI(String uri) {
		synchronized(lock) {
			for (String u : closingURI) {
				if (u.equals(uri)) {
					//DebugLog.info("cache found = " + cache);
					return true;
				}
			}
			return false;
		}
	}
	
	/** Creates or finds an  CachedInputStreamsSys. Blocks until the cache can be retreived/created.
	 *  @param uniqueURI  unique Uniform Ressource Identifier for the file
	 *  @param optionalConstructor  constructor for the underlying InputStream, only created if no reference to it already exists.
	 *  @return  a refecence to an already existing cache, or a new cache. The returned cache always is in the local (readers) list.
	 *  @throws IOException
	 */
	static protected CachedInputStreamSys createOrFindInput(String uniqueURI, OptionalInputStreamConstructor optionalConstructor) throws IOException {
		
		synchronized(lock) {
			// Blocks while a writer is using the ressource,
			// or the ressource is being closed by another reader (who was just been closed,
			// and is closing his underlying InputStream, URIs are unique in readers + writers)
			while ( (findOutput(uniqueURI) != null) || (isClosingURI(uniqueURI)) ) {
				try {
					lock.wait(); // no possible deadlock here, lock is released while I wait
				} catch (InterruptedException e) { // should not happen
					e.printStackTrace();
					throw new FileNotFoundException("Critical problem while waiting for the writer to release lock.");
				}
			}
			
			// Here, no writer can take the file before I take it, I'm still sync on lock.
			CachedInputStreamSys cache = findInput(uniqueURI);
			if (cache == null) {
				cache = new CachedInputStreamSys(uniqueURI, optionalConstructor);
				readers.add(cache);
				//DebugLog.info("new cache = " + cache);
			}
			cache.refCount.incrementAndGet();
			return cache;
		}
	}
	
	/** Get the cache associated with uniqueID or null if no cache could be found.
	 * @param uniqueURI  the uniqueID of the cache (the ressource URI (Uniform Ressource Identifier)
	 *                   as a file path on local machine, or path on s3, HDFS, ...).
	 * @return  the associated cache, or null if none could be found.
	 */
	static protected CachedInputStreamSys findInput(String uniqueURI) {
		synchronized(lock) {
			for (CachedInputStreamSys cache : readers) {
				if (cache.getUniqueURI().equals(uniqueURI)) {
					//DebugLog.info("cache found = " + cache);
					return cache;
				}
			}
		}
		//DebugLog.info("cache not found");
		return null;
	}
	
	
	/** Finds the CachedOutputStream associated with the URI, if any.
	 * @param uniqueURI  unique Uniform Ressource Identifier for the file
	 * @return  the cache if already in the local (writers) list, null if does not exist.
	 */
	static protected CachedOutputStream findOutput(String uniqueURI) {
		synchronized(lock) {
			for (CachedOutputStream cache : writers) {
				if (cache.getUniqueURI().equals(uniqueURI)) {
					//DebugLog.info("cache found = " + cache);
					return cache;
				}
			}
		}
		//DebugLog.info("cache not found");
		return null;
	}
	
	/** Unregisters the cache. The cache is supposed to be closed at this point.
	 *  Removes it from the list.
	 * @param cache  cache to remove
	 * @return  true if the cache was found, false otherwise.
	 */
	static protected boolean unregisterInput(CachedInputStreamSys cache) {
		synchronized(lock) {
			boolean success = readers.remove(cache);
			
			if (success) { // tries to wake-up any reader and writer waiting
				lock.notifyAll();
			}
			
			return success;
		}
	}
	
	/** Unregisters the CachedOutputStream, meaning removing it from the local (writers) list
	 *  and wakes up all threads currently waiting on any fonction of CachedStreamCotroller.
	 *  Only a thread waiting to acquire the URI associates with the cache will be able to proceed.
	 *  @param cache  the cache to be removed from the local (writers) list.
	 *  @return  true if the cache was present, false otherwise.
	 */
	static protected boolean unregisterOutput(CachedOutputStream cache) {
		synchronized(lock) {
			boolean success = writers.remove(cache);
			
			if (success) { // tries to wake-up any reader and writer waiting
				lock.notifyAll();
			}
			
			return success;
		}
	}
	
	/** Updates last use, gets the list ready for LRU
	 *  @param cache
	 */
	static protected void updateLastUsedInput(CachedInputStreamSys cache) {
		synchronized(lock) {
			boolean removed = readers.remove(cache);
			if (removed) {
				readers.addFirst(cache);
			} else {
				// Cache not found, this is not supposed to happen.
				// Print something, mostly for debug.
				DebugLog.error("InputStreamCachesHandler : updateLastUsed  cache not found.");
			}
		}
	}
	
	/** Waits until I can have a write access on cache.uniqueURI
	 *  FIFO is not guaranteed, but anyway, a writer stream should not be used concurrently with a reader (I think),
	 *  this is just a protection.
	 *  @param cache  the cache we'd like to register and use.
	 *  @throws IOException  if was unable to obtain the uniqueURI passed
	 */
	static protected void waitForOutputRights(CachedOutputStream cache) throws IOException { // String uniqueURI

		CachedInputStreamSys rd;
		boolean closeRd;
		
		synchronized(lock) {
			String uri = cache.getUniqueURI();
			
			boolean stopLoop = false;
			
			// Waits until no reader and writer are using the uri
			do {
				closeRd = false;
				rd = findInput(uri);
				// A reader uses the uri, mening no writer uses it.
				if (rd != null) {
					// No active CachedInputStream
					if (rd.getRefCount() == 0) {
						unregisterInput(rd); // unregisters it : no reader will be able to access it.
						closeRd = true;
						stopLoop = true; // not useful
						if (findOutput(uri) != null) { // SHOULD NOT HAPPEN
							String m = "A writer exists at the same time than a reader ! Critical access error.";
							DebugLog.error(m);
							throw new FileAlreadyExistsException(m);
						}
						break;
					} else {
						if (rd.getRefCount() < 0) { // should be positive or null, never negative.
							String m = "Critical : refCount negative = " + rd.getRefCount();
							DebugLog.error(m);
							throw new AccessDeniedException(m);
						}
						// refCount not null, waits.
						try {
							lock.wait();
						} catch (InterruptedException e) {
							e.printStackTrace();
							throw new AccessDeniedException("Critical problem while waiting for the writer to release lock.");
						}
					}
				}
				
				// Breaks if no writer (since no readers are here)
				if (findOutput(uri) == null) {
					stopLoop = true;
					break;
				}
				// Waits if a writer is using the uri
				try {
					lock.wait();
				} catch (InterruptedException e) {
					e.printStackTrace();
					throw new AccessDeniedException("Critical problem while waiting for the writer to release lock.");
				}
				
			} while(! stopLoop);
		}
		
		// I should close the writer.
		// Makes sure no other reader can access the uri until the underlying InputStream is closed.
		if (closeRd) {
			String uri = rd.getUniqueURI();
			
			synchronized(lock) {
				closingURI.add(uri);
			}
			
			rd.closeCache();
			
			synchronized(lock) {
				closingURI.remove(uri);
				// wakes everyone waiting, but only the instance wating the closingURL will be able to proceed
				lock.notifyAll(); 
			}
		}
			
	}
	
	/** Wakes all waiting threads, for example after a CachedInputStreamSys.closeInstance on refCount == 0
	 */
	protected static void wakeLocked() {
		synchronized(lock) {
			lock.notifyAll();
		}
	}

	
	// TODO : put cached on the local storage.
	public static final int maxCachedInputsInMemory = 10;
	public static final int maxCachedInputsOnDisk = 10;
	// Flushes 
	
	public static final boolean useOnlyDisk = false;
	/**
	 * Streams can also be accessed while on the disk.
	 * 1) Load the entire file in memory.
	 * 2) Write the file on the disk.
	 * 3) When the file is fully written, the memory can be freed.
	 * 
	 * Then, simply link the local file as an InputStream.
	 * 
	 * 
	 * 
	 */
	
	
}
