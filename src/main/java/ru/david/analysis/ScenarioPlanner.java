package ru.david.analysis;

import ru.david.model.Concept;
import ru.david.model.FuzzyCognitiveMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Составляет план эксперимента: в какие вершины и с каким знаком подавать импульс. */
public final class ScenarioPlanner {

    /** Сколько случайных вершин берётся для плана (каждая даёт два сценария: +1 и -1). */
    public static final int RANDOM_VERTEX_COUNT = 3;

    private ScenarioPlanner() {
    }

    /**
     * Выбирает {@link #RANDOM_VERTEX_COUNT} случайных различных вершин (или все, если их меньше)
     * и строит по два сценария на вершину: сначала положительные (+1), затем отрицательные (-1).
     * Для трёх вершин получается 6 сценариев, то есть 6 графиков. Вершины внутри каждой
     * половины идут в порядке модели, а не в порядке случайного выбора.
     * У пустой модели плана нет.
     */
    public static List<Scenario> randomPlan(FuzzyCognitiveMap map, Random random) {
        List<String> codes = new ArrayList<>(map.concepts().stream().map(Concept::code).toList());
        Collections.shuffle(codes, random);
        List<String> chosen = new ArrayList<>(codes.subList(0, Math.min(RANDOM_VERTEX_COUNT, codes.size())));
        chosen.sort((a, b) -> Integer.compare(map.indexOf(a), map.indexOf(b)));

        List<Scenario> plan = new ArrayList<>();
        for (int sign : new int[]{1, -1}) {
            for (String code : chosen) {
                plan.add(new Scenario(plan.size() + 1, List.of(new Scenario.Impulse(code, sign))));
            }
        }
        return plan;
    }
}
