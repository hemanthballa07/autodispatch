package com.autodispatch.dispatch.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static com.autodispatch.dispatch.internal.RideStatus.ARRIVED;
import static com.autodispatch.dispatch.internal.RideStatus.ASSIGNED;
import static com.autodispatch.dispatch.internal.RideStatus.BROADCASTING;
import static com.autodispatch.dispatch.internal.RideStatus.CANCELLED;
import static com.autodispatch.dispatch.internal.RideStatus.COMPLETED;
import static com.autodispatch.dispatch.internal.RideStatus.EXPIRED;
import static com.autodispatch.dispatch.internal.RideStatus.IN_PROGRESS;
import static com.autodispatch.dispatch.internal.RideStatus.REQUESTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pure domain test: the FULL 8x8 transition matrix against the spec, plus the
 * rebroadcast guard. The LEGAL map below is an independent restatement of the
 * locked design, NOT a reference to the production map — so a production
 * regression cannot silently rewrite the expectation.
 */
class RideTransitionMatrixTest {

    private static final Map<RideStatus, Set<RideStatus>> LEGAL = Map.of(
            REQUESTED, EnumSet.of(BROADCASTING, CANCELLED),
            BROADCASTING, EnumSet.of(ASSIGNED, EXPIRED, CANCELLED),
            ASSIGNED, EnumSet.of(ARRIVED, CANCELLED, BROADCASTING),
            ARRIVED, EnumSet.of(IN_PROGRESS),
            IN_PROGRESS, EnumSet.of(COMPLETED),
            COMPLETED, EnumSet.noneOf(RideStatus.class),
            CANCELLED, EnumSet.noneOf(RideStatus.class),
            EXPIRED, EnumSet.noneOf(RideStatus.class));

    /** Legal path that drives a fresh ride into each state. */
    private static final Map<RideStatus, List<RideStatus>> PATH_TO = Map.of(
            REQUESTED, List.of(),
            BROADCASTING, List.of(BROADCASTING),
            ASSIGNED, List.of(BROADCASTING, ASSIGNED),
            ARRIVED, List.of(BROADCASTING, ASSIGNED, ARRIVED),
            IN_PROGRESS, List.of(BROADCASTING, ASSIGNED, ARRIVED, IN_PROGRESS),
            COMPLETED, List.of(BROADCASTING, ASSIGNED, ARRIVED, IN_PROGRESS, COMPLETED),
            CANCELLED, List.of(CANCELLED),
            EXPIRED, List.of(BROADCASTING, EXPIRED));

    static Stream<Arguments> fullMatrix() {
        return Arrays.stream(RideStatus.values())
                .flatMap(from -> Arrays.stream(RideStatus.values())
                        .map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("fullMatrix")
    void every_edge_of_the_matrix_behaves_per_spec(RideStatus from, RideStatus to) {
        Ride ride = rideIn(from);
        if (LEGAL.get(from).contains(to)) {
            ride.transitionTo(to);
            assertEquals(to, ride.getStatus());
        } else {
            assertThrows(IllegalRideTransitionException.class, () -> ride.transitionTo(to));
            assertEquals(from, ride.getStatus(), "status must be unchanged after a rejected transition");
        }
    }

    @Test
    void second_driver_cancel_cannot_return_ride_to_broadcasting() {
        Ride ride = rideIn(ASSIGNED);

        ride.transitionTo(BROADCASTING); // first driver-cancel: allowed
        assertEquals(1, ride.getRebroadcastCount());

        ride.transitionTo(ASSIGNED);     // re-assigned

        assertThrows(IllegalRideTransitionException.class,
                () -> ride.transitionTo(BROADCASTING),
                "second driver-cancel must be rejected by the rebroadcast guard");
        assertEquals(ASSIGNED, ride.getStatus());
        assertEquals(1, ride.getRebroadcastCount());
    }

    private Ride rideIn(RideStatus target) {
        Ride ride = new Ride(UUID.randomUUID(), "Main Gate", "Library");
        PATH_TO.get(target).forEach(ride::transitionTo);
        return ride;
    }
}
