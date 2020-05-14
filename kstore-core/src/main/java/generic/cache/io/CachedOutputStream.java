package generic.cache.io;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * In the future, a CachedOutputStream may directly modify any opened CachedInputStream, making concurrent
 * use of (a single) OutputStream and (possibly many) InputStream possible.
 * 
 * 
 * @author admin
 *
 */
public class CachedOutputStream extends FilterOutputStream {
	
	protected final String uniqueURI;
	
	// Diffrent strategies for accessing data : here, only one at a time is permitted.

	public CachedOutputStream(String uniqueURI, OutputStream out) throws IOException {
		super(out);
		this.uniqueURI = uniqueURI;
		
		// Blocks while more than one reader is opened, or if a writer is still using the same uri.
		CachedStreamController.waitForOutputRights(this);
		
	}
	
	public String getUniqueURI() {
		return uniqueURI;
	}
	
	@Override
	public void close() throws IOException {
        out.close();
		CachedStreamController.unregisterOutput(this);
    }
	

}
