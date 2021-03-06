package generic.cache.io;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Please use CachedOutputStream if you use CachedInputStream, for synchronization reasons.</br></br>
 * 
 * In the future, a CachedOutputStream may directly modify any opened CachedInputStream, making concurrent
 * use of (a single) OutputStream and (possibly many) InputStream possible.
 * 
 * @author Sylvain Joube
 * @version 2020-05 v1
 *
 */
public class CachedOutputStream extends FilterOutputStream {
	
	protected final String uniqueURI;
	
	// Diffrent strategies for accessing data : here, only one at a time is permitted.
	
	/** Blocks until every CachedInputStream and CachedOutputStream associated with this URI has been closed.
	 * @param uniqueURI  Uniform Ressource Identifier, an URI should be unique to a ressource
	 * @param out  the already open OutputStream we'd like to use.
	 * @throws IOException  if was unable to obtain the uniqueURI passed
	 * @see FilterOutputStream#FilterOutputStream
	 */
	public CachedOutputStream(String uniqueURI, OutputStream out) throws IOException {
		super(out);
		this.uniqueURI = uniqueURI;
		
		// Blocks while more than one reader is opened, or if a writer is still using the same uri.
		CachedStreamController.waitForOutputRights(this);
		
	}
	
	/** Returns the URI associated with this cache.
	 *  @return  the unique URI associated with this cache.
	 */
	public String getUniqueURI() {
		return uniqueURI;
	}
	
	/** Closes the underlying OutputStream (same as FilterOutputStream),
	 *  unregisters the URI and wakes other threads waiting to obtain this URI.
	 */
	@Override
	public void close() throws IOException {
        out.close();
		CachedStreamController.unregisterOutput(this);
    }
	

}
