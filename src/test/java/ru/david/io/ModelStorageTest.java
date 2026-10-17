package ru.david.io;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.david.model.Concept;
import ru.david.model.Edge;
import ru.david.model.FuzzyCognitiveMap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModelStorageTest {

    @TempDir
    Path dir;

    private Path file;

    @BeforeEach
    void setUp() {
        file = dir.resolve("model.json");
    }

    // ---------- сохранение и загрузка ----------

    @Test
    void roundTripKeepsModelAndLayout() throws Exception {
        FuzzyCognitiveMap map = new FuzzyCognitiveMap();
        map.addConcept(new Concept("A", "Спрос > предложение"));
        map.addConcept(new Concept("B", "Цена \"рыночная\""));
        map.addEdge("A", "B", 0.8);
        map.addEdge("B", "A", -0.6);
        map.addEdge("B", "B", 0.25);
        LayoutData layout = new LayoutData(
                List.of(new LayoutData.NodePosition("A", 100.5, 200), new LayoutData.NodePosition("B", 300, 50)),
                List.of(new LayoutData.EdgeBend("A", "B", 42.5), new LayoutData.EdgeBend("B", "B", -1.2)));

        ModelStorage.save(file, map, layout);
        ModelStorage.LoadedModel loaded = ModelStorage.load(file);

        assertEquals(map.concepts(), loaded.map().concepts());
        assertEquals(map.edges(), loaded.map().edges());
        assertEquals(layout, loaded.layout());
    }

    @Test
    void savedFileKeepsRussianTextReadable() throws Exception {
        FuzzyCognitiveMap map = new FuzzyCognitiveMap();
        map.addConcept(new Concept("A", "Спрос"));

        ModelStorage.save(file, map, LayoutData.EMPTY);

        assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains("Спрос"));
    }

    @Test
    void fileWithoutLayoutLoadsWithEmptyLayout() throws Exception {
        write("""
                {"version": 1,
                 "concepts": [{"code": "A", "name": "Первая"}, {"code": "B"}],
                 "edges": [{"from": "A", "to": "B", "weight": -0.5}]}""");

        ModelStorage.LoadedModel loaded = ModelStorage.load(file);

        assertEquals(2, loaded.map().concepts().size());
        assertEquals("", loaded.map().findConcept("B").orElseThrow().name());
        assertEquals(new Edge("A", "B", -0.5), loaded.map().edges().get(0));
        assertEquals(LayoutData.EMPTY, loaded.layout());
    }

    @Test
    void fileWithoutEdgesIsValid() throws Exception {
        write("""
                {"version": 1, "concepts": [{"code": "A", "name": ""}]}""");

        assertTrue(ModelStorage.load(file).map().edges().isEmpty());
    }

    @Test
    void fileWithUtf8BomLoads() throws Exception {
        Files.writeString(file, "﻿{\"version\": 1, \"concepts\": [{\"code\": \"A\"}]}", StandardCharsets.UTF_8);

        assertEquals(1, ModelStorage.load(file).map().concepts().size());
    }

    @Test
    void demoExampleLoads() throws Exception {
        ModelStorage.LoadedModel loaded = ModelStorage.load(Path.of("examples", "demo.json"));

        // Файл можно перезаписывать из приложения, поэтому конкретные числа не проверяем
        assertFalse(loaded.map().concepts().isEmpty());
    }

    // ---------- проверка при сохранении ----------

    @Test
    void savingEmptyModelIsRejected() {
        ModelFileException e = assertThrows(ModelFileException.class,
                () -> ModelStorage.save(file, new FuzzyCognitiveMap(), LayoutData.EMPTY));

        assertTrue(e.getMessage().contains("пуста"));
        assertFalse(Files.exists(file));
    }

    // ---------- проверка при загрузке ----------

    @Test
    void missingFileIsReported() {
        assertThrows(ModelFileException.class, () -> ModelStorage.load(dir.resolve("нет-такого.json")));
    }

    @Test
    void emptyFileIsRejected() throws Exception {
        write("");

        assertTrue(assertThrows(ModelFileException.class, () -> ModelStorage.load(file))
                .getMessage().contains("пуст"));
    }

    @Test
    void brokenJsonIsRejected() throws Exception {
        write("{\"version\": 1, \"concepts\": [");

        assertThrows(ModelFileException.class, () -> ModelStorage.load(file));
    }

    @Test
    void wrongJsonStructureIsRejected() throws Exception {
        write("[1, 2, 3]");

        assertThrows(ModelFileException.class, () -> ModelStorage.load(file));
    }

    @Test
    void unknownVersionIsRejected() throws Exception {
        write("{\"version\": 99, \"concepts\": [{\"code\": \"A\"}]}");

        assertTrue(assertThrows(ModelFileException.class, () -> ModelStorage.load(file))
                .getMessage().contains("версия"));
    }

    @Test
    void missingVersionIsRejected() throws Exception {
        write("{\"concepts\": [{\"code\": \"A\"}]}");

        assertThrows(ModelFileException.class, () -> ModelStorage.load(file));
    }

    @Test
    void modelWithoutConceptsIsRejected() throws Exception {
        write("{\"version\": 1, \"concepts\": [], \"edges\": []}");

        assertTrue(assertThrows(ModelFileException.class, () -> ModelStorage.load(file))
                .getMessage().contains("нет ни одной вершины"));
    }

    @Test
    void duplicateConceptCodeIsRejected() throws Exception {
        write("{\"version\": 1, \"concepts\": [{\"code\": \"A\"}, {\"code\": \"A\"}]}");

        assertTrue(assertThrows(ModelFileException.class, () -> ModelStorage.load(file))
                .getMessage().contains("Вершина №2"));
    }

    @Test
    void blankConceptCodeIsRejected() throws Exception {
        write("{\"version\": 1, \"concepts\": [{\"code\": \" \"}]}");

        assertThrows(ModelFileException.class, () -> ModelStorage.load(file));
    }

    @Test
    void weightOutOfRangeIsRejected() throws Exception {
        write(twoConceptsWithEdge("{\"from\": \"A\", \"to\": \"B\", \"weight\": 1.5}"));

        assertTrue(assertThrows(ModelFileException.class, () -> ModelStorage.load(file))
                .getMessage().contains("Связь №1"));
    }

    @Test
    void zeroWeightIsRejected() throws Exception {
        write(twoConceptsWithEdge("{\"from\": \"A\", \"to\": \"B\", \"weight\": 0}"));

        assertThrows(ModelFileException.class, () -> ModelStorage.load(file));
    }

    @Test
    void missingWeightIsRejected() throws Exception {
        write(twoConceptsWithEdge("{\"from\": \"A\", \"to\": \"B\"}"));

        assertTrue(assertThrows(ModelFileException.class, () -> ModelStorage.load(file))
                .getMessage().contains("не задан вес"));
    }

    @Test
    void duplicateEdgeIsRejected() throws Exception {
        write(twoConceptsWithEdge(
                "{\"from\": \"A\", \"to\": \"B\", \"weight\": 0.5}, {\"from\": \"A\", \"to\": \"B\", \"weight\": 0.7}"));

        assertTrue(assertThrows(ModelFileException.class, () -> ModelStorage.load(file))
                .getMessage().contains("Связь №2"));
    }

    @Test
    void edgeToUnknownConceptIsRejected() throws Exception {
        write(twoConceptsWithEdge("{\"from\": \"A\", \"to\": \"Z\", \"weight\": 0.5}"));

        assertThrows(ModelFileException.class, () -> ModelStorage.load(file));
    }

    // ---------- служебное ----------

    private void write(String json) throws IOException {
        Files.writeString(file, json, StandardCharsets.UTF_8);
    }

    private static String twoConceptsWithEdge(String edges) {
        return "{\"version\": 1, \"concepts\": [{\"code\": \"A\"}, {\"code\": \"B\"}], \"edges\": [" + edges + "]}";
    }
}
