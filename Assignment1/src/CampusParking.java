import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Mohamed Aslam H | 192572147 | Campus Smart Parking assignment. Java 8+. */
public class CampusParking {
    enum UserType { STUDENT, STAFF, VISITOR }
    enum VehicleType { TWO_WHEELER, CAR, ELECTRIC_CAR, E_BIKE, DELIVERY_VAN }
    enum SlotType { STANDARD, CHARGING }

    interface Payable { double fee(double hours, LocalDateTime exit, boolean dayPass); }
    interface Chargeable { double chargingFee(double kWh); }

    static final class Slot {
        final int floor, number; final SlotType type; String regNo;
        Slot(int floor, int number, SlotType type) { this.floor=floor; this.number=number; this.type=type; }
        boolean free() { return regNo == null; }
    }

    static abstract class Vehicle {
        private final String regNo;
        Vehicle(String regNo) {
            if (regNo == null || regNo.trim().isEmpty()) throw new IllegalArgumentException("Registration required");
            this.regNo=regNo.trim().toUpperCase(Locale.ROOT);
        }
        public final String getRegNo() { return regNo; }
        abstract double ratePerHour();
        public double calculateFee(int minutes) {
            if (minutes < 0) throw new IllegalArgumentException("Negative duration");
            int billableHours = minutes == 0 ? 0 : (minutes + 59) / 60;
            return billableHours * ratePerHour();
        }
        public double calculateFee(LocalDateTime in, LocalDateTime out) {
            if (out.isBefore(in)) throw new IllegalArgumentException("Exit precedes entry");
            long minutes=ChronoUnit.MINUTES.between(in,out);
            return calculateFee((int)Math.min(Integer.MAX_VALUE, minutes));
        }
        @Override public final boolean equals(Object o) { return o instanceof Vehicle && regNo.equals(((Vehicle)o).regNo); }
        @Override public final int hashCode() { return regNo.hashCode(); }
        @Override public String toString() { return getClass().getSimpleName()+" "+regNo; }
    }
    static class TwoWheeler extends Vehicle { TwoWheeler(String r){super(r);} double ratePerHour(){return 10;} }
    static class Car extends Vehicle { Car(String r){super(r);} double ratePerHour(){return 30;} }
    static final class ElectricCar extends Car implements Chargeable {
        ElectricCar(String r){super(r);} @Override public double chargingFee(double kWh){if(kWh<0)throw new IllegalArgumentException();return kWh*12;}
    }
    static final class EBike extends TwoWheeler implements Chargeable {
        EBike(String r){super(r);} @Override public double calculateFee(int minutes){return minutes<0?badDuration():8;}
        private double badDuration(){throw new IllegalArgumentException("Negative duration");}
        @Override public double chargingFee(double kWh){if(kWh<0)throw new IllegalArgumentException();return kWh*5;}
    }
    static final class DeliveryVan extends Vehicle {
        DeliveryVan(String r){super(r);} double ratePerHour(){return 60;}
        @Override public double calculateFee(LocalDateTime in, LocalDateTime out) {
            if(out.isBefore(in))throw new IllegalArgumentException("Exit precedes entry");
            LocalDateTime start=LocalDateTime.of(in.toLocalDate(),LocalTime.of(9,0));
            LocalDateTime end=LocalDateTime.of(in.toLocalDate(),LocalTime.of(17,0));
            long mins=0; LocalDate d=in.toLocalDate();
            while(!d.isAfter(out.toLocalDate())) {
                LocalDateTime a=LocalDateTime.of(d,LocalTime.of(9,0)), b=LocalDateTime.of(d,LocalTime.of(17,0));
                LocalDateTime lo=in.isAfter(a)?in:a, hi=out.isBefore(b)?out:b;
                if(hi.isAfter(lo)) mins+=ChronoUnit.MINUTES.between(lo,hi);
                d=d.plusDays(1);
            }
            return calculateFee((int)Math.min(Integer.MAX_VALUE,mins));
        }
    }
    static final class ParkingPass {
        final UserType user; final LocalDate validOn;
        ParkingPass(UserType user,LocalDate validOn){this.user=user;this.validOn=validOn;}
        boolean valid(){return user!=UserType.VISITOR && validOn.equals(LocalDate.now());}
    }
    static final class Ticket {
        private static long nextId=1;
        final long id; final Vehicle vehicle; final UserType user; final ParkingPass pass;
        final Slot slot; final LocalDateTime entry;
        Ticket(Vehicle v,UserType u,ParkingPass p,Slot s,LocalDateTime t){id=nextId++;vehicle=v;user=u;pass=p;slot=s;entry=t;}
        double fee(LocalDateTime exit,boolean dayPass){
            if(pass!=null && pass.valid()) return 0;
            if(dayPass && user==UserType.VISITOR) return 100;
            if(vehicle instanceof DeliveryVan) return vehicle.calculateFee(entry,exit);
            return vehicle.calculateFee((int)Math.min(Integer.MAX_VALUE,ChronoUnit.MINUTES.between(entry,exit)));
        }
    }
    static final class ParkingFacility {
        private final Slot[][] floors; private final Map<String,Ticket> active=new LinkedHashMap<>();
        private static long nextTicketId=1; // facility-independent ID allocation below uses Ticket's global counter
        ParkingFacility(int[] slotsPerFloor,int[] chargingPerFloor) {
            if(slotsPerFloor.length!=chargingPerFloor.length)throw new IllegalArgumentException("Floor arrays differ");
            floors=new Slot[slotsPerFloor.length][];
            for(int f=0;f<floors.length;f++){
                if(slotsPerFloor[f]<0||chargingPerFloor[f]<0||chargingPerFloor[f]>slotsPerFloor[f])throw new IllegalArgumentException("Invalid capacity");
                floors[f]=new Slot[slotsPerFloor[f]];
                for(int s=0;s<floors[f].length;s++) floors[f][s]=new Slot(f,s,s<chargingPerFloor[f]?SlotType.CHARGING:SlotType.STANDARD);
            }
        }
        Slot allocate(Vehicle v){
            if(active.containsKey(v.getRegNo()))throw new IllegalArgumentException("Duplicate active registration");
            for(Slot[] floor:floors)for(Slot s:floor)if(s.free() && (!(v instanceof Chargeable)||s.type==SlotType.CHARGING)){s.regNo=v.getRegNo();return s;}
            return null;
        }
        Ticket enter(Vehicle v,UserType u,ParkingPass p,LocalDateTime now){
            if(u==null)throw new IllegalArgumentException("User required");
            if(p!=null&&p.user!=u)throw new IllegalArgumentException("Pass owner mismatch");
            Slot s=allocate(v); if(s==null)return null;
            Ticket t=new Ticket(v,u,p,s,now);active.put(v.getRegNo(),t);return t;
        }
        double exit(String reg,LocalDateTime now,boolean dayPass){
            Ticket t=active.get(reg.toUpperCase(Locale.ROOT));if(t==null)throw new IllegalArgumentException("No active ticket");
            double amount=t.fee(now,dayPass);t.slot.regNo=null;active.remove(t.vehicle.getRegNo());return amount;
        }
        Ticket find(String reg){return active.get(reg.toUpperCase(Locale.ROOT));}
        int occupancy(){return active.size();}
        static int totalTicketsIssued(){return (int)(Ticket.nextId-1);}
    }

    static final class TestResult {
        static int pass=0, fail=0;
        static void check(String name, boolean ok){System.out.println((ok?"PASS ":"FAIL ")+name);if(ok)pass++;else fail++;}
    }
    public static void main(String[] args) {
        ParkingFacility p=new ParkingFacility(new int[]{2,1,3,2,2},new int[]{1,1,1,1,1});
        LocalDateTime now=LocalDateTime.of(2026,11,18,10,0);
        Ticket t1=p.enter(new TwoWheeler("TN01A1"),UserType.VISITOR,null,now);
        Ticket t2=p.enter(new Car("TN02B2"),UserType.VISITOR,null,now);
        Ticket t3=p.enter(new ElectricCar("TN03C3"),UserType.STAFF,new ParkingPass(UserType.STAFF,LocalDate.now()),now);
        TestResult.check("normal entries and unique tickets", t1!=null&&t2!=null&&t3!=null&&t1.id!=t2.id);
        TestResult.check("visitor hourly fees", p.exit("TN01A1",now.plusMinutes(61),false)==20 && p.exit("TN02B2",now.plusMinutes(60),false)==30);
        TestResult.check("valid staff pass is free", p.exit("TN03C3",now.plusHours(2),false)==0);
        TestResult.check("zero-minute charge is zero", new Car("Z").calculateFee(0)==0);
        TestResult.check("fee slab exact boundary", new Car("B").calculateFee(60)==30 && new Car("C").calculateFee(61)==60);
        TestResult.check("visitor day pass", new Ticket(new Car("D"),UserType.VISITOR,null,new Slot(0,0,SlotType.STANDARD),now).fee(now.plusHours(3),true)==100);
        TestResult.check("duplicate active registration rejected", rejects(()->p.enter(new Car("TN02B2"),UserType.VISITOR,null,now)));
        TestResult.check("invalid exit rejected", rejects(()->p.exit("UNKNOWN",now,false)));
        TestResult.check("capacity boundary", fillAndCheckCapacity());
        TestResult.check("electric vehicle requires charging slot", chargingOnly());
        TestResult.check("baseline two-wheeler calculation", new TwoWheeler("TN01A1").calculateFee(130)==30);
        TestResult.check("baseline car calculation", new Car("TN02B2").calculateFee(130)==90);
        TestResult.check("EV parking and charging calculation", new ElectricCar("TN03C3").calculateFee(130)+new ElectricCar("TN03C3").chargingFee(10)==210);
        TestResult.check("negative duration rejected", rejects(()->new Car("N").calculateFee(-1)));
        TestResult.check("van charges only between 09:00 and 17:00", new DeliveryVan("V").calculateFee(LocalDateTime.of(2026,11,18,8,0),LocalDateTime.of(2026,11,18,10,0))==60);
        System.out.println("TESTS "+TestResult.pass+" passed, "+TestResult.fail+" failed");
    }
    interface Action{void run();}
    static boolean rejects(Action a){try{a.run();return false;}catch(IllegalArgumentException ex){return true;}}
    static boolean fillAndCheckCapacity(){ParkingFacility f=new ParkingFacility(new int[]{1},new int[]{0});return f.enter(new Car("A"),UserType.VISITOR,null,LocalDateTime.now())!=null&&f.enter(new Car("B"),UserType.VISITOR,null,LocalDateTime.now())==null;}
    static boolean chargingOnly(){ParkingFacility f=new ParkingFacility(new int[]{1},new int[]{1});return f.enter(new ElectricCar("E"),UserType.VISITOR,null,LocalDateTime.now())!=null;}
}



