package com.example.ridehailing.web;

import com.example.ridehailing.simulator.DriverSimulator;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/simulator")
public class SimulatorController {
    private final DriverSimulator simulator;

    public SimulatorController(DriverSimulator simulator) { this.simulator = simulator; }

    @PostMapping("/start")
    public Map<String, Object> start(@RequestParam(defaultValue = "10") int count,
                                     @RequestParam(defaultValue = "37.7749") double lat,
                                     @RequestParam(defaultValue = "-122.4194") double lon,
                                     @RequestParam(defaultValue = "2") double spreadKm,
                                     @RequestParam(defaultValue = "1000") long intervalMs) {
        simulator.start(count, lat, lon, spreadKm, intervalMs);
        return status();
    }

    @PostMapping("/stop")
    public Map<String, Object> stop() {
        simulator.stop();
        return status();
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("running", simulator.isRunning(), "drivers", simulator.driverCount());
    }
}
