package ru.david.ui;

/** Режим работы редактора графа: определяет, что делает щелчок мышью по холсту. */
public enum EditMode {
    SELECT("Выбор",
            "Перетаскивайте вершины и связи (у связи двигается изгиб). "
                    + "Двойной щелчок: изменить вершину или вес связи."),
    ADD_NODE("Вершина",
            "Щёлкните по пустому месту, чтобы добавить вершину."),
    ADD_EDGE("Связь",
            "Щёлкните по вершине-источнику, затем по вершине-приёмнику "
                    + "(та же вершина даёт петлю). Щелчок по пустому месту отменяет выбор."),
    DELETE("Удаление",
            "Щёлкните по вершине (удалится вместе со всеми её связями) или по связи.");

    private final String title;
    private final String hint;

    EditMode(String title, String hint) {
        this.title = title;
        this.hint = hint;
    }

    public String title() {
        return title;
    }

    public String hint() {
        return hint;
    }
}
