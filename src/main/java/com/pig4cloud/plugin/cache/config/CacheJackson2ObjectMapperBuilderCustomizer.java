package com.pig4cloud.plugin.cache.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.cache.support.NullValue;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * 为 jackson 添加序列化和反序列化 NullValue, CacheMessage 支持
 *
 * @author FlyInWind
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(Jackson2ObjectMapperBuilder.class)
public class CacheJackson2ObjectMapperBuilderCustomizer implements Jackson2ObjectMapperBuilderCustomizer {

	@Override
	public void customize(Jackson2ObjectMapperBuilder builder) {
		// 由于 NullValue 有 final 修饰，而 jackson 配置的是 NON_FINAL
		// 会导致 @class 信息不会添加到 json 中，序列化出来的 json 为空，最终报错
		// 这里利用 ObjectMapper 的 mixIn 强制添加 @class 信息
		builder.mixIn(NullValue.class, UseTypeInfo.class);
		// 反序列化会创建新的对象，而由于 NullValue#equal 方法仅通过 == 判断是否相等，会导致 equal 结果为 false
		// 这里新建一个 Deserializer 专门返回 NullValue.INSTANCE
		builder.deserializers(NullValueDeserializer.INSTANCE);
	}

	public static class NullValueDeserializer extends StdDeserializer<NullValue> {

		public static final NullValueDeserializer INSTANCE = new NullValueDeserializer();

		protected NullValueDeserializer() {
			super(NullValue.class);
		}

		@Override
		public NullValue deserialize(JsonParser p, DeserializationContext ctx) {
			return (NullValue) NullValue.INSTANCE;
		}

	}

	@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
	public static class UseTypeInfo {

	}

}
