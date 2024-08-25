package com.zyj.service;

import com.zyj.spring.Aware.BeanPostProcessor;
import com.zyj.spring.Annotation.Component;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

@Component
public class ZyjBeanPostProcessor implements BeanPostProcessor {

    /**
     * 所有的 Bean 在实例化后都会进到这个方法
     * 可以针对某个 Bean 特殊处理也可以对所有
     * Bean 进行处理
     * 前置处理，发生在 Bean 依赖注入之后
     * 实例化之前
     *
     * @param BeanName
     * @param bean
     */
    @Override
    public Object postProcessBeforeInitialization(String BeanName, Object bean) {
        return bean;
    }

    /**
     * 后置处理，发生在 Bean 初始化之后
     *
     * @param BeanName
     * @param bean
     */
    @Override
    public Object postProcessAfterInitialization(String BeanName, Object bean) {
        if (BeanName.equals("userService")) {
            Object proxyInstance = Proxy.newProxyInstance(
                    ZyjBeanPostProcessor.class.getClassLoader(),
                    bean.getClass().getInterfaces(),
                    new InvocationHandler() {
                        /**
                         * proxy 代理对象
                         * method 代理对象正在执行的方法
                         * args 代理对象执行方法传入参数
                         */
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            System.out.println("这是一个代理对象");
                            return method.invoke(bean , args);
                        }
                    }
            );
            return proxyInstance;
        }
        return bean;
    }
}
