package com.zyj.service;

import com.zyj.spring.ZyjSpringApplicationContext;

public class Test {

    public static void main(String[] args) {
        ZyjSpringApplicationContext applicationContext = new ZyjSpringApplicationContext(AppConfig.class);

        UserInterfaces userService = (UserInterfaces) applicationContext.GetBean("userService");
        userService.test();
    }
}
