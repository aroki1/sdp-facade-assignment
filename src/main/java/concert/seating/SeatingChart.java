package concert.seating;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Business logic: venue layout and seat validation. */
public class SeatingChart {
    public enum Section {
        VIP('A', 'B', 8, 15_000),
        FLOOR('C', 'F', 10, 9_000),
        BALCONY('G', 'H', 12, 4_500);

        private final char firstRow;
        private final char lastRow;
        private final int seatsPerRow;
        private final long basePriceCents;

        Section(char firstRow, char lastRow, int seatsPerRow, long basePriceCents) {
            this.firstRow = firstRow;
            this.lastRow = lastRow;
            this.seatsPerRow = seatsPerRow;
            this.basePriceCents = basePriceCents;
        }

        public long basePriceCents() {
            return basePriceCents;
        }

        public int capacity() {
            return (lastRow - firstRow + 1) * seatsPerRow;
        }

        boolean containsRow(char row) {
            return row >= firstRow && row <= lastRow;
        }
    }

    private static final Pattern SEAT_CODE = Pattern.compile("([A-Z])(\\d{1,2})");

    public String normalise(String seatCode) {
        return seatCode == null ? "" : seatCode.trim().toUpperCase(Locale.ROOT);
    }

    public boolean exists(String seatCode) {
        return findSection(normalise(seatCode)) != null;
    }

    public Section sectionOf(String seatCode) {
        Section section = findSection(normalise(seatCode));
        if (section == null) {
            throw new IllegalArgumentException("No such seat in this venue: '" + seatCode + "'");
        }
        return section;
    }

    public List<String> seatsIn(Section section) {
        List<String> seats = new ArrayList<>();
        for (char row = section.firstRow; row <= section.lastRow; row++) {
            for (int n = 1; n <= section.seatsPerRow; n++) {
                seats.add(String.valueOf(row) + n);
            }
        }
        return seats;
    }

    private Section findSection(String code) {
        Matcher m = SEAT_CODE.matcher(code);
        if (!m.matches()) {
            return null;
        }
        char row = m.group(1).charAt(0);
        int number = Integer.parseInt(m.group(2));
        for (Section s : Section.values()) {
            if (s.containsRow(row) && number >= 1 && number <= s.seatsPerRow) {
                return s;
            }
        }
        return null;
    }
}
