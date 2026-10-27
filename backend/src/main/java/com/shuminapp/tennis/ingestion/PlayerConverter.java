package com.shuminapp.tennis.ingestion;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PlayerConverter {
    public record Player(int id, String first, String last, String hand, LocalDate dob,
                         String ioc, Short height, String wikidata) { }
    public record Issue(String field, String code, String action, String metric) { }
    public record Converted(Player player, List<Issue> issues) {
        public Converted { issues = List.copyOf(issues); }
    }
    public Converted convert(Map<String,String> raw) {
        List<Issue> issues = new ArrayList<>();
        Integer id = positiveInt(raw.get("player_id"));
        if (id == null) return new Converted(null, List.of(new Issue("player_id", "INVALID_PLAYER_ID", "QUARANTINE", "PLAYER_RECORD")));
        String first = empty(raw.get("name_first")), last = empty(raw.get("name_last"));
        String hand = formatted(raw.get("hand"), "[RLUA]", "hand", issues);
        String ioc = formatted(raw.get("ioc"), "[A-Z]{3}", "ioc", issues);
        String wiki = formatted(raw.get("wikidata_id"), "Q[1-9][0-9]*", "wikidata_id", issues);
        LocalDate dob = null;
        String date = raw.get("dob");
        if (date != null && !date.isEmpty()) {
            try {
                if (!date.matches("[0-9]{8}") || date.startsWith("0000")) throw new IllegalArgumentException();
                dob = LocalDate.parse(date, DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT));
            } catch (RuntimeException e) { issues.add(new Issue("dob","INVALID_DOB","FLAG","AGE_ANALYSIS")); }
        }
        Short height = null;
        String h = raw.get("height");
        if (h != null && !h.isEmpty()) {
            Integer parsed = positiveInt(h);
            if (parsed == null || parsed > Short.MAX_VALUE) issues.add(new Issue("height","INVALID_HEIGHT","FLAG","HEIGHT_ANALYSIS"));
            else {
                height = parsed.shortValue();
                if (height < 100 || height > 250) issues.add(new Issue("height","SUSPECT_HEIGHT","FLAG","HEIGHT_ANALYSIS"));
            }
        }
        String name = ((first == null ? "" : first) + " " + (last == null ? "" : last)).strip().toLowerCase(Locale.ROOT);
        if (List.of("u unknown", "unknown", "x x").contains(name)) {
            issues.add(new Issue("name", "PLACEHOLDER_PLAYER", "FLAG", "PLAYER_SEARCH"));
            issues.add(new Issue("name", "PLACEHOLDER_PERSONAL_STATS", "FLAG", "PERSONAL_STATS"));
        } else if (name.contains("unknown")) issues.add(new Issue("name","REVIEW_PLAYER_NAME","FLAG","PERSONAL_STATS"));
        return new Converted(new Player(id,first,last,hand,dob,ioc,height,wiki),issues);
    }
    private String formatted(String value, String pattern, String field, List<Issue> issues) {
        if (value == null || value.isEmpty()) return null;
        if (value.matches(pattern)) return value;
        issues.add(new Issue(field,"INVALID_"+field.toUpperCase(Locale.ROOT),"FLAG",field.toUpperCase(Locale.ROOT)+"_ANALYSIS"));
        return null;
    }
    private Integer positiveInt(String value) {
        try { if (value == null || !value.matches("[0-9]+")) return null; int n=Integer.parseInt(value); return n>0?n:null; }
        catch (NumberFormatException e) { return null; }
    }
    private String empty(String value) { return value == null || value.isEmpty() ? null : value; }
}
