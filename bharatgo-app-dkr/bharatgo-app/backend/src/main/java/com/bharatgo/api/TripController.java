package com.bharatgo.api;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class TripController {
    private final JdbcTemplate jdbc;
    public TripController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/health")
    public Map<String, String> health() { return Map.of("status", "ok", "service", "bharatgo-api", "java", "21"); }

    @GetMapping("/ready")
    public ResponseEntity<Map<String, String>> ready() {
        try { jdbc.queryForObject("SELECT 1", Integer.class); return ResponseEntity.ok(Map.of("status", "ready", "database", "connected")); }
        catch (Exception e) { return ResponseEntity.status(503).body(Map.of("status", "not_ready", "database", "unavailable")); }
    }

    @GetMapping("/trips")
    public List<Map<String, Object>> allTrips() {
        return jdbc.queryForList("SELECT id,destination,start_date,end_date,budget,travelers,notes,created_at,updated_at FROM trips ORDER BY created_at DESC");
    }

    @PostMapping("/trips")
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        TripInput t = validate(body);
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO trips(destination,start_date,end_date,budget,travelers,notes) VALUES(?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            bind(ps, t); return ps;
        }, key);
        return ResponseEntity.status(HttpStatus.CREATED).body(getTrip(key.getKey().longValue()));
    }

    @PutMapping("/trips/{id}")
    public Map<String, Object> update(@PathVariable long id, @RequestBody Map<String, Object> body) {
        if (id < 1) throw bad("Invalid trip ID.");
        TripInput t = validate(body);
        int changed = jdbc.update("UPDATE trips SET destination=?,start_date=?,end_date=?,budget=?,travelers=?,notes=? WHERE id=?",
                t.destination, t.startDate, t.endDate, t.budget, t.travelers, t.notes, id);
        if (changed == 0 && !exists(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found.");
        return getTrip(id);
    }

    @DeleteMapping("/trips/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        if (id < 1) throw bad("Invalid trip ID.");
        if (jdbc.update("DELETE FROM trips WHERE id=?", id) == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found.");
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> getTrip(long id) {
        try { return jdbc.queryForMap("SELECT id,destination,start_date,end_date,budget,travelers,notes,created_at,updated_at FROM trips WHERE id=?", id); }
        catch (EmptyResultDataAccessException e) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found."); }
    }
    private boolean exists(long id) { return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM trips WHERE id=?)", Boolean.class, id)); }
    private void bind(PreparedStatement ps, TripInput t) throws java.sql.SQLException {
        ps.setString(1, t.destination); ps.setObject(2, t.startDate); ps.setObject(3, t.endDate); ps.setBigDecimal(4, t.budget); ps.setInt(5, t.travelers); ps.setString(6, t.notes);
    }

    private TripInput validate(Map<String, Object> body) {
        Object rawDestination = body.get("destination");
        String destination = rawDestination instanceof String s ? s.trim() : "";
        if (destination.isEmpty() || destination.length() > 120) throw bad("Destination is required (maximum 120 characters).");
        int travelers = 2;
        try { if (body.get("travelers") != null) travelers = new BigDecimal(body.get("travelers").toString()).intValueExact(); }
        catch (Exception e) { throw bad("Travellers must be a whole number from 1 to 30."); }
        if (travelers < 1 || travelers > 30) throw bad("Travellers must be a whole number from 1 to 30.");
        BigDecimal budget = null;
        Object rawBudget = body.get("budget");
        if (rawBudget != null && !rawBudget.toString().isBlank()) {
            try { budget = new BigDecimal(rawBudget.toString()); }
            catch (Exception e) { throw bad("Budget must be a valid number."); }
            if (budget.signum() < 0 || budget.compareTo(new BigDecimal("100000000")) > 0) throw bad("Budget must be between 0 and 100,000,000.");
        }
        LocalDate start = parseDate(body.get("start_date"), "Start");
        LocalDate end = parseDate(body.get("end_date"), "End");
        if (start != null && end != null && end.isBefore(start)) throw bad("End date must be on or after the start date.");
        Object rawNotes = body.get("notes"); String notes = rawNotes instanceof String s ? s.trim() : "";
        if (notes.length() > 1000) throw bad("Notes must be 1000 characters or fewer.");
        return new TripInput(destination, start, end, budget, travelers, notes);
    }
    private LocalDate parseDate(Object value, String label) {
        if (value == null || value.toString().isBlank()) return null;
        try { return LocalDate.parse(value.toString()); }
        catch (DateTimeParseException e) { throw bad(label + " date must use YYYY-MM-DD."); }
    }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private record TripInput(String destination, LocalDate startDate, LocalDate endDate, BigDecimal budget, int travelers, String notes) {}

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleStatus(ResponseStatusException ex) {
        String message = ex.getReason() == null ? "Request failed." : ex.getReason();
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("error", message));
    }
}
