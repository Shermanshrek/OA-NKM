package ru.david.analysis;

import org.junit.jupiter.api.Test;
import ru.david.model.Concept;
import ru.david.model.FuzzyCognitiveMap;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ScenarioPlannerTest {

    private static FuzzyCognitiveMap mapOf(int n) {
        FuzzyCognitiveMap map = new FuzzyCognitiveMap();
        for (int i = 1; i <= n; i++) {
            map.addConcept(new Concept("C" + i, ""));
        }
        return map;
    }

    @Test
    void planHasSixScenariosPositiveFirst() {
        List<Scenario> plan = ScenarioPlanner.randomPlan(mapOf(10), new Random(1));

        assertEquals(6, plan.size());
        for (int i = 0; i < 6; i++) {
            assertEquals(i + 1, plan.get(i).number());
            assertEquals(1, plan.get(i).impulses().size());
            assertEquals(i < 3, plan.get(i).isPositive());
        }
    }

    @Test
    void negativeScenariosRepeatTheSameVerticesAsPositive() {
        List<Scenario> plan = ScenarioPlanner.randomPlan(mapOf(10), new Random(7));

        for (int i = 0; i < 3; i++) {
            assertEquals(plan.get(i).impulses().get(0).vertex(), plan.get(i + 3).impulses().get(0).vertex());
        }
    }

    @Test
    void chosenVerticesAreDistinctAndKeepModelOrder() {
        FuzzyCognitiveMap map = mapOf(10);
        for (long seed = 0; seed < 50; seed++) {
            List<Scenario> plan = ScenarioPlanner.randomPlan(map, new Random(seed));

            Set<String> vertices = new HashSet<>();
            int previousIndex = -1;
            for (int i = 0; i < 3; i++) {
                String vertex = plan.get(i).impulses().get(0).vertex();
                assertTrue(vertices.add(vertex), "вершина повторилась, seed " + seed);
                assertTrue(map.indexOf(vertex) > previousIndex, "нарушен порядок модели, seed " + seed);
                previousIndex = map.indexOf(vertex);
            }
        }
    }

    @Test
    void sameSeedGivesSamePlanAndSeedsDiffer() {
        FuzzyCognitiveMap map = mapOf(20);

        assertEquals(ScenarioPlanner.randomPlan(map, new Random(5)), ScenarioPlanner.randomPlan(map, new Random(5)));

        Set<List<Scenario>> plans = new HashSet<>();
        for (long seed = 0; seed < 20; seed++) {
            plans.add(ScenarioPlanner.randomPlan(map, new Random(seed)));
        }
        assertTrue(plans.size() > 1, "случайный выбор не меняется");
    }

    @Test
    void fewVerticesGiveFewerScenarios() {
        assertEquals(4, ScenarioPlanner.randomPlan(mapOf(2), new Random(1)).size());
        assertEquals(2, ScenarioPlanner.randomPlan(mapOf(1), new Random(1)).size());
        assertEquals(6, ScenarioPlanner.randomPlan(mapOf(3), new Random(1)).size());
    }

    @Test
    void emptyModelHasNoPlan() {
        assertTrue(ScenarioPlanner.randomPlan(new FuzzyCognitiveMap(), new Random(1)).isEmpty());
    }
}
