package com.zyj.service;

import com.zyj.spring.Annotation.Autowired;
import com.zyj.spring.Annotation.Component;

@Component
public class UserService implements UserInterfaces{

    /**
     * 自定义注解实现依赖注入
     */
    @Autowired
    private OrderService orderService;

    public void test() {
        orderService.test();
    }

}
