# Tool tasks


## Sanya
### based
- [x] correct styling
- [ ] commentaries
- [x] main functions
- [x] compilable verification
- [ ] tests
- [ ] interfaces - DAG, abstract classes - graph DFS, implementation of interfaces - zip dag to tree
- [ ] method implementation - paths in tree divisino in half, random points
- [ ] each call in graph path - may be virtual invoke, provide arguments on which this call should via generated hierarchy tree
- [ ] field and method overloading implementation
- [ ] return statement
- [ ] virtual calls in vulnerability path

### After inheritance
- [ ] type and/or signature correlation, covariance/contravariance - this should be part of overloading
- [ ] methods equals should consider if one overrides another if `this` is method
- [ ] assert initial value type is assignable to type
- [ ] assert that field is accessible to code value type
- [ ] protected modifier
- [ ] ifs, cycles, arrays, assignments, lambda invokes, returns
- [ ] generate data flow - first only simple returns and initial values in fields + tree types generation
- [ ] then do complex reassignments with conditions
- [ ] after that we can think of exceptions, lambdas, generics
- [ ] connecting already defined code

## Things to discuss
### Far future
- [ ] c++ implementation
- [ ] analyses aware constructors
- [ ] per language features to enable/disable some generations
- [ ] final boss will be unsoundiness - reflection and jni
- [ ] conditional paths - generating IFDS false positives to test USVM

### Do not see usefulness in foreseeable future
- [ ] extension methods - should be functions/methods with additional mark
- [ ] annotations - tbh dunno for what right now it might be required
- [ ] enums
- [ ] static initializer
- [ ] kotlinx serialization for hierarchy
- [ ] flip inner element nullability

### Hard to implement or involves much design and refactoring
- [ ] accessors - in some sense this should be method with some field reference. but not all languages support this, so skip for now
- [ ] generics and templates - this is just hard
- [ ] verifications - all interfaces methods are implemented, no collisions, all abstract methods are defined in non-abstract classes etc
