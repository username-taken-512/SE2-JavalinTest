package models;

public class Household {
    private int householdId;
    private String name;

    public Household() {
    }

    public Household(int householdId, String name) {
        this.householdId = householdId;
        this.name = name;
    }

    public int getHouseholdId() {
        return householdId;
    }

    public void setHouseholdId(int householdId) {
        this.householdId = householdId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
