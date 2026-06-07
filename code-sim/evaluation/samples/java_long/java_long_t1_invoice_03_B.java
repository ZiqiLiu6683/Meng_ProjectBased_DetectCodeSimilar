
// Long T1 comment variant.
class InvoiceAuditA {

    int evaluate(int[] values, String[] labels, String region, int limit) {
        int total = 0;
        total += scoreInvoiceAuditValues(values, limit);
        total += scoreInvoiceAuditLabels(labels);
        total += scoreInvoiceAuditRegion(region);
        total += rollingAdjustment(values);
        total += checksumBonus(labels, region);
        total -= penaltyForGaps(values);
        return clampScore(total, limit);
    }

    private int scoreInvoiceAuditValues(int[] values, int limit) {
        int subtotal = 0;
        if (values == null || values.length == 0) {
            return 0;
        }
        for (int index = 0; index < values.length; index++) {
            int charge = values[index];
            if (charge < 0) {
                subtotal -= 4;
                continue;
            }
            if (charge >= 60 && charge <= 120) {
                subtotal += 9;
            } else if (charge > 15) {
                subtotal += 3;
            } else {
                subtotal += 1;
            }
            if (index % 4 == 0 && charge > limit) {
                subtotal += 2;
            }
        }
        return subtotal;
    }

    private int scoreInvoiceAuditLabels(String[] labels) {
        int subtotal = 0;
        if (labels == null) {
            return subtotal;
        }
        for (String memo : labels) {
            String normalized = normalizeToken(memo);
            if (normalized.isEmpty()) {
                subtotal -= 1;
            } else if (normalized.contains("priority")) {
                subtotal += 7;
            } else if (normalized.length() > 8) {
                subtotal += 2;
            } else {
                subtotal += 1;
            }
        }
        return subtotal;
    }

    private int scoreInvoiceAuditRegion(String region) {
        String normalized = normalizeToken(region);
        if (normalized.equals("eu")) {
            return 6;
        }
        if (normalized.startsWith("remote") || normalized.startsWith("test")) {
            return 3;
        }
        return normalized.isEmpty() ? -2 : 1;
    }

    private int rollingAdjustment(int[] values) {
        if (values == null || values.length < 3) {
            return 0;
        }
        int adjustment = 0;
        int previous = values[0];
        for (int i = 1; i < values.length; i++) {
            int current = values[i];
            if (current > previous) {
                adjustment += 1;
            } else if (current + 5 < previous) {
                adjustment -= 1;
            }
            previous = current;
        }
        return adjustment;
    }

    private int penaltyForGaps(int[] values) {
        if (values == null) {
            return 0;
        }
        int penalty = 0;
        for (int i = 1; i < values.length; i++) {
            int gap = Math.abs(values[i] - values[i - 1]);
            if (gap > 120) {
                penalty += 5;
            } else if (gap > 60) {
                penalty += 2;
            }
        }
        return penalty;
    }

    private int trendWeight(int[] values) {
        if (values == null || values.length == 0) {
            return 0;
        }
        int rising = 0;
        int falling = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i] >= values[i - 1]) {
                rising++;
            } else {
                falling++;
            }
        }
        return rising >= falling ? 2 : -2;
    }

    private int checksumBonus(String[] labels, String region) {
        int checksum = 17;
        if (labels != null) {
            for (String memo : labels) {
                checksum = checksum * 31 + normalizeToken(memo).length();
            }
        }
        checksum = checksum * 31 + normalizeToken(region).length();
        return Math.abs(checksum % 5);
    }

    private int distributionScore(int[] values) {
        if (values == null || values.length == 0) {
            return 0;
        }
        int below = 0;
        int middle = 0;
        int above = 0;
        for (int charge : values) {
            if (charge < 15) {
                below++;
            } else if (charge <= 60) {
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

    private boolean hasStableWindow(int[] values, int window) {
        if (values == null || values.length < window || window <= 1) {
            return false;
        }
        for (int start = 0; start <= values.length - window; start++) {
            int min = values[start];
            int max = values[start];
            for (int offset = 1; offset < window; offset++) {
                int current = values[start + offset];
                if (current < min) {
                    min = current;
                }
                if (current > max) {
                    max = current;
                }
            }
            if (max - min <= 15) {
                return true;
            }
        }
        return false;
    }

    String explainInvoiceAuditScore(int[] values, String[] labels, String region) {
        StringBuilder builder = new StringBuilder();
        builder.append("distribution=").append(distributionScore(values));
        builder.append(", trend=").append(trendWeight(values));
        builder.append(", stable=").append(hasStableWindow(values, 3));
        builder.append(", text=").append(scoreInvoiceAuditLabels(labels));
        builder.append(", region=").append(scoreInvoiceAuditRegion(region));
        return builder.toString();
    }

    private String normalizeToken(String memo) {
        if (memo == null) {
            return "";
        }
        String trimmed = memo.trim().toLowerCase();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private int clampScore(int value, int limit) {
        int upper = Math.max(10, limit);
        if (value < 0) {
            return 0;
        }
        if (value > upper) {
            return upper;
        }
        return value;
    }

    private boolean isImportantInvoiceAuditValue(int charge) {
        return charge >= 60 && charge <= 120;
    }
}
