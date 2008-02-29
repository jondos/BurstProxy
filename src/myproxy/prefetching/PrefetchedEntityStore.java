package myproxy.prefetching;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Logger;

import myproxy.MyProxy;


/**
 * Singleton store of prefetched entities
 * 
 * @author dh
 *
 */
public class PrefetchedEntityStore implements Runnable{
	// locally cached prefetched entities
	// stores URLs and PrefetchedEntities
	
	private static PrefetchedEntityStore _instance = null;

	private static final Logger _logger = Logger.getLogger("myproxy");
	
	/** how many milliseconds should elements be kept in the store before they are purged? */
	private static final int WEED_DELAY = 10 * 1000;
	
	private static final Map store = new HashMap();
	
	protected PrefetchedEntityStore() {	}
	
	public static PrefetchedEntityStore getInstance() {
		if(_instance==null){
			_instance = new PrefetchedEntityStore();
			Thread t = 	new Thread(_instance, "Weeder");
			t.setDaemon(true);
			t.start();
		}
		return _instance;
	}
	public boolean containsURL(String url) {
		synchronized (store) {
			return store.containsKey(url);
		}
		
	}
	
	public void store(String url, PrefetchedEntity entity) {
		synchronized (store) {
			store.put(url, entity);
		}
	}
	
	public void prepareForStorage(String url) {
		synchronized (store) {
			store.put(url, null);
		}
	}
	
	public boolean isEntityAvailable(String url) {
		synchronized (store) {
			return store.get(url)!=null;
		}
	}
	
	public PrefetchedEntity getEntity(String url) {
		synchronized (store) {
			return (PrefetchedEntity) store.get(url);
		}
	}
	
	public synchronized void weedStore() {
		ArrayList toBeDeleted = new ArrayList();
		synchronized (store) {
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
			if(toBeDeleted.size() > 0){
				_logger.finer("Store contained " + store.size() +" entities. Deleting "+toBeDeleted.size());
			}
			for(Iterator i=toBeDeleted.iterator();i.hasNext();) {
				store.remove((String)i.next());
			}
		}
	}

	public void run() {
		// TODO Auto-generated method stub
		while(true){
			try {
				Thread.sleep(1000);
				weedStore();
			} catch (InterruptedException e) {;}
		}
	}
}