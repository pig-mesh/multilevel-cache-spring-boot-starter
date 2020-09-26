package com.pig4cloud.plugin.cache.support;

import java.io.Serializable;

/**
 * @author fuwei.deng
 * @date 2018年1月29日 下午1:31:17
 * @version 1.0.0
 */
public class CacheMessage implements Serializable {

	private static final long serialVersionUID = 5987219310442078193L;

	private String cacheName;

	private Object key;

	public CacheMessage(String cacheName, Object key) {
		super();
		this.cacheName = cacheName;
		this.key = key;
	}

	public String getCacheName() {
		return cacheName;
	}

	public void setCacheName(String cacheName) {
		this.cacheName = cacheName;
	}

	public Object getKey() {
		return key;
	}

	public void setKey(Object key) {
		this.key = key;
	}

}
