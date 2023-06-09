# Module jacodb-approximation

[Approximations] is used for overriding and extending existed classes by methods with their bodies.

Use [Approximate] annotation on class and point it to target class. 

```java
@Approximate(java.lang.Integer.class)
public class IntegerApprox {
    private final int value;

    public IntegerApprox(int value) {
        this.value = value;
    }

    public static IntegerApprox valueOf(int value) {
        return new IntegerApprox(value);
    }

    public int getValue() {
        return value;
    }
}
```

Let's assume code:

```kotlin
val db = jacodb {
    installFeature(Approximations)
}
val cp = db.classpath(emptyList(), listOf(Approximations))
val clazz = cp.findClass("java.lang.Integer")
```

`clazz` object will represent mix between `java.lang.Integer` and `IntegerApprox`:
- static `valueOf` method will be from `IntegerApprox` and will reduce all complexity of java runtime
- `getValue` and `<init>` method will be also from `IntegerApprox`
- all other methods will be from `java.lang.Integer`

<!--- MODULE jacodb-approximations -->
<!--- INDEX org.jacodb.approximations -->

[Approximations]: https://jacodb.org/docs/jacodb-approximations/org.jacodb.approximation/-approximations/index.html
[Approximate]: https://jacodb.org/docs/jacodb-approximations/org.jacodb.approximation.annotation/-approximate/index.html