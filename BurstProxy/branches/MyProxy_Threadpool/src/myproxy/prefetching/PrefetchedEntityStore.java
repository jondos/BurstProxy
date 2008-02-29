package myproxy.prefetching;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


/**
 * Singleton store of prefetched entities
 * 
 * @author dh
 *
 */
public class PrefetchedEntityStore {
	// locally cached prefetched entities
	// stores URLs and PrefetchedEntities
	
	private static PrefetchedEntityStore _instance = null;
	
	/** how many milliseconds should elements be kept in the store before they are purged? */
	private static final int WEED_DELAY = 10 * 1000;
	
	private static final Map store = new HashMap();
	
	protected PrefetchedEntityStore() {	}
	
	public static PrefetchedEntityStore getInstance() {
		if(_instance==null)
			_instance = new PrefetchedEntityStore();
		return _instance;
	}
	public boolean containsURL(String url) {
		return store.containsKey(url);
	}
	
	public void store(String url, PrefetchedEntity entity) {
		store.put(url, entity);
	}
	
	public void prepareForStorage(String url) {
		store.put(url, null);
	}
	
	public boolean isEntityAvailable(String url) {
		return store.get(url)!=null;
	}
	
	public PrefetchedEntity getEntity(String url) {
		return (PrefetchedEntity) store.get(url);
	}
	
	public synchronized void weedStore() {
		ArrayList toBeDeleted = new ArrayList();
		for(Iterator i=store.values().iterator();i.hasNext();) {
			PrefetchedEntity pe = (PrefetchedEntity)i.next();
			
			if(pe==null) continue;
			
			Date completedAt = pe.getCompletedAt();
			
			if(completedAt==null) continue;
			
			Date now = new Date();
			
			if((now.getTime()-completedAt.getTime())>PrefetchedEntityStore.WEED_DELAY) {
				toBeDeleted.add(pe.getURI());
				System.out.println("Removing "+ pe.getURI() + " from store.");
			}
		}
		
		for(Iterator i=toBeDeleted.iterator();i.hasNext();) {
			store.remove((String)i.next());
		}
	}
}