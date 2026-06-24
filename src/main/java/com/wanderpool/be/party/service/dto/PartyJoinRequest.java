package com.wanderpool.be.party.service.dto;

import jakarta.validation.constraints.*;

public record PartyJoinRequest(

        @NotBlank
        @Size(max = 100)
        String pickupName,

        @NotNull
        @DecimalMin("-90.0") @DecimalMax("90.0")
        Double pickupLat,

        @NotNull
        @DecimalMin("-180.0") @DecimalMax("180.0")
        Double pickupLng,

        @NotBlank
        @Size(max = 100)
        String dropoffName,

        @NotNull
        @DecimalMin("-90.0") @DecimalMax("90.0")
        Double dropoffLat,

        @NotNull
        @DecimalMin("-180.0") @DecimalMax("180.0")
        Double dropoffLng,

        @NotNull
        @Min(0)
        Integer pointCost

) {
        public PartyJoinRequest(
                String pickupName,
                Double pickupLat,
                Double pickupLng,
                String dropoffName,
                Integer pointCost
        ) {
                this(pickupName, pickupLat, pickupLng, dropoffName, pickupLat, pickupLng, pointCost);
        }
}
