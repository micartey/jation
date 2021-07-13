# jation

<div align="center">
  <a href="https://jitpack.io/v/micartey/jation/LATEST">
    <img
      src="https://jitpack.io/v/micartey/jation.svg"
    />
  </a>
</div>

<br>

- [jation](#jation)
  - [Dependencies required](#dependencies-required)
    - [Step 1. Add the JitPack repository to your build file](#step-1-add-the-jitpack-repository-to-your-build-file)
    - [Step 2. Add the dependencies](#step-2-add-the-dependencies)
  - [How to use](#how-to-use)
    - [Create a new Observer](#create-a-new-observer)
    - [Subscribe classes](#subscribe-classes)
    - [Observe methods](#observe-methods)
    - [Publish JationEvents](#publish-jationevents)

## Dependencies required

### Step 1. Add the JitPack repository to your build file

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

### Step 2. Add the dependencies

```xml
<dependency>
    <groupId>com.github.micartey</groupId>
    <artifactId>jation</artifactId>
    <version>1.0.7</version>
</dependency>

<dependency>
    <groupId>org.reflections</groupId>
    <artifactId>reflections</artifactId>
    <version>LATEST</version>
</dependency>
```

## How to use

### Create a new Observer

```java
JationObserver observer = new JationObserver("my.main.package");
```

### Subscribe classes

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

### Observe methods

To observe methods you need to annotate them with `@Observe`. <br>
Another annotation `@Async` is optional to call them in the [ForkJoinPool](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ForkJoinPool.html#commonPool--) asynchronously.

```java
@Async
@Observe
public void onEvent(MyTestEvent event, @Null String additive) {

}
```

`JationEvents` can be `published` with additional parameters. In case your method uses them and there is a possiblity, that the parameter is not always defined, you need to annotate the parameter with `@Null`.

### Publish JationEvents

```java
public class TestEvent implements JationEvent<TestEvent> {

}
```

```java
new TestEvent().publish(observer, "additional information", 5);

new TestEvent().publishAsync(observer);
```
