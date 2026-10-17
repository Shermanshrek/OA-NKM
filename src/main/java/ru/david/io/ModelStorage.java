package ru.david.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import ru.david.model.Concept;
import ru.david.model.FuzzyCognitiveMap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Сохранение и загрузка модели в JSON. Формат файла (version 1):
 * <pre>
 * { "version": 1,
 *   "concepts": [ {"code": "A", "name": "Спрос"} ],
 *   "edges":    [ {"from": "A", "to": "B", "weight": 0.8} ],
 *   "layout":   { "nodes": [ {"code": "A", "x": 120, "y": 90} ],
 *                 "bends": [ {"from": "A", "to": "B", "bend": 40} ] } }
 * </pre>
 * Секция layout необязательна. Корректность данных проверяет сама модель: при загрузке
 * файл превращается в вызовы addConcept/addEdge, поэтому правила (уникальные коды,
 * вес в [-1; 1], нет дубликатов связей, связи только между существующими вершинами)
 * в одном месте и для UI, и для файла.
 */
public final class ModelStorage {

    private static final int FORMAT_VERSION = 1;
    // Без экранирования HTML, иначе русские буквы и символы вроде «>» в названиях станут нечитаемыми
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    // Структура файла. Поля-объекты (Double, Integer) нужны, чтобы отличить «не задано» от нуля.
    private record FileData(Integer version, List<ConceptData> concepts,
                            List<EdgeData> edges, LayoutData layout) {
    }

    private record ConceptData(String code, String name) {
    }

    private record EdgeData(String from, String to, Double weight) {
    }

    /** Результат загрузки: модель и данные расположения (пустые, если в файле их нет). */
    public record LoadedModel(FuzzyCognitiveMap map, LayoutData layout) {
    }

    private ModelStorage() {
    }

    public static void save(Path file, FuzzyCognitiveMap map, LayoutData layout) throws ModelFileException {
        if (map.concepts().isEmpty()) {
            throw new ModelFileException("Модель пуста, сохранять нечего: добавьте хотя бы одну вершину");
        }
        List<ConceptData> concepts = map.concepts().stream()
                .map(c -> new ConceptData(c.code(), c.name())).toList();
        List<EdgeData> edges = map.edges().stream()
                .map(e -> new EdgeData(e.from(), e.to(), e.weight())).toList();
        String json = GSON.toJson(new FileData(FORMAT_VERSION, concepts, edges, layout));
        try {
            Files.writeString(file, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ModelFileException("Не удалось записать файл " + file.getFileName() + ": " + e.getMessage(), e);
        }
    }

    public static LoadedModel load(Path file) throws ModelFileException {
        FileData data = read(file);

        if (data.version() == null || data.version() != FORMAT_VERSION) {
            throw new ModelFileException("Неподдерживаемая версия файла: " + data.version()
                    + " (ожидается " + FORMAT_VERSION + ")");
        }
        if (data.concepts() == null || data.concepts().isEmpty()) {
            throw new ModelFileException("В файле нет ни одной вершины");
        }

        FuzzyCognitiveMap map = new FuzzyCognitiveMap();
        int number = 0;
        for (ConceptData c : data.concepts()) {
            number++;
            try {
                if (c == null) {
                    throw new IllegalArgumentException("пустая запись");
                }
                map.addConcept(new Concept(c.code(), c.name()));
            } catch (IllegalArgumentException e) {
                throw new ModelFileException("Вершина №" + number + ": " + e.getMessage(), e);
            }
        }
        number = 0;
        // Вершины уже добавлены, поэтому связь на несуществующую вершину отвергнет сама модель
        for (EdgeData e : data.edges() == null ? List.<EdgeData>of() : data.edges()) {
            number++;
            try {
                if (e == null) {
                    throw new IllegalArgumentException("пустая запись");
                }
                if (e.weight() == null) {
                    throw new IllegalArgumentException("не задан вес связи " + e.from() + "->" + e.to());
                }
                map.addEdge(e.from(), e.to(), e.weight());
            } catch (IllegalArgumentException ex) {
                throw new ModelFileException("Связь №" + number + ": " + ex.getMessage(), ex);
            }
        }

        LayoutData layout = data.layout() == null ? LayoutData.EMPTY : data.layout();
        return new LoadedModel(map, layout);
    }

    private static FileData read(Path file) throws ModelFileException {
        String json;
        try {
            json = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ModelFileException("Не удалось прочитать файл " + file.getFileName() + ": " + e.getMessage(), e);
        }
        if (json.startsWith("﻿")) { // метка порядка байт, её добавляют некоторые редакторы Windows
            json = json.substring(1);
        }
        try {
            FileData data = GSON.fromJson(json, FileData.class);
            if (data == null) {
                throw new ModelFileException("Файл пуст");
            }
            return data;
        } catch (RuntimeException e) {
            // Gson бросает JsonParseException на неверном JSON, а ошибки конструкторов record
            // заворачивает в обычный RuntimeException; для пользователя это одно и то же
            throw new ModelFileException("Файл не является корректным файлом модели (JSON): " + e.getMessage(), e);
        }
    }
}
