package com.zyj.service;

import com.zyj.spring.ZyjSpringApplicationContext;

public class Test {

    public static void main(String[] args) {
        ZyjSpringApplicationContext applicationContext = new ZyjSpringApplicationContext(AppConfig.class);

        UserService userBean = (UserService) applicationContext.GetBean("userService");


    }
}
