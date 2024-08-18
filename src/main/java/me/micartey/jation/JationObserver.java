package me.micartey.jation;

import lombok.NonNull;
import lombok.SneakyThrows;
import me.micartey.jation.annotations.Async;
import me.micartey.jation.annotations.Null;
import me.micartey.jation.annotations.Observe;
import me.micartey.jation.interfaces.JationEvent;
import me.micartey.jation.interfaces.TriConsumer;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JationObserver {

    public static JationObserver DEFAULT_OBSERVER = new JationObserver();

    private final Map<Class<? extends JationEvent<?>>, List<Function<List<Object>, Boolean>>> forEach;
    private final Map<Class<? extends JationEvent<?>>, List<Function<JationEvent<?>, Boolean>>> outset;
    private final Map<Class<? extends JationEvent<?>>, List<Function<JationEvent<?>, Boolean>>> closing;

    private final ExecutorService executorService;

    private final Map<Object, List<Method>> instances;

    public JationObserver(@NonNull ExecutorService executorService) {
        this.executorService = executorService;

        this.instances = new HashMap<>();

        this.outset = new WeakHashMap<>();
        this.forEach = new WeakHashMap<>();
        this.closing = new WeakHashMap<>();
    }

    public JationObserver() {
        this(Executors.newCachedThreadPool());
    }

    @SuppressWarnings("unused")
    public void subscribe(Object... instances) {
        Arrays.stream(instances).forEach(instance -> {
            List<Method> methods = Arrays.stream(instance.getClass().getDeclaredMethods())
                    .filter(method -> method.isAnnotationPresent(Observe.class))
                    .collect(Collectors.toList());

            this.instances.put(instance, methods);
        });
    }

    @SuppressWarnings("unused")
    public void unsubscribe(Object... instances) {
        Arrays.stream(instances).forEach(this.instances::remove);
    }

    public <T extends JationEvent<T>> void publish(@NonNull JationEvent<T> event, Object... additional) {
        Set<Method> methods = getMethods(event.getClass());

        if (!getFunctions(event.getClass(), this.outset).stream().allMatch(function -> function.apply(event)))
            return;

        methods.stream().sorted(Comparator.comparingInt(o -> -o.getAnnotation(Observe.class).priority())).forEach(method -> {
            getInstance(method.getDeclaringClass()).ifPresent(instance -> {
                Runnable task = () -> {
                    if (!getFunctions(event.getClass(), this.forEach).stream().allMatch(function -> function.apply(Arrays.asList(event, method, instance))))
                        return;

                    this.invoke(method, instance, getParameters(method, Stream.concat(Stream.of(event), Arrays.stream(additional)).toArray()));
                };

                if (method.isAnnotationPresent(Async.class)) {
                    CompletableFuture.runAsync(task);
                    return;
                }

                task.run();
            });
        });

        getFunctions(event.getClass(), this.closing).forEach(function -> function.apply(event));
    }

    public <T extends JationEvent<T>> void publishAsync(@NonNull JationEvent<T> event, Object... additional) {
        CompletableFuture.runAsync(() -> publish(event, Arrays.stream(additional).toArray()), this.executorService);
    }

    @SneakyThrows
    private void invoke(Method method, Object instance, Object[] parameters) {
        method.setAccessible(true);
        method.invoke(instance, parameters);
    }

    @SuppressWarnings("unused")
    public <T extends JationEvent<T>> void on(Class<T> clazz, @NonNull Consumer<T> consumer) {
        this.on(clazz, t -> {
            consumer.accept(t);
            return true;
        });
    }

    public <T extends JationEvent<T>> void on(Class<T> clazz, @NonNull Function<T, Boolean> function) {
        List<Function<JationEvent<?>, Boolean>> functions = this.outset.getOrDefault(clazz, new ArrayList<>());
        functions.add(this.transformFunction(function));
        this.outset.put(clazz, functions);
    }

    @SuppressWarnings("unused")
    public <T extends JationEvent<T>> void forEach(Class<T> clazz, @NonNull TriConsumer<Boolean, T, Method, Object> consumer) {
        List<Function<List<Object>, Boolean>> functions = this.forEach.getOrDefault(clazz, new ArrayList<>());
        functions.add(object -> consumer.accept((T) object.get(0), (Method) object.get(1), object.get(2)));
        this.forEach.put(clazz, functions);
    }

    @SuppressWarnings("unused")
    public <T extends JationEvent<T>> void after(Class<T> clazz, @NonNull Consumer<T> consumer) {
        List<Function<JationEvent<?>, Boolean>> functions = this.closing.getOrDefault(clazz, new ArrayList<>());
        functions.add(object -> {
            consumer.accept((T) object);
            return true;
        });
        this.closing.put(clazz, functions);
    }

    private Object[] getParameters(@NonNull Method method, Object... additional) {
        List<Object> unused = new ArrayList<>(Arrays.asList(additional));
        return Arrays.stream(method.getParameters()).map(parameter -> {
            Optional<?> optional = Arrays.stream(additional).filter(parameter.getType()::isInstance).filter(unused::contains).findFirst();

            if (!optional.isPresent() && !parameter.isAnnotationPresent(Null.class))
                throw new IllegalStateException(method.getName() + ": Parameter is not allowed to be null: " + parameter.getName());

            optional.ifPresent(unused::remove);
            return optional.orElse(null);
        }).toArray();
    }

    private Set<Method> getMethods(@NonNull Class<?> event) {
        return this.instances.values().stream().flatMap(List::stream).filter(method -> Arrays.stream(method.getParameters())
                .anyMatch(parameter -> parameter.getType().equals(event)))
                .collect(Collectors.toSet());
    }

    /*
     * This shit is getting to the edge of the compilers capabilities for generics.
     * This works due to Java not validating the generics at runtime but throwing a ClassCastException if not able.
     * The compiler doesn't like this however - Me as well
     */
    private <T extends JationEvent<T>, U> Function<JationEvent<?>, U> transformFunction(Function<T, U> function) {
        return (Function<JationEvent<?>, U>) function;
    }

    private <T> Set<Function<T, Boolean>> getFunctions(Class<?> clazz, Map<Class<? extends JationEvent<?>>, List<Function<T, Boolean>>> map) {
        return new HashSet<>(map.getOrDefault(clazz, new ArrayList<>()));
    }

    public Optional<Object> getInstance(Class<?> clazz) {
        return this.instances.keySet().stream().filter(instance -> instance.getClass().equals(clazz))
                .findAny();
    }
}
