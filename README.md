# jation

<div align="center">
    <img src="https://img.shields.io/badge/Written_In-Java%2021-fd5c63?style=for-the-badge&logo=openjdk" alt="Java" />
    <img src="https://img.shields.io/badge/Build%20Tool-Gradle-50C878?style=for-the-badge&logo=gradle" alt="Gradle" />
</div>

<br>

<p align="center">
  <a href="#-introduction">Introduction</a> |
  <a href="#-build-tools">Maven/Gradle</a> |
  <a href="#-getting-started">Getting started</a>
</p>


## 📚 Introduction

jation is a java event system which uses the build in java reflection api to automatically invoke methods with parameters.
The most interesting feature however, is the addition of network adapters which provide the possibility to distribute events between JVMs and machines.

### 🎈 Getting Started

Use the following maven repository server in order to add jation as a dependency.
The following syntax is for gradle, but it works for maven as well.

```groovy
maven {
    url "https://artifacts.micartey.dev/public"
}

// ...

implementation "me.micartey:jation:2.2.0"
```

#### Create a new Observer

> This step is **optional**.
> 
> You can also use the static `DEFAULT_OBSERVER`

```java
JationObserver observer = new JationObserver(); // Alternatively you can pass a custom executor to the constructor
```

#### Subscribe classes

To subscribe classes you need to call the `subscribe` method and pass the object instances to the varargs parameter.

```java
observer.subscribe(
   new TestClass(),
   new OtherTestClass()
);
```

#### Reflection pattern

To *observe* methods you need to annotate them with `@Observe`. <br>
The `@Async` annotation is optional and can invoke the method in their own virtual threads.

```java
@Async
@Observe
public void onEvent(MyTestEvent event, @Null String additive) {

}
```

Events can be published with additional parameters. 
In case your method uses them and there is a possibility, that the parameter is not always defined, you need to annotate the parameter with `@Null`.

#### Consumer Pattern

Reflection is slow. Some benchmarks indicate it is more than twice as slow as normal method invocations.
This is likely the result of the runtime being unable to perform any optimizations.

Therefore, it is recommended to use the consumer pattern for performance critical projects.

```java
observer.on(TestEvent.class, (event) -> {
    // Access the event
});
```

The consumer pattern has a major disadvantage apart from scalability: Additional parameters can't be passed on.


#### Create Events

To create an event you need to implement the `JationEvent` interface. The interface has a generic type parameter which is the event itself.

```java
public class TestEvent implements JationEvent<TestEvent> {

}
```

#### Publish Events

To publish an event you need to call the `publish` method and pass the event instance to the first parameter and the additional parameters to the varargs parameter.

```java
// Publish the event to all subscribed classes
new TestEvent().publish(observer, "additional information", 5);


// Publish the event to all subscribed classes asynchronously
new TestEvent().publishAsync(observer);
```

#### Distributed Events

> [!NOTE]  
> Both events and additional data that might be shared with that event, must implement the Java `Serializable` interface

Events can be distributed and executed on another JVM.
It also supports additional arguments.
For the feature to be enabled, you need to add a network adapter.

```java
observer.addAdapter(
        new UdpNetworkAdapter(LISTEN_PORT, TARGET_PORT) // ports can also be the same
                .useLoopbackInterface()                 // Or useBroadcastInterface()
);
```

For distributed events, you need to add the `Distribution` annotation.
You can choose between `AT_LEAST_ONCE` or `EXACTLY_ONCE`.
The difference between these guarantees is the amount of machines/instances that possibly receive the events.
However, an event won't be received twice per JVM instance.

```java
@AllArgsConstructor
@Distribution(Distribution.Guarantee.AT_LEAST_ONCE)
public class TestEvent implements JationEvent<TestEvent>, Serializable {
    
    public String someData;
    
}
```