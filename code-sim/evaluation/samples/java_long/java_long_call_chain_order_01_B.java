
class OrderRiskE {

    int compute(int[] entries, String[] tokens, String zone, int cap) {
        int stageOne = collectNumericEvidence(entries, cap);
        int stageTwo = collectTextEvidence(tokens, zone);
        int stageThree = collectStabilityEvidence(entries, tokens);
        return clampScore(stageOne + stageTwo + stageThree, cap);
    }

    private int collectNumericEvidence(int[] entries, int cap) {
        int result = scoreOrderRiskValues(entries, cap);
        result += rollingAdjustment(entries);
        result -= penaltyForGaps(entries);
        return result;
    }

    private int collectTextEvidence(String[] tokens, String zone) {
        return scoreOrderRiskLabels(tokens) + scoreOrderRiskRegion(zone);
    }

    private int collectStabilityEvidence(int[] entries, String[] tokens) {
        return checksumBonus(tokens, "CN") + trendWeight(entries);
    }

    private int scoreOrderRiskValues(int[] entries, int cap) {
        int subtotal = 0;
        if (entries == null || entries.length == 0) {
            return 0;
        }
        for (int index = 0; index < entries.length; index++) {
            int amountValue = entries[index];
            if (amountValue < 0) {
                subtotal -= 4;
                continue;
            }
            if (isImportantOrderRiskValue(amountValue)) {
                subtotal += 9;
            } else if (amountValue > 12) {
                subtotal += 3;
            } else {
                subtotal += 1;
            }
            if (index % 4 == 0 && amountValue > cap) {
                subtotal += 2;
            }
        }
        return subtotal;
    }

    private int scoreOrderRiskLabels(String[] tokens) {
        int subtotal = 0;
        if (tokens == null) {
            return subtotal;
        }
        for (String emailText : tokens) {
            String normalized = normalizeToken(emailText);
            if (normalized.isEmpty()) {
                subtotal -= 1;
            } else if (normalized.contains("vip")) {
                subtotal += 7;
            } else if (normalized.length() > 8) {
                subtotal += 2;
            } else {
                subtotal += 1;
            }
        }
        return subtotal;
    }

    private int scoreOrderRiskRegion(String zone) {
        String normalized = normalizeToken(zone);
        if (normalized.equals("cn")) {
            return 6;
        }
        if (normalized.startsWith("remote") || normalized.startsWith("test")) {
            return 3;
        }
        return normalized.isEmpty() ? -2 : 1;
    }

    private int rollingAdjustment(int[] entries) {
        if (entries == null || entries.length < 3) {
            return 0;
        }
        int adjustment = 0;
        int previous = entries[0];
        for (int i = 1; i < entries.length; i++) {
            int current = entries[i];
            if (current > previous) {
                adjustment += 1;
            } else if (current + 5 < previous) {
                adjustment -= 1;
            }
            previous = current;
        }
        return adjustment;
    }

    private int penaltyForGaps(int[] entries) {
        if (entries == null) {
            return 0;
        }
        int penalty = 0;
        for (int i = 1; i < entries.length; i++) {
            int gap = Math.abs(entries[i] - entries[i - 1]);
            if (gap > 90) {
                penalty += 5;
            } else if (gap > 45) {
                penalty += 2;
            }
        }
        return penalty;
    }

    private int trendWeight(int[] entries) {
        if (entries == null || entries.length == 0) {
            return 0;
        }
        int rising = 0;
        int falling = 0;
        for (int i = 1; i < entries.length; i++) {
            if (entries[i] >= entries[i - 1]) {
                rising++;
            } else {
                falling++;
            }
        }
        return rising >= falling ? 2 : -2;
    }

    private int checksumBonus(String[] tokens, String zone) {
        int checksum = 17;
        if (tokens != null) {
            for (String emailText : tokens) {
                checksum = checksum * 31 + normalizeToken(emailText).length();
            }
        }
        checksum = checksum * 31 + normalizeToken(zone).length();
        return Math.abs(checksum % 5);
    }

    private int distributionScore(int[] entries) {
        if (entries == null || entries.length == 0) {
            return 0;
        }
        int below = 0;
        int middle = 0;
        int above = 0;
        for (int amountValue : entries) {
            if (amountValue < 12) {
                below++;
            } else if (amountValue <= 45) {
                middle++;
            } else {
                above++;
            }
        }
        if (above > middle && above > below) {
            return 4;
        }
        if (middle >= below) {
            return 2;
        }
        return -1;
    }

    private boolean hasStableWindow(int[] entries, int window) {
        if (entries == null || entries.length < window || window <= 1) {
            return false;
        }
        for (int start = 0; start <= entries.length - window; start++) {
            int min = entries[start];
            int max = entries[start];
            for (int offset = 1; offset < window; offset++) {
                int current = entries[start + offset];
                if (current < min) {
                    min = current;
                }
                if (current > max) {
                    max = current;
                }
            }
            if (max - min <= 12) {
                return true;
            }
        }
        return false;
    }

    String explainOrderRiskScore(int[] entries, String[] tokens, String zone) {
        StringBuilder builder = new StringBuilder();
        builder.append("distribution=").append(distributionScore(entries));
        builder.append(", trend=").append(trendWeight(entries));
        builder.append(", stable=").append(hasStableWindow(entries, 3));
        builder.append(", text=").append(scoreOrderRiskLabels(tokens));
        builder.append(", region=").append(scoreOrderRiskRegion(zone));
        return builder.toString();
    }

    private String normalizeToken(String emailText) {
        if (emailText == null) {
            return "";
        }
        String trimmed = emailText.trim().toLowerCase();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private int clampScore(int value, int cap) {
        int upper = Math.max(10, cap);
        if (value < 0) {
            return 0;
        }
        if (value > upper) {
            return upper;
        }
        return value;
    }

    private boolean isImportantOrderRiskValue(int amountValue) {
        return amountValue >= 45 && amountValue <= 90;
    }
}
