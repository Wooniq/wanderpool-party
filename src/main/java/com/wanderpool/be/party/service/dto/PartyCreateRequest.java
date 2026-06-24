package com.wanderpool.be.party.service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public record PartyCreateRequest(

        @Size(max = 200)
        String title,

        String description,

        @NotBlank
        @Size(max = 100)
        String originName,

        @NotNull
        @DecimalMin("-90.0") @DecimalMax("90.0")
        Double originLat,

        @NotNull
        @DecimalMin("-180.0") @DecimalMax("180.0")
        Double originLng,

        @NotBlank
        @Size(max = 100)
        String destinationName,

        @NotNull
        @DecimalMin("-90.0") @DecimalMax("90.0")
        Double destLat,

        @NotNull
        @DecimalMin("-180.0") @DecimalMax("180.0")
        Double destLng,

        @NotNull
        LocalDateTime departureTime,

        @NotNull
        LocalDateTime arrivalTime,

        @NotNull
        @Min(1)
        Integer maxPassengers,

        @Size(max = 3)
        List<@NotNull @Valid WaypointRequest> waypoints

) {
    public record WaypointRequest(

            @Min(1) @Max(3)
            int orderIndex,

            @NotBlank
            @Size(max = 100)
            String name,

            @NotNull
            @DecimalMin("-90.0") @DecimalMax("90.0")
            Double latitude,

            @NotNull
            @DecimalMin("-180.0") @DecimalMax("180.0")
            Double longitude

    ) {}
}
