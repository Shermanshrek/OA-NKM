package ru.david.analysis;

/**
 * Собственное число матрицы отношений. Матрица несимметричная, поэтому число может быть
 * комплексным: re + im*i.
 */
public record EigenValue(double re, double im) {

    /** Модуль комплексного числа: sqrt(re^2 + im^2). */
    public double modulus() {
        return Math.hypot(re, im);
    }
}
