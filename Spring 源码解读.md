# Spring 源码分析

**围绕 Bean & IOC 、事务 、 三级缓存 、整合 Mybatis 展开分析**

## 解读 Bean 的创建流程

`Spring` 通过一个容器存储所有的 `Bean`，例如 `ClassPathXmlApplication` 来获取 `Bean`

```java
public static void main(String[] args) {
    // IOC 容器
    ZyjSpringApplicationContext applicationContext = new ZyjSpringApplicationContext(AppConfig.class);

    UserService userService = (UserService) applicationContext.GetBean("userService");
    userService.test();
}
```

### BeanDefinition

```java
public class BeanDefinition {
    // 真正的 Bean Class 对象
    private Class type;
    private String scope;
    private String BeanName;
}
```

`Spring` 不会直接创建 `Bean`，而是先通过一个 `BeanDefinition` 管理 `Bean` 的属性

### 获取 Bean

```java
public Object GetBean(String BeanName) {
    // 创建实例 Bean 要通过 BeanDefinition 来创建 Bean
    BeanDefinition beanDefinition = Definition_Containers.get(BeanName);
    if (beanDefinition == null) throw new RuntimeException("不存在名称为 " + BeanName + " 的 Bean");
	
    // Bean 作用域
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
```

上述代码中，首先判断 BeanDefinition 容器是否已经存在这个名称的 Bean，否则双重校验锁创建单例 Bean

###  创建 Bean

```java
private Object createBean(BeanDefinition beanDefinition) {
    Class clazz = beanDefinition.getType();

    try {
        // Bean 生命周期第一步:初始化，通过构造方法创建
        Object bean = clazz.getConstructor().newInstance();

        // Bean 生命周期第二步:属性赋值
        for (Field field : clazz.getDeclaredFields()) {
            // 如果对应的属性具备 Autowired 注解
            if (field.isAnnotationPresent(Autowired.class)) {
                // 减少反射不必要的安全检查，提高执行效率
                field.setAccessible(true);
                // 执行相应依赖注入
                field.set(bean , GetBean(field.getName()));
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
}
```

### 统一的前后置 Bean 处理器：BeanPostProcessor

```java
public interface BeanPostProcessor {

    Object postProcessBeforeInitialization(String BeanName , Object bean);

    Object postProcessAfterInitialization(String BeanName , Object bean);
}
```

`Spring` 会维护一个统一的前后置处理器，专门用在 `Bean` 初始化前后执行相应操作，最经典的就是为每一个 `Bean` 生成其代理对象并放入 `IOC` 容器

```java
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
```

这个前后置处理器同样要被 `Spring` 扫描到，这里使用了 `JDK` 动态代理生成代理对象，对于没有实现接口的类，`Spring` 会采用 `CGlib` 动态代理为其生成代理对象

### IOC 容器构造函数解析

```java
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

// 放入 BeanDefinition 容器
Definition_Containers.put(beanDefinition.getBeanName() , beanDefinition);
```

首先放入 `BeanDefinition` 容器，这一步会校验所有带有 `Component `注解的类，并初始化 `BeanDefinition` 容器

```java
/**
 * 这一步保证了，所有被扫描出来的 Bean 类，都会被加载到单例池的 IOC 容器
 */
Definition_Containers.keySet().forEach(beanName -> {
    BeanDefinition beanDefinition = Definition_Containers.get(beanName);
    if (beanDefinition.getScope().equals("Singleton")) {
        Object bean = createBean(beanDefinition);
        IOC_Containers.put(beanName , bean);
    }
});
```

这一步才会真正创建出所有的 `Bean` 对象，对于 `Bean` 对象的实际引用，指向的是一个代理对象，这非常有利于实现 `AOP` 操作

```java
public static void main(String[] args) {
    ZyjSpringApplicationContext applicationContext = new ZyjSpringApplicationContext(AppConfig.class);

    UserInterfaces userService = (UserInterfaces) applicationContext.GetBean("userService");
    userService.test();
}
```

这样，我们就可以通过 `IOC` 容器获取指定的代理对象

`Spring IOC` 容器最终存入单例池的是代理对象，是通过默认构造函数（如果有，没有使用唯一构造函数创建实例）创建 `Bean` 实例

### Spring 依赖注入的顺序

`Spring` 底层会维护类型 -> `Bean` 和 名称 ->`Bean` 俩种类型，支持通过名称和类型注入依赖，在没有使用注解的情况下，`Spring` 还会通过构造函数自动注入依赖，也是通过类型和名称自行匹配

## Spring 事务解析

首先在代理对象执行代理方法之前，判断是否存在 `@Transactional` 注解，如果有就创建一个数据库连接，并开启数据库事务，修改 `autocommit` 属性修改为 `false` ，取消自动提交事务的能力，后续再调用原先方法，在前面开启了事务的连接之上，执行 `SQL`，如果没有抛出异常，才会提交事务

开启事务是由代理对象执行的，如果在事务方法里面调用非实物方法，由于事务方法是 `Target` 类在执行，嵌套调用非实物方法理所应当也是 `Target` 类在执行，而非其代理类，因此没有办法开始事务连接，导致事务失效

```java
@Transactional
public void test(){
    // AOP逻辑，开启事务连接
	Target.test(); // 实际执行代理类目标方法
    // AOP逻辑，没有抛异常，提交事务，抛出异常，回滚事务
}
```

此时如果仍要让事务生效，要么拆类，要么自己注入自己，再调用目标方法

### @Configuration作用

如果想要执行的 `SQL` 跑在开启事务的数据库连接之上，就必须拿到 `@Transactional` 注解所建立的连接，这一步，`Spring` 通过 `ThreadLocal` 来实现，因为二者都在同一个线程之内

```java
ThreadLocal<Map<DataSource, Connect>> // 以 Datasource 为 Key ，同时支持多种数据库连接
```

通过这个注解，才能保证二者跑在同一个连接之上

## Spring 循环依赖问题

**Bean 的生命周期**

- 实例化 `Bean` 对象
- 填充/依赖注入
- 初始化（`Aware` 接口）
- 添加到单例池
- 使用 `Bean`
- 销毁 `Bean`

### 三级缓存

- `singletonObjects`：单例池，用来存放完全创建好的 Bean
- `earlySingletonObjects`：存放早期暴露对象，用来打破循环依赖问题
- `singletonFactories`：单例工厂

```java
AService 的 Bean 生命周期
0. CreateSet<'AService'> 提前放入正在实例化的 Bean    

1. 实例化 --> AService普通对象（此时的普通对象没有被放入任何一个容器）

// 实例化 BService 对象
2. 填充 BService ---> 单例池 Map ---> 创建BService
    BService 的 Bean 生命周期
    2.1 实例化 BService ---> 普通对象
    2.2 填充 BService ---> 单例池Map ---> 查询CreateSet，如果查到则说明发生了循环依赖，目标对象正在创建 ---> 先查询二级缓存 earlySingletonObjects ---> 没有存在，执行 AOP ---> 创建 AService 代理对象（创建 AOP 代理对象就需要拿到 AService 普通对象）---> 把前期暴露对象存入 earlySingletonObjects 二级缓存，此时 AService 代理对象还不能存入单例池
    2.3 填充其他属性
    2.4 做一些其他的事情（AOP）
    2.5 添加 BService 代理对象到单例池

3. 初始化

4. 做一些其他事情，如 AOP 代理（发生循环依赖时，这一步的 AOP 会被停止）
    4.5 earlySingletonObjects.get('AService') 取出代理对象，此时 AService 已经完成完整的生命周期，存入单例池

5. 添加到单例池
    
6. CreateSet.remove<'AService'>
```

**Spring 的 AOP 提前机制**

如果没有发生循环依赖，`Spring` 会在完成初始化之后再进行 `AOP`，发生循环依赖条件下，`Spring` 会把 `AOP` 提前至创建完实例就执行

这一步是通过判断 `CreateSet` 是否存在目标类而实现的

**CreateSet**

表示当前正在创建的 Bean，创建完成会被移除，需要 AService 的时候来这个集合查找，如果找到，说明该类正在创建 Bean，则判断发生了循环依赖

**earlySingletonObjects**

何谓前期暴露对象：注入目标对象的代理对象依赖，在依赖实例化之后就创建的代理对象，内部的 `Target` 类仅仅只是完成实例化，其本身依赖注入、初始化、`AOP` 等阶段均为完成，调用 `API` 可能出错，且这个依赖不能存入单例池，但是又必须保存起来，否则如 `CService` 也要注入 AService 的时候重复创建代理对象，于是引入二级缓存

**一二级缓存的区别**

一级缓存存放的是已经完成初始化等一系列操作的完整 `Target` 类的代理对象，二级缓存仅存放完成了实例化的 `Target` 类的代理对象

通俗来讲，就是内部目标类没有经过完整 `Bean` 的生命周期

**第三级缓存**

在 2.2 中，创建代理对象需要拿到普通对象，于是需要有一个缓存来暂存普通对象，这就是三级缓存，并且 1 中需要发生更改

```java
1. 实例化 --> AService普通对象（此时的普通对象没有被放入任何一个容器）---> singletonFactories.put(beanName , mbd , AService普通对象)
```

2.2 也需要发生变动

```java
2.2 填充 BService ---> 单例池Map ---> 查询CreateSet，如果查到则说明发生了循环依赖，目标对象正在创建 ---> 先查询二级缓存 earlySingletonObjects ---> 没查到，查询三级缓存 singletonFactories  ---> 没有存在，执行 AOP ---> 本质是执行 Lambda 表达式 ---> 创建 AService 代理对象（创建 AOP 代理对象就需要拿到 AService 普通对象）---> 把前期暴露对象存入 earlySingletonObjects 二级缓存
```

### 使用 @Lazy 可以解决循环依赖问题

循环依赖问题是如何通过`@Lazy` 解决的呢？这里举一个例子，比如说有两个 Bean，A 和 B，他们之间发生了循环依赖，那么 A 的构造器上添加 `@Lazy` 注解之后（延迟 Bean B 的实例化），加载的流程如下

- 首先 Spring 会去创建 A 的 Bean，创建时需要注入 B 的属性
- 由于在 A 上标注了 `@Lazy` 注解，因此 Spring 会去创建一个 B 的代理对象，将这个代理对象注入到 A 中的 B 属性
- 之后开始执行 B 的实例化、初始化，在注入 B 中的 A 属性时，此时 A 已经创建完毕了，就可以将 A 给注入进去

从上面的加载流程可以看出： `@Lazy` 解决循环依赖的关键点在于代理对象的使用

- **没有 `@Lazy` 的情况下**：在 Spring 容器初始化 `A` 时会立即尝试创建 `B`，而在创建 `B` 的过程中又会尝试创建 `A`，最终导致循环依赖（即无限递归，最终抛出异常）
- **使用 `@Lazy` 的情况下**：Spring 不会立即创建 `B`，而是会注入一个 `B` 的代理对象。由于此时 `B` 仍未被真正初始化，`A` 的初始化可以顺利完成。等到 `A` 实例实际调用 `B` 的方法时，代理对象才会触发 `B` 的真正初始化

`@Lazy` 能够在一定程度上打破循环依赖链，允许 `Spring` 容器顺利地完成 `Bean` 的创建和注入。但这并不是一个根本性的解决方案，尤其是在构造函数注入、复杂的多级依赖等场景中，`@Lazy` 无法有效地解决问题。因此，最佳实践仍然是尽量避免设计上的循环依赖

**三级缓存 和 @Lazy 的区别**

只有使用到 `BService` 的时候才会开始 `BService` 的真正初始化，而在三级缓存中 `BService` 会借助 `AService` 的前期暴露对象就直接进行初始化

## Spring 整合 Mybatis 原理

```java
@Component
public class UserService {
    
    @Autowired
    private UserMapper userMapper; // Mybatis 实际上也会生成一个代理对象，这里是如何注入到 Spring Bean 中的呢？
    
    public void test() {
        System.out.println(userMapper);
    }
}
```

使用 `FactoryBean` 接口

```java
public class ZyjFactoryBean implements FactoryBean {
      
    @Override
    public Object getObject() {
		Object proxyInstance = Proxy.newProxyInstance(
        	ZyjFactoryBean.class.getClassLoader(),
            new InvokerHandler(
            	@Overrider
                public Object invoke(Object proxy, Method method, Object[] args) {
                    System.out.println(method.getName());
                    return null;
                }
            )
        )    
        return Instance;
    }
}
```

这个接口可以帮助我们获取第三方类作为 `Spring` 的 `Bean` 进行管理



















