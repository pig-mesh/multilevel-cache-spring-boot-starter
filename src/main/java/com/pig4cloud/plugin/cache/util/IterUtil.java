package com.pig4cloud.plugin.cache.util;

import java.util.Iterator;
import java.util.function.Function;

/**
 * 拷贝自 hutool
 *
 * @author Looly
 */
public class IterUtil {

	/**
	 * 按照给定函数，转换{@link Iterator}为另一种类型的{@link Iterator}
	 * @param <F> 源元素类型
	 * @param <T> 目标元素类型
	 * @param iterator 源{@link Iterator}
	 * @param function 转换函数
	 * @return 转换后的{@link Iterator}
	 * @since 5.4.3
	 */
	public static <F, T> Iterator<T> trans(Iterator<F> iterator, Function<? super F, ? extends T> function) {
		return new TransIter<>(iterator, function);
	}

}
