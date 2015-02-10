package org.eclipse.orion.server.cf;

import java.util.*;

public class ExpiryCache<T> {
	Map<Object, Expirable<T>> cache = Collections.synchronizedMap(new HashMap<Object, Expirable<T>>());
	private int maxSize;
	private int expires_ms;

	static class Expirable<T> {
		T value;
		long expires;

		public Expirable(T value, long expires) {
			this.value = value;
			this.expires = expires;
		}

		public boolean valid() {
			return System.currentTimeMillis() < expires;
		}
	}

	public ExpiryCache(int maxSize, int expires_ms) {
		this.maxSize = maxSize;
		this.expires_ms = expires_ms;

	}

	public T get(Object key) {
		Expirable<T> expirable = cache.get(key);
		if (expirable != null) {
			if (expirable.valid()) {
				return expirable.value;
			} else {
				cache.remove(key);
			}
		}
		return null;
	}

	public void put(Object key, T value) {
		synchronized (cache) {
			if (cache.size() > maxSize) {
				for (Iterator<Expirable<T>> it = cache.values().iterator(); it.hasNext();) {
					Expirable<T> expirable = it.next();
					if (!expirable.valid()) {
						it.remove();
					}
				}
				if (cache.size() > maxSize) {
					cache.clear();
				}
			}
			cache.put(key, new Expirable<T>(value, System.currentTimeMillis() + expires_ms));
		}
	}

	public void remove(Object key) {
		cache.remove(key);
	}
}
