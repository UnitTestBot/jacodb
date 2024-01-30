package org.jacodb.testing.analysis.alias;

@SuppressWarnings({"UnnecessaryLocalVariable", "unused", "UnusedAssignment"})
public class AliasExamples {
    public static class Engine {
        public int id;

        Engine(int id) {
            this.id = id;
        }
    }

    public static class Car {
        public Engine engine;

        Car(Engine engine) {
            this.engine = engine;
        }
    }

    public static class Garage {
        public Car car;

        Garage(Car car) {
            this.car = car;
        }
    }

    public Engine aliasOnField() {
        Engine engine = new Engine(1);
        Car car1 = new Car(engine);
        Car car2 = new Car(engine);
        return engine;
    }

    public Engine aliasOnAssignment() {
        Engine engine1 = new Engine(1);
        Car car1 = new Car(engine1);
        Car car2 = car1;
        Engine engine2 = new Engine(2);
        car1.engine = engine2;
        return engine2;
    }

    public Engine aliasOnAssignmentsAndParameters(Garage garage1) {
        Car car = new Car(null);
        Garage garage2 = garage1;
        garage1.car = car;
        Car carFromGarage2 = garage2.car;
        Car result = putEngineIntoSecondAndReturnFirst(carFromGarage2, car);
        return garage2.car.engine;
    }

    private Car putEngineIntoSecondAndReturnFirst(Car first, Car second) {
        second.engine = new Engine(1);
        return first;
    }

    public Engine aliasOnAssignmentWithBranch(boolean reuse) {
        Engine engine1 = new Engine(1);
        Engine engine2 = new Engine(2);

        Car car1 = new Car(engine1);
        Car car2;
        if (reuse) {
            car2 = new Car(engine1);
        } else {
            car2 = new Car(engine2);
        }

        return car1.engine;
    }
}
