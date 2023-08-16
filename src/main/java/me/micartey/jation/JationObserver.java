package me.micartey.jation;

import lombok.NonNull;
import lombok.SneakyThrows;
import me.micartey.jation.annotations.Async;
import me.micartey.jation.annotations.AutoSubscribe;
import me.micartey.jation.annotations.Null;
import me.micartey.jation.annotations.Observe;
import me.micartey.jation.interfaces.JationEvent;
import me.micartey.jation.interfaces.TriConsumer;
import org.reflections.Reflections;
import org.reflections.scanners.MethodAnnotationsScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;

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

    private final Map<Class<? extends JationEvent<?>>, List<Function<List<Object>, Boolean>>> forEach;
    private final Map<Class<? extends JationEvent<?>>, List<Function<JationEvent<?>, Boolean>>> outset;
    private final Map<Class<? extends JationEvent<?>>, List<Function<JationEvent<?>, Boolean>>> closing;

    private final ExecutorService executorService;

    private final List<Object> instances;
    private final Set<Method> methods;

    public JationObserver(@NonNull String classpath, @NonNull ExecutorService executorService) {
        this.methods = new Reflections(new ConfigurationBuilder()
                .setUrls(ClasspathHelper.forPackage(classpath))
                .setScanners(new MethodAnnotationsScanner()))
                .getMethodsAnnotatedWith(Observe.class);

        this.instances = new ArrayList<>();

        new Reflections(new ConfigurationBuilder()
                .setUrls(ClasspathHelper.forPackage(classpath))
                .setScanners(new TypeAnnotationsScanner(), new SubTypesScanner()))
                .getTypesAnnotatedWith(AutoSubscribe.class)
                .stream().map(this::createInstance)
                .forEach(this::subscribe);

        this.executorService = executorService;

        this.outset = new WeakHashMap<>();
        this.forEach = new WeakHashMap<>();
        this.closing = new WeakHashMap<>();
    }

    public JationObserver(@NonNull String classpath) {
        this(classpath, Executors.newCachedThreadPool());
    }

    @SuppressWarnings("unused")
    public void subscribe(Object... instance) {
        this.instances.addAll(Arrays.asList(instance));
    }

    @SuppressWarnings("unused")
    public void unsubscribe(Object... instance) {
        this.instances.removeAll(Arrays.asList(instance));
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

    @SneakyThrows
    private Object createInstance(Class<?> clazz) {
        return clazz.newInstance();
    }

    @SuppressWarnings("unused")
    public void on(Class<? extends JationEvent<?>> clazz, @NonNull Consumer<JationEvent<?>> consumer) {
        this.on(clazz, object -> {
            consumer.accept(object);
            return true;
        });
    }

    public void on(Class<? extends JationEvent<?>> clazz, @NonNull Function<JationEvent<?>, Boolean> function) {
        List<Function<JationEvent<?>, Boolean>> functions = this.outset.getOrDefault(clazz, new ArrayList<>());
        functions.add(function);
        this.outset.put(clazz, functions);
    }

    @SuppressWarnings("unused")
    public void forEach(Class<? extends JationEvent<?>> clazz, @NonNull TriConsumer<Boolean, JationEvent<?>, Method, Object> consumer) {
        List<Function<List<Object>, Boolean>> functions = this.forEach.getOrDefault(clazz, new ArrayList<>());
        functions.add(object -> consumer.accept((JationEvent<?>) object.get(0), (Method) object.get(1), object.get(2)));
        this.forEach.put(clazz, functions);
    }

    @SuppressWarnings("unused")
    public void after(Class<? extends JationEvent<?>> clazz, @NonNull Consumer<JationEvent<?>> consumer) {
        List<Function<JationEvent<?>, Boolean>> functions = this.closing.getOrDefault(clazz, new ArrayList<>());
        functions.add(object -> {
            consumer.accept(object);
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
        return this.methods.stream().filter(method -> Arrays.stream(method.getParameters())
                .anyMatch(parameter -> parameter.getType().equals(event)))
                .collect(Collectors.toSet());
    }

    private <T> Set<Function<T, Boolean>> getFunctions(Class<?> clazz, Map<Class<? extends JationEvent<?>>, List<Function<T, Boolean>>> map) {
        return new HashSet<>(map.getOrDefault(clazz, new ArrayList<>()));
    }

    public Optional<Object> getInstance(Class<?> clazz) {
        return this.instances.stream().filter(clazz::isInstance).findFirst();
    }
}
