package generic.cache.io;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** 
 * 
 * Not intended for end-used, this is a package-private class.</br>
 * 
 * Storage for the CachedInputStreamSys class.</br>
 * Supports the asynchronous load of data from the stream. On asynchronous mode,
 * as soon as data is loaded from the stream it is available for reading.</br></br>
 * 
 * Data is stored in variable size chunks. A chunk size is determined by the nnumber of bytes read
 * at once by the given InputStream. If the associated InputStream gives only 10 bytes per read,
 * blocks will be as little as 10 bytes. Otherwise, reads given 
 * 
 * . This isn't the best way to handle storage as it creates a lot of objects,
 * but I think it's a good first approach, reducing memory used and re-allocations
 * as much as possible.</br></br>
 * 
 * It's more like a first glimpse into what can be made in the future.
 * 
 * @author Sylvain Joube
 * @version 2020-05 v1
 */
class CISStorageSys {
	
	/** Careful, a big file will result in many objects, really not good for GC speed. */
	protected ArrayList<CISStorageChunkSys> blocks = new ArrayList<>();
	
	/** Number of bytes in this cache (grows as more of the ressource gets loaded). */
	protected AtomicInteger loadedLength = new AtomicInteger(0);
	
	
	protected AtomicBoolean finishedLoading = new AtomicBoolean(false);
	protected AtomicBoolean continueLoading = new AtomicBoolean(true);
	
	/** Load size from the ressource InputStream. Small numbers are good for small files,
	    big numbers are good for big files. Ideally, this number should be a little more
	    than the number of bytes we can expect to read from the InputStream at once.*/
	protected static final int loadChunkSize = 4 * 1024;// 4kbytes
	
	/** Protects from memory saturation.
	    Higher is loadChunkSize, lower should be maxAsyncLoads
	    as a fixed buffer of size loadChunkSize is allocated per loadRessourceAsync(..),
	    and as long as a ressource is still being loaded. (i.e EOF in the primary InputStream is not reached)
	    Here, I didn't use a thread pool since I don't really know yet how to properly use them. */
	protected static final int maxAsyncLoads = 100;
	protected static final Semaphore protectMemOverload = new Semaphore(maxAsyncLoads, true);
	
	/** Asynchronous load of the ressource */
	protected Thread loadingThread;
	
	/** Loads from an InputStream, asychronously. If for the underlying InputStream, available() != 0,
	 *  then waits till this cache.avaiable() returns a non-zero value.
	 *  May be a (quite big) performance issue if thousands of files are loaded at the same time :
	 *  each file has a thread loading it asychronously, object creation an most importantly, context-switch
	 *  are a big performance issue. To solve this, we could use a thread-pool.
	 *  @param optionalConstructor
	 * @throws IOException 
	 */
	protected void loadRessourceAsync(OptionalInputStreamConstructor optionalConstructor) throws IOException {
		
		try {
			protectMemOverload.acquire();
		} catch (InterruptedException e) {
			return; // Critical error (I think)
		}
		
		// Creation on the InputStream here to throw an IOException if needed.
		InputStream in;
		try {
			in = optionalConstructor.create();
		} catch (IOException e) {
			DebugLog.error("error on optionalConstructor.create(). " + e);
			finishedLoading.set(true);
			throw e;
		}
		
		// Asychronous loading
		loadingThread = new Thread( () -> {
			loadRessourceBlocking(in);
			protectMemOverload.release();
			synchronized (this) { this.notifyAll(); } // new data is available, wakes up sleeping threads on read
		});
		
		loadingThread.start();
	}
	
	/** Same as loadRessourceBlocking(InputStream in).
	 *  @param optionalConstructor
	 *  @throws IOException
	 */
	protected void loadRessourceBlocking(OptionalInputStreamConstructor optionalConstructor) throws IOException {
		InputStream in;
		try {
			in = optionalConstructor.create();
		} catch (IOException e) {
			DebugLog.error("error on optionalConstructor.create(). " + e);
			finishedLoading.set(true);
			throw e;
		}
		loadRessourceBlocking(in);
	}
	
	
	/** Loads all the data of a file, blocking.
	 *  This method isn't synchronized on this object, at it would stop parallel reads (on loadAsync)
	 *  @param optionalConstructor  the function to call in order to create the inputStream
	 *                              from which to read.
	 * TODO : Not tested for stream liberation with free() function.
	 */
	protected void loadRessourceBlocking(InputStream in) {
		
		/**
		 * TODO
		 * Here, a good approach would be to have a dynamic (and predictive) loadChunkSize.
		 * The final goal should be to minimize the number of chunks, but also not having a
		 * temporary buffer too big.
		 * Instead of semaphores, I may use a ThreadPool (but I don't have time to leanr it now)
		 */
		
		byte[] buf = new byte[loadChunkSize];
		int len;
		do {
			len = tryLoadBlock(in, buf);
		} while ((len != -1) && continueLoading.get());
		
		// Close the stream, reading is over.
		try {
			in.close();
		} catch (IOException e) { 
			DebugLog.error("error on in.close. " + e);
		}
		
		synchronized(finishedLoading) {
			finishedLoading.set(true);
			finishedLoading.notifyAll(); // wakes any thread waiting on free(), in is finally closed.
		}
		//DebugLog.info("load finished.");
	}
	
	/** Request a read from an InputStream, and store the block read in the current object.
	 *  This function is blocking.
	 *  This method is synchronized on the current object.
	 *  @param in
	 *  @param buf
	 *  @return  the read byte number, -1 if an error occured or end of stream is reached.
	 */
	protected synchronized int tryLoadBlock(InputStream in, byte[] buf) {
		
		synchronized(finishedLoading) {
			// If I should stop loading, maybe I need to free the ressource. (free() call)
			if (continueLoading.get() == false) {
				return -1;
			}
	
			int len;
			if (CachedInputStream.testOnly_sleepOnLoadAsync) {
				DebugLog.warning("CachedInputStream parameter \"testOnly_sleepOnLoadAsync\" should only be used when debugging.");
				try {
					Thread.sleep(200);
				} catch (InterruptedException e1) { e1.printStackTrace(); }
			}
			
			try {
				len = in.read(buf);
			} catch (IOException e) {
				return -1;
			}
			
			if (len > 0) {
				synchronized (this) {
					// if in.read was blocking, it's important to check if this load is still relevent
					if (! continueLoading.get()) return -1; 
					
					// Copy of the new data
					byte[] passingData = new byte[len];
					System.arraycopy(buf, 0, passingData, 0, len);
					
					// Block creation, storage and file postion increment
					CISStorageChunkSys block = new CISStorageChunkSys(loadedLength.get(), passingData);
					blocks.add(block);
					loadedLength.addAndGet(len);
					//DebugLog.info("new chunk loaded !");
					this.notifyAll(); // new data is available, wakes up sleeping threads on read
				}
			}
			return len;
		}
	}
	
	/**
	 * 
	 * @param readTo
	 * @return
	 * @throws IOException
	 */
	protected synchronized int readAll(byte readTo[]) throws IOException {
		return read(readTo, 0, readTo.length, 0, true);
	}
	
	/** Read stored data, assembling data stored from blocks together.
	 * 
	 *  @see java.io.InputStream#read(byte[], int, int)
	 *  @param readTo     the buffer to save the read data
	 *  @param offBuff    write offset on readTo
	 *  @param lenRead    desired real length (lenRead + offBuff) should not be higher than readTo.length 
	 *  @param streamPos  the position in the "stream" to read from
	 *  @param blocking   whether the read should block or not, if no data is currently available.
	 *                    Blocking only means it will block the current thread if no data is available,
	 *                    not if the available data length is inferior to lenRead.
	 *  @return  the number of bytes read. -1 means end of stream. 0 means no data currently available.
	 *  @throws IOException
	 */
	protected synchronized int read(byte readTo[], int offBuff, int lenRead, int streamPos, boolean blocking) throws IOException {
		
		// Same exception raising than java.io.InputStream#read(byte[], int, int)
		if (readTo == null) {
			throw new NullPointerException();
		} else if (offBuff < 0 || lenRead < 0 || lenRead > readTo.length - offBuff) {
			throw new IndexOutOfBoundsException();
		} else if (lenRead == 0) {
			return 0;
		}
		
		// If the starting read position is higher that what's currently loaded:
 		if (streamPos >= loadedLength.get()) {
			
			// Nothing to read anymore (end of file)
			if (finishedLoading.get()) return -1;
			
			// If non-blocking nothing to read (yet)
			if (! blocking) return 0;
			
			// If blocking, then waits for more data to be loaded.
			// While not enough data is read and load is not complete, wait.
			while ( (streamPos >= loadedLength.get()) && (! finishedLoading.get()) ) {
				try {
					//DebugLog.info("Not enough data.");
					this.wait();
					//DebugLog.info("Woken up !");
				} catch (InterruptedException e) { return 0; }
			}
			
			// Here, this is true : (startPos < loadedLength.get()) OR finishedLoading.get() == true
			// meaning we can read something OR the file is read completely.
			
			// If startPos still is too high that means end of stream (finishedLoading.get() is true)
			if (streamPos >= loadedLength.get()) return -1;
		}
 		
 		// Attempting to read beyond the end of stream and loading is finished
 		// => return -1 meaning end of stream.
 		if ( (streamPos >= loadedLength.get()) && finishedLoading.get()) {
 			return -1;
 		}
		
		int reallyRead = CurReadPos.getPossibleReadLen(streamPos, lenRead, loadedLength.get());
		
		if (reallyRead == 0) return 0;
		
		int cpos = streamPos; // current read position
		int readToPos = offBuff; // position in the buffer we need to write into
		int lenReadLeft = lenRead; // number of bytes to read
		
		// Now, I need to find the blocks and assemble data info the readTo buffer.
		// To increase speed on many blocks, we could use a HashMap to store the blocks position
		// in the blocks list... but this only is a v.1
		for (CISStorageChunkSys block : blocks) {
			int bpos = block.getBlockPosition();

			if (bpos + block.length() < cpos) continue; // block entirely before cpos
			
			// block passed the read range, break.
			// should be similar to (lenReadLeft == 0)  cpos -> bpos
			if (bpos > streamPos + lenRead) {
				/* debug only -> */ if (lenReadLeft != 0) DebugLog.error("lenReadLeft != 0 : " + lenReadLeft);
				break; 
			}
			
			
			// where do I start to read in this block ?
			int rpos = cpos - bpos;
			
			// how many bytes are left in this block for me to read ?
			int available = block.length() - rpos;
			
			// number of bytes we can really read from this block
			int readAmount = Math.min(lenReadLeft, available); //CurReadPos.getPossibleReadLen(rpos, lenReadLeft, available);
			
			if (readAmount < 0) { // should never happen, debug only
				DebugLog.error("readAmount = " + readAmount);
				continue;
			}
			
			// readAmount may equals 0.
			if (readAmount != 0) {
				
				System.arraycopy(block.getBlockData(), rpos, readTo, readToPos, readAmount);
				
				// increments current read position and decreses the size left to read
				cpos += readAmount;
				lenReadLeft -= readAmount;
				readToPos += readAmount;
			}
		}
		
		return lenRead - lenReadLeft;
	}

	protected int getLoadedLength() {
		return loadedLength.get();
	}

	protected int length() {
		return loadedLength.get();
	}
	
	/** Frees the storage, stops loading if were loading.
	 *  Wats until the underlying InputStream is closed.
	 *  
	 *  TODO : Not tested fonction
	 */
	protected void free() {
		continueLoading.set(false);
		
		synchronized(finishedLoading) {
			if ( ! finishedLoading.get()) {
				try { finishedLoading.wait(); } catch (InterruptedException e) {e.printStackTrace();}
			}
		}
		
		// GC magic will do all the rest.
		
	}
	
	
}
