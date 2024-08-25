package com.zyj.service;

import com.zyj.spring.Annotation.Component;

@Component(BeanName = "orderService")
public class OrderService {

    public void test() {
        System.out.println("123");
    }
}
