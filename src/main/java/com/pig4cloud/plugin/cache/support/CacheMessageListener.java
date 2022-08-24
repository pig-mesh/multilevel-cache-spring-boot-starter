package com.pig4cloud.plugin.cache.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * @author fuwei.deng
 * @version 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class CacheMessageListener implements MessageListener {

	private RedisSerializer<Object> javaSerializer = RedisSerializer.java();

	private final RedisCaffeineCacheManager redisCaffeineCacheManager;

	@Override
	public void onMessage(Message message, byte[] pattern) {

		/**
		 * 发送端固定了jdk序列户方式，接收端同样固定了jdk序列化方式进行反序列化。
		 */
		CacheMessage cacheMessage = (CacheMessage) javaSerializer.deserialize(message.getBody());
		log.debug("recevice a redis topic message, clear local cache, the cacheName is {}, the key is {}",
				cacheMessage.getCacheName(), cacheMessage.getKey());
		redisCaffeineCacheManager.clearLocal(cacheMessage.getCacheName(), cacheMessage.getKey());
	}

}
