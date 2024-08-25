package com.zyj.spring.Aware;

/**
 * Bean 初始化的后置处理器，也是 Spring AOP 切面的实现
 */
public interface BeanPostProcessor {

    Object postProcessBeforeInitialization(String BeanName , Object bean);

    Object postProcessAfterInitialization(String BeanName , Object bean);
}
