package it.unibo.collektive.examples.geoChat.utils

import kotlin.Float.Companion.POSITIVE_INFINITY
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.aggregate.api.mapNeighborhood

/**
 * Builds a map that associates each neighboring device (identified by its ID)
 * with the distance at which it intends to send a message.
 *
 * This function is used to propagate message availability information through the network.
 * If a neighbor is not present in the [senders] map, it is considered to have no message to send,
 * and is therefore associated with a distance of [POSITIVE_INFINITY].
 *
 * @param senders A map of sender device IDs to pairs containing the distance and message content.
 * @return A map where each neighbor ID is associated with a distance value. Devices not in [senders]
 * are assigned [POSITIVE_INFINITY], effectively excluding them from further propagation.
 */
fun Aggregate<Int>.getListOfDevicesValues(
    senders: Map<Int, Pair<Float, String>>
) : Map<Int, Float>{
    return mapNeighborhood { id -> 
        if (senders.containsKey(id)) { senders.getValue(id).first } else { POSITIVE_INFINITY }
    }.toMap()
}
