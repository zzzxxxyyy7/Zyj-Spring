package com.zyj.spring;

import com.zyj.BeanCreateException;

import java.io.File;
import java.net.Socket;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;

public class ZyjSpringApplicationContext {

    private Class AppConfig;

    /**
     * IOC 容器，Bean 名称到 Definition 对象
     */
    private static final ConcurrentHashMap<String , BeanDefinition> IOC_Containers = new ConcurrentHashMap<>();

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
                                     * Spring 不会一开始就创建 Bean，而是使用一个 BeanDefinition 对象来
                                     * 统一代理这个 Bean 的属性
                                     */
                                    BeanDefinition beanDefinition = new BeanDefinition();

                                    beanDefinition.setType(clazz);

                                    if (clazz.isAnnotationPresent(Component.class)) beanDefinition.setBeanName(clazz.getAnnotation(Component.class).BeanName());
                                    else beanDefinition.setScope("BeanName_" + clazz.getName());

                                    if (clazz.isAnnotationPresent(Scope.class)) beanDefinition.setScope(clazz.getAnnotation(Scope.class).domain());
                                    else beanDefinition.setScope("Singleton");

                                    // 放入 IOC 容器
                                    IOC_Containers.put(beanDefinition.getBeanName() , beanDefinition);
                                }

                            } catch (BeanCreateException e) {
                                System.out.println("Bean 创建异常");
                            }

                        } catch (ClassNotFoundException e) {
                            e.printStackTrace();
                        }

                    }
                }
            } else throw new RuntimeException("该 ComponentScan 路径错误，该路径不是一个文件夹路径");
        }

        IOC_Containers.keySet().forEach(x -> {
            BeanDefinition beanDefinition = IOC_Containers.get(x);
            if (beanDefinition.getScope().equals("Singleton")) createBean(beanDefinition);
        });
    }

    private Object createBean(BeanDefinition beanDefinition) {

    }

    public Object GetBean(String BeanName) {

        BeanDefinition beanDefinition = IOC_Containers.get(BeanName);
        if (beanDefinition == null) throw new RuntimeException("不存在名称为 " + BeanName + " 的 Bean");

        String scope = beanDefinition.getScope();

        // TODO 后续改用策略模式优化
        if ("Singleton".equals(scope)) return beanDefinition;
        else {
            // 多例 Bean
            return null;
        }
    }
}
