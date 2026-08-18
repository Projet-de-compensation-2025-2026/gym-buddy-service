package fr.projetcompensation.gymbuddy.auth;

import java.util.function.Supplier;

public interface TransactionRunner {

    <T> T inTransaction(Supplier<T> work);

    static TransactionRunner immediate() {
        return Supplier::get;
    }
}
