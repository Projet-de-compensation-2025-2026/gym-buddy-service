package fr.projetcompensation.gymbuddy.health;

public interface PostgresHealthPort {

    boolean reachable();

    String detail();
}
