package ru.david.analysis;

/** Вывод об устойчивости по максимальному по модулю собственному числу M. */
public enum StabilityStatus {
    STABLE("устойчива", "<"),
    UNSTABLE("неустойчива", ">"),
    /** Случай |M| = 1: на слайде не описан, поэтому показывается отдельным статусом. */
    BOUNDARY("на границе устойчивости", "=");

    private final String title;
    private final String relationToOne;

    StabilityStatus(String title, String relationToOne) {
        this.title = title;
        this.relationToOne = relationToOne;
    }

    public String title() {
        return title;
    }

    /** Знак сравнения |M| с единицей, который соответствует статусу. */
    public String relationToOne() {
        return relationToOne;
    }
}
