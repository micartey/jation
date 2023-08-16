# jation

<div align="center">
  <a href="https://www.oracle.com/java/">
    <img
      src="https://img.shields.io/badge/Written%20in-java-%23EF4041?style=for-the-badge"
      height="30"
    />
  </a>
  <a href="https://jitpack.io/#micartey/jation/master-SNAPSHOT">
    <img
      src="https://img.shields.io/badge/jitpack-master-%2321f21?style=for-the-badge"
      height="30"
    />
  </a>
  <a href="https://micartey.github.io/jation/docs" target="_blank">
    <img
      src="https://img.shields.io/badge/javadoc-reference-5272B4.svg?style=for-the-badge"
      height="30"
    />
  </a>
</div>

<br>

<p align="center">
  <a href="#-introduction">Introduction</a> |
  <a href="#-build-tools">Maven/Gradle</a> |
  <a href="#-getting-started">Getting started</a>
</p>


## 📚 Introduction

jation is a java reflection based event manager which uses the build in java reflection api to automatically invoke methods with parameters and eases the work of a developer by reducing the amount of possible mistakes or missing method invokations.

An event manager is a must have in big or event based applications. This project amies to optimize the event manager I previously build and which was used to power my anticheat.

### 🔗 Build Tools

You can use Maven or Gradle to add this dependency to your project. Therefore you have to add use [jitpack](https://jitpack.io/#micartey/jation/master-SNAPSHOT) and apply the changes as documented.

Furthermore, you have to add another dependency named `refelctions` because jation depends on this dependency in order to `@AutoSubscribe` classes.

```xml
<dependency>
    <groupId>org.reflections</groupId>
    <artifactId>reflections</artifactId>
    <version>LATEST</version>
</dependency>
```

### 🎈 Getting Started

#### Create a new Observer

```java
JationObserver observer = new JationObserver("my.main.package");
```

#### Subscribe classes

There are two different methods to subscribe a class. <br>
First of all you can add them manually:

```java
observer.subscribe(
   new TestClass(),
   new OtherTestClass()
);
```

Secondly you can add the annotation `@AutoSubscribe` to a class. <br>
However, this requires a constructor without any arguments:

```java
package my.main.package.sub;

@AutoSubscribe
public class TestClass {

   public TestClass() {

   }

}
```

#### Reflection pattern

To *observe* methods you need to annotate them with `@Observe`. <br>
The `@Async` annotation is optional and can invoke the method in the [ForkJoinPool](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ForkJoinPool.html#commonPool--) asynchronously.

```java
@Async
@Observe
public void onEvent(MyTestEvent event, @Null String additive) {

}
```

`JationEvents` can be `published` with additional parameters. 
In case your method uses them and there is a possiblity, that the parameter is not always defined, you need to annotate the parameter with `@Null`.

#### Consumer Pattern

Reflection is slow. Some say it is more than 100% slower then normal method invokations.
This is the result of the runtime being unable to perform any optimizations.

Therefore, it is recommended to use the consumer pattern for performance critical projects.

```java
observer.on(TestEvent.class, (event) -> {
    // Access the event
});
```

The consumer pattern has a major disadvantage apart from scalability: Additional parameters can't be passed on.


#### Create Events

```java
public class TestEvent implements JationEvent<TestEvent> {

}
```

#### Publish Events

```java
new TestEvent().publish(observer, "additional information", 5);

new TestEvent().publishAsync(observer);
```
