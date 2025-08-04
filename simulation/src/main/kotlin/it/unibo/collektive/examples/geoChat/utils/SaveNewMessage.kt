package it.unibo.collektive.examples.geoChat.utils

import it.unibo.collektive.aggregate.api.Aggregate
import kotlin.Float.Companion.POSITIVE_INFINITY
import it.unibo.collektive.aggregate.api.neighboring
import it.unibo.collektive.stdlib.spreading.gradientCast
import it.unibo.collektive.stdlib.util.Point3D
import it.unibo.collektive.stdlib.util.euclideanDistance3D

/**
 * Data class representing the distances from a source device to another non-source device.
 */
data class SourceDistances(
    /**
     * Contains the id of the source node being considered.
    */
    val to: Int, 
    /**
     * Contains the id of the non-source node being considered.
    */
    val from: Int,
    /**
     * Contains the distance set by the source to be able to receive its messages.
    */
    val distanceForMessaging: Float,
    /**
     * Contains the distance between node in [to] value and node in [from] value.
    */
    val distance: Double, 
    /**
     * Is a boolean value indicating whether the identified messaging distance has been
     * communicated by a source node.
    */
    val isSourceValues: Boolean
)

/**
 * Extracts and validates message propagation data based on neighboring devices and spatial relationships.
 *
 * This function analyzes neighboring devices by computing the Euclidean distance from the current node
 * to each neighbor. It then produces a list of [SourceDistances] for each neighbor, capturing:
 * - the sender (source) ID,
 * - the receiver (current node) ID,
 * - the intended transmission radius (`distanceForMessaging`),
 * - the actual distance between the nodes,
 * - and a boolean flag (`isSourceValues`) indicating whether a valid message from a known sender
 *   is available for the current node.
 *
 * The [senders] parameter must include the `sourceCounter` as part of its triple to track multiple emissions
 * from the same sender. However, this function currently uses only the sender ID to validate
 * message availability and does not explicitly process the `sourceCounter` in its logic.
 *
 * After processing, the resulting map is filtered to retain only entries:
 * - where the sender is present in [senders],
 * - where the sender is not the current node (`localId`),
 * - and where `isSourceValues` is `true` and the `to` field matches the sender ID.
 *
 * @param devices A map associating device IDs with float values (usually distance metrics),
 *                used to determine which neighbors are within communication range.
 * @param position The 3D spatial position of the current node, used for distance evaluation.
 * @param senders A map of sender node IDs to a [Triple] of:
 *                - the sender’s propagation distance,
 *                - the message string,
 *                - the `sourceCounter` (number of emission events from that sender).
 *
 * @return A filtered map where each key is a valid sender ID and the value is a list of
 *         [SourceDistances] representing confirmed message propagation to the current node.
 */
fun Aggregate<Int>.saveNewMessage(
    devices:  Map<Int, Float>,
    position: Point3D,
    senders: Map<Int, Triple<Float, String, Int>>,
) : Map<Int, List<SourceDistances>> {
    return neighboring(devices).alignedMap(euclideanDistance3D(position)) {
        _: Int, deviceValues: Map<Int, Float>, distance: Double ->
        deviceValues.entries.map { (to, distanceForMessaging) ->
            SourceDistances(
                to,
                localId,
                distanceForMessaging,
                distance,
                senders.containsKey(to) &&
                distanceForMessaging != POSITIVE_INFINITY &&
                to != localId
            )
        }
    }.toMap()
        .filterKeys { senders.containsKey(it) && it != localId }
        .mapValues { (key, list) ->
            list.filter { it.isSourceValues && it.distance <= it.distanceForMessaging && it.to == key}
        }
}

/**
 * The dissemination occurs correctly; however, the storage mechanism encounters issues when
 * two devices are not directly connected. This is due to the reliance on the 'neighboring' function.
 * A later post-dissemination step for message storage should be implemented similarly.
 */
fun Aggregate<Int>.spreadNewMessage(
    incomingMessage: SourceDistances,
    from: Boolean,
    position: Point3D
) : SourceDistances {
    return gradientCast(
        source = from,
        local = incomingMessage,
        metric = euclideanDistance3D(position),
        accumulateData = { fromSource, toNeighbor, value ->
            val totalDistance = fromSource + toNeighbor + value.distance
            if (totalDistance <= value.distanceForMessaging.toDouble()) {
                SourceDistances(
                    value.to,
                    value.from,
                    value.distanceForMessaging,
                    totalDistance,
                    true
                )
            } else {
                SourceDistances(
                    localId,
                    localId,
                    Float.MAX_VALUE,
                    Double.MAX_VALUE,
                    false
                )
            }
        }
    )
}
