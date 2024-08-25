package com.zyj.spring;

import com.zyj.BeanCreateException;
import com.zyj.spring.Annotation.*;
import com.zyj.spring.Aware.BeanNameAware;
import com.zyj.spring.Aware.BeanPostProcessor;
import com.zyj.spring.Aware.InitializingBean;

import java.beans.Introspector;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class ZyjSpringApplicationContext {

    private Class AppConfig;

    /**
     * Bean 名称到 Definition 对象
     */
    private static ConcurrentHashMap<String , BeanDefinition> Definition_Containers = new ConcurrentHashMap<>();

    /**
     * IOC 容器
     */
    private static ConcurrentHashMap<String , Object> IOC_Containers = new ConcurrentHashMap<>();

    /**
     * BeanPostProcessor 实现类，专门用来实现 Spring AOP 使用到了装饰器模式
     */
    private static List<BeanPostProcessor> beanPostProcessors = new ArrayList<>();


    public ZyjSpringApplicationContext(Class appConfigclass) {
        this.AppConfig = appConfigclass;

        // 扫描
        // 如果配置类上存在这个注解
        if (AppConfig.isAnnotationPresent(ComponentScan.class)) {

            // 拿到注解
            ComponentScan componentScan = (ComponentScan) AppConfig.getAnnotation(ComponentScan.class);

            // 拿到注解后拿到属性
            String classpath = componentScan.classpath(); // 扫描路径 com.zyj.service
            classpath = classpath.replace("." , "/"); // 转换为 com/zyj/service

            ClassLoader classLoader = ZyjSpringApplicationContext.class.getClassLoader();
            URL resource = classLoader.getResource(classpath);
            if (resource == null) throw new RuntimeException("获取 ComponentScan 路径扫描错误，该路径不存在");

            File file = new File(resource.getFile());

            if (file.isDirectory()) {
                File[] files = file.listFiles();

                if (files != null) {
                    for (File f : files) {

                        // 拿到文件的绝对路径
                        String fileName = f.getAbsolutePath();

                        // 判断是否是一个 Class 文件
                        if (fileName.endsWith(".class")) {
                            String className = fileName.substring(fileName.indexOf("com") , fileName.indexOf(".class"));

                            className = className.replace("\\" , ".");

                            try {
                                Class<?> clazz = classLoader.loadClass(className);

                                try {
                                    // 如果类上有 Component 注解
                                    if (clazz.isAnnotationPresent(Component.class)) {

                                        /**
                                         * 判断这个类对象是否实现了 BeanPostProcessor 接口
                                         */
                                        if (BeanPostProcessor.class.isAssignableFrom(clazz)) {
                                            Object instance = clazz.newInstance();
                                            // 额外执行一步：提取类，加入容器
                                            beanPostProcessors.add((BeanPostProcessor) instance);
                                        }

                                        /**
                                         * Spring 不会一开始就创建 Bean，而是使用一个 BeanDefinition 对象来
                                         * 统一代理这个 Bean 的属性
                                         */
                                        BeanDefinition beanDefinition = new BeanDefinition();

                                        beanDefinition.setType(clazz);

                                        beanDefinition.setBeanName(clazz.getAnnotation(Component.class).BeanName());
                                        if (beanDefinition.getBeanName().isEmpty()) beanDefinition.setBeanName(Introspector.decapitalize(clazz.getSimpleName()));

                                        if (clazz.isAnnotationPresent(Scope.class)) beanDefinition.setScope(clazz.getAnnotation(Scope.class).domain());
                                        else beanDefinition.setScope("Singleton");

                                        // 放入 IOC 容器
                                        Definition_Containers.put(beanDefinition.getBeanName() , beanDefinition);
                                    }

                                } catch (BeanCreateException e) {
                                    System.out.println("Bean 创建异常");

                                } catch (InstantiationException | IllegalAccessException e) {
                                    throw new RuntimeException(e);

                                }

                            } catch (ClassNotFoundException e) {
                                e.printStackTrace();
                            }

                        }
                    }
                }
            } else throw new RuntimeException("该 ComponentScan 路径错误，该路径不是一个文件夹路径");
        }

        /**
         * 这一步保证了，所有被扫描出来的 Bean 类，都会被加载到单例池的 IOC 容器
         */
        Definition_Containers.keySet().forEach(beanName -> {
            BeanDefinition beanDefinition = Definition_Containers.get(beanName);
            if (beanDefinition.getScope().equals("Singleton")) {
                Object bean = createBean(beanDefinition);
                /**
                 * 真正存入 IOC 容器的实际上是代理对象
                 */
                IOC_Containers.put(beanName , bean);
            }
        });
    }

    /**
     * 创建 IOC Bean
     * @param beanDefinition
     * @return
     */
    private Object createBean(BeanDefinition beanDefinition) {
        Class clazz = beanDefinition.getType();

        try {
            // Bean 生命周期第一步:初始化
            Object bean = clazz.getConstructor().newInstance();

            // Bean 生命周期第二步:属性赋值
            // 遍历字段
            for (Field field : clazz.getDeclaredFields()) {
                // 如果对应的属性具备 Autowired 注解
                if (field.isAnnotationPresent(Autowired.class)) {
                    // 减少反射不必要的安全检查，提高执行效率
                    field.setAccessible(true);
                    field.set(bean , GetBean(field.getName()));
                }
            }

            // 遍历方法
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(PostConstruct.class)) {
                    method.invoke(bean);
                }
            }

            // Bean 初始化后关键一步 AOP 操作 前置操作
            for (BeanPostProcessor beanPostProcessor : beanPostProcessors) {
                bean = beanPostProcessor.postProcessBeforeInitialization(beanDefinition.getBeanName() , bean);
            }

            // Bean 声明周期第三步:实现相关Aware接口 TODO 策略模式优化
            if (bean instanceof BeanNameAware) ((BeanNameAware) bean).setBeanName(beanDefinition.getBeanName());
            if (bean instanceof InitializingBean) ((InitializingBean) bean).afterPropertiesSet();

            // Bean 初始化后关键一步 AOP 操作 后置操作
            for (BeanPostProcessor beanPostProcessor : beanPostProcessors) {
                bean = beanPostProcessor.postProcessAfterInitialization(beanDefinition.getBeanName() , bean);
            }

            /**
             * 最后创建出来的 Bean 对象就是代理对象
             */
            return bean;

        } catch (InstantiationException | InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }

    }

    public Object GetBean(String BeanName) {
        // 创建实例 Bean 要通过 BeanDefinition 来创建 Bean
        BeanDefinition beanDefinition = Definition_Containers.get(BeanName);
        if (beanDefinition == null) throw new RuntimeException("不存在名称为 " + BeanName + " 的 Bean");

        String scope = beanDefinition.getScope();

        // TODO Modify 双重校验锁
        if ("Singleton".equals(scope)) {
            if (!IOC_Containers.containsKey(BeanName)) {
                synchronized (ZyjSpringApplicationContext.class) { // 通常我们使用一个专门的锁对象，而不是类对象
                    // 再次检查以避免不必要的实例化
                    if (!IOC_Containers.containsKey(BeanName)) {
                        // 创建Bean并缓存
                        IOC_Containers.put(BeanName, createBean(beanDefinition));
                    }
                }
            }
            return IOC_Containers.get(BeanName);
        } else return createBean(beanDefinition); // 多例 Bean , 每次获取都创建一个全新的实例 , 这个实例不需要放到容器里
    }
}
