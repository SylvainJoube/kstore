package generic.cache.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Assert;
import org.junit.Test;

/**
 * 
 * Public class made for tests, as some classes .
 * 
 * @author Sylvain Joube
 *
 */
public class CachedInputStreamTests {
	
	
	public byte[] setUpBigArray(int testLen) throws IOException {
		// Writes the first testLen positive integers in a ByteArrayStream
		
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(bos);
		
		for (int i = 0; i < testLen; ++i) {
			dos.writeInt(i);
		}
		
		byte[] byteOutput = bos.toByteArray();
		
		dos.close();
		
		return byteOutput;
	}
	
	/** Tests the storage unit associated with the CachedInputStream : class CISStorageSys.
	 *  
	 *  @throws IOException 
	 */
	@Test
	public void testStorage() throws IOException {
		
		byte[] byteOutput = setUpBigArray(1000000);
		
		byte[] readData;
		int readPos, readLenLeft, lenRead;
		int constEOF = 1; // to make the stream detect EOF (does not detect it if len = 0)
		
		
		
		
		// --------- Blocking load ---------
		CISStorageSys storeSync = new CISStorageSys();
		storeSync.loadRessourceBlocking(() -> new ByteArrayInputStream(byteOutput));
		
		readData = new byte[byteOutput.length + 100];
		readPos = 0;
		readLenLeft = byteOutput.length + constEOF;
		
		do {
			lenRead = storeSync.read(readData, readPos, readLenLeft, readPos, true);
			if (lenRead > 0) {
				readPos += lenRead;
				readLenLeft -= lenRead;
				DebugLog.info("read blocking " + lenRead + " bytes. readLenLeft = " + readLenLeft);
			}
		} while (lenRead != -1);
		
		
		assertEquals(0, readLenLeft - constEOF);
		
		for (int i = 0; i < byteOutput.length; ++i) {
			//DebugLog.info("i -> rd : " + byteOutput[i] + " -> " + readData[i]);
			assertEquals(byteOutput[i], readData[i]);
		}
		
		
		
		
		// --------- Asynchronous load ---------
		CISStorageSys storeAsync = new CISStorageSys();
		storeAsync.loadRessourceAsync(() -> new ByteArrayInputStream(byteOutput));
		
		
		readData = new byte[byteOutput.length + 100];
		readPos = 0;
		readLenLeft = byteOutput.length + constEOF;
		
		do {
			lenRead = storeAsync.read(readData, readPos, readLenLeft, readPos, true);
			if (lenRead > 0) {
				readPos += lenRead;
				readLenLeft -= lenRead;
			}
			DebugLog.info("read async " + lenRead + " bytes. readLenLeft = " + readLenLeft);
		} while (lenRead != -1);
		
		
		assertEquals(0, readLenLeft - constEOF);
		
		for (int i = 0; i < byteOutput.length; ++i) {
			//DebugLog.info("i -> rd : " + byteOutput[i] + " -> " + readData[i]);
			assertEquals(byteOutput[i], readData[i]);
		}
		
		
		
		
		// --------- Random reads ---------
		// (here, the file is totally loaded)
		Random rand = new Random();
		// Sets the random seed to have a consistent test
		rand.setSeed(84); // [joke] I'd rather study at jussieu that at 42.
		
		
		int testNumber = 1000;
		/*int askReadLen = 10;
		byte[] bytesRead = new byte[askReadLen];
		int askReadPos = byteOutput.length-1;
		lenRead = storeAsync.read(bytesRead, 0, askReadLen, askReadPos, true);
		DebugLog.info("read at " + askReadPos + " with len = " + askReadLen + " effective = " + lenRead);
		*/
		
		for (int i = 0; i < testNumber; ++i) {

			int askReadLen, askReadPos;
			
			// Special cases
			switch (i) {
			case 0 : askReadLen = 0; askReadPos = 4521; break;
			case 1 : askReadLen = 42; askReadPos = CISStorageSys.loadChunkSize - 1; break;
			case 2 : askReadLen = 42; askReadPos = CISStorageSys.loadChunkSize; break;
			case 3 : askReadLen = 42; askReadPos = CISStorageSys.loadChunkSize + 1; break;
			case 4 : askReadLen = 2442; askReadPos = CISStorageSys.loadChunkSize - 1; break;
			case 5 : askReadLen = 2442; askReadPos = CISStorageSys.loadChunkSize; break;
			case 6 : askReadLen = 2442; askReadPos = CISStorageSys.loadChunkSize + 1; break;
			case 7 : askReadLen = 0; askReadPos = 0; break;
			case 8 : askReadLen = 0; askReadPos = -1; break;
			case 9 : askReadLen = 10; askReadPos = byteOutput.length - 1; break;
			default :
				askReadLen = rand.nextInt(byteOutput.length);
				askReadPos = rand.nextInt(byteOutput.length - 1);
			}
			
			// hevy for memory, but a good test
			byte[] bytesRead = new byte[askReadLen];
			lenRead = storeAsync.read(bytesRead, 0, askReadLen, askReadPos, true);
			//DebugLog.info("read at " + askReadPos + " with len = " + askReadLen + " effective = " + lenRead);
			
			assertNotEquals(-1, lenRead);
			
			// Checks wheter or not the data read is correct
			for (int j = 0; j < lenRead; ++j) {
				assertEquals(byteOutput[askReadPos + j], bytesRead[j]);
			}
		}
		
		// Make sure we detect the EOF
		byte[] dat = new byte[10];
		lenRead = storeAsync.read(dat, 0, dat.length, byteOutput.length, true);
		assertEquals(-1, lenRead);
		
	}
	
	
	/** Tests whether the CachedInputStream
	 * @throws IOException
	 */
	@Test
	public void testImmediateAccess() throws IOException {
		byte[] byteOutput = setUpBigArray(10000);
		
		byte[] readBuf = new byte[512];
		
		CachedInputStream.enterTestAsyncMode();
		try {
			
			InputStream cached = new CachedInputStream("myArrayuniqueID", () -> new ByteArrayInputStream(byteOutput));
			
			int av = cached.available(); // should be 0, simulates a very slow (but not EOF) underlying InputStream
			int len = cached.read(readBuf); // should be non-zero, waits until some data is available (returns -1 is no data, but here isn't the case)
			
			cached.close();
			
			assertEquals(0, av);
			assertNotEquals(0, len);
			
			//DebugLog.info("av = " + av + " len = " + len);
			
		} finally {
			CachedInputStream.leaveTestAsyncMode();
		}
	}
	
	
	@Test
	public void testConcurrentUse() throws IOException, InterruptedException {
		
		String uniqueURI = "myArrayuniqueID";
		
		byte[] byteOutput = setUpBigArray(10000);
		
		AtomicBoolean readerClosed = new AtomicBoolean(false);
		
		DebugLog.traceExec();
		
		new Thread( () -> {
			InputStream cached = null;
			DebugLog.traceExec();
			try {
				DebugLog.traceExec();
				cached = new CachedInputStream(uniqueURI, () -> new ByteArrayInputStream(byteOutput));
				DebugLog.traceExec();
			} catch (IOException e1) { e1.printStackTrace(); Assert.fail("InputStream creation impossible."); return; }
			try { Thread.sleep(600); } catch (InterruptedException e) { e.printStackTrace(); }
			if (cached != null)
				try {
					DebugLog.info("refcount = " + ((CachedInputStream)cached).getUnderlyingRefCount());
					readerClosed.set(true);
					cached.close();
				} catch (IOException e) { e.printStackTrace(); }
			DebugLog.traceExec();
			
		}).start();
		
		Thread.sleep(100);
		DebugLog.traceExec();
		InputStream reader2 = new CachedInputStream(uniqueURI, () -> new ByteArrayInputStream(byteOutput));
		
		int refCount = ((CachedInputStream) reader2).getUnderlyingRefCount();
		
		assertEquals(2, refCount);
		
		reader2.close();
		
		DebugLog.traceExec();
		OutputStream writer = new CachedOutputStream(uniqueURI, new ByteArrayOutputStream());
		
		assertEquals(true, readerClosed.get());
		
		writer.close();
		
		// More test could be done, but I have no time here.
		
	}
	
	
	
}
