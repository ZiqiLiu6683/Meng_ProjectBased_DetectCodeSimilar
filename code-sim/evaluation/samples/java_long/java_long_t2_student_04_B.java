
class StudentProfileB {

    int compute(int[] entries, String[] tokens, String zone, int cap) {
        int score = 0;
        score += scoreStudentProfileValues(entries, cap);
        score += scoreStudentProfileLabels(tokens);
        score += scoreStudentProfileRegion(zone);
        score += rollingAdjustment(entries);
        score += checksumBonus(tokens, zone);
        score -= penaltyForGaps(entries);
        return clampScore(score, cap);
    }

    private int scoreStudentProfileValues(int[] entries, int cap) {
        int subtotal = 0;
        if (entries == null || entries.length == 0) {
            return 0;
        }
        for (int index = 0; index < entries.length; index++) {
            int scoreValue = entries[index];
            if (scoreValue < 0) {
                subtotal -= 4;
                continue;
            }
            if (scoreValue >= 70 && scoreValue <= 95) {
                subtotal += 9;
            } else if (scoreValue > 30) {
                subtotal += 3;
            } else {
                subtotal += 1;
            }
            if (index % 4 == 0 && scoreValue > cap) {
                subtotal += 2;
            }
        }
        return subtotal;
    }

    private int scoreStudentProfileLabels(String[] tokens) {
        int subtotal = 0;
        if (tokens == null) {
            return subtotal;
        }
        for (String noteText : tokens) {
            String normalized = normalizeToken(noteText);
            if (normalized.isEmpty()) {
                subtotal -= 1;
            } else if (normalized.contains("honor")) {
                subtotal += 7;
            } else if (normalized.length() > 8) {
                subtotal += 2;
            } else {
                subtotal += 1;
            }
        }
        return subtotal;
    }

    private int scoreStudentProfileRegion(String zone) {
        String normalized = normalizeToken(zone);
        if (normalized.equals("online")) {
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
            if (gap > 95) {
                penalty += 5;
            } else if (gap > 70) {
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
            for (String noteText : tokens) {
                checksum = checksum * 31 + normalizeToken(noteText).length();
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
        for (int scoreValue : entries) {
            if (scoreValue < 30) {
                below++;
            } else if (scoreValue <= 70) {
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
            if (max - min <= 30) {
                return true;
            }
        }
        return false;
    }

    String explainStudentProfileScore(int[] entries, String[] tokens, String zone) {
        StringBuilder builder = new StringBuilder();
        builder.append("distribution=").append(distributionScore(entries));
        builder.append(", trend=").append(trendWeight(entries));
        builder.append(", stable=").append(hasStableWindow(entries, 3));
        builder.append(", text=").append(scoreStudentProfileLabels(tokens));
        builder.append(", region=").append(scoreStudentProfileRegion(zone));
        return builder.toString();
    }

    private String normalizeToken(String noteText) {
        if (noteText == null) {
            return "";
        }
        String trimmed = noteText.trim().toLowerCase();
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

    private boolean isImportantStudentProfileValue(int scoreValue) {
        return scoreValue >= 70 && scoreValue <= 95;
    }
}
