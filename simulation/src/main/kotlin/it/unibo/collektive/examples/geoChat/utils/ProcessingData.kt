package it.unibo.collektive.examples.geoChat.utils

import kotlin.Float.Companion.POSITIVE_INFINITY
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.aggregate.api.mapNeighborhood

/**
 * Constructs a map associating each neighboring device (identified by its ID)
 * with the distance at which it intends to broadcast a message.
 *
 * This function is typically used during the gradient-cast propagation phase
 * to inform neighbors of potential message sources and their respective distances.
 *
 * Each entry in the [senders] map represents a device that has broadcasted a message,
 * with values encoded as a [Triple] of:
 * - the propagation distance,
 * - the message content,
 * - the `sourceCount` (i.e., number of times the node has been a source).
 *
 * If a neighbor is not listed among the [senders], it is assumed to be inactive
 * or not currently broadcasting, and is assigned a value of [POSITIVE_INFINITY]
 * to effectively exclude it from message propagation.
 *
 * @param senders A map where keys are sender device IDs and values are triples containing:
 *   1. The distance from the source,
 *   2. The message content,
 *   3. The number of times the sender has acted as a source (`sourceCounter`).
 *
 * @return A map where each neighbor ID is associated with its message broadcast distance.
 *         Devices not included in [senders] are assigned [POSITIVE_INFINITY].
 */
fun Aggregate<Int>.getListOfDevicesValues(
    senders: Map<Int, Triple<Float, String, Int>>
): Map<Int, Float> =
    mapNeighborhood { id ->
        senders[id]?.first ?: POSITIVE_INFINITY
    }.toMap()

