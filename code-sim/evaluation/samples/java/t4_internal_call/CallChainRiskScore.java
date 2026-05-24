public class CallChainRiskScore {
    public int risk(int age, int incidents, boolean verified) {
        return ageRisk(age) + incidentRisk(incidents) + verificationRisk(verified);
    }

    private int ageRisk(int age) {
        return age < 21 ? 20 : 0;
    }

    private int incidentRisk(int incidents) {
        return incidents > 2 ? incidents * 10 : 0;
    }

    private int verificationRisk(boolean verified) {
        return verified ? 0 : 15;
    }
}
