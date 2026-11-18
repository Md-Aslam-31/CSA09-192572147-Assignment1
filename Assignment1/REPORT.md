# Campus Smart Parking System - Assignment 1

**Student:** Mohamed Aslam H  
**Registration number:** 192572147  
**Course outcome:** CO1 - Java fundamentals and object-oriented programming  
**Assignment:** Design, implement and validate an object-oriented model for a campus smart-parking system

> Prepared as a worked submission draft. Review it, run the program in your own Java environment, capture your own execution evidence, and add your GitHub repository URL before submission. The source PDF requires individual execution and a working GitHub link.

## Design summary

The design retains an object-oriented model, with a focused modification for the day-four requirements. A jagged `Slot[][]` stores uneven floor capacities. Vehicles share fee and registration behavior through an abstract `Vehicle`; new categories are added as subclasses. `Chargeable` expresses the charging capability independently of the vehicle inheritance tree. `ParkingPass` models eligibility and validity; `Ticket` captures one parking session; `ParkingFacility` owns the slots and active tickets.

The changed requirement adds e-bikes, delivery vans, visitor day passes, five floors of differing capacity, and van peak-hours pricing. These needs are addressed by extending vehicle classes, introducing pass and charging capabilities, and keeping facility allocation generalized. A production system would make rates and hours data-driven and persist state; the compact submission keeps them explicit for clarity.

## Task 1 - Problem analysis and candidate objects

**Inputs:** vehicle registration and category, user category, optional pass, arrival and exit times, optional energy used, facility floor capacities, and day-pass choice. **Outputs:** assigned floor/slot and ticket ID, rejection when full or invalid, fee at exit, updated occupancy, and test results.

| Candidate | Why it is needed | Main responsibilities |
|---|---|---|
| `Vehicle` | Every parked vehicle has a registration identity and fee behavior. | Validate and normalize registration; define fee contract; equality/hash by registration. |
| Vehicle subclasses | Rates and special behavior differ by category. | Supply category rates and specialized fee rules. |
| `ParkingFacility` | Represents the managed resource and coordinates operations. | Allocate/release slots, track active tickets, prevent duplicate active registrations. |
| `Slot` | A physical space has identity, type and occupancy state. | Track floor/number, standard or charging capability, and assigned registration. |
| `Ticket` | Each parking visit is a distinct transaction. | Preserve unique ID, vehicle, user/pass, slot and entry time; calculate exit amount. |
| `ParkingPass` | A user's entitlement is separate from vehicle identity. | Check user class and validity date. |
| `UserType` | User eligibility affects rates. | Distinguish student, staff and visitor rules. |
| `Chargeable` / `Payable` | Capabilities are independent of vehicle ancestry. | Expose charging and payment contracts for capable objects. |

Business rules: one active ticket per normalized registration; one vehicle per slot; tickets are assigned monotonically increasing IDs and never reused; charging vehicles need charging slots; visitor day passes cost Rs 100; valid student/staff daily passes waive parking fees; negative durations/energy and invalid exits are rejected; a full facility returns no ticket. Assumptions: durations are billed by started hour (zero minutes costs zero); day pass applies only to visitors; a monthly pass is represented here by a validity date in the compact demo and should be replaced with date-range validation in a deployed system; all times are local campus time.

**Alternatives and complexity:** With three parallel arrays (`regNo[]`, `type[]`, `entryTime[]`), lookup is O(n), and each added attribute requires synchronized changes to all arrays. One object per vehicle keeps its attributes together, also O(n) for a plain active-ticket array search, but is safer to extend and supports overriding. The implementation uses a map keyed by registration for expected O(1) active-ticket lookup; object memory is O(n), plus each object's fields and references.

## Task 2 - Data representation

Use `String` for registration, `int` for floor/slot/minutes, `long` for ticket ID, `LocalDateTime` for timestamps, `double` for illustrative fee arithmetic, and enums for finite categories. A real currency implementation should use `BigDecimal` or integer paise to avoid floating-point rounding. `Slot[][]` is jagged: each row is a floor and may have its own slot count. Inputs are constructor parameters in this demo; a user-facing application could use `Scanner` or validated command-line arguments.

Integer division can undercharge: `61 / 60 == 1`, so a 61-minute visit would cost one hour if rounded down. The code avoids this by computing `(minutes + 59) / 60` for positive minutes. Integer math also avoids an unsafe cast from `ceil(double)`; negative durations are rejected first. A zero-minute stay is explicitly zero.

**Alternative:** A flat array with `index = floor * slotsPerFloor + slot` assumes equal slots per floor. For uneven capacities, that formula becomes invalid or needs prefix offsets per floor. Jagged indexing uses `floors[f][s]` directly. Both representations locate a known index in O(1); first-fit allocation scans O(S) slots where S is total capacity. Jagged arrays use O(S+F) references/arrays; a flat array uses O(S), with an offset table adding O(F). The jagged representation directly expresses each floor's capacity and makes bounds validation straightforward.

## Task 3 - Control logic and pseudocode

**First-fit allocation**

```text
ALLOCATE(vehicle)
  if active ticket exists for vehicle.registration: reject duplicate
  for each floor f
    for each slot s on floor f
      if slot is free and slot supports vehicle requirements
        mark slot occupied by registration
        return (f, s)
  return FULL
```

**Exit and fee**

```text
EXIT(registration, exitTime, dayPass)
  ticket <- activeTickets[registration]
  if ticket does not exist: reject
  duration <- minutes between entry and exit; reject if negative
  if eligible valid pass: fee <- 0
  else if visitor selected day pass: fee <- fixed day-pass amount
  else fee <- vehicle-specific fee(duration)
  release ticket.slot; remove active ticket
  return fee
```

The nested loops stop immediately at the first suitable slot (`return`), avoiding unnecessary scans. If no slot exists, allocation returns `null` and does not create a ticket. Pricing rules reside in vehicle subclasses and ticket policy, not in a giant loop or type-switch.

**Alternative complexity:** First-fit `allocate` is O(S) and `release` by ticket/map lookup is expected O(1). Maintaining a free-slot stack per floor and a free count makes allocation O(F) to select a floor (or O(1) with a global index), and release O(1), at extra O(S) references plus O(F) counters; it also requires careful synchronization so stack and occupancy stay consistent. For modest campus capacity, straightforward scanning is easier to validate.

## Task 4 - Classes, objects and constructors

The source uses parameterized constructors to establish required state immediately and constructor chaining through `super(regNo)`. Vehicle registration is final and validated once. `ElectricCar` chains through `Car` to `Vehicle`; the order is `Vehicle` constructor, `Car` constructor, then `ElectricCar` constructor body. Default constructors are inappropriate because they could create an invalid vehicle with no registration. Overloads can be added where optional details are meaningful; this small model does not need a copy constructor. Tickets are not copied to issue a duplicate: each new parking session receives a new unique ticket ID.

A local reference variable is typically on the stack; an object created with `new` resides on the heap, and the variable holds a reference to it. In a ticket, fields refer to a vehicle and slot; the facility map refers to active tickets. After exit removes the active ticket and releases the slot, the ticket and vehicle become eligible for garbage collection when no other references remain. Garbage collection timing is JVM-managed.

**Alternative constructors:** A default constructor followed by setters can expose partially initialized/invalid objects; validation is spread across calls. Static factories can provide named creation modes and enforce validation before returning, but still allocate O(1) time and space per object. Constructor creation here is O(1); creating n tickets costs O(n) time and O(n) retained memory while active. Factories offer comparable asymptotic cost, with clearer names and flexibility; a private constructor plus factory is appropriate if ticket issuance must be centralized.

## Task 5 - Static members and object life cycle

Ticket ID sequence is class-wide state because IDs must not repeat across facility instances in this process; it belongs to `Ticket`, not to one `ParkingFacility`. Occupancy is instance state because each facility has its own slots. The fee constants or immutable policy can be static. A `static` method is suitable for total issued tickets or pure fee helpers; a static initialization block is unnecessary for these simple constants. In a production multi-process deployment, IDs need a database sequence/UUID rather than a JVM static counter.

Objects are passed by value of reference in Java: methods receive a copy of the reference and can mutate the referenced slot/ticket, but cannot rebind the caller's variable. Entry returns the ticket or `null`; exit returns a fee. On departure the facility removes its active-map reference and frees the slot. If no other references survive, ticket and vehicle are collectible.

**Alternative:** An instance counter on `ParkingFacility` resets per facility, so two gates can issue duplicate IDs. For k facilities it needs O(k) counter storage; the shared static counter uses O(1) counter space, though IDs may reveal aggregate volume and must be synchronized/centralized for concurrent or distributed systems. The implementation's monotonically increasing counter is suitable only for this single-process example.

## Task 6 - Inheritance hierarchy and UML

```text
                  Vehicle (abstract)
                  /      |       \
       TwoWheeler       Car      DeliveryVan
          /               |
       EBike          ElectricCar ---- Chargeable
         |                 |
         +-----------------+ implements Chargeable

ParkingFacility *-- Slot[][]
ParkingFacility --> Ticket --> Vehicle
Ticket --> ParkingPass (optional)
ParkingPass --> UserType
```

`Vehicle` is the superclass. `TwoWheeler` and `Car` specialize rates; `EBike` and `ElectricCar` refine those classes and implement `Chargeable`. This is single plus multilevel inheritance. `super(regNo)` initializes the inherited registration and `super` constructor calls must be the first statement. For an `ElectricCar`, calls execute `Vehicle(String)`, `Car(String)`, then `ElectricCar(String)`. A UML diagram is expressed above in text; relationship markers `*--` mean composition and `-->` means association.

**Alternative composition:** One `Vehicle` holding a `VehicleType`/`PricingRule` object avoids a deep subclass tree. Adding a category requires a new rule class and registration in a factory, rather than subclassing; changing rates is easy when rules are injected. Fee calculation is one delegation call either way (O(1)); composition adds one rule reference per vehicle and possibly one rule object, while inheritance uses the object's runtime class metadata. Composition is preferable if rates vary by contract/time; inheritance remains clear for stable category behavior. The updated design can adopt `PricingRule` as rates grow.

## Task 7 - Overriding and specialization

`calculateFee(int)` is inherited and specialized by e-bike (flat fee) and van (business-hour window). `ratePerHour()` is overridden by vehicle category. Every override uses `@Override`, matching method name, parameter list, compatible return type and non-narrower visibility. `Vehicle.equals` and `hashCode` use normalized registration; two different runtime vehicle types with the same registration compare equal because registration identifies one physical vehicle in this model. This is intentional for duplicate prevention. `final` is used on immutable registration access/equality implementation to keep identity consistent; making every class final would impede extension, so the abstract/base classes remain extensible.

**Alternative switch:** One central switch on type can compute each rate in O(1), but each new category edits the shared pricing method and can break existing behavior. With k categories, the switch has O(k) source branches and grows in maintenance burden; overriding dispatch is O(1) virtual dispatch with behavior localized to each category. The main trade-off is more classes, which is justified as category-specific rules increase.

## Task 8 - Compile-time and runtime polymorphism

Overloading `calculateFee(int)` and `calculateFee(LocalDateTime, LocalDateTime)` is compile-time polymorphism; argument types select the signature. Runtime polymorphism occurs when a `Vehicle` reference points to an `ElectricCar` or `DeliveryVan` and the JVM dispatches `calculateFee`/`ratePerHour` to that object's override. Example trace: declared type `Vehicle`, actual object `DeliveryVan`, call `vehicle.calculateFee(entry, exit)`, selected implementation `DeliveryVan.calculateFee` applies the 09:00-17:00 window. This avoids repeated `instanceof` chains and keeps new behavior open for extension.

The supplied `FeeDemo` exercise is represented by the same source's hierarchy and fee tests; the expected baseline calculations are: two-wheeler TN01A1 at 130 minutes = Rs 30, car TN02B2 at 130 minutes = Rs 90, EV TN03C3 at 130 minutes plus 10 kWh = Rs 210 (hourly parking Rs 90 plus charging Rs 120 at Rs 12 per kWh). Equality by same registration prints `true`. Note: the compact facility demo waives the staff EV's parking fee but does not add kWh unless supplied by caller; charging is an explicitly separate capability.

**Alternative instanceof/casting:** A chain of k checks costs O(k) worst case per fee call and centralizes all category logic. Dynamic dispatch has O(1) selection (implementation body may have its own cost) and one override per specialized class. The switch/instanceof approach is simple for a fixed tiny set but becomes harder to extend and test as categories grow.

## Task 9 - Abstraction

`Vehicle` is abstract because a generic vehicle has no concrete rate. Its registration state and common fee calculation are shared; `ratePerHour()` is abstract. `Chargeable` is an interface because charging is a capability that only some vehicle types have and Java classes can implement multiple interfaces. `Payable` illustrates an orthogonal contract for payment policies, while concrete demo fees currently use `Ticket.fee`. Interface default/static methods suit small shared policy helpers or factories, but should not conceal category-specific business rules. If `Vehicle` became an interface, shared constructor/registration state would move into every class or a composed identity object; if `Chargeable` became an abstract class, it would block subclasses from extending another class and mix capability with identity. Dispatch cost is O(1) in either abstraction; per-object space depends on fields, not abstract-vs-interface declarations.

## Task 10 - Packages, access modifiers and encapsulation

Suggested package layout for a multi-file deployment:

```text
parking.model: Vehicle, subclasses, Slot, Ticket, ParkingPass, UserType
parking.service: ParkingFacility, PricingRule, allocation services
parking.app: CampusParking main/demo
parking.test: test cases
```

For ease of running as a single file in this submission, related types are nested under `CampusParking`; this preserves clear encapsulation without requiring a multi-file package setup. Fields are private/final where possible; slot fields are package-scoped within the enclosing source and the state is mutated only by facility logic; public methods form the usable API; constructors are package-visible for the demo. Getters expose registration but no setter can alter identity. Setters, when needed, should validate before assignment (nonnegative capacity/energy, valid slot coordinates). Wrapper types such as `Integer` allow null and have object identity concerns: compare numeric values with `.equals()` or unbox safely, never use `==` for numeric equality. Primitive `int` is preferred when absence is not meaningful.

**Alternative public fields:** If callers directly update fields, every call site must repeat validation, creating O(m) validation implementations across m update locations and risking inconsistent checks. Encapsulated setters validate O(1) per update in one location, with O(1) runtime overhead and a small additional method call; JIT can inline it. Encapsulation makes invariants enforceable and testable.

## Task 11 - Implementation and validation

The Java source includes demonstration checks for normal entry/exit, visitor hourly fees, valid staff pass, zero minutes, 60/61-minute boundaries, day pass, duplicate registration rejection, unknown exit, full capacity, charging-slot compatibility, negative duration and van-hours behavior. Expected outcomes are printed as `PASS`/`FAIL` with a summary. For a hand calculation: a visitor car parked 61 minutes owes 2 × Rs 30 = Rs 60; a visitor two-wheeler parked 61 minutes owes Rs 20; a valid staff pass owes Rs 0; a visitor day-pass stay owes Rs 100. The report's test table below records the expected behavior; actual results must be confirmed on the student's Java installation.

| Case | Expected result |
|---|---|
| Car 60 min / 61 min | Rs 30 / Rs 60 |
| Two-wheeler 61 min | Rs 20 |
| Eligible staff pass | Rs 0 |
| Visitor day pass | Rs 100 |
| Zero-minute stay | Rs 0 |
| Duplicate active registration | Rejected |
| Unknown registration on exit | Rejected |
| More vehicles than capacity | Additional entry returns no ticket |
| EV with charging capacity | Allocated to charging slot |
| Negative duration | Rejected |
| Van 08:00-10:00 | 1 billable peak-hour = Rs 60 |

**Alternative structures:** Active tickets in an array require O(n) search on exit and O(n) storage. An `ArrayList<Ticket>` has the same O(n) lookup and O(n) storage but simpler resizing. A `HashMap<String,Ticket>` uses expected O(1) lookup/insertion and O(n) storage, with hash-table overhead; this is used. Performance measurement should compare realistic n and repeated trials; asymptotic expectation does not replace local measurements. A known limitation is the single-process counter and in-memory state, which do not survive restart or coordinate concurrent gates.

## Task 12 - Day-four design challenge

The facility now has five floors with different capacities; e-bikes (flat parking fee and charging), delivery vans (double car rate from 09:00-17:00), and visitor one-day passes. The uniform `int slotsPerFloor` model is replaced by a jagged array constructed from per-floor capacities. `Vehicle` remains the shared contract; `EBike` and `DeliveryVan` are added by extension; `Chargeable` captures charging; `ParkingPass` models eligibility and day validity; `Ticket` includes user and pass context. Existing car/two-wheeler behavior remains in its class. The facility allocation/search interface continues to work with jagged floors and per-slot capabilities.

The original design is appropriate in principle but needs modification: a fixed uniform grid and only vehicle types do not express the new variation. Pricing must not spread through `ParkingFacility`; subclass overrides keep type-specific rules local. New types can be added through subclasses/interfaces, although introducing a new `SlotType` or pricing policy may require modifying allocation rules. In a larger solution, use injected `PricingRule` and `SlotRequirement` strategies so new rules need not change the facility. Existing tests for existing categories remain valid; new regression tests cover e-bike, van hours, visitor pass and uneven floors.

**Decision:** Modify the design, retaining `Vehicle`, `ParkingFacility`, and ticket allocation while extending the hierarchy and replacing the uniform floor sizing with jagged slot rows. The evidence is the preservation of existing rate behavior and explicit checks for new categories. Trade-offs: new subclasses are easy to add but inheritance can become deep; fee rules are O(1) per calculation, while extensible rule objects add one reference and a small dispatch cost. The revised slot scan is O(S), map lookup is expected O(1), and storage is O(S+N) for S slots and N active tickets. Before final submission, run all checks locally, record the actual Java version and console output, include input/output screenshots as the PDF demands, and publish the completed repository under `CSA09-192572147-Assignment1` with faculty access.

## Compile and run

Requires JDK 8 or newer. From the `src` folder:

```text
javac CampusParking.java
java CampusParking
```

The program prints the checks and pass/fail summary. This environment has a Java runtime but no `javac`, so the source was not compiled here; verify it with a JDK before submitting and include your own execution evidence.



