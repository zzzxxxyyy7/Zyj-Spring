package com.zyj.service;

import com.zyj.spring.Annotation.Autowired;
import com.zyj.spring.Annotation.Component;
import com.zyj.spring.Annotation.PostConstruct;
import com.zyj.spring.Aware.InitializingBean;

@Component
public class UserService implements UserInterfaces , InitializingBean {

    /**
     * 自定义注解实现依赖注入
     */
    @Autowired
    private OrderService orderService;

    @PostConstruct
    public void show() {
        System.out.println(7788);
    }

    public void test() {
        orderService.test();
    }

    @Override
    public void afterPropertiesSet() {
        System.out.println("这是 InitializingBean 接口方法");
    }
}
