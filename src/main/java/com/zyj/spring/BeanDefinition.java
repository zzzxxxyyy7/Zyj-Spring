package com.zyj.spring;

/**
 * Spring 不会直接生成 Bean 对象
 * 而是先生成 BeanDefinition 对象
 */
public class BeanDefinition {

    private Class type;
    private String scope;
    private String BeanName;

    public String getBeanName() {
        return BeanName;
    }

    public void setBeanName(String beanName) {
        BeanName = beanName;
    }

    public Class getType() {
        return type;
    }

    public void setType(Class type) {
        this.type = type;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }
}
