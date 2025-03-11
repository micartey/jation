package me.micartey.jation.utilities;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Optional;

public class Base64 {

    public static Optional<String> toBase64(Object object) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream);
            objectOutputStream.writeObject(object);
            objectOutputStream.close();
            return Optional.ofNullable(java.util.Base64.getEncoder().encodeToString(outputStream.toByteArray()));
        } catch (Throwable ex) {
            return Optional.empty();
        }
    }

    public static <T> Optional<T> fromBase64(String content) {
        try {
            byte[] bytes = java.util.Base64.getDecoder().decode(content);
            ObjectInputStream outputStream = new ObjectInputStream(new ByteArrayInputStream(bytes));
            T object = (T) outputStream.readObject();
            outputStream.close();
            return Optional.ofNullable(object);
        } catch (Throwable ex) {
            return Optional.empty();
        }
    }
}