package me.micartey.jation.network.serializer;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class Serializer {

    private final String seperator;

    @SneakyThrows
    public String serialize(Object instance, Class<?> clazz) {
        Set<Field> fields = this.getFields(clazz);

        StringBuilder output = new StringBuilder();

        for (Field field : fields) {
            output.append(field.getAnnotation(Serialize.class).value())
                    .append(":")
                    .append(field.get(instance))
                    .append(this.seperator);
        }

        return output.toString();
    }

    @SneakyThrows
    public <T> T deserialize(String representation, Class<T> clazz) {
        Set<Field> fields = this.getFields(clazz);

        Map<String, String> pairs = new HashMap<>();

        Arrays.stream(representation.split(this.seperator)).parallel().forEach(pair -> {
            String[] data = pair.split(":");
            pairs.put(data[0], pair.substring(data[0].length() + 1));
        });

        T instance = clazz.newInstance();

        for (Field field : new CopyOnWriteArrayList<>(fields)) {
            String value = field.getAnnotation(Serialize.class).value();
            String content = pairs.get(value);
            field.set(instance, this.convert(field.getType(), content));
            fields.remove(field);
        }

        if (!fields.isEmpty())
            throw new RuntimeException("Could not deserialize " + fields.size() + " fields. Is this the correct type?");

        return instance;
    }

    public Object deserialize(String representation, Class<?>... clazz) {
        for(Class<?> clazz1 : clazz) {
            try {
                return this.deserialize(representation, clazz1);
            } catch(Exception ignored) { }
        }

        return null;
    }

    private Set<Field> getFields(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredFields())
                .parallel()
                .peek(field -> field.setAccessible(true))
                .filter(field -> field.isAnnotationPresent(Serialize.class))
                .collect(Collectors.toSet());
    }

    private Object convert(Class<?> type, String name) {
        if (name == null || name.equals("null"))
            return null;

        try {
            if (type.equals(List.class)) {
                String data = name.replaceAll("^\\[|]$", "");

                if (data.isEmpty())
                    return new ArrayList<>();

                return Arrays.asList(data.split(", "));
            }

            String className = type.equals(long.class) ? "java.lang.Long" : type.equals(int.class) ? "java.lang.Integer" : type.equals(double.class) ? "java.lang.Double" : type.equals(float.class) ? "java.lang.Float" : type.equals(byte.class) ? "java.lang.Byte" : type.equals(boolean.class) ? "java.lang.Boolean" : type.equals(short.class) ? "java.lang.Short" : type.getName();
            Method method = Class.forName(className).getMethod("valueOf", String.class);
            return method.invoke(null, name);
        } catch (Exception e) {
            return name;
        }
    }
}