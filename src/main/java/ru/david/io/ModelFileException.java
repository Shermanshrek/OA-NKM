package ru.david.io;

/** Файл модели нельзя прочитать, записать или он содержит некорректную модель. Сообщение пригодно для показа пользователю. */
public class ModelFileException extends Exception {

    public ModelFileException(String message) {
        super(message);
    }

    public ModelFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
